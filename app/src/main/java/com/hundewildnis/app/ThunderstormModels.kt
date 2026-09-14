package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// GEWITTERWARNER – Datenmodelle & Berechnungslogik
// Datei: ThunderstormModels.kt
//
// Enthält die reine Rechenlogik für den Blitz-Donner-Distanzrechner
// (Schallgeschwindigkeit) sowie die Risikoeinstufung aus Open-Meteo-
// Daten (CAPE-Wert, Niederschlagswahrscheinlichkeit, Wetter-Code).
// Erzeugt KEINE eigenen API-Calls – reine Auswertungsschicht,
// analog zu TerrainRecommendationEngine.
// ════════════════════════════════════════════════════════════════

/**
 * Schallgeschwindigkeit in Luft bei ca. 20°C.
 * Wird bewusst als Konstante angezeigt (Lernfunktion), nicht versteckt.
 */
const val SPEED_OF_SOUND_MPS = 343.0

/** Eine einzelne Blitz→Donner-Messung mit berechneter Distanz. */
data class LightningMeasurement(
    val timestampMillis: Long,
    val secondsUntilThunder: Double,
    val distanceKm: Double
)

/** Ampel-Einstufung nach Distanz zum Gewitter. */
enum class StormProximityLevel(val icon: String, val color: String) {
    SAFE("🟢", "#4CAF50"),
    CAUTION("🟠", "#FF9800"),
    DANGER("🔴", "#E64A19");

    fun displayName(context: android.content.Context): String = when (this) {
        SAFE -> context.getString(R.string.storm_proximity_level_safe)
        CAUTION -> context.getString(R.string.storm_proximity_level_caution)
        DANGER -> context.getString(R.string.storm_proximity_level_danger)
    }

    companion object {
        fun fromDistanceKm(km: Double): StormProximityLevel = when {
            km > 10.0 -> SAFE
            km >= 3.0 -> CAUTION
            else -> DANGER
        }
    }
}

/** Trend über mehrere Messungen: nähert sich, entfernt sich, oder unklar/stabil. */
enum class StormTrend(val icon: String) {
    APPROACHING("⬇️"),
    RECEDING("⬆️"),
    STABLE("➡️"),
    UNKNOWN("❔");

    fun displayName(context: android.content.Context): String = when (this) {
        APPROACHING -> context.getString(R.string.storm_trend_approaching)
        RECEDING -> context.getString(R.string.storm_trend_receding)
        STABLE -> context.getString(R.string.storm_trend_stable)
        UNKNOWN -> context.getString(R.string.storm_trend_unknown)
    }
}

/**
 * Ergebnis der Näherungsgeschwindigkeit-Berechnung.
 * ETA wird nur ausgewiesen, wenn sich das Gewitter nachweislich nähert
 * (speedKmh > 0) UND die aktuelle Distanz noch über der Sicherheitsschwelle
 * von 1 km liegt – darunter ist eine Ankunftszeit keine sinnvolle Information
 * mehr, dort zählt nur noch "sofort Schutz suchen".
 */
data class StormApproachEstimate(
    val speedKmh: Double,
    val currentDistanceKm: Double,
    val etaMinutes: Double?
)

/**
 * Reine Rechenlogik für den Blitz-Donner-Distanzrechner.
 * Jeder Schritt ist einzeln aufrufbar, damit die Activity den Rechenweg
 * transparent Schritt für Schritt anzeigen kann (Lernfunktion).
 */
object LightningCalculator {

    /** Unterhalb dieser Distanz wird keine ETA mehr ausgegeben – nur noch Sofortmaßnahmen. */
    const val ETA_MIN_DISTANCE_KM = 1.0

    /** Distanz in km aus der gestoppten Zeit (Sekunden) zwischen Blitz und Donner. */
    fun calculateDistanceKm(secondsUntilThunder: Double): Double {
        val distanceMeters = secondsUntilThunder * SPEED_OF_SOUND_MPS
        return distanceMeters / 1000.0
    }

    fun proximityLevel(distanceKm: Double): StormProximityLevel =
        StormProximityLevel.fromDistanceKm(distanceKm)

