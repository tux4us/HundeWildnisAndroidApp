package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// BIOWETTER-ENGINE
// Datei: BiowetterEngine.kt
//
// Berechnet alle Biowetter-Indikatoren offline aus bereits
// vorhandenen Open-Meteo-Daten. Kein eigener API-Call nötig.
// Einzige Erweiterung im fetchWeatherData()-URL: "&hourly=cape"
//
// Wissenschaftliche Grundlagen:
// - Luftdrucktendenz: DWD, 3h-Delta < -3 hPa gilt als wetterfühligkeitsrelevant
// - Taupunkt: Magnus-Formel (Alduchov & Eskridge 1996)
// - Hitze-Index: Steadman (1979), US NWS Rothfusz-Regression
// - Schwüle: Taupunkt > 16°C = spürbar, > 20°C = belastend (DWD-Klassifikation)
// - CAPE: Nutzung des ThunderstormForecastEngine aus ThunderstormModels.kt
// ════════════════════════════════════════════════════════════════

/** Ampel-Stufen für alle Biowetter-Indikatoren */
enum class BioLevel(val color: Int) {
    NONE    (0xFF4CAF50.toInt()),   // grün – kein Einfluss
    LOW     (0xFF8BC34A.toInt()),   // hellgrün – geringer Einfluss
    MODERATE(0xFFFFC107.toInt()),   // gelb – mäßiger Einfluss
    HIGH    (0xFFFF9800.toInt()),   // orange – starker Einfluss
    EXTREME (0xFFF44336.toInt());   // rot – sehr starker Einfluss

    fun label(context: android.content.Context): String = when (this) {
        NONE     -> context.getString(R.string.biolevel_none)
        LOW      -> context.getString(R.string.biolevel_low)
        MODERATE -> context.getString(R.string.biolevel_moderate)
        HIGH     -> context.getString(R.string.biolevel_high)
        EXTREME  -> context.getString(R.string.biolevel_extreme)
    }
}

/** Ergebnis eines einzelnen Biowetter-Indikators */
data class BioIndicator(
    val name: String,
    val emoji: String,
    val level: BioLevel,
    val valueText: String,       // z.B. "-4,2 hPa/3h"
    val humanEffect: String,     // Auswirkung auf Mensch
    val dogEffect: String        // Auswirkung auf Hund / Training
)

/** Gesamtergebnis der Biowetter-Bewertung */
data class BiowetterResult(
    val indicators: List<BioIndicator>,
    val overallLevel: BioLevel,
    val overallSummary: String,
    val trainingAdvice: String
)

object BiowetterEngine {

    /**
     * Hauptfunktion: wertet alle Indikatoren aus und gibt ein Gesamtergebnis zurück.
     *
     * @param pressureHourly  Luftdruckwerte der letzten Stunden (hPa), zeitlich aufsteigend
     * @param tempC           aktuelle Temperatur (°C)
     * @param relHumidityPct  aktuelle relative Luftfeuchtigkeit (%)
     * @param windSpeedKmh    aktuelle Windgeschwindigkeit (km/h)
     * @param capeJPerKg      CAPE-Wert aktuell (J/kg), -1 wenn nicht verfügbar
     * @param weatherCode     aktueller Open-Meteo WMO-Code
     * @param precipProb      Niederschlagswahrscheinlichkeit aktuell (%)
     */
    fun evaluate(
        context: android.content.Context,
        pressureHourly: List<Double>,
        tempC: Double,
        relHumidityPct: Int,
        windSpeedKmh: Double,
        capeJPerKg: Double,
        weatherCode: Int,
        precipProb: Int
    ): BiowetterResult {

        val indicators = mutableListOf<BioIndicator>()

        // ── 1. Wetterfühligkeit (Luftdrucktendenz 3h) ─────────────────────
        val pressureTendency = calcPressureTendency3h(pressureHourly)
        indicators.add(buildPressureIndicator(context, pressureTendency))

        // ── 2. Schwüle (Taupunkt) ──────────────────────────────────────────
        val dewPoint = calcDewPoint(tempC, relHumidityPct)
        indicators.add(buildHumidityIndicator(context, dewPoint, tempC))

        // ── 3. Thermische Belastung (Hitze-Index oder Windchill) ──────────
        indicators.add(buildThermalIndicator(context, tempC, relHumidityPct, windSpeedKmh))

        // ── 4. Konvektionspotenzial (CAPE / Gewitterpotenzial) ─────────────
        if (capeJPerKg >= 0) {
            val forecastLevel = ThunderstormForecastEngine.classify(
                capeJPerKg, precipProb, weatherCode
            )
            indicators.add(buildCapeIndicator(context, capeJPerKg, forecastLevel))
        }

        // ── Gesamteinstufung: höchste Einzelstufe ─────────────────────────
        val overallLevel = indicators.maxByOrNull { it.level.ordinal }?.level ?: BioLevel.NONE

        val (summary, advice) = buildOverallTexts(context, overallLevel, indicators)

        return BiowetterResult(
            indicators    = indicators,
            overallLevel  = overallLevel,
            overallSummary = summary,
            trainingAdvice = advice
        )
    }

