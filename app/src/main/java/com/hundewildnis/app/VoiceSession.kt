package com.hundewildnis.app

/**
 * Repräsentiert einen einzelnen Frequenz-Messpunkt
 */
data class FrequencyPoint(
    val timestamp: Long = 0L,      // Zeitstempel relativ zum Session-Start (ms)
    val frequency: Float = 0f,     // Gemessene Frequenz in Hz
    val amplitude: Float = 0f      // Lautstärke (0.0 - 1.0)
)

/**
 * Zielfrequenz mit Namen und Bedeutung
 */
data class TargetFrequency(
    val hz: Float = 432f,
    val name: String = "",
    val description: String = ""
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
    val startTime: Long = 0L,                    // Zeitstempel Start
    val endTime: Long = 0L,                      // Zeitstempel Ende
    val mode: TrainingMode = TrainingMode.FREE,  // Trainingsmodus
    val focusFrequency: TargetFrequency? = null, // Nur bei Fokus-Training

    // Statistische Daten
    val totalDuration: Long = 0L,                // Gesamtdauer in ms
    val voiceDuration: Long = 0L,                // Reine Sprechdauer in ms
    val pauseDuration: Long = 0L,                // Pausenzeit in ms

    // Treffer pro Frequenz
    val hits108Hz: Int = 0,
    val hits216Hz: Int = 0,
    val hits324Hz: Int = 0,
    val hits432Hz: Int = 0,
    val hits648Hz: Int = 0,

    // Verweildauer in Toleranzbereich (±5 Hz) in ms
    val time108Hz: Long = 0L,
    val time216Hz: Long = 0L,
    val time324Hz: Long = 0L,
    val time432Hz: Long = 0L,
    val time648Hz: Long = 0L,

    // Frequenz-Statistiken
    val avgFrequency: Float = 0f,                // Durchschnittsfrequenz
    val minFrequency: Float = 0f,                // Niedrigste Frequenz
    val maxFrequency: Float = 0f,                // Höchste Frequenz

    // Detaillierte Kurvendaten (für PDF-Export & Detail-Ansicht)
    val dataPoints: List<FrequencyPoint> = emptyList(),

    // Während der Messung erkannte Kommandos (leer bei älteren Sessions oder ausgeschalteter Erkennung)
    val commandEvents: List<CommandEvent> = emptyList(),

    // Während der Messung per GPS zurückgelegte Strecke in Metern; -1 = nicht erfasst oder kein Fix
    val totalDistanceMeters: Float = -1f
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