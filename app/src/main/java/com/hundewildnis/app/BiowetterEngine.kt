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

    val label: String get() = when (this) {
        NONE     -> "Kein Einfluss"
        LOW      -> "Geringer Einfluss"
        MODERATE -> "Mäßiger Einfluss"
        HIGH     -> "Starker Einfluss"
        EXTREME  -> "Sehr starker Einfluss"
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
        indicators.add(buildPressureIndicator(pressureTendency))

        // ── 2. Schwüle (Taupunkt) ──────────────────────────────────────────
        val dewPoint = calcDewPoint(tempC, relHumidityPct)
        indicators.add(buildHumidityIndicator(dewPoint, tempC))

        // ── 3. Thermische Belastung (Hitze-Index oder Windchill) ──────────
        indicators.add(buildThermalIndicator(tempC, relHumidityPct, windSpeedKmh))

        // ── 4. Konvektionspotenzial (CAPE / Gewitterpotenzial) ─────────────
        if (capeJPerKg >= 0) {
            val forecastLevel = ThunderstormForecastEngine.classify(
                capeJPerKg, precipProb, weatherCode
            )
            indicators.add(buildCapeIndicator(capeJPerKg, forecastLevel))
        }

        // ── Gesamteinstufung: höchste Einzelstufe ─────────────────────────
        val overallLevel = indicators.maxByOrNull { it.level.ordinal }?.level ?: BioLevel.NONE

        val (summary, advice) = buildOverallTexts(overallLevel, indicators)

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

    private fun buildPressureIndicator(tendency: Double?): BioIndicator {
        if (tendency == null) {
            return BioIndicator(
                name        = "Wetterfühligkeit",
                emoji       = "🌡️",
                level       = BioLevel.NONE,
                valueText   = "Nicht genug Daten",
                humanEffect = "Keine Auswertung möglich.",
                dogEffect   = "Keine Auswertung möglich."
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
        val direction = if (tendency < 0) "Druckabfall" else "Druckanstieg"
        val valueText = "${String.format("%.1f", tendency)} hPa / 3h ($direction)"

        val humanEffect = when (level) {
            BioLevel.NONE     -> "Stabiler Luftdruck. Kein wetterfühligkeitsbedingter Einfluss erwartet."
            BioLevel.LOW      -> "Leichter Druckwechsel. Sehr empfindliche Personen bemerken möglicherweise leichte Müdigkeit."
            BioLevel.MODERATE -> if (tendency < 0)
                "Merklicher Druckabfall. Häufig: Kopfschmerzen, erhöhte Reizbarkeit, Konzentrationsschwäche – besonders bei wetterfühligen Personen, Migränepatienten und Personen mit alten Verletzungen."
            else
                "Merklicher Druckanstieg. Manche Personen reagieren mit Schlafstörungen oder innerer Anspannung."
            BioLevel.HIGH     -> if (tendency < 0)
                "Starker Druckabfall. Wetterfühligkeit ausgeprägt wahrscheinlich: Migräne, Gelenkschmerzen, Narben-/Wundschmerzen, Kreislaufprobleme möglich. Ruhepausen einplanen."
            else
                "Starker Druckanstieg. Manche Personen berichten von Kopfdruck, Ohrenpfeifen, gesteigerter Anspannung."
            BioLevel.EXTREME  -> "Extremer Druckwechsel. Körperlich belastend, besonders für chronisch kranke, ältere oder wetterempfindliche Personen. Heute Belastungen reduzieren."
        }

        val dogEffect = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                "Keine Auswirkungen auf das Training erwartet."
            BioLevel.MODERATE ->
                "Hunde registrieren Druckveränderungen oft früher als Menschen und reagieren mit erhöhter Aufmerksamkeit oder leichter Unruhe. Wenn Du selbst wetterfühlig bist: kürzere Einheiten, keine Präzisionsarbeit."
            BioLevel.HIGH ->
                "Erhöhte Sensibilität wahrscheinlich – sowohl beim Hund als auch beim Menschen. Dein eigener Stresslevel beeinflusst die Trainingsqualität direkt. Heute Routineübungen statt neuer Anforderungen. Ruhige Geländerunden bevorzugen."
            BioLevel.EXTREME ->
                "Extremer Wetterwechsel. Hunde können deutlich unruhiger und schwerer führbar sein. Auf Zwang und intensive Trainingsanforderungen heute verzichten. Nur kurze, entspannte Runden."
        }

        return BioIndicator("Wetterfühligkeit", "🌡️", level, valueText, humanEffect, dogEffect)
    }

    private fun buildHumidityIndicator(dewPoint: Double, tempC: Double): BioIndicator {
        val level = when {
            dewPoint < 10  -> BioLevel.NONE
            dewPoint < 16  -> BioLevel.LOW
            dewPoint < 20  -> BioLevel.MODERATE
            dewPoint < 24  -> BioLevel.HIGH
            else           -> BioLevel.EXTREME
        }
        val schwüleText = when (level) {
            BioLevel.NONE     -> "Angenehm trocken"
            BioLevel.LOW      -> "Leicht feucht, angenehm"
            BioLevel.MODERATE -> "Spürbar schwül"
            BioLevel.HIGH     -> "Deutlich schwül"
            BioLevel.EXTREME  -> "Extrem schwül"
        }
        val valueText = "Taupunkt ${String.format("%.1f", dewPoint)}°C – $schwüleText"

        val humanEffect = when (level) {
            BioLevel.NONE     -> "Die Luft fühlt sich trocken und angenehm an. Kein Schwüleeinfluss."
            BioLevel.LOW      -> "Angenehme Luftfeuchtigkeit. Kein nennenswerter Einfluss auf das Wohlbefinden."
            BioLevel.MODERATE -> "Spürbare Schwüle. Schwitzen kühlt weniger effektiv. Erhöhtes Durstgefühl, Leistungsfähigkeit leicht reduziert bei körperlicher Aktivität."
            BioLevel.HIGH     -> "Deutliche Schwüle. Thermoregulation des Körpers eingeschränkt. Kreislauf stärker belastet. Auf ausreichend Trinken achten, intensive Aktivitäten besser in kühlere Stunden verlegen."
            BioLevel.EXTREME  -> "Extreme Schwüle. Hitzeerschöpfung möglich. Körperliche Aktivitäten stark einschränken. Viel trinken, Schatten aufsuchen, Körpertemperatur beobachten."
        }

        val dogEffect = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                "Keine schwülebedingten Einschränkungen für Training oder Aktivität."
            BioLevel.MODERATE ->
                "Hunde können bei Schwüle weniger effektiv über die Zunge kühlen. Tempotraining und intensives Apportieren heute reduzieren. Trinkpausen einplanen."
            BioLevel.HIGH ->
                "Hunde sind bei hoher Schwüle deutlich überhitzungsgefährdeter als bei gleicher Temperatur bei trockener Luft. Intensive Belastung heute meiden, kurze Runden, viel Wasser, Schatten. Brachycephale Rassen (Bulldogge, Mops, Französische Bulldogge) besonders gefährdet."
            BioLevel.EXTREME ->
                "Extrem schwüle Bedingungen – akute Überhitzungsgefahr für Hunde. Körperliche Aktivität auf das Minimum (kurze Gassi-Runden) begrenzen. Zeichen für Hitzschlag: starkes Hecheln, Sabberfluss, Koordinationsstörungen, Apathie → sofort kühlen und Tierarzt aufsuchen."
        }

        return BioIndicator("Schwüle", "💧", level, valueText, humanEffect, dogEffect)
    }

    private fun buildThermalIndicator(tempC: Double, relHumidityPct: Int, windSpeedKmh: Double): BioIndicator {
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
                val valueText = "Hitze-Index: ${String.format("%.1f", hi)}°C (gefühlt)"
                val humanEffect = when (level) {
                    BioLevel.NONE     -> "Angenehme Wärme. Kein Hitzestress."
                    BioLevel.LOW      -> "Leichter Hitzestress bei anhaltender körperlicher Aktivität möglich."
                    BioLevel.MODERATE -> "Mäßiger Hitzestress. Viel trinken, Mittagshitze meiden. Kreislauf-empfindliche Personen besonders achtsam sein."
                    BioLevel.HIGH     -> "Starker Hitzestress. Intensive körperliche Aktivitäten meiden, im Schatten aufhalten, ausreichend trinken. Hitzekrämpfe und -erschöpfung möglich."
                    BioLevel.EXTREME  -> "Extremer Hitzestress. Gefahr von Hitzschlag. Aktivitäten auf ein Minimum reduzieren."
                }
                val dogEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> "Auf Trinkwasser achten. Kurze Pausen einplanen."
                    BioLevel.MODERATE -> "Training auf frühe Morgen- oder Abendstunden verlegen. Asphalt meiden – kann Pfoten verbrennen. Viel Wasser."
                    BioLevel.HIGH     -> "Keine intensiven Trainingseinheiten. Kürzeste Runden, maximaler Schatten. Körpertemperatur und Hecheln beobachten."
                    BioLevel.EXTREME  -> "Hund nur für Notdurft raus. Akute Hitzschlaggefahr. Brachycephale Rassen, ältere und kranke Hunde sind besonders gefährdet."
                }
                BioIndicator("Wärmebelastung", "🌡️", level, valueText, humanEffect, dogEffect)
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
                val valueText = "Windchill: ${String.format("%.1f", wc)}°C (gefühlt)"
                val humanEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> "Kühle Bedingungen, kein kritischer Kältestress."
                    BioLevel.MODERATE -> "Spürbarer Kältestress. Warme Kleidung notwendig, besonders Hände, Kopf und Füße schützen."
                    BioLevel.HIGH     -> "Starke Kältebelastung. Erfrierungsrisiko bei Windexposition ohne Schutzkleidung. Aufenthalt begrenzen."
                    BioLevel.EXTREME  -> "Extreme Kältegefahr. Erfrierungen möglich innerhalb von Minuten auf unbedeckter Haut."
                }
                val dogEffect = when (level) {
                    BioLevel.NONE, BioLevel.LOW -> "Kein kritischer Kältestress für gesunde Hunde mit Unterwolle."
                    BioLevel.MODERATE -> "Kurzhaarige, kleine und alte Hunde: Mantel empfohlen. Pfoten auf Salz und Eis prüfen."
                    BioLevel.HIGH     -> "Alle Hunde: Runden kurz halten. Kurzhaarige Rassen: Mantel notwendig. Pfoten nach der Runde reinigen und auf Risse prüfen."
                    BioLevel.EXTREME  -> "Lebensgefährliche Kälte auch für Hunde. Nur für Notdurft raus, so kurz wie möglich."
                }
                BioIndicator("Kältebelastung", "❄️", level, valueText, humanEffect, dogEffect)
            }
            else -> BioIndicator(
                name        = "Thermische Belastung",
                emoji       = "🌡️",
                level       = BioLevel.NONE,
                valueText   = "Angenehmer Temperaturbereich",
                humanEffect = "Keine thermische Sonderbelastung. Angenehme Bedingungen für Aktivitäten im Freien.",
                dogEffect   = "Sehr gute Bedingungen für Training und Freilandrunden."
            )
        }
    }

    private fun buildCapeIndicator(
        capeJPerKg: Double,
        forecastLevel: ThunderstormForecastLevel
    ): BioIndicator {
        val level = when (forecastLevel) {
            ThunderstormForecastLevel.LOW      -> BioLevel.NONE
            ThunderstormForecastLevel.MODERATE -> BioLevel.MODERATE
            ThunderstormForecastLevel.HIGH     -> BioLevel.HIGH
        }
        val valueText = "CAPE: ${String.format("%.0f", capeJPerKg)} J/kg – ${forecastLevel.displayName}"
        val humanEffect = when (level) {
            BioLevel.NONE     -> "Geringes Gewitterpotenzial. Kein Einfluss auf Wohlbefinden."
            BioLevel.MODERATE -> "Erhöhtes Gewitterpotenzial. Atmosphärische Instabilität kann Unruhe und Unwohlsein verstärken, besonders bei gewitterempfindlichen Personen."
            else              -> "Hohes Gewitterpotenzial. Wetterbedingte Stimmungsschwankungen und Kreislaufbeschwerden möglich. Rückweg früh einplanen."
        }
        val dogEffect = when (level) {
            BioLevel.NONE     -> "Kein Einfluss auf Training erwartet."
            BioLevel.MODERATE -> "Viele Hunde reagieren auf atmosphärische Instabilität vor Gewittern mit erhöhter Unruhe oder Ängstlichkeit – auch ohne sichtbare Wetterzeichen. Leine als Sicherheitsbasis beibehalten."
            else              -> "Gewitterangst: Hunde können Druckveränderungen und elektrostatische Aufladung vor Gewittern wahrnehmen. Runde zeitlich begrenzen, sicheres Rückzugsquartier bereithalten. Ängstliche Hunde nicht ableinen."
        }
        return BioIndicator("Gewitterpotenzial", "⛈️", level, valueText, humanEffect, dogEffect)
    }

    // ════════════════════════════════════════════════════════════════
    // GESAMTBEWERTUNG
    // ════════════════════════════════════════════════════════════════

    private fun buildOverallTexts(
        level: BioLevel,
        indicators: List<BioIndicator>
    ): Pair<String, String> {
        val activeFactors = indicators.filter { it.level.ordinal >= BioLevel.MODERATE.ordinal }
            .joinToString(", ") { it.name }

        val summary = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                "Günstige Biowetterlage. Keine nennenswerten meteorologischen Einflüsse auf Mensch und Hund."
            BioLevel.MODERATE ->
                "Mäßige Biowetterbelastung durch: $activeFactors. Beobachte Dich und Deinen Hund aufmerksam."
            BioLevel.HIGH ->
                "Erhöhte Biowetterbelastung durch: $activeFactors. Trainingsplanung anpassen, Pausen einplanen."
            BioLevel.EXTREME ->
                "Starke Biowetterbelastung durch: $activeFactors. Heute Belastungen für Mensch und Hund deutlich reduzieren."
        }

        val advice = when (level) {
            BioLevel.NONE, BioLevel.LOW ->
                "✅ Gute Bedingungen für Training. Alle Übungen wie geplant möglich."
            BioLevel.MODERATE ->
                "⚡ Moderate Belastung: Präzisionsübungen und neue Anforderungen eher meiden. Entspannte Geländerunden bevorzugen. Auf Stresssignale des Hundes achten."
            BioLevel.HIGH ->
                "⚠️ Erhöhte Belastung: Trainingseinheiten heute kürzen und Anforderungen deutlich reduzieren. Dein eigenes Wohlbefinden beeinflusst den Hund direkt – Stress überträgt sich. Ruhige Spaziergänge statt Training."
            BioLevel.EXTREME ->
                "🚨 Starke Belastung: Nur Notfallrunden. Kein Training heute. Mensch und Hund brauchen heute Erholung statt Leistung."
        }

        return Pair(summary, advice)
    }
}
