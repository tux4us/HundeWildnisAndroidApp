package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// GELÄNDE-ANALYST – Auswertungslogik
// Datei: TerrainAnalysisModels.kt
//
// Wertet die bereits in RouteData vorhandenen Felder aus
// (elevationPoints, totalAscent, totalDescent, surfaceSummary)
// und kombiniert sie mit Wetterdaten zu Steigungsklasse,
// Warnungen und einer Streckenempfehlung.
//
// Erzeugt KEINE eigenen API-Calls – reine Auswertungsschicht.
// ════════════════════════════════════════════════════════════════

// ── Steigungsklasse ──────────────────────────────────────────────
enum class SlopeClass(
    val displayName: String,
    val icon: String,
    val color: String,
    val maxGradePercent: Double
) {
    FLAT("Flach", "🟢", "#4CAF50", 4.0),
    LIGHT("Leicht hügelig", "🟡", "#8BC34A", 8.0),
    MODERATE("Hügelig", "🟠", "#FF9800", 15.0),
    DEMANDING("Anspruchsvoll", "🔴", "#E64A19", 25.0),
    ALPINE("Alpin", "⛰️", "#7B0000", Double.MAX_VALUE);

    companion object {
        fun fromGrade(gradePercent: Double): SlopeClass = when {
            gradePercent <= FLAT.maxGradePercent      -> FLAT
            gradePercent <= LIGHT.maxGradePercent      -> LIGHT
            gradePercent <= MODERATE.maxGradePercent   -> MODERATE
            gradePercent <= DEMANDING.maxGradePercent  -> DEMANDING
            else                                       -> ALPINE
        }

        /** Klassifiziert direkt aus Distanz + Höhenmetern (Ascent oder Descent, größerer Wert) */
        fun fromRoute(distanceMeters: Double, ascent: Double, descent: Double): SlopeClass {
            if (distanceMeters <= 0.0) return FLAT
            val maxClimb = maxOf(ascent, descent)
            val grade = (maxClimb / distanceMeters) * 100.0
            return fromGrade(grade)
        }
    }
}

// ── Witterungsrisiko ──────────────────────────────────────────────
enum class TerrainWarningLevel(val displayName: String, val icon: String, val color: String) {
    CAUTION("Hinweis", "⚠️", "#FF9800"),
    WARNING("Warnung", "🔴", "#E64A19")
}

data class TerrainWarning(
    val level: TerrainWarningLevel,
    val title: String,
    val message: String
)

// ── Hundetyp-Empfehlung & Warnungs-Engine ────────────────────────
object TerrainRecommendationEngine {

    /**
     * Empfohlene maximale Streckenlänge in Metern – abhängig von
     * Steigungsklasse, Wesensmerkmalen und Körpergewicht des Hundes.
     */
    fun recommendMaxDistance(
        character: DogCharacter?,
        weightKg: Double?,
        slopeClass: SlopeClass
    ): Int {
        val base = when (slopeClass) {
            SlopeClass.FLAT      -> 8000
            SlopeClass.LIGHT     -> 6000
            SlopeClass.MODERATE  -> 4000
            SlopeClass.DEMANDING -> 2500
            SlopeClass.ALPINE    -> 1500
        }

        // Alle Anpassungsfaktoren werden gesammelt und gemeinsam gedeckelt,
        // damit sie sich nicht auf unrealistisch kleine Werte stapeln können.
        var combinedFactor = 1.0

        character?.let {
            when {
                it.temperament <= 2 -> combinedFactor *= 0.6
                it.temperament >= 4 -> combinedFactor *= 1.3
            }
            if (it.frustrationTolerance <= 2) combinedFactor *= 0.8
        }
        weightKg?.let {
            when {
                it < 8.0  -> combinedFactor *= 0.7
                it > 40.0 -> combinedFactor *= 0.85
            }
        }

        // Untergrenze: der kombinierte Dämpfungsfaktor darf die Basisdistanz
        // nie um mehr als die Hälfte reduzieren (verstärkende Faktoren > 1.0
        // bleiben davon unberührt).
        val cappedFactor = combinedFactor.coerceAtLeast(0.5)

        return (base * cappedFactor).toInt().coerceIn(500, 15000)
    }

    /**
     * Erstellt Warnungen aus Steigungsklasse, Asphaltanteil, Temperatur,
     * Niederschlagswahrscheinlichkeit und Wind.
     * Alle Parameter stammen aus bereits vorhandenen RouteData- und
     * Wetter-Werten in LocationActivity_POI – kein zusätzlicher Call.
     */
    fun buildWarnings(
        slopeClass: SlopeClass,
        asphaltPercent: Double,
        tempC: Double?,
        precipProbabilityPercent: Int?,
        windKmh: Double?
    ): List<TerrainWarning> {
        val warnings = mutableListOf<TerrainWarning>()
        val isRain = (precipProbabilityPercent ?: 0) >= 50

        if (tempC != null && tempC >= 25.0 && asphaltPercent >= 50.0) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, "Heißer Asphalt",
                "Bei ${tempC.toInt()} °C und ${asphaltPercent.toInt()} % Asphaltanteil besteht Verbrennungsgefahr an den Pfoten. Handrücken-Test vor dem Loslaufen empfohlen."
            ))
        } else if (tempC != null && tempC >= 22.0 && asphaltPercent >= 30.0) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, "Warmer Untergrund",
                "Asphaltanteil und Temperatur erhöht – Pfoten unterwegs kontrollieren."
            ))
        }

        if (isRain && (slopeClass == SlopeClass.DEMANDING || slopeClass == SlopeClass.ALPINE)) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, "Rutschgefahr",
                "Steiles Gelände in Kombination mit Regenwahrscheinlichkeit (${precipProbabilityPercent}%) erhöht das Rutsch- und Verletzungsrisiko erheblich."
            ))
        } else if (isRain && slopeClass == SlopeClass.MODERATE) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, "Nässe im Gelände",
                "Bei Regenwahrscheinlichkeit von ${precipProbabilityPercent}% auf rutschigen Abschnitten besonders vorsichtig sein."
            ))
        }

        if (tempC != null && tempC >= 28.0) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, "Hitzewarnung",
                "Bei ${tempC.toInt()} °C besteht Hitzschlaggefahr. Strecke kürzen, schattige Route wählen, ausreichend Wasser mitführen."
            ))
        }

        if (windKmh != null && windKmh >= 40.0 &&
            (slopeClass == SlopeClass.DEMANDING || slopeClass == SlopeClass.ALPINE)) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, "Starker Wind im Höhengelände",
                "${windKmh.toInt()} km/h Windgeschwindigkeit in exponiertem Gelände – erhöhtes Sturzrisiko."
            ))
        }

        if (slopeClass == SlopeClass.ALPINE) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, "Alpines Gelände",
                "Steile Passagen erfordern Trittsicherheit. Nicht für Welpen, Senioren oder gesundheitlich eingeschränkte Hunde geeignet."
            ))
        }

        return warnings
    }
}