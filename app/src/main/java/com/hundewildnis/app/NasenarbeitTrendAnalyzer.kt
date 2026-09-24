package com.hundewildnis.app

import android.content.Context

/**
 * NasenarbeitTrendAnalyzer
 *
 * Wertet die gespeicherten Sessions EINES Hundes statistisch aus und leitet
 * daraus textuelle Trend-Hinweise ab (kein KI/LLM – rein regelbasierte
 * Aggregation, analog zum Aufbau von NasenarbeitScorer).
 *
 * Läuft ausschließlich auf bereits vorhandenen Daten
 * (NasenarbeitManager.loadSessionsForDog) – kein neues Speicherformat.
 */
object NasenarbeitTrendAnalyzer {

    /** Ab dieser Gesamt-Session-Anzahl werden überhaupt Trends berechnet. */
    const val MIN_SESSIONS_TOTAL = 5

    /** Ab dieser Anzahl Sessions in einer Vergleichsgruppe wird die Gruppe gewertet. */
    private const val MIN_SESSIONS_PER_GROUP = 3

    /** Wie viele der jüngsten Sessions für die Plateau-Erkennung betrachtet werden. */
    private const val PLATEAU_WINDOW = 5

    enum class InsightSeverity { POSITIVE, INFO, WARNING }

    data class TrendInsight(
        val text: String,
        val severity: InsightSeverity,
        val disziplin: NasenarbeitDisziplin? = null
    )

    data class TrendResult(
        val insights: List<TrendInsight>,
        val sessionCount: Int
    ) {
        val hasEnoughData: Boolean get() = sessionCount >= MIN_SESSIONS_TOTAL
    }

    fun analyze(context: Context, sessions: List<NasenarbeitSession>): TrendResult {
        // Defensiver Filter: erfolgsbewertung ist beim Speichern (NasenarbeitErgebnisActivity)
        // per .coerceAtLeast(1) immer 1-4, ein Wert von 0 sollte also nicht vorkommen.
        // Der Filter schützt trotzdem gegen ältere/importierte Daten außerhalb dieses Bereichs.
        val bewertete = sessions.filter { it.erfolgsbewertung in 1..4 }

        if (sessions.size < MIN_SESSIONS_TOTAL) {
            return TrendResult(emptyList(), sessions.size)
        }

        val insights = mutableListOf<TrendInsight>()
        insights += analyzeBodenKorrelation(context, bewertete)
        insights += analyzeEmpfehlungVsErgebnis(context, bewertete)
        insights += analyzeLevelPlateau(context, sessions)
        insights += analyzeWindKorrelation(context, bewertete)

        return TrendResult(insights, sessions.size)
    }

    // ════════════════════════════════════════════════════════════
    // 1) BODEN-/BEDINGUNGS-KORRELATION
    // ════════════════════════════════════════════════════════════

    private fun analyzeBodenKorrelation(context: Context, sessions: List<NasenarbeitSession>): List<TrendInsight> {
        val result = mutableListOf<TrendInsight>()

        NasenarbeitDisziplin.values().forEach { diz ->
            val relevant = sessions.filter { it.durchgefuehrteDisziplin == diz }
            if (relevant.size < MIN_SESSIONS_TOTAL) return@forEach

            // Gruppierung nach dem Bodenzustand des ersten Geländeabschnitts der Session
            val gruppen = relevant.groupBy { it.parameter.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: "" }
                .filterKeys { it.isNotEmpty() }
                .filterValues { it.size >= MIN_SESSIONS_PER_GROUP }

            if (gruppen.size < 2) return@forEach

            val avgByGroup = gruppen.mapValues { (_, list) -> list.map { it.erfolgsbewertung }.average() }
            val best = avgByGroup.maxByOrNull { it.value } ?: return@forEach
            val worst = avgByGroup.minByOrNull { it.value } ?: return@forEach

            if (best.key == worst.key || (best.value - worst.value) < 0.7) return@forEach

            val bestLabel = bodenZustandLabel(context, best.key)
            val worstLabel = bodenZustandLabel(context, worst.key)
            val bestCount = gruppen[best.key]?.size ?: 0
            val worstCount = gruppen[worst.key]?.size ?: 0

            result += TrendInsight(
                text = context.getString(
                    R.string.nasenarbeit_trend_boden_korrelation,
                    diz.icon, diz.displayName(context),
                    bestLabel, formatStars(best.value), bestCount,
                    worstLabel, formatStars(worst.value), worstCount
                ),
                severity = InsightSeverity.INFO,
                disziplin = diz
            )
        }
        return result
    }

    // ════════════════════════════════════════════════════════════
    // 2) EMPFEHLUNG-VS-ERGEBNIS-ABGLEICH
    // ════════════════════════════════════════════════════════════

    private fun analyzeEmpfehlungVsErgebnis(context: Context, sessions: List<NasenarbeitSession>): List<TrendInsight> {
        if (sessions.size < MIN_SESSIONS_TOTAL) return emptyList()

        val gefolgt = sessions.filter { it.durchgefuehrteDisziplin == it.empfohleneDisziplin }
        val abgewichen = sessions.filter { it.durchgefuehrteDisziplin != it.empfohleneDisziplin }

        if (gefolgt.size < MIN_SESSIONS_PER_GROUP || abgewichen.size < MIN_SESSIONS_PER_GROUP) {
            return emptyList()
        }

        fun erfolgsQuote(list: List<NasenarbeitSession>): Double =
            list.count { it.erfolgsbewertung >= 3 }.toDouble() / list.size * 100.0

        val quoteGefolgt = erfolgsQuote(gefolgt)
        val quoteAbgewichen = erfolgsQuote(abgewichen)

        if (kotlin.math.abs(quoteGefolgt - quoteAbgewichen) < 15.0) return emptyList()

        return listOf(
            TrendInsight(
                text = context.getString(
                    R.string.nasenarbeit_trend_empfehlung_abgleich,
                    quoteGefolgt.toInt(), gefolgt.size,
                    quoteAbgewichen.toInt(), abgewichen.size
                ),
                severity = if (quoteGefolgt > quoteAbgewichen) InsightSeverity.POSITIVE else InsightSeverity.INFO
            )
        )
    }