    /**
     * Ermittelt den Trend aus den letzten Messungen (zeitlich aufsteigend sortiert).
     * Vergleicht den Mittelwert der ersten Hälfte mit dem der zweiten Hälfte,
     * um einzelne Messungenauigkeiten (Reaktionszeit beim Tippen) zu glätten.
     */
    fun determineTrend(measurements: List<LightningMeasurement>): StormTrend {
        if (measurements.size < 2) return StormTrend.UNKNOWN

        val sorted = measurements.sortedBy { it.timestampMillis }
        val mid = sorted.size / 2
        if (mid == 0) return StormTrend.UNKNOWN

        val firstHalfAvg = sorted.take(mid).map { it.distanceKm }.average()
        val secondHalfAvg = sorted.takeLast(sorted.size - mid).map { it.distanceKm }.average()

        val delta = secondHalfAvg - firstHalfAvg
        // Schwelle von 0.3 km verhindert, dass Mess- bzw. Reaktionszeitrauschen
        // fälschlich als Trend interpretiert wird.
        return when {
            delta <= -0.3 -> StormTrend.APPROACHING
            delta >= 0.3 -> StormTrend.RECEDING
            else -> StormTrend.STABLE
        }
    }

    /**
     * Berechnet die Näherungsgeschwindigkeit aus erster und letzter Messung
     * der Serie sowie – falls das Gewitter sich nähert und noch mehr als
     * [ETA_MIN_DISTANCE_KM] entfernt ist – die theoretische Zeit bis zum Eintreffen.
     *
     * Rechenweg (bewusst einfach nachvollziehbar, Lernfunktion):
     *   Geschwindigkeit = zurückgelegte Distanz ÷ vergangene Zeit
     *   ETA = aktuelle Restdistanz ÷ Geschwindigkeit
     *
     * Gibt null zurück, wenn weniger als 2 Messungen vorliegen oder seit der
     * ersten und letzten Messung keine messbare Zeit vergangen ist.
     */
    fun estimateApproach(measurements: List<LightningMeasurement>): StormApproachEstimate? {
        if (measurements.size < 2) return null

        val sorted = measurements.sortedBy { it.timestampMillis }
        val first = sorted.first()
        val last = sorted.last()

        val elapsedHours = (last.timestampMillis - first.timestampMillis) / 3_600_000.0
        if (elapsedHours <= 0.0) return null

        val distanceCoveredKm = first.distanceKm - last.distanceKm
        val speedKmh = distanceCoveredKm / elapsedHours

        val currentDistanceKm = last.distanceKm
        val etaMinutes = if (speedKmh > 0.0 && currentDistanceKm > ETA_MIN_DISTANCE_KM) {
            val remainingDistanceKm = currentDistanceKm - ETA_MIN_DISTANCE_KM
            (remainingDistanceKm / speedKmh) * 60.0
        } else {
            null
        }

        return StormApproachEstimate(
            speedKmh = speedKmh,
            currentDistanceKm = currentDistanceKm,
            etaMinutes = etaMinutes
        )
    }

    /** Handlungsempfehlung passend zur Ampel-Stufe. */
    fun recommendationFor(context: android.content.Context, level: StormProximityLevel): String = when (level) {
        StormProximityLevel.SAFE -> context.getString(R.string.storm_recommendation_safe)
        StormProximityLevel.CAUTION -> context.getString(R.string.storm_recommendation_caution)
        StormProximityLevel.DANGER -> context.getString(R.string.storm_recommendation_danger)
    }
}

/** Risikoeinstufung aus Open-Meteo-Daten (CAPE, Niederschlag, Wetter-Code). */
enum class ThunderstormForecastLevel(val icon: String, val color: String) {
    LOW("🟢", "#4CAF50"),
    MODERATE("🟠", "#FF9800"),
    HIGH("🔴", "#E64A19");

    fun displayName(context: android.content.Context): String = when (this) {
        LOW -> context.getString(R.string.thunderstorm_forecast_level_low)
        MODERATE -> context.getString(R.string.thunderstorm_forecast_level_moderate)
        HIGH -> context.getString(R.string.thunderstorm_forecast_level_high)
    }
}