    // ════════════════════════════════════════════════════════════════
    // BERECHNUNGEN
    // ════════════════════════════════════════════════════════════════

    /**
     * Luftdrucktendenz der letzten 3 Stunden.
     * Gibt null zurück wenn weniger als 4 Messwerte vorliegen.
     * Vorzeichen: negativ = Druckabfall, positiv = Druckanstieg.
     */
    fun calcPressureTendency3h(pressureHourly: List<Double>): Double? {
        if (pressureHourly.size < 4) return null
        val recent = pressureHourly.last()
        val threeHoursAgo = pressureHourly[pressureHourly.size - 4]
        return recent - threeHoursAgo
    }

    /**
     * Taupunkt (°C) nach Magnus-Formel (Alduchov & Eskridge 1996).
     * Gültig für -40°C bis +60°C, Fehler < 0,1°C.
     */
    fun calcDewPoint(tempC: Double, relHumidityPct: Int): Double {
        val a = 17.625
        val b = 243.04
        val rh = relHumidityPct.toDouble() / 100.0
        val alpha = Math.log(rh) + (a * tempC) / (b + tempC)
        return (b * alpha) / (a - alpha)
    }

    /**
     * Hitze-Index (°C) nach US NWS Rothfusz-Regression.
     * Nur gültig ab 27°C und 40% Luftfeuchtigkeit – darunter ist
     * thermische Belastung durch Kälte/Wind relevant (Windchill).
     */
    fun calcHeatIndex(tempC: Double, relHumidityPct: Int): Double {
        val T = tempC * 9.0 / 5.0 + 32.0   // Fahrenheit
        val R = relHumidityPct.toDouble()
        val hi = -42.379 + 2.04901523 * T + 10.14333127 * R -
                0.22475541 * T * R - 0.00683783 * T * T -
                0.05481717 * R * R + 0.00122874 * T * T * R +
                0.00085282 * T * R * R - 0.00000199 * T * T * R * R
        return (hi - 32.0) * 5.0 / 9.0     // zurück in Celsius
    }

    // ════════════════════════════════════════════════════════════════
    // INDIKATOREN AUFBAUEN
    // ════════════════════════════════════════════════════════════════

    private fun buildPressureIndicator(context: android.content.Context, tendency: Double?): BioIndicator {
        if (tendency == null) {
            return BioIndicator(
                name        = context.getString(R.string.biowetter_pressure_name),
                emoji       = "🌡️",
                level       = BioLevel.NONE,
                valueText   = context.getString(R.string.biowetter_not_enough_data),
                humanEffect = context.getString(R.string.biowetter_no_evaluation_possible),
                dogEffect   = context.getString(R.string.biowetter_no_evaluation_possible)
            )
        }
        val absT = Math.abs(tendency)
        val level = when {
            absT < 1.0  -> BioLevel.NONE
            absT < 3.0  -> BioLevel.LOW
            absT < 6.0  -> BioLevel.MODERATE
            absT < 10.0 -> BioLevel.HIGH
            else        -> BioLevel.EXTREME
        }
        val direction = if (tendency < 0) context.getString(R.string.biowetter_pressure_drop) else context.getString(R.string.biowetter_pressure_rise)
        val valueText = context.getString(R.string.biowetter_pressure_value, String.format("%.1f", tendency), direction)

        val humanEffect = when (level) {
            BioLevel.NONE     -> context.getString(R.string.biowetter_pressure_human_none)
            BioLevel.LOW      -> context.getString(R.string.biowetter_pressure_human_low)
            BioLevel.MODERATE -> if (tendency < 0)
                context.getString(R.string.biowetter_pressure_human_moderate_drop)
            else
                context.getString(R.string.biowetter_pressure_human_moderate_rise)
            BioLevel.HIGH     -> if (tendency < 0)
                context.getString(R.string.biowetter_pressure_human_high_drop)
            else
                context.getString(R.string.biowetter_pressure_human_high_rise)
            BioLevel.EXTREME  -> context.getString(R.string.biowetter_pressure_human_extreme)
        }

        val dogEffect = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                context.getString(R.string.biowetter_pressure_dog_none_low)
            BioLevel.MODERATE ->
                context.getString(R.string.biowetter_pressure_dog_moderate)
            BioLevel.HIGH ->
                context.getString(R.string.biowetter_pressure_dog_high)
            BioLevel.EXTREME ->
                context.getString(R.string.biowetter_pressure_dog_extreme)
        }

