package com.hundewildnis.app

/**
 * NasenarbeitScorer
 * Regelbasiertes Expertensystem zur Disziplinempfehlung
 * Alle 6 Stufen der Scoring-Logik
 */
object NasenarbeitScorer {

    // Discipline indices
    const val IDX_V = 0  // Verlorensuche
    const val IDX_P = 1  // Personensuche
    const val IDX_F = 2  // Freisuche

    data class ScoringResult(
        val scores: DoubleArray,              // [V, P, F]
        val suitability: IntArray,            // [V, P, F] 0=ok, 1=warn, 2=bad
        val empfohlen: List<NasenarbeitDisziplin>,
        val primaer: NasenarbeitDisziplin,
        val begruendung: String,
        val isHardExcluded: Boolean = false,
        val hardExclusionReason: String = ""
    )

    // ── WMO CODES → SUITABILITY [V, P, F] ─────────────────────
    private val WMO_SUIT = mapOf(
        0 to intArrayOf(0,0,0), 1 to intArrayOf(0,0,0), 2 to intArrayOf(0,0,0), 3 to intArrayOf(0,0,0),
        45 to intArrayOf(0,0,2), 48 to intArrayOf(0,0,2),
        51 to intArrayOf(0,0,0), 53 to intArrayOf(0,0,0), 55 to intArrayOf(0,0,0),
        56 to intArrayOf(0,0,0), 57 to intArrayOf(2,0,0),
        61 to intArrayOf(0,0,0), 63 to intArrayOf(0,0,0), 65 to intArrayOf(2,0,0),
        66 to intArrayOf(0,0,0), 67 to intArrayOf(2,0,0),
        71 to intArrayOf(0,0,0), 73 to intArrayOf(0,0,0), 75 to intArrayOf(2,0,0),
        77 to intArrayOf(0,0,0),
        80 to intArrayOf(0,0,0), 81 to intArrayOf(0,0,0), 82 to intArrayOf(0,0,0),
        85 to intArrayOf(0,0,0), 86 to intArrayOf(2,1,0),
        95 to intArrayOf(2,2,2), 96 to intArrayOf(2,2,2), 99 to intArrayOf(2,2,2)
    )

