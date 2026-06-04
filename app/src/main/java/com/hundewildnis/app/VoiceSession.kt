package com.hundewildnis.app

/**
 * Repräsentiert einen einzelnen Frequenz-Messpunkt
 */
data class FrequencyPoint(
    val timestamp: Long,      // Zeitstempel relativ zum Session-Start (ms)
    val frequency: Float,     // Gemessene Frequenz in Hz
    val amplitude: Float      // Lautstärke (0.0 - 1.0)
)

/**
 * Zielfrequenz mit Namen und Bedeutung
 */
data class TargetFrequency(
    val hz: Float,
    val name: String,
    val description: String
) {
    companion object {
        val FREQUENCY_108 = TargetFrequency(108f, "Tiefenentspannung", "Ruhe & Erdung")
        val FREQUENCY_216 = TargetFrequency(216f, "Beruhigung", "Entspannung & Vertrauen")
        val FREQUENCY_324 = TargetFrequency(324f, "Aufmerksamkeit", "Fokus & Konzentration")
        val FREQUENCY_432 = TargetFrequency(432f, "Harmonie", "Balance & Verbindung")
        val FREQUENCY_648 = TargetFrequency(648f, "Motivation", "Aktivierung & Freude")

        val ALL = listOf(FREQUENCY_108, FREQUENCY_216, FREQUENCY_324, FREQUENCY_432, FREQUENCY_648)
    }
}

/**
 * Eine komplette Kommunikations-Session
 */
data class VoiceSession(
    val startTime: Long,                    // Zeitstempel Start
    val endTime: Long,                      // Zeitstempel Ende
    val mode: TrainingMode,                 // Trainingsmodus
    val focusFrequency: TargetFrequency?,   // Nur bei Fokus-Training

    // Statistische Daten
    val totalDuration: Long,                // Gesamtdauer in ms
    val voiceDuration: Long,                // Reine Sprechdauer in ms
    val pauseDuration: Long,                // Pausenzeit in ms

    // Treffer pro Frequenz
    val hits108Hz: Int,
    val hits216Hz: Int,
    val hits324Hz: Int,
    val hits432Hz: Int,
    val hits648Hz: Int,

    // Verweildauer in Toleranzbereich (±5 Hz) in ms
    val time108Hz: Long,
    val time216Hz: Long,
    val time324Hz: Long,
    val time432Hz: Long,
    val time648Hz: Long,

    // Frequenz-Statistiken
    val avgFrequency: Float,                // Durchschnittsfrequenz
    val minFrequency: Float,                // Niedrigste Frequenz
    val maxFrequency: Float,                // Höchste Frequenz

    // Detaillierte Kurvendaten (für PDF-Export & Detail-Ansicht)
    val dataPoints: List<FrequencyPoint>
) {
    /**
     * Effizienz: Prozent der Sprechzeit in harmonischen Bereichen
     */
    fun getEfficiency(): Float {
        if (voiceDuration == 0L) return 0f
        val totalHarmonicTime = time108Hz + time216Hz + time324Hz + time432Hz + time648Hz
        return (totalHarmonicTime.toFloat() / voiceDuration.toFloat() * 100f)
    }

    /**
     * Beste Frequenz (längste Verweildauer)
     */
    fun getBestFrequency(): TargetFrequency {
        val times = mapOf(
            TargetFrequency.FREQUENCY_108 to time108Hz,
            TargetFrequency.FREQUENCY_216 to time216Hz,
            TargetFrequency.FREQUENCY_324 to time324Hz,
            TargetFrequency.FREQUENCY_432 to time432Hz,
            TargetFrequency.FREQUENCY_648 to time648Hz
        )
        return times.maxByOrNull { it.value }?.key ?: TargetFrequency.FREQUENCY_432
    }
}

/**
 * Trainingsmodus
 */
enum class TrainingMode {
    FREE,       // Freies Training (alle 4 Frequenzen)
    FOCUS       // Fokus-Training (eine Frequenz)
}