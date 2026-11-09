package com.hundewildnis.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Kommandoerkennung per Vorlagenvergleich (kein Android-Bezug, keine externen Bibliotheken):
 *
 *   PCM 16 kHz → MFCC (12 Koeffizienten, 25 ms Fenster, 10 ms Schritt) → Mittelwert-/Varianz-
 *   Normalisierung je Äußerung → Subsequence-DTW gegen die vom Nutzer eingesprochenen Vorlagen.
 *
 * Dieselbe Datei enthält Resampler (Eingang 22,05 kHz des Pitch-Detektors → 16 kHz).
 */

/**
 * MFCC-Extraktion. Nicht thread-sicher (interne Arbeitspuffer): je Thread eine Instanz.
 */
class MfccExtractor(private val sampleRate: Int = SAMPLE_RATE) {

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME = 400          // 25 ms
        const val HOP = 160            // 10 ms
        const val NFFT = 512
        const val NMEL = 26
        const val NCEP = 12            // c1..c12 (c0 = Gesamtenergie entfällt bewusst)
        private const val LOW_HZ = 100.0
        private const val HIGH_HZ = 7000.0
        private const val PRE_EMPHASIS = 0.97
        private const val MIN_STD = 0.25
    }

    private val bins = NFFT / 2 + 1
    private val window = DoubleArray(FRAME) { 0.54 - 0.46 * cos(2.0 * PI * it / (FRAME - 1)) }
    private val melFilters: Array<DoubleArray> = buildMelFilters()
    private val dct = Array(NCEP) { k -> DoubleArray(NMEL) { m -> cos(PI * (k + 1) * (m + 0.5) / NMEL) } }
    private val cosTable = DoubleArray(NFFT / 2) { cos(2.0 * PI * it / NFFT) }
    private val sinTable = DoubleArray(NFFT / 2) { -sin(2.0 * PI * it / NFFT) }
    private val bitReverse = IntArray(NFFT)

    private val re = DoubleArray(NFFT)
    private val im = DoubleArray(NFFT)
    private val logMel = DoubleArray(NMEL)

    init {
        val bits = Integer.numberOfTrailingZeros(NFFT)
        for (i in 0 until NFFT) {
            bitReverse[i] = Integer.reverse(i) ushr (32 - bits)
        }
    }

    private fun hzToMel(f: Double) = 2595.0 * log10(1.0 + f / 700.0)
    private fun melToHz(m: Double) = 700.0 * (10.0.pow(m / 2595.0) - 1.0)

    private fun buildMelFilters(): Array<DoubleArray> {
        val lowMel = hzToMel(LOW_HZ)
        val highMel = hzToMel(HIGH_HZ)
        val points = DoubleArray(NMEL + 2) { melToHz(lowMel + (highMel - lowMel) * it / (NMEL + 1)) }
        return Array(NMEL) { m ->
            DoubleArray(bins) { k ->
                val f = k * sampleRate.toDouble() / NFFT
                val p0 = points[m]
                val p1 = points[m + 1]
                val p2 = points[m + 2]
                when {
                    f > p0 && f <= p1 -> (f - p0) / (p1 - p0)
                    f > p1 && f < p2 -> (p2 - f) / (p2 - p1)
                    else -> 0.0
                }
            }
        }
    }

    private fun fft() {
        for (i in 0 until NFFT) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= NFFT) {
            val half = size / 2
            val step = NFFT / size
            var start = 0
            while (start < NFFT) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val tr = re[j + half] * wr - im[j + half] * wi
                    val ti = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - tr
                    im[j + half] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    /**
     * MFCC-Matrix (Frames × [NCEP]) für pcm[from, to). Leer, wenn weniger als [FRAME] Samples.
     */
    fun compute(pcm: ShortArray, from: Int, to: Int): Array<FloatArray> {
        val len = to - from
        if (len < FRAME) return emptyArray()
        val frames = 1 + (len - FRAME) / HOP
        val out = Array(frames) { FloatArray(NCEP) }

        for (f in 0 until frames) {
            val s = from + f * HOP
            java.util.Arrays.fill(re, 0.0)
            java.util.Arrays.fill(im, 0.0)
            for (i in 0 until FRAME) {
                val cur = pcm[s + i] / 32768.0
                val prev = if (s + i > from) pcm[s + i - 1] / 32768.0 else 0.0
                re[i] = (cur - PRE_EMPHASIS * prev) * window[i]
            }
            fft()
            for (m in 0 until NMEL) {
                val w = melFilters[m]
                var e = 0.0
                for (k in 0 until bins) {
                    val wk = w[k]
                    if (wk != 0.0) e += wk * (re[k] * re[k] + im[k] * im[k])
                }
                logMel[m] = ln(max(e, 1e-10))
            }
            for (k in 0 until NCEP) {
                var c = 0.0
                val row = dct[k]
                for (m in 0 until NMEL) c += row[m] * logMel[m]
                out[f][k] = c.toFloat()
            }
        }
        return out
    }

    /**
     * Mittelwert- und Varianznormalisierung je Koeffizient über die gesamte Äußerung (in place).
     * Entfernt Lautstärke- und Kanaleinflüsse, die bei Aufnahme und Messung verschieden sind.
     */
    fun normalize(frames: Array<FloatArray>) {
        if (frames.isEmpty()) return
        val n = frames.size
        for (k in 0 until NCEP) {
            var mean = 0.0
            for (f in frames) mean += f[k]
            mean /= n
            var variance = 0.0
            for (f in frames) {
                val x = f[k] - mean
                variance += x * x
            }
            val std = max(sqrt(variance / n), MIN_STD)
            for (f in frames) f[k] = ((f[k] - mean) / std).toFloat()
        }
    }
}

