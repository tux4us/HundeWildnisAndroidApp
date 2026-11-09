package com.hundewildnis.app

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Erkennt Kommandos im laufenden Audiostrom (kein Android-Bezug).
 *
 * [feed] wird mit den PCM-Puffern des Pitch-Detektors (Eingangsrate [inputRate]) aufgerufen,
 * resampelt auf 16 kHz und teilt den Strom in Äußerungen: Eine Äußerung beginnt, wenn der
 * 10-ms-Pegel die Schwelle [gateDb] erreicht, und endet nach [END_SILENCE_MS] darunter oder
 * nach [MAX_UTTERANCE_MS]. Die Äußerung (mit 150 ms Vorlauf) wird in einem Hintergrund-Thread
 * mit den Vorlagen verglichen; das Ergebnis kommt über [listener] (nicht im Main-Thread).
 */
class CommandDetector(
    templates: List<CommandTemplate>,
    inputRate: Int,
    private val listener: (Recognition) -> Unit
) {

    /**
     * @param onsetMs    Beginn der Äußerung in ms seit dem ersten gelieferten Audio-Sample
     * @param durationMs Dauer der lauten Phase in ms
     */
    data class Recognition(val onsetMs: Long, val durationMs: Long, val result: MatchResult)

    companion object {
        const val RATE = MfccExtractor.SAMPLE_RATE
        const val DEFAULT_ACCEPT_THRESHOLD = 0.80f

        private const val HOP = MfccExtractor.HOP               // 10 ms
        private const val PRE_ROLL_MS = 150
        private const val POST_ROLL_MS = 150
        private const val END_SILENCE_MS = 350
        private const val MAX_UTTERANCE_MS = 4000
        private const val MIN_LOUD_FRAMES = 10                  // mindestens 100 ms über der Schwelle
        private const val MAX_PENDING_JOBS = 3

        private const val PRE_SAMPLES = RATE / 1000 * PRE_ROLL_MS
        private const val POST_SAMPLES = RATE / 1000 * POST_ROLL_MS
        private const val END_SILENCE_SAMPLES = RATE / 1000 * END_SILENCE_MS
        private const val MAX_UTTERANCE_SAMPLES = RATE / 1000 * MAX_UTTERANCE_MS
        private const val BUFFER_SIZE = PRE_SAMPLES + MAX_UTTERANCE_SAMPLES + 2 * HOP
    }

    /** Pegelschwelle (dBFS, 10-ms-Frames) für „es wird gesprochen“. */
    @Volatile var gateDb: Float = -50f

    /** Maximale DTW-Distanz für einen Treffer; wird bei jeder Äußerung neu gelesen. */
    @Volatile var acceptThreshold: Float = DEFAULT_ACCEPT_THRESHOLD

    private val matcher = CommandMatcher(templates)
    private val mfcc = MfccExtractor(RATE)                      // nur im Worker-Thread benutzt
    private val resampler = StreamResampler(inputRate, RATE)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "CommandMatcher").apply { isDaemon = true }
    }
    private val pendingJobs = AtomicInteger(0)

    private var resampled = ShortArray(0)

    // Frame-Aufbau (160 Samples = 10 ms)
    private val frame = ShortArray(HOP)
    private var frameFill = 0
    private var streamIndex = 0L                                // Index des nächsten Samples im 16-kHz-Strom

    // Vorlauf-Ringpuffer
    private val preRoll = ShortArray(PRE_SAMPLES)
    private var preWrite = 0
    private var preCount = 0

    // Aktuelle Äußerung
    private val buffer = ShortArray(BUFFER_SIZE)
    private var bufferLen = 0
    private var preLenInBuffer = 0
    private var inUtterance = false
    private var onsetIndex = 0L
    private var lastLoudEnd = 0
    private var loudFrames = 0

    val hasTemplates: Boolean get() = !matcher.isEmpty

    /** Aufruf aus dem Audio-Thread. [pcm] wird nicht behalten. */
    fun feed(pcm: ShortArray, n: Int) {
        if (n <= 0) return
        val needed = resampler.maxOutput(n)
        if (resampled.size < needed) resampled = ShortArray(needed)
        val m = resampler.process(pcm, n, resampled)

        for (i in 0 until m) {
            frame[frameFill++] = resampled[i]
            if (frameFill == HOP) {
                processFrame(streamIndex)
                streamIndex += HOP
                frameFill = 0
            }
        }
    }

    fun release() {
        executor.shutdownNow()
    }

    private fun processFrame(frameStart: Long) {
        var sum = 0.0
        for (i in 0 until HOP) {
            val s = frame[i] / 32768.0
            sum += s * s
        }
        val db = 20.0 * log10(max(sqrt(sum / HOP), 1e-6))
        val loud = db >= gateDb

        if (!inUtterance) {
            if (loud) startUtterance(frameStart) else pushPreRoll()
            return
        }

        if (bufferLen + HOP > BUFFER_SIZE) {
            finishUtterance()
            return
        }
        System.arraycopy(frame, 0, buffer, bufferLen, HOP)
        bufferLen += HOP
        if (loud) {
            lastLoudEnd = bufferLen
            loudFrames++
        }

        val silent = bufferLen - lastLoudEnd
        val speechLen = bufferLen - preLenInBuffer
        if (silent >= END_SILENCE_SAMPLES || speechLen >= MAX_UTTERANCE_SAMPLES) finishUtterance()
    }

    private fun pushPreRoll() {
        for (i in 0 until HOP) {
            preRoll[preWrite] = frame[i]
            preWrite = (preWrite + 1) % PRE_SAMPLES
        }
        preCount = min(PRE_SAMPLES, preCount + HOP)
    }

    private fun startUtterance(frameStart: Long) {
        // Vorlauf in zeitlicher Reihenfolge übernehmen
        val first = if (preCount < PRE_SAMPLES) 0 else preWrite
        for (i in 0 until preCount) {
            buffer[i] = preRoll[(first + i) % PRE_SAMPLES]
        }
        bufferLen = preCount
        preLenInBuffer = preCount
        preCount = 0
        preWrite = 0

        System.arraycopy(frame, 0, buffer, bufferLen, HOP)
        bufferLen += HOP
        onsetIndex = frameStart
        lastLoudEnd = bufferLen
        loudFrames = 1
        inUtterance = true
    }

    private fun finishUtterance() {
        if (loudFrames >= MIN_LOUD_FRAMES) {
            val end = min(bufferLen, lastLoudEnd + POST_SAMPLES)
            val segment = buffer.copyOfRange(0, end)
            val onsetMs = onsetIndex * 1000L / RATE
            val durationMs = (lastLoudEnd - preLenInBuffer).toLong() * 1000L / RATE
            submit(segment, onsetMs, durationMs)
        }
        inUtterance = false
        bufferLen = 0
        preLenInBuffer = 0
        loudFrames = 0
    }

    private fun submit(segment: ShortArray, onsetMs: Long, durationMs: Long) {
        if (pendingJobs.get() >= MAX_PENDING_JOBS) return
        pendingJobs.incrementAndGet()
        try {
            executor.execute {
                try {
                    val features = mfcc.compute(segment, 0, segment.size)
                    if (features.size >= 8) {
                        mfcc.normalize(features)
                        val result = matcher.match(features, acceptThreshold)
                        listener(Recognition(onsetMs, durationMs, result))
                    }
                } catch (e: Exception) {
                    // Eine fehlgeschlagene Äußerung darf die Messung nicht beeinträchtigen
                } finally {
                    pendingJobs.decrementAndGet()
                }
            }
        } catch (e: Exception) {
            pendingJobs.decrementAndGet() // Executor bereits beendet
        }
    }
}
