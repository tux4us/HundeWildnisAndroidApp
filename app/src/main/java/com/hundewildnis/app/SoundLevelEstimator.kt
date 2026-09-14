package com.hundewildnis.app

import kotlin.math.log10

/**
 * Schätzt einen dB(A)-Wert aus dem RMS-Amplitudensignal des Mikrofons und
 * klassifiziert ihn nach Sprechweise-Stufen (Stufe A der fachlichen
 * Klassifikation für Stimmarbeit mit Hunden).
 *
 * WICHTIG - fachliche Einordnung:
 * Ein Smartphone-Mikrofon ist kein Schallpegelmesser der Klasse 1 oder 2
 * nach DIN EN 61672. Der hier berechnete Wert ist eine geraete- und
 * abstandsabhaengige Schaetzung, keine kalibrierte Messung. Er wird erst
 * durch eine einmalige Nutzerkalibrierung (Referenzsprechen auf definiertem
 * Abstand) grob vergleichbar. Ohne Kalibrierung darf kein dB-Wert angezeigt
 * werden (siehe isCalibrated-Pruefung in SoundLevelActivity).
 *
 * Rechenweg:
 * 1) RMS-Amplitude (0.0-1.0, wie von SimplePitchDetector.calculateAmplitude
 *    geliefert, inklusive dessen 30x-Verstaerkung) wird zurueck auf eine
 *    reine RMS-Groesse normiert.
 * 2) dBFS = 20 * log10(rms), typischerweise negativ.
 * 3) geschaetztes dB(A) = dBFS + Kalibrierungs-Offset.
 * 4) Klassifikation nach den Grenzen 60 / 70 / 80 dB(A) analog zur
 *    fachlichen Vorlage (A1 leise/getragen, A2 Gespraechspegel,
 *    A3 angehoben/fuehrend, A4 scharf/rufend).
 */
object SoundLevelEstimator {

    /** Muss mit der Verstärkung in SimplePitchDetector.calculateAmplitude übereinstimmen. */
    private const val AMPLITUDE_GAIN_FACTOR = 30.0

    /** Untere/obere Kappung für die geschätzte dB(A)-Anzeige, um Ausreißer abzufangen. */
    private const val DB_MIN = 30f
    private const val DB_MAX = 110f

    /** Zielwert, auf den die Kalibrierung den Referenzpegel normiert (Mitte A2 = Gesprächspegel). */
    const val CALIBRATION_TARGET_DB = 60f

    /**
     * Schätzt den dB(A)-Wert aus der (bereits verstärkten) RMS-Amplitude.
     * Gibt null zurück, wenn amplitude zu klein für eine sinnvolle log-Berechnung ist (Stille).
     */
    fun estimateDb(amplitude: Float, calibrationOffsetDb: Float): Float? {
        if (amplitude <= 0f) return null
        val rawRms = amplitude / AMPLITUDE_GAIN_FACTOR
        if (rawRms <= 0.0000001) return null

        val dBFS = 20.0 * log10(rawRms.toDouble())
        val estimated = dBFS.toFloat() + calibrationOffsetDb
        return estimated.coerceIn(DB_MIN, DB_MAX)
    }

    /**
     * Berechnet aus einer Referenzmessung (Nutzer spricht normal auf definiertem Abstand)
     * den Kalibrierungs-Offset, sodass estimateDb() für dieselbe Amplitude künftig
     * ungefähr CALIBRATION_TARGET_DB liefert.
     */
    fun calculateCalibrationOffset(referenceAmplitude: Float): Float? {
        if (referenceAmplitude <= 0f) return null
        val rawRms = referenceAmplitude / AMPLITUDE_GAIN_FACTOR
        if (rawRms <= 0.0000001) return null

        val dBFS = 20.0 * log10(rawRms.toDouble())
        return (CALIBRATION_TARGET_DB - dBFS.toFloat())
    }

    fun classify(db: Float): SpeechLevel {
        return when {
            db < 60f -> SpeechLevel.A1_LEISE
            db < 70f -> SpeechLevel.A2_GESPRAECH
            db < 80f -> SpeechLevel.A3_ANGEHOBEN
            else -> SpeechLevel.A4_SCHARF
        }
    }
}

/**
 * Sprechweise-Stufen nach fachlicher Vorlage (Stufe A - akustisch, nur Orientierung).
 * Die Grenzwerte sind KEINE Norm, sondern dienen dazu, die eigene Sprechweise
 * über mehrere Sessions vergleichbar zu machen.
 */
enum class SpeechLevel(
    val rangeLabel: String
) {
    A1_LEISE("< 60 dB(A)"),
    A2_GESPRAECH("60–70 dB(A)"),
    A3_ANGEHOBEN("70–80 dB(A)"),
    A4_SCHARF("> 80 dB(A)");

    fun label(context: android.content.Context): String = when (this) {
        A1_LEISE -> context.getString(R.string.speech_level_a1_label)
        A2_GESPRAECH -> context.getString(R.string.speech_level_a2_label)
        A3_ANGEHOBEN -> context.getString(R.string.speech_level_a3_label)
        A4_SCHARF -> context.getString(R.string.speech_level_a4_label)
    }

    fun description(context: android.content.Context): String = when (this) {
        A1_LEISE -> context.getString(R.string.speech_level_a1_desc)
        A2_GESPRAECH -> context.getString(R.string.speech_level_a2_desc)
        A3_ANGEHOBEN -> context.getString(R.string.speech_level_a3_desc)
        A4_SCHARF -> context.getString(R.string.speech_level_a4_desc)
    }
}