/**
 * Subsequence-DTW: sucht die Vorlage an beliebiger Stelle innerhalb einer längeren Äußerung
 * (freier Anfang und freies Ende in der Äußerung, Vorlage vollständig).
 */
object Dtw {

    /** Aufschlag für nicht-diagonale Schritte; verhindert, dass die Vorlage auf wenige Frames kollabiert. */
    private const val OFF_DIAGONAL_PENALTY = 0.1f

    private fun distance(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum / a.size)
    }

    /**
     * Normierte Kosten (mittlerer Abstand je Pfadschritt). Kleiner = ähnlicher.
     * Bei zufälligen Paaren liegt der Wert bei ca. 1,4, bei guten Treffern deutlich darunter.
     */
    fun subsequenceCost(template: Array<FloatArray>, utterance: Array<FloatArray>): Float {
        val m = template.size
        val n = utterance.size
        if (m == 0 || n < m / 2) return Float.POSITIVE_INFINITY

        var prevD = FloatArray(n)
        var prevL = IntArray(n)
        var curD = FloatArray(n)
        var curL = IntArray(n)

        for (j in 0 until n) {
            prevD[j] = distance(template[0], utterance[j])
            prevL[j] = 1
        }
        for (i in 1 until m) {
            curD[0] = prevD[0] + distance(template[i], utterance[0]) + OFF_DIAGONAL_PENALTY
            curL[0] = prevL[0] + 1
            for (j in 1 until n) {
                val d = distance(template[i], utterance[j])
                var best = prevD[j - 1]                 // diagonal
                var len = prevL[j - 1]
                val vertical = prevD[j] + OFF_DIAGONAL_PENALTY
                if (vertical < best) { best = vertical; len = prevL[j] }
                val horizontal = curD[j - 1] + OFF_DIAGONAL_PENALTY
                if (horizontal < best) { best = horizontal; len = curL[j - 1] }
                curD[j] = best + d
                curL[j] = len + 1
            }
            val td = prevD; prevD = curD; curD = td
            val tl = prevL; prevL = curL; curL = tl
        }

        var best = Float.POSITIVE_INFINITY
        for (j in 0 until n) {
            val c = prevD[j] / prevL[j]
            if (c < best) best = c
        }
        return best
    }
}

