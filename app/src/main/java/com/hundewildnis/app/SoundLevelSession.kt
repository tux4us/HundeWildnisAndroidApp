package com.hundewildnis.app

/**
 * Einzelner Pegel-Messpunkt einer Sitzung.
 */
data class SoundLevelPoint(
    val timestamp: Long = 0L,      // Zeitstempel relativ zum Session-Start (ms)
    val estimatedDb: Float = 0f    // Geschätzter Pegel in dB(A)
)

/**
 * Eine komplette Pegel-Trainings-Sitzung (Stufe A der fachlichen Klassifikation
 * für Stimmarbeit mit Hunden: akustische Orientierung per Smartphone).
 */
data class SoundLevelSession(
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val totalDuration: Long = 0L,          // Gesamtdauer in ms

    val avgEstimatedDb: Float = 0f,
    val minEstimatedDb: Float = 0f,
    val maxEstimatedDb: Float = 0f,

    // Anzahl Datenpunkte je Sprechweise-Stufe (Reihenfolge A1–A4)
    val countA1: Int = 0,
    val countA2: Int = 0,
    val countA3: Int = 0,
    val countA4: Int = 0,

    val dataPoints: List<SoundLevelPoint> = emptyList()
) {
    /** Stufe mit den meisten Datenpunkten dieser Sitzung. */
    fun getDominantLevel(): SpeechLevel {
        val counts = mapOf(
            SpeechLevel.A1_LEISE to countA1,
            SpeechLevel.A2_GESPRAECH to countA2,
            SpeechLevel.A3_ANGEHOBEN to countA3,
            SpeechLevel.A4_SCHARF to countA4
        )
        return counts.maxByOrNull { it.value }?.key ?: SpeechLevel.A2_GESPRAECH
    }

    fun getTotalPoints(): Int = countA1 + countA2 + countA3 + countA4

    /** Prozentualer Anteil einer Stufe an allen Datenpunkten dieser Sitzung. */
    fun getPercentage(count: Int): Int {
        val total = getTotalPoints()
        if (total == 0) return 0
        return (count.toFloat() / total.toFloat() * 100f).toInt()
    }
}