    // ── BEAUFORT → SUITABILITY per level [A, F, E, X] ─────────
    val BF_SUIT_V = arrayOf(
        intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0),
        intArrayOf(2,1,0,0), intArrayOf(2,1,0,0), intArrayOf(2,2,1,0), intArrayOf(2,2,1,0),
        intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0)
    )
    val BF_SUIT_P = BF_SUIT_V  // same
    val BF_SUIT_F = arrayOf(
        intArrayOf(1,1,0,0), intArrayOf(1,1,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0),
        intArrayOf(1,0,0,0), intArrayOf(1,0,0,0), intArrayOf(2,1,0,0), intArrayOf(2,1,0,0),
        intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0), intArrayOf(2,2,2,0)
    )

    // ── WIND DIRECTION × LEVEL ─────────────────────────────────
    // [hinten, seite, vorn] × [A, F, E, X]
    private val WIND_DIR_V = arrayOf(
        intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(2,2,0,0)
    )
    private val WIND_DIR_P = arrayOf(
        intArrayOf(2,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0)
    )
    private val WIND_DIR_F = arrayOf(
        intArrayOf(2,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0)
    )

    // ── TOPOGRAFIE → SUITABILITY ───────────────────────────────
    private val TOPO_SUIT = mapOf(
        "Eben" to intArrayOf(0,0,0),
        "Leichte Steigung" to intArrayOf(0,0,0),
        "Steiles Gelände" to intArrayOf(1,1,0),
        "Kuppe" to intArrayOf(0,0,0),
        "Hanglage" to intArrayOf(0,0,0),
        "Mulde" to intArrayOf(0,0,0),
        "Wechselndes Relief" to intArrayOf(0,0,1),
        "Felsabbrüche / Steilwände" to intArrayOf(1,1,2)
    )

    // ── UMGEBUNG → SUITABILITY ─────────────────────────────────
    private val UMB_SUIT = mapOf(
        "Offenes Gelände" to intArrayOf(0,0,0),
        "Waldgebiet" to intArrayOf(0,0,0),
        "Grünland" to intArrayOf(0,0,0),
        "Urbanes Umfeld" to intArrayOf(0,0,1),
        "Industriegelände" to intArrayOf(0,0,1),
        "Dörfliche Bebauung" to intArrayOf(0,0,1),
        "Parkanlage" to intArrayOf(0,0,0),
        "Mischgebiet" to intArrayOf(0,0,1)
    )

    // ── BODEN SUITABILITY ──────────────────────────────────────
    private val BODEN_SUIT = mapOf(
        "gl_kurz" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(2,1,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,0),"schnee_h" to intArrayOf(2,1,0)),
        "gl_lang" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(2,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,0),"schnee_h" to intArrayOf(2,1,0)),
        "gl_frisch" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(2,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,0),"schnee_h" to intArrayOf(2,0,0)),
        "wb_laub" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,0),"schnee_h" to intArrayOf(2,1,0)),
        "wb_nadel" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,2)),
        "wb_misch" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,2)),
        "wb_ohne" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,2)),
        "wb_mit" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,1),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(1,0,2),"schnee_h" to intArrayOf(2,1,2)),
        "ab_grob" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(2,0,1),"matschig" to intArrayOf(2,0,1),"gefroren" to intArrayOf(2,0,1),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,1)),
        "ab_fein" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(1,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(0,0,0)),
        "ab_niedrig" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(0,0,0)),
        "ab_hoch" to mapOf("trocken" to intArrayOf(0,0,1),"feucht" to intArrayOf(0,0,1),"nass" to intArrayOf(0,0,1),"stehend" to intArrayOf(0,0,1),"matschig" to intArrayOf(0,0,1),"gefroren" to intArrayOf(0,0,1),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(0,0,1),"schnee_h" to intArrayOf(0,0,1)),
        "sb_ohne" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,1),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(0,0,0)),
        "sb_mit" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,1),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(0,0,0)),
        "asphalt" to mapOf("trocken" to intArrayOf(1,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,1),"gefroren" to intArrayOf(1,1,1),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,1)),
        "ks_ohne" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,1),"gefroren" to intArrayOf(1,1,1),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(1,0,1),"schnee_h" to intArrayOf(2,1,1)),
        "ks_mit" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(1,0,0),"schnee_h" to intArrayOf(2,0,0)),
        "mg_fels" to mapOf("trocken" to intArrayOf(1,0,0),"feucht" to intArrayOf(0,0,1),"nass" to intArrayOf(0,0,1),"stehend" to intArrayOf(2,0,1),"matschig" to intArrayOf(1,0,1),"gefroren" to intArrayOf(2,2,2),"schnee_l" to intArrayOf(0,0,1),"schnee_m" to intArrayOf(1,1,1),"schnee_h" to intArrayOf(2,1,1)),
        "mg_erdig" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(1,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(0,0,0),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(2,0,1)),
        "mg_mineral" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(1,1,1),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(2,0,0)),
        "mg_unspez" to mapOf("trocken" to intArrayOf(0,0,0),"feucht" to intArrayOf(0,0,0),"nass" to intArrayOf(0,0,0),"stehend" to intArrayOf(0,0,0),"matschig" to intArrayOf(0,0,0),"gefroren" to intArrayOf(1,1,1),"schnee_l" to intArrayOf(0,0,0),"schnee_m" to intArrayOf(0,0,0),"schnee_h" to intArrayOf(2,0,0))
    )

    // ════════════════════════════════════════════════════════════
    // PUBLIC HELPERS
    // ════════════════════════════════════════════════════════════

    /** Returns IntArray[V,P,F] 0=ok 1=warn 2=bad for a given soil combination, or null if unknown */
    fun getBodenSuit(bodenTyp: String, zustand: String): IntArray? =
        BODEN_SUIT[bodenTyp]?.get(zustand)

    // ════════════════════════════════════════════════════════════
    // MAIN SCORING FUNCTION
    // ════════════════════════════════════════════════════════════

    fun calculate(
        param: NasenarbeitParameter,
        fortschritt: NasenarbeitFortschritt
    ): ScoringResult {

        val levelV = fortschritt.levelVerlorensuche.ordinal
        val levelP = fortschritt.levelPersonensuche.ordinal
        val levelF = fortschritt.levelFreisuche.ordinal
        val levels = intArrayOf(levelV, levelP, levelF)

        // ── STUFE 1: HARTE AUSSCHLÜSSE ────────────────────────
        if (param.temperaturCelsius > 30.0) {
            return hardExclude("Temperatur über 30°C – Training nicht möglich")
        }
        if (param.temperaturCelsius < -10.0) {
            return hardExclude("Temperatur unter −10°C – Training nicht möglich")
        }
        if (param.wmoCode in listOf(95, 96, 99)) {
            return hardExclude("Gewitter – Training nicht möglich")
        }
        if (param.beaufort >= 9) {
            return hardExclude("Sturm/Orkan (Beaufort ${param.beaufort}) – Training nicht möglich")
        }

        // ── STUFE 2: BASIS-EIGNUNG ─────────────────────────────
        val wmoSuit = WMO_SUIT[param.wmoCode] ?: intArrayOf(0,0,0)
        val bfSuit = intArrayOf(
            BF_SUIT_V[param.beaufort.coerceIn(0,12)][levelV],
            BF_SUIT_P[param.beaufort.coerceIn(0,12)][levelP],
            BF_SUIT_F[param.beaufort.coerceIn(0,12)][levelF]
        )
        val windDirSuit = intArrayOf(
            WIND_DIR_V[param.windrichtung][levelV],
            WIND_DIR_P[param.windrichtung][levelP],
            WIND_DIR_F[param.windrichtung][levelF]
        )

        // Gelände
        val gelSuit = calcGelaendeSuit(param)

        // Boden (gewichtet, Schwellwert 33%)
        val bodenSuit = calcBodenSuit(param)

        // Kombiniert: restriktivster Wert
        val combined = IntArray(3) { i ->
            maxOf(wmoSuit[i], bfSuit[i], windDirSuit[i], gelSuit[i], bodenSuit[i])
        }

        val baseScore = DoubleArray(3) { i ->
            when (combined[i]) {
                0 -> 10.0
                1 -> 5.0
                else -> -999.0
            }
        }

        // ── STUFE 3: BONUS/MALUS BEDINGUNGEN ──────────────────
        val firstZustand = param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: ""
        val bodenBonus = getBodenBonus(firstZustand)
        val windBonus = getWindBonus(param.beaufort)
        val nebelBonus = if (param.wmoCode in listOf(45, 48)) doubleArrayOf(1.0, 0.0, 0.0) else doubleArrayOf(0.0, 0.0, 0.0)

        // ── STUFE 4: GEBIETSEIGNUNG ────────────────────────────
        val gebietBonus = calcGebietBonus(param, fortschritt)

        // ── STUFE 5: TRAININGSFORTSCHRITT ─────────────────────
        // (Recency – minor adjustment when all 3 disciplines trained)
        val recencyBonus = doubleArrayOf(0.0, 0.0, 0.0)

        // ── FINALER SCORE ──────────────────────────────────────
        val finalScore = DoubleArray(3) { i ->
            if (baseScore[i] <= -999.0) -999.0
            else baseScore[i] + bodenBonus[i] + windBonus[i] + nebelBonus[i] + gebietBonus[i] + recencyBonus[i]
        }

        val maxScore = finalScore.filter { it > -999.0 }.maxOrNull() ?: 0.0
        val empfohlen = NasenarbeitDisziplin.values().filterIndexed { i, _ ->
            finalScore[i] > -999.0 && finalScore[i] >= maxScore - 2.0
        }
        val primaer = empfohlen.firstOrNull() ?: NasenarbeitDisziplin.VERLORENSUCHE

        val begruendung = buildBegruendung(primaer, param, firstZustand)

        return ScoringResult(
            scores = finalScore,
            suitability = combined,
            empfohlen = empfohlen,
            primaer = primaer,
            begruendung = begruendung
        )
    }

    // ── GELÄNDE ───────────────────────────────────────────────

    private fun calcGelaendeSuit(param: NasenarbeitParameter): IntArray {
        val suit = intArrayOf(0, 0, 0)
        param.topoSequenz.forEach { topo ->
            val s = TOPO_SUIT[topo] ?: intArrayOf(0,0,0)
            for (i in 0..2) suit[i] = maxOf(suit[i], s[i])
        }
        param.gelaendeAbschnitte.forEach { abschnitt ->
            val s = UMB_SUIT[abschnitt.umgebung] ?: intArrayOf(0,0,0)
            for (i in 0..2) suit[i] = maxOf(suit[i], s[i])
        }
        return suit
    }

    // ── BODEN SCORING (Option 1: Schwellwert 33% + gewichtet) ──

    private fun calcBodenSuit(param: NasenarbeitParameter): IntArray {
        val abschnitte = param.gelaendeAbschnitte.filter { it.bodenTyp.isNotEmpty() && it.bodenZustand.isNotEmpty() }
        if (abschnitte.isEmpty()) return intArrayOf(0, 0, 0)
        val n = abschnitte.size
        val THRESHOLD = 0.33

        return IntArray(3) { d ->
            val badCount = abschnitte.count { a ->
                BODEN_SUIT[a.bodenTyp]?.get(a.bodenZustand)?.get(d) == 2
            }
            val badRatio = badCount.toDouble() / n
            if (badRatio >= THRESHOLD) {
                2
            } else {
                // Weighted score
                val knownSchr = abschnitte.mapNotNull { it.laengeSchritte?.toDouble() }
                val avgSchr = if (knownSchr.isNotEmpty()) knownSchr.average() else 1.0
                val weightsSchr = abschnitte.map { it.laengeSchritte?.toDouble() ?: avgSchr }
                val knownPct = abschnitte.mapNotNull { it.flaechenanteilProzent }
                val avgPct = if (knownPct.isNotEmpty()) knownPct.average() else 100.0 / n
                val weightsPct = abschnitte.map { it.flaechenanteilProzent ?: avgPct }
                val weights = if (d < 2) weightsSchr else weightsPct
                val totalW = weights.sum().takeIf { it > 0 } ?: 1.0
                var score = 0.0
                abschnitte.forEachIndexed { i, a ->
                    val suit = BODEN_SUIT[a.bodenTyp]?.get(a.bodenZustand)?.get(d) ?: 1
                    val pts = when (suit) { 0 -> 10.0; 1 -> 5.0; else -> 0.0 }
                    score += pts * (weights[i] / totalW)
                }
                when {
                    score >= 8.0 -> 0
                    score >= 5.0 -> 1
                    else -> 2
                }
            }
        }
    }

    // ── BONUS/MALUS ────────────────────────────────────────────

    private fun getBodenBonus(zustand: String): DoubleArray = when (zustand) {
        "stehend", "matschig" -> doubleArrayOf(4.0, -2.0, 0.0)
        "nass"    -> doubleArrayOf(3.0, -1.0, 0.0)
        "feucht"  -> doubleArrayOf(2.0,  0.0, 0.0)
        "trocken" -> doubleArrayOf(-2.0, 3.0, 0.0)
        "gefroren"-> doubleArrayOf(-2.0,-1.0, 0.0)
        "schnee_l"-> doubleArrayOf(1.0,  0.0, 0.0)
        "schnee_m","schnee_h" -> doubleArrayOf(-3.0, 0.0, 0.0)
        else      -> doubleArrayOf(0.0,  0.0, 0.0)
    }

    private fun getWindBonus(bf: Int): DoubleArray = when {
        bf <= 2 -> doubleArrayOf(2.0, 0.0, -1.0)
        bf <= 5 -> doubleArrayOf(0.0, 0.0,  2.0)
        bf <= 8 -> doubleArrayOf(-2.0,0.0,  3.0)
        else    -> doubleArrayOf(0.0, 0.0,  0.0)
    }

    private fun calcGebietBonus(param: NasenarbeitParameter, fortschritt: NasenarbeitFortschritt): DoubleArray {
        val levV = fortschritt.levelVerlorensuche
        val levF = fortschritt.levelFreisuche
        val isLinear = param.gebietSchritte > 500 && param.gebietHa < 0.5
        val isWide = param.gebietHa >= 0.5 && param.gebietSchritte < 500
        val isSmall = param.gebietSchritte < levV.maxSchritte_V || param.gebietHa < levF.maxHa_F
        val isVerySmall = param.gebietSchritte < levV.maxSchritte_V * 0.5 || param.gebietHa < levF.maxHa_F * 0.5
        val bonus = doubleArrayOf(0.0, 0.0, 0.0)
        if (isLinear) { bonus[0] += 3.0; bonus[1] += 3.0 }
        if (isWide) bonus[2] += 3.0
        if (isVerySmall) { bonus[0] -= 4.0; bonus[1] -= 4.0; bonus[2] -= 1.0 }
        else if (isSmall) { bonus[0] -= 2.0; bonus[1] -= 2.0 }
        return bonus
    }

    // ── HELPERS ────────────────────────────────────────────────

    private fun hardExclude(reason: String) = ScoringResult(
        scores = doubleArrayOf(-999.0, -999.0, -999.0),
        suitability = intArrayOf(2, 2, 2),
        empfohlen = emptyList(),
        primaer = NasenarbeitDisziplin.VERLORENSUCHE,
        begruendung = reason,
        isHardExcluded = true,
        hardExclusionReason = reason
    )

    private fun buildBegruendung(diz: NasenarbeitDisziplin, param: NasenarbeitParameter, zustand: String): String {
        val sb = StringBuilder()
        sb.append(diz.displayName)
        sb.append(" ist heute optimal. ")
        when {
            zustand in listOf("feucht","nass","stehend","matschig") && diz == NasenarbeitDisziplin.VERLORENSUCHE ->
                sb.append("Feuchter Boden begünstigt die Spurhaltung.")
            zustand == "trocken" && diz == NasenarbeitDisziplin.PERSONENSUCHE ->
                sb.append("Trockener Boden eignet sich gut für Personensuche.")
            param.beaufort in 6..8 && diz == NasenarbeitDisziplin.FREISUCHE ->
                sb.append("Starker Wind begünstigt die Freisuche.")
        }
        return sb.toString()
    }
}