/** Normalisierte Merkmale einer Aufnahme eines Kommandos. */
class CommandTemplate(val commandId: String, val name: String, val features: Array<FloatArray>) {
    companion object {
        /** Erzeugt eine Vorlage aus einer 16-kHz-Aufnahme. Null, wenn die Aufnahme zu kurz ist. */
        fun fromPcm(commandId: String, name: String, pcm: ShortArray, mfcc: MfccExtractor): CommandTemplate? {
            val features = mfcc.compute(pcm, 0, pcm.size)
            if (features.size < 8) return null
            mfcc.normalize(features)
            return CommandTemplate(commandId, name, features)
        }
    }
}

data class MatchResult(
    val accepted: Boolean,
    val bestId: String?,
    val bestName: String?,
    val bestDistance: Float,
    val secondName: String?,
    val secondDistance: Float
)

class CommandMatcher(private val templates: List<CommandTemplate>) {

    companion object {
        /** Mindestabstand zwischen bestem und zweitbestem Kommando, sonst gilt der Treffer als unsicher. */
        const val MIN_MARGIN = 0.05f
    }

    val isEmpty: Boolean get() = templates.isEmpty()

    /**
     * Distanz je Kommando = Mittel der zwei besten Vorlagen (bei nur einer Vorlage deren Wert).
     * Angenommen wird das beste Kommando, wenn seine Distanz ≤ [acceptThreshold] ist und das
     * zweitbeste Kommando mindestens [MIN_MARGIN] schlechter liegt.
     */
    fun match(utterance: Array<FloatArray>, acceptThreshold: Float): MatchResult {
        val perCommand = LinkedHashMap<String, MutableList<Float>>()
        val names = HashMap<String, String>()
        for (t in templates) {
            perCommand.getOrPut(t.commandId) { mutableListOf() }
                .add(Dtw.subsequenceCost(t.features, utterance))
            names[t.commandId] = t.name
        }

        val ranked = perCommand.map { (id, costs) ->
            val sorted = costs.sorted()
            val distance = if (sorted.size >= 2) (sorted[0] + sorted[1]) / 2f else sorted[0]
            Triple(id, names[id] ?: "", distance)
        }.sortedBy { it.third }

        if (ranked.isEmpty() || ranked[0].third.isInfinite()) {
            return MatchResult(false, null, null, Float.POSITIVE_INFINITY, null, Float.POSITIVE_INFINITY)
        }
        val best = ranked[0]
        val second = ranked.getOrNull(1)
        val secondDistance = second?.third ?: Float.POSITIVE_INFINITY
        val accepted = best.third <= acceptThreshold && secondDistance - best.third >= MIN_MARGIN
        return MatchResult(accepted, best.first, best.second, best.third, second?.second, secondDistance)
    }
}

/**
 * Streaming-Resampler (lineare Interpolation) mit 3-Tap-Tiefpass gegen Aliasing.
 * Der Tiefpass dämpft 5 kHz um ca. 5 dB; das ist ein konstanter Pegelversatz je Frequenzband
 * und wird durch die Mittelwertnormalisierung der MFCC entfernt.
 */
class StreamResampler(inRate: Int, outRate: Int) {

    private val step = inRate.toDouble() / outRate
    private var pos = 1.0
    private var prevFiltered = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var tmp = DoubleArray(0)

    /** Maximale Anzahl Ausgabesamples für [inputSamples] Eingabesamples. */
    fun maxOutput(inputSamples: Int): Int = (inputSamples / step).toInt() + 3

    /** Gibt die Anzahl geschriebener Samples in [out] zurück. */
    fun process(input: ShortArray, n: Int, out: ShortArray): Int {
        if (n <= 0) return 0
        if (tmp.size < n + 1) tmp = DoubleArray(n + 1)
        tmp[0] = prevFiltered
        for (i in 0 until n) {
            val x = input[i].toDouble()
            tmp[i + 1] = 0.25 * x + 0.5 * x1 + 0.25 * x2
            x2 = x1
            x1 = x
        }
        var written = 0
        while (pos < n && written < out.size) {
            val k = floor(pos).toInt()
            val frac = pos - k
            val v = tmp[k] * (1.0 - frac) + tmp[k + 1] * frac
            out[written++] = v.toInt().coerceIn(-32768, 32767).toShort()
            pos += step
        }
        pos -= n
        prevFiltered = tmp[n]
        return written
    }
}
