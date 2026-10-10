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
    val icon: String,
    val color: String,
    val maxGradePercent: Double
) {
    FLAT("🟢", "#4CAF50", 4.0),
    LIGHT("🟡", "#8BC34A", 8.0),
    MODERATE("🟠", "#FF9800", 15.0),
    DEMANDING("🔴", "#FF8A65", 25.0),
    ALPINE("⛰️", "#FF5C7C", Double.MAX_VALUE);

    fun displayName(context: android.content.Context): String = when (this) {
        FLAT -> context.getString(R.string.slope_class_flat)
        LIGHT -> context.getString(R.string.slope_class_light)
        MODERATE -> context.getString(R.string.slope_class_moderate)
        DEMANDING -> context.getString(R.string.slope_class_demanding)
        ALPINE -> context.getString(R.string.slope_class_alpine)
    }

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
enum class TerrainWarningLevel(val icon: String, val color: String) {
    CAUTION("⚠️", "#FF9800"),
    WARNING("🔴", "#FF8A65");

    fun displayName(context: android.content.Context): String = when (this) {
        CAUTION -> context.getString(R.string.terrain_warning_level_caution)
        WARNING -> context.getString(R.string.terrain_warning_level_warning)
    }
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
        context: android.content.Context,
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
                TerrainWarningLevel.WARNING, context.getString(R.string.terrain_warning_hot_asphalt_title),
                context.getString(R.string.terrain_warning_hot_asphalt_message, tempC.toInt(), asphaltPercent.toInt())
            ))
        } else if (tempC != null && tempC >= 22.0 && asphaltPercent >= 30.0) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, context.getString(R.string.terrain_warning_warm_ground_title),
                context.getString(R.string.terrain_warning_warm_ground_message)
            ))
        }

        if (isRain && (slopeClass == SlopeClass.DEMANDING || slopeClass == SlopeClass.ALPINE)) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, context.getString(R.string.terrain_warning_slip_risk_title),
                context.getString(R.string.terrain_warning_slip_risk_message, precipProbabilityPercent)
            ))
        } else if (isRain && slopeClass == SlopeClass.MODERATE) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, context.getString(R.string.terrain_warning_wet_terrain_title),
                context.getString(R.string.terrain_warning_wet_terrain_message, precipProbabilityPercent)
            ))
        }

        if (tempC != null && tempC >= 28.0) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, context.getString(R.string.terrain_warning_heat_warning_title),
                context.getString(R.string.terrain_warning_heat_warning_message, tempC.toInt())
            ))
        }

        if (windKmh != null && windKmh >= 40.0 &&
            (slopeClass == SlopeClass.DEMANDING || slopeClass == SlopeClass.ALPINE)) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.WARNING, context.getString(R.string.terrain_warning_strong_wind_title),
                context.getString(R.string.terrain_warning_strong_wind_message, windKmh.toInt())
            ))
        }

        if (slopeClass == SlopeClass.ALPINE) {
            warnings.add(TerrainWarning(
                TerrainWarningLevel.CAUTION, context.getString(R.string.terrain_warning_alpine_terrain_title),
                context.getString(R.string.terrain_warning_alpine_terrain_message)
            ))
        }

        return warnings
    }
}