    // ════════════════════════════════════════════════════════════
    // 3) LEVEL-PLATEAU-ERKENNUNG
    // ════════════════════════════════════════════════════════════

    private fun analyzeLevelPlateau(context: Context, allSessions: List<NasenarbeitSession>): List<TrendInsight> {
        val result = mutableListOf<TrendInsight>()
        val sortedDesc = allSessions.sortedByDescending { it.datum }

        NasenarbeitDisziplin.values().forEach { diz ->
            val letzte = sortedDesc.filter { it.durchgefuehrteDisziplin == diz }.take(PLATEAU_WINDOW)
            if (letzte.size < PLATEAU_WINDOW) return@forEach

            val level = when (diz) {
                NasenarbeitDisziplin.VERLORENSUCHE -> letzte.map { it.levelVerlorensuche }
                NasenarbeitDisziplin.PERSONENSUCHE -> letzte.map { it.levelPersonensuche }
                NasenarbeitDisziplin.FREISUCHE -> letzte.map { it.levelFreisuche }
            }
            val sameLevel = level.toSet().size == 1
            if (!sameLevel) return@forEach

            val bewertet = letzte.filter { it.erfolgsbewertung in 1..4 }
            if (bewertet.size < PLATEAU_WINDOW) return@forEach

            val avg = bewertet.map { it.erfolgsbewertung }.average()
            val levelName = NasenarbeitLevel.fromLegacyStoredName(level.first()).displayName(context)

            when {
                avg >= 3.0 -> result += TrendInsight(
                    text = context.getString(
                        R.string.nasenarbeit_trend_plateau_aufstieg,
                        diz.icon, diz.displayName(context), PLATEAU_WINDOW, levelName, formatStars(avg)
                    ),
                    severity = InsightSeverity.POSITIVE,
                    disziplin = diz
                )
                avg <= 2.0 -> result += TrendInsight(
                    text = context.getString(
                        R.string.nasenarbeit_trend_plateau_ueberforderung,
                        diz.icon, diz.displayName(context), PLATEAU_WINDOW, levelName, formatStars(avg)
                    ),
                    severity = InsightSeverity.WARNING,
                    disziplin = diz
                )
            }
        }
        return result
    }

    // ════════════════════════════════════════════════════════════
    // 4) WIND-KORRELATION (FREISUCHE)
    // ════════════════════════════════════════════════════════════

    private fun analyzeWindKorrelation(context: Context, sessions: List<NasenarbeitSession>): List<TrendInsight> {
        val relevant = sessions.filter { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.FREISUCHE }
        if (relevant.size < MIN_SESSIONS_TOTAL) return emptyList()

        fun windGruppe(bf: Int): String = when {
            bf <= 2 -> "schwach"
            bf <= 5 -> "mittel"
            else -> "stark"
        }

        val gruppen = relevant.groupBy { windGruppe(it.parameter.beaufort) }
            .filterValues { it.size >= MIN_SESSIONS_PER_GROUP }

        if (gruppen.size < 2) return emptyList()

        val avgByGroup = gruppen.mapValues { (_, list) -> list.map { it.erfolgsbewertung }.average() }
        val best = avgByGroup.maxByOrNull { it.value } ?: return emptyList()
        val worst = avgByGroup.minByOrNull { it.value } ?: return emptyList()

        if (best.key == worst.key || (best.value - worst.value) < 0.7) return emptyList()

        return listOf(
            TrendInsight(
                text = context.getString(
                    R.string.nasenarbeit_trend_wind_korrelation,
                    windLabel(context, best.key), formatStars(best.value),
                    windLabel(context, worst.key), formatStars(worst.value)
                ),
                severity = InsightSeverity.INFO,
                disziplin = NasenarbeitDisziplin.FREISUCHE
            )
        )
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private fun formatStars(avg: Double): String = String.format("%.1f", avg)

    private fun bodenZustandLabel(context: Context, code: String): String = when (code) {
        "trocken" -> context.getString(R.string.nasenarbeit_boden_zustand_trocken)
        "feucht" -> context.getString(R.string.nasenarbeit_boden_zustand_feucht)
        "nass" -> context.getString(R.string.nasenarbeit_boden_zustand_nass)
        "stehend" -> context.getString(R.string.nasenarbeit_boden_zustand_stehend)
        "matschig" -> context.getString(R.string.nasenarbeit_boden_zustand_matschig)
        "gefroren" -> context.getString(R.string.nasenarbeit_boden_zustand_gefroren)
        "schnee_l" -> context.getString(R.string.nasenarbeit_boden_zustand_schnee_l)
        "schnee_m" -> context.getString(R.string.nasenarbeit_boden_zustand_schnee_m)
        "schnee_h" -> context.getString(R.string.nasenarbeit_boden_zustand_schnee_h)
        else -> code
    }

    private fun windLabel(context: Context, gruppe: String): String = when (gruppe) {
        "schwach" -> context.getString(R.string.nasenarbeit_trend_wind_schwach)
        "mittel" -> context.getString(R.string.nasenarbeit_trend_wind_mittel)
        else -> context.getString(R.string.nasenarbeit_trend_wind_stark)
    }
}