        return BioIndicator(context.getString(R.string.biowetter_pressure_name), "🌡️", level, valueText, humanEffect, dogEffect)
    }

    private fun buildHumidityIndicator(context: android.content.Context, dewPoint: Double, tempC: Double): BioIndicator {
        val level = when {
            dewPoint < 10  -> BioLevel.NONE
            dewPoint < 16  -> BioLevel.LOW
            dewPoint < 20  -> BioLevel.MODERATE
            dewPoint < 24  -> BioLevel.HIGH
            else           -> BioLevel.EXTREME
        }
        val schwueleText = when (level) {
            BioLevel.NONE     -> context.getString(R.string.biowetter_humidity_none)
            BioLevel.LOW      -> context.getString(R.string.biowetter_humidity_low)
            BioLevel.MODERATE -> context.getString(R.string.biowetter_humidity_moderate)
            BioLevel.HIGH     -> context.getString(R.string.biowetter_humidity_high)
            BioLevel.EXTREME  -> context.getString(R.string.biowetter_humidity_extreme)
        }
        val valueText = context.getString(R.string.biowetter_humidity_value, String.format("%.1f", dewPoint), schwueleText)

        val humanEffect = when (level) {
            BioLevel.NONE     -> context.getString(R.string.biowetter_humidity_human_none)
            BioLevel.LOW      -> context.getString(R.string.biowetter_humidity_human_low)
            BioLevel.MODERATE -> context.getString(R.string.biowetter_humidity_human_moderate)
            BioLevel.HIGH     -> context.getString(R.string.biowetter_humidity_human_high)
            BioLevel.EXTREME  -> context.getString(R.string.biowetter_humidity_human_extreme)
        }

        val dogEffect = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                context.getString(R.string.biowetter_humidity_dog_none_low)
            BioLevel.MODERATE ->
                context.getString(R.string.biowetter_humidity_dog_moderate)
            BioLevel.HIGH ->
                context.getString(R.string.biowetter_humidity_dog_high)
            BioLevel.EXTREME ->
                context.getString(R.string.biowetter_humidity_dog_extreme)
        }

        return BioIndicator(context.getString(R.string.biowetter_humidity_name), "💧", level, valueText, humanEffect, dogEffect)
    }

    private fun buildThermalIndicator(context: android.content.Context, tempC: Double, relHumidityPct: Int, windSpeedKmh: Double): BioIndicator {
        return when {
            tempC >= 27 && relHumidityPct >= 40 -> {
                // Hitze-Index-Regime
                val hi = calcHeatIndex(tempC, relHumidityPct)
                val level = when {
                    hi < 27 -> BioLevel.NONE
                    hi < 32 -> BioLevel.LOW
                    hi < 38 -> BioLevel.MODERATE
                    hi < 44 -> BioLevel.HIGH
                    else    -> BioLevel.EXTREME
                }
                val valueText = context.getString(R.string.biowetter_heat_index_value, String.format("%.1f", hi))
                val humanEffect = when (level) {
                    BioLevel.NONE     -> context.getString(R.string.biowetter_heat_human_none)
                    BioLevel.LOW      -> context.getString(R.string.biowetter_heat_human_low)
                    BioLevel.MODERATE -> context.getString(R.string.biowetter_heat_human_moderate)
                    BioLevel.HIGH     -> context.getString(R.string.biowetter_heat_human_high)
                    BioLevel.EXTREME  -> context.getString(R.string.biowetter_heat_human_extreme)
                }
                val dogEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> context.getString(R.string.biowetter_heat_dog_none_low)
                    BioLevel.MODERATE -> context.getString(R.string.biowetter_heat_dog_moderate)
                    BioLevel.HIGH     -> context.getString(R.string.biowetter_heat_dog_high)
                    BioLevel.EXTREME  -> context.getString(R.string.biowetter_heat_dog_extreme)
                }
                BioIndicator(context.getString(R.string.biowetter_heat_name), "🌡️", level, valueText, humanEffect, dogEffect)
            }
            tempC <= 10 && windSpeedKmh >= 10 -> {
                // Windchill-Regime
                val wc = 13.12 + 0.6215 * tempC - 11.37 * Math.pow(windSpeedKmh, 0.16) +
                        0.3965 * tempC * Math.pow(windSpeedKmh, 0.16)
                val level = when {
                    wc > 0   -> BioLevel.NONE
                    wc > -10 -> BioLevel.LOW
                    wc > -20 -> BioLevel.MODERATE
                    wc > -30 -> BioLevel.HIGH
                    else     -> BioLevel.EXTREME
                }
                val valueText = context.getString(R.string.biowetter_windchill_value, String.format("%.1f", wc))
                val humanEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> context.getString(R.string.biowetter_cold_human_none_low)
                    BioLevel.MODERATE -> context.getString(R.string.biowetter_cold_human_moderate)
                    BioLevel.HIGH     -> context.getString(R.string.biowetter_cold_human_high)
                    BioLevel.EXTREME  -> context.getString(R.string.biowetter_cold_human_extreme)
                }
                val dogEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> context.getString(R.string.biowetter_cold_dog_none_low)
                    BioLevel.MODERATE -> context.getString(R.string.biowetter_cold_dog_moderate)
                    BioLevel.HIGH     -> context.getString(R.string.biowetter_cold_dog_high)
                    BioLevel.EXTREME  -> context.getString(R.string.biowetter_cold_dog_extreme)
                }
                BioIndicator(context.getString(R.string.biowetter_cold_name), "❄️", level, valueText, humanEffect, dogEffect)
            }
            else -> BioIndicator(
                name        = context.getString(R.string.biowetter_thermal_neutral_name),
                emoji       = "🌡️",
                level       = BioLevel.NONE,
                valueText   = context.getString(R.string.biowetter_thermal_neutral_value),
                humanEffect = context.getString(R.string.biowetter_thermal_neutral_human),
                dogEffect   = context.getString(R.string.biowetter_thermal_neutral_dog)
            )
        }
    }

    private fun buildCapeIndicator(
        context: android.content.Context,
        capeJPerKg: Double,
        forecastLevel: ThunderstormForecastLevel
    ): BioIndicator {
        val level = when (forecastLevel) {
            ThunderstormForecastLevel.LOW      -> BioLevel.NONE
            ThunderstormForecastLevel.MODERATE -> BioLevel.MODERATE
            ThunderstormForecastLevel.HIGH     -> BioLevel.HIGH
        }
        val valueText = context.getString(R.string.biowetter_cape_value, String.format("%.0f", capeJPerKg), forecastLevel.displayName(context))
        val humanEffect = when (level) {
            BioLevel.NONE     -> context.getString(R.string.biowetter_cape_human_none)
            BioLevel.MODERATE -> context.getString(R.string.biowetter_cape_human_moderate)
            else              -> context.getString(R.string.biowetter_cape_human_high)
        }
        val dogEffect = when (level) {
            BioLevel.NONE     -> context.getString(R.string.biowetter_cape_dog_none)
            BioLevel.MODERATE -> context.getString(R.string.biowetter_cape_dog_moderate)
            else              -> context.getString(R.string.biowetter_cape_dog_high)
        }
        return BioIndicator(context.getString(R.string.biowetter_cape_name), "⛈️", level, valueText, humanEffect, dogEffect)
    }

    // ════════════════════════════════════════════════════════════════
    // GESAMTBEWERTUNG
    // ════════════════════════════════════════════════════════════════

    private fun buildOverallTexts(
        context: android.content.Context,
        level: BioLevel,
        indicators: List<BioIndicator>
    ): Pair<String, String> {
        val activeFactors = indicators.filter { it.level.ordinal >= BioLevel.MODERATE.ordinal }
            .joinToString(", ") { it.name }

        val summary = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                context.getString(R.string.biowetter_summary_none_low)
            BioLevel.MODERATE ->
                context.getString(R.string.biowetter_summary_moderate, activeFactors)
            BioLevel.HIGH ->
                context.getString(R.string.biowetter_summary_high, activeFactors)
            BioLevel.EXTREME ->
                context.getString(R.string.biowetter_summary_extreme, activeFactors)
        }

        val advice = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                context.getString(R.string.biowetter_advice_none_low)
            BioLevel.MODERATE ->
                context.getString(R.string.biowetter_advice_moderate)
            BioLevel.HIGH ->
                context.getString(R.string.biowetter_advice_high)
            BioLevel.EXTREME ->
                context.getString(R.string.biowetter_advice_extreme)
        }

        return Pair(summary, advice)
    }
}