data class ThunderstormForecastHour(
    val timeLabel: String,
    val capeValue: Double,
    val precipProbabilityPercent: Int,
    val weatherCode: Int,
    val level: ThunderstormForecastLevel
)

/**
 * Bewertung der Gewittergefahr aus CAPE-Wert (convective available potential energy),
 * Niederschlagswahrscheinlichkeit und Wetter-Code der Open-Meteo-Stundenprognose.
 *
 * WICHTIG: CAPE beschreibt nur die theoretisch verfügbare Energie für Konvektion,
 * nicht ob diese Energie tatsächlich freigesetzt wird. An heißen, schwülen Tagen
 * kann CAPE auch bei stabilem, wolkenlosem Wetter auf sehr hohe Werte steigen, wenn
 * eine Inversionsschicht ("Deckel") die Auslösung verhindert – hoher CAPE-Wert
 * bedeutet also nicht automatisch ein bevorstehendes Gewitter. Daher wird CAPE
 * ausschließlich als Verstärker gewertet, niemals als alleiniger Auslöser für die
 * Stufe "Hoch": Diese wird nur erreicht, wenn zusätzlich der Wetter-Code bereits
 * ein Gewitter anzeigt, oder spürbarer bis starker Niederschlag (nicht bereits
 * leichter Regen/Nieselregen) vorhergesagt ist.
 *
 * Zusätzlich zu beachten: precipitation_probability wird von Open-Meteo aus einem
 * Ensemble-Modell mit ~27 km Gitterauflösung berechnet (siehe API-Dokumentation).
 * Bei lokal begrenzten Schauer-/Gewitterlagen ("einzelne Gewitter in der Region")
 * kann dieser Wert für die betroffene Gitterzelle spürbar erhöht sein, obwohl es
 * am exakten Standort trocken bleibt. Die Schwelle für "Niederschlagswahrschein-
 * lichkeit als alleiniger HIGH-Verstärker" ist deshalb bewusst hoch angesetzt.
 *
 * CAPE-Schwellen orientieren sich an gängigen meteorologischen Faustregeln:
 * < 1000 J/kg: geringes Gewitterpotenzial, 1000–2500 J/kg: erhöht, > 2500 J/kg: hoch
 * – jeweils nur als Potenzial, nicht als Vorhersage eines tatsächlichen Ereignisses.
 */
object ThunderstormForecastEngine {

    private val THUNDER_WEATHER_CODES = setOf(95, 96, 99)
    // Nur moderater bis starker Niederschlag zählt als "aktive Aktivität" für HIGH –
    // leichter Regen/Nieselregen (51,53,55,56,57,61,66,67) allein reicht dafür nicht aus,
    // auch nicht in Kombination mit hohem CAPE.
    private val MODERATE_TO_HEAVY_PRECIP_CODES = setOf(63, 65, 81, 82)

    fun classify(capeValue: Double, precipProbabilityPercent: Int, weatherCode: Int): ThunderstormForecastLevel {
        val isThunderCode = weatherCode in THUNDER_WEATHER_CODES
        val hasModerateToHeavyPrecip = weatherCode in MODERATE_TO_HEAVY_PRECIP_CODES

        return when {
            // Eindeutig: Wettermodell meldet bereits Gewitter
            isThunderCode -> ThunderstormForecastLevel.HIGH
            // Hohes CAPE nur dann "Hoch", wenn zusätzlich moderater/starker Niederschlag
            // vorhergesagt ist – leichter Regen oder reine Wahrscheinlichkeitswerte allein
            // reichen nicht, da diese bei grober Modellauflösung leicht überzeichnen können
            capeValue >= 2500.0 && hasModerateToHeavyPrecip -> ThunderstormForecastLevel.HIGH
            capeValue >= 1000.0 && hasModerateToHeavyPrecip -> ThunderstormForecastLevel.MODERATE
            capeValue >= 2500.0 -> ThunderstormForecastLevel.MODERATE
            capeValue >= 1000.0 && precipProbabilityPercent >= 50 -> ThunderstormForecastLevel.MODERATE
            precipProbabilityPercent >= 70 && capeValue >= 500.0 -> ThunderstormForecastLevel.MODERATE
            else -> ThunderstormForecastLevel.LOW
        }
    }
}