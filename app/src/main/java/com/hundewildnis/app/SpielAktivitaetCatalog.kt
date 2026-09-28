package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – STATISCHER AKTIVITÄTENKATALOG
// ════════════════════════════════════════════════════════════════
//
// Quellenbasis (Recherche, deutschsprachige Fachratgeber, September 2026):
// zooplus.de, fressnapf.de, hey-fiffi.com, hunde-kausnacks.de, heyhund.com,
// tierfreund.de, edogs.de, belcando.de, ideen-mehrhundehaltung.de,
// zooroyal.de, talesandtails.de, futalis.de, josera.de, tibeo.de,
// jameswellbeloved.de, purina.de, anicare.eu (Zerrspiel-Regeln).
// Inhalte sachlich zusammengefasst und für den App-Kontext neu formuliert;
// keine wörtliche Übernahme einzelner Formulierungen.
//
// ID-Schema: "so_<name>" (Outdoor: Wald/Grünland/Park/Garten),
//            "si_<name>" (Indoor: Haus/Wohnung).
// IDs sind stabile Speicher-Schlüssel für Sessions/Statistik – nach
// Veröffentlichung NIEMALS mehr ändern oder wiederverwenden.
//
object SpielAktivitaetCatalog {

    val alle: List<SpielAktivitaet> = listOf(

        // ────────────────────────────────────────────────────────
        // NASENARBEIT – OUTDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "so_leckerlifaehrte_anfaenger",
            nameRes = R.string.spiel_so_leckerlifaehrte_anfaenger_name,
            beschreibungRes = R.string.spiel_so_leckerlifaehrte_anfaenger_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.ALLE, SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_faehrtenarbeit_fortgeschritten",
            nameRes = R.string.spiel_so_faehrtenarbeit_fortgeschritten_name,
            beschreibungRes = R.string.spiel_so_faehrtenarbeit_fortgeschritten_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GRUENLAND),
            minDauerMinuten = 15, maxDauerMinuten = 30,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_mantrailing_einstieg",
            nameRes = R.string.spiel_so_mantrailing_einstieg_name,
            beschreibungRes = R.string.spiel_so_mantrailing_einstieg_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.PARK, SpielOrt.GRUENLAND),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.SOZIALKONTAKT)
        ),
        SpielAktivitaet(
            id = "so_futtersuche_gelaende",
            nameRes = R.string.spiel_so_futtersuche_gelaende_name,
            beschreibungRes = R.string.spiel_so_futtersuche_gelaende_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GRUENLAND, SpielOrt.PARK, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_objektsuche_holzstapel",
            nameRes = R.string.spiel_so_objektsuche_holzstapel_name,
            beschreibungRes = R.string.spiel_so_objektsuche_holzstapel_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT)
        ),
        SpielAktivitaet(
            id = "so_zapfensuche",
            nameRes = R.string.spiel_so_zapfensuche_name,
            beschreibungRes = R.string.spiel_so_zapfensuche_desc,
            orte = listOf(SpielOrt.WALD),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_entspanntes_schnueffeln",
            nameRes = R.string.spiel_so_entspanntes_schnueffeln_name,
            beschreibungRes = R.string.spiel_so_entspanntes_schnueffeln_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 15, maxDauerMinuten = 30,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_versteckspiel_person",
            nameRes = R.string.spiel_so_versteckspiel_person_name,
            beschreibungRes = R.string.spiel_so_versteckspiel_person_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.PARK, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.SOZIALKONTAKT)
        ),
        SpielAktivitaet(
            id = "so_neue_wege",
            nameRes = R.string.spiel_so_neue_wege_name,
            beschreibungRes = R.string.spiel_so_neue_wege_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 20, maxDauerMinuten = 40,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.RUHE_ENTSPANNUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),

        // ────────────────────────────────────────────────────────
        // BEWEGUNG / APPORTIEREN – OUTDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "so_apportieren_klassisch",
            nameRes = R.string.spiel_so_apportieren_klassisch_name,
            beschreibungRes = R.string.spiel_so_apportieren_klassisch_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_sprung_stoss
        ),
        SpielAktivitaet(
            id = "so_apportier_gedaechtnis",
            nameRes = R.string.spiel_so_apportier_gedaechtnis_name,
            beschreibungRes = R.string.spiel_so_apportier_gedaechtnis_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.NICHT_WELPE)
        ),
        SpielAktivitaet(
            id = "so_frisbee",
            nameRes = R.string.spiel_so_frisbee_name,
            beschreibungRes = R.string.spiel_so_frisbee_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM),
            hinweisRes = R.string.spiel_hinweis_sprung_stoss
        ),
        SpielAktivitaet(
            id = "so_dummytraining",
            nameRes = R.string.spiel_so_dummytraining_name,
            beschreibungRes = R.string.spiel_so_dummytraining_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.WALD),
            minDauerMinuten = 15, maxDauerMinuten = 25,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "so_reizangel",
            nameRes = R.string.spiel_so_reizangel_name,
            beschreibungRes = R.string.spiel_so_reizangel_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.PARK, SpielOrt.GRUENLAND),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM),
            hinweisRes = R.string.spiel_hinweis_richtungswechsel
        ),
        SpielAktivitaet(
            id = "so_zerrspiel_tau",
            nameRes = R.string.spiel_so_zerrspiel_tau_name,
            beschreibungRes = R.string.spiel_so_zerrspiel_tau_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.PARK, SpielOrt.GRUENLAND),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.SOZIALKONTAKT),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_zahnwechsel
        ),
        SpielAktivitaet(
            id = "so_fangspiel",
            nameRes = R.string.spiel_so_fangspiel_name,
            beschreibungRes = R.string.spiel_so_fangspiel_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.SOZIALKONTAKT),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_richtungswechsel
        ),
        SpielAktivitaet(
            id = "so_treibball_grundlagen",
            nameRes = R.string.spiel_so_treibball_grundlagen_name,
            beschreibungRes = R.string.spiel_so_treibball_grundlagen_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_freies_spiel_hunde",
            nameRes = R.string.spiel_so_freies_spiel_hunde_name,
            beschreibungRes = R.string.spiel_so_freies_spiel_hunde_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK, SpielOrt.WALD),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.SOZIALKONTAKT, SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "so_ausdauertrab",
            nameRes = R.string.spiel_so_ausdauertrab_name,
            beschreibungRes = R.string.spiel_so_ausdauertrab_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.WALD),
            minDauerMinuten = 20, maxDauerMinuten = 40,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE)
        ),

        // ────────────────────────────────────────────────────────
        // GELÄNDE / KOORDINATION – OUTDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "so_wald_slalom_baeume",
            nameRes = R.string.spiel_so_wald_slalom_baeume_name,
            beschreibungRes = R.string.spiel_so_wald_slalom_baeume_desc,
            orte = listOf(SpielOrt.WALD),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_naturmaterial_slalom",
            nameRes = R.string.spiel_so_naturmaterial_slalom_name,
            beschreibungRes = R.string.spiel_so_naturmaterial_slalom_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK, SpielOrt.GARTEN),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_baumstamm_balancieren",
            nameRes = R.string.spiel_so_baumstamm_balancieren_name,
            beschreibungRes = R.string.spiel_so_baumstamm_balancieren_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.PARK),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM)
        ),
        SpielAktivitaet(
            id = "so_klettern_wurzeln",
            nameRes = R.string.spiel_so_klettern_wurzeln_name,
            beschreibungRes = R.string.spiel_so_klettern_wurzeln_desc,
            orte = listOf(SpielOrt.WALD),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM)
        ),
        SpielAktivitaet(
            id = "so_sprung_erhoehung",
            nameRes = R.string.spiel_so_sprung_erhoehung_name,
            beschreibungRes = R.string.spiel_so_sprung_erhoehung_desc,
            orte = listOf(SpielOrt.PARK, SpielOrt.WALD, SpielOrt.GARTEN),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_sprung_stoss
        ),
        SpielAktivitaet(
            id = "so_untertunneln",
            nameRes = R.string.spiel_so_untertunneln_name,
            beschreibungRes = R.string.spiel_so_untertunneln_desc,
            orte = listOf(SpielOrt.PARK, SpielOrt.WALD),
            minDauerMinuten = 5, maxDauerMinuten = 5,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_agility_selbstbau",
            nameRes = R.string.spiel_so_agility_selbstbau_name,
            beschreibungRes = R.string.spiel_so_agility_selbstbau_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.PARK, SpielOrt.GRUENLAND),
            minDauerMinuten = 15, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_sprungelemente_weglassen
        ),
        SpielAktivitaet(
            id = "so_buddelkiste_garten",
            nameRes = R.string.spiel_so_buddelkiste_garten_name,
            beschreibungRes = R.string.spiel_so_buddelkiste_garten_desc,
            orte = listOf(SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "so_buddelspiel_freiland",
            nameRes = R.string.spiel_so_buddelspiel_freiland_name,
            beschreibungRes = R.string.spiel_so_buddelspiel_freiland_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.GARTEN),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "so_truemmerfeld_parcours",
            nameRes = R.string.spiel_so_truemmerfeld_parcours_name,
            beschreibungRes = R.string.spiel_so_truemmerfeld_parcours_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.PARK),
            minDauerMinuten = 15, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_sortierspiel",
            nameRes = R.string.spiel_so_sortierspiel_name,
            beschreibungRes = R.string.spiel_so_sortierspiel_desc,
            orte = listOf(SpielOrt.GARTEN, SpielOrt.GRUENLAND),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.NASENARBEIT)
        ),

        // ────────────────────────────────────────────────────────
        // KONTROLLE / RUHE – OUTDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "so_distanz_positionen",
            nameRes = R.string.spiel_so_distanz_positionen_name,
            beschreibungRes = R.string.spiel_so_distanz_positionen_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_distanz_rueckruf",
            nameRes = R.string.spiel_so_distanz_rueckruf_name,
            beschreibungRes = R.string.spiel_so_distanz_rueckruf_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_umweltreiz_spaziergang",
            nameRes = R.string.spiel_so_umweltreiz_spaziergang_name,
            beschreibungRes = R.string.spiel_so_umweltreiz_spaziergang_desc,
            orte = listOf(SpielOrt.PARK, SpielOrt.GRUENLAND, SpielOrt.WALD),
            minDauerMinuten = 15, maxDauerMinuten = 30,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "so_ruhe_platz_reizumgebung",
            nameRes = R.string.spiel_so_ruhe_platz_reizumgebung_name,
            beschreibungRes = R.string.spiel_so_ruhe_platz_reizumgebung_desc,
            orte = listOf(SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG)
        ),

        // ────────────────────────────────────────────────────────
        // WASSER / WETTER – OUTDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "so_wasserapportieren",
            nameRes = R.string.spiel_so_wasserapportieren_name,
            beschreibungRes = R.string.spiel_so_wasserapportieren_desc,
            orte = listOf(SpielOrt.WALD, SpielOrt.GRUENLAND, SpielOrt.PARK),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "so_gartenschlauch",
            nameRes = R.string.spiel_so_gartenschlauch_name,
            beschreibungRes = R.string.spiel_so_gartenschlauch_desc,
            orte = listOf(SpielOrt.GARTEN),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "so_schnee_schnueffelhuegel",
            nameRes = R.string.spiel_so_schnee_schnueffelhuegel_name,
            beschreibungRes = R.string.spiel_so_schnee_schnueffelhuegel_desc,
            orte = listOf(SpielOrt.GRUENLAND, SpielOrt.GARTEN, SpielOrt.WALD),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT)
        ),

        // ────────────────────────────────────────────────────────
        // NASENARBEIT – INDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "si_schnueffelteppich",
            nameRes = R.string.spiel_si_schnueffelteppich_name,
            beschreibungRes = R.string.spiel_si_schnueffelteppich_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_futtersuche_wohnung",
            nameRes = R.string.spiel_si_futtersuche_wohnung_name,
            beschreibungRes = R.string.spiel_si_futtersuche_wohnung_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 15, maxDauerMinuten = 30,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_muffinform",
            nameRes = R.string.spiel_si_muffinform_name,
            beschreibungRes = R.string.spiel_si_muffinform_desc,
            orte = listOf(SpielOrt.WOHNUNG, SpielOrt.HAUS),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_kartonbox",
            nameRes = R.string.spiel_si_kartonbox_name,
            beschreibungRes = R.string.spiel_si_kartonbox_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT),
            hinweisRes = R.string.spiel_hinweis_aufsicht_papier
        ),
        SpielAktivitaet(
            id = "si_klopapierrollen",
            nameRes = R.string.spiel_si_klopapierrollen_name,
            beschreibungRes = R.string.spiel_si_klopapierrollen_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT),
            hinweisRes = R.string.spiel_hinweis_aufsicht_papier
        ),
        SpielAktivitaet(
            id = "si_decken_verstecke",
            nameRes = R.string.spiel_si_decken_verstecke_name,
            beschreibungRes = R.string.spiel_si_decken_verstecke_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_leckerlipaeckchen",
            nameRes = R.string.spiel_si_leckerlipaeckchen_name,
            beschreibungRes = R.string.spiel_si_leckerlipaeckchen_desc,
            orte = listOf(SpielOrt.WOHNUNG, SpielOrt.HAUS),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT),
            hinweisRes = R.string.spiel_hinweis_aufsicht_papier
        ),
        SpielAktivitaet(
            id = "si_futterball_kong",
            nameRes = R.string.spiel_si_futterball_kong_name,
            beschreibungRes = R.string.spiel_si_futterball_kong_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 30,
            material = SpielMaterial.FUTTER_DISPENSER,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_intelligenzspielzeug",
            nameRes = R.string.spiel_si_intelligenzspielzeug_name,
            beschreibungRes = R.string.spiel_si_intelligenzspielzeug_desc,
            orte = listOf(SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.FUTTER_DISPENSER,
            fokus = listOf(SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_flaschendreh",
            nameRes = R.string.spiel_si_flaschendreh_name,
            beschreibungRes = R.string.spiel_si_flaschendreh_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "si_spielzeug_verstecken",
            nameRes = R.string.spiel_si_spielzeug_verstecken_name,
            beschreibungRes = R.string.spiel_si_spielzeug_verstecken_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "si_versteckspiel_person",
            nameRes = R.string.spiel_si_versteckspiel_person_name,
            beschreibungRes = R.string.spiel_si_versteckspiel_person_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.NASENARBEIT, SpielFokus.SOZIALKONTAKT)
        ),

        // ────────────────────────────────────────────────────────
        // BEWEGUNG / KOORDINATION – INDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "si_apportieren_wohnung",
            nameRes = R.string.spiel_si_apportieren_wohnung_name,
            beschreibungRes = R.string.spiel_si_apportieren_wohnung_desc,
            orte = listOf(SpielOrt.WOHNUNG, SpielOrt.HAUS),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG)
        ),
        SpielAktivitaet(
            id = "si_zerrspiel",
            nameRes = R.string.spiel_si_zerrspiel_name,
            beschreibungRes = R.string.spiel_si_zerrspiel_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.SOZIALKONTAKT),
            eignung = listOf(SpielEignung.NICHT_WELPE),
            hinweisRes = R.string.spiel_hinweis_zahnwechsel
        ),
        SpielAktivitaet(
            id = "si_indoor_agility",
            nameRes = R.string.spiel_si_indoor_agility_name,
            beschreibungRes = R.string.spiel_si_indoor_agility_desc,
            orte = listOf(SpielOrt.HAUS),
            minDauerMinuten = 15, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM)
        ),
        SpielAktivitaet(
            id = "si_cavaletti",
            nameRes = R.string.spiel_si_cavaletti_name,
            beschreibungRes = R.string.spiel_si_cavaletti_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_slalom_beine",
            nameRes = R.string.spiel_si_slalom_beine_name,
            beschreibungRes = R.string.spiel_si_slalom_beine_desc,
            orte = listOf(SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.BEWEGUNG, SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "si_hocker_podest",
            nameRes = R.string.spiel_si_hocker_podest_name,
            beschreibungRes = R.string.spiel_si_hocker_podest_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 10,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_GELENKPROBLEM)
        ),
        SpielAktivitaet(
            id = "si_treppentraining",
            nameRes = R.string.spiel_si_treppentraining_name,
            beschreibungRes = R.string.spiel_si_treppentraining_desc,
            orte = listOf(SpielOrt.HAUS),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.BEWEGUNG),
            eignung = listOf(SpielEignung.NICHT_WELPE, SpielEignung.NICHT_GELENKPROBLEM)
        ),

        // ────────────────────────────────────────────────────────
        // KOPFARBEIT / TRICKS – INDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "si_becherspiel",
            nameRes = R.string.spiel_si_becherspiel_name,
            beschreibungRes = R.string.spiel_si_becherspiel_desc,
            orte = listOf(SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_targettraining",
            nameRes = R.string.spiel_si_targettraining_name,
            beschreibungRes = R.string.spiel_si_targettraining_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_pfote_geben",
            nameRes = R.string.spiel_si_pfote_geben_name,
            beschreibungRes = R.string.spiel_si_pfote_geben_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 5,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.SOZIALKONTAKT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_trickfolge",
            nameRes = R.string.spiel_si_trickfolge_name,
            beschreibungRes = R.string.spiel_si_trickfolge_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.NICHT_GELENKPROBLEM)
        ),
        SpielAktivitaet(
            id = "si_objekte_benennen",
            nameRes = R.string.spiel_si_objekte_benennen_name,
            beschreibungRes = R.string.spiel_si_objekte_benennen_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "si_spielzeug_aufraeumen",
            nameRes = R.string.spiel_si_spielzeug_aufraeumen_name,
            beschreibungRes = R.string.spiel_si_spielzeug_aufraeumen_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 10,
            material = SpielMaterial.SPIELZEUG,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.SOZIALKONTAKT)
        ),
        SpielAktivitaet(
            id = "si_alltagshelfer",
            nameRes = R.string.spiel_si_alltagshelfer_name,
            beschreibungRes = R.string.spiel_si_alltagshelfer_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.KOPFARBEIT, SpielFokus.SOZIALKONTAKT)
        ),
        SpielAktivitaet(
            id = "si_trickcapturing",
            nameRes = R.string.spiel_si_trickcapturing_name,
            beschreibungRes = R.string.spiel_si_trickcapturing_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),

        // ────────────────────────────────────────────────────────
        // RUHE / IMPULSKONTROLLE – INDOOR
        // ────────────────────────────────────────────────────────
        SpielAktivitaet(
            id = "si_balance_leckerli",
            nameRes = R.string.spiel_si_balance_leckerli_name,
            beschreibungRes = R.string.spiel_si_balance_leckerli_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_impuls_faust",
            nameRes = R.string.spiel_si_impuls_faust_name,
            beschreibungRes = R.string.spiel_si_impuls_faust_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_impuls_pfote",
            nameRes = R.string.spiel_si_impuls_pfote_name,
            beschreibungRes = R.string.spiel_si_impuls_pfote_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 10,
            material = SpielMaterial.LECKERLI,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_deckentraining",
            nameRes = R.string.spiel_si_deckentraining_name,
            beschreibungRes = R.string.spiel_si_deckentraining_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 20,
            material = SpielMaterial.ALLTAGSGEGENSTAND,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_konditionierte_entspannung",
            nameRes = R.string.spiel_si_konditionierte_entspannung_name,
            beschreibungRes = R.string.spiel_si_konditionierte_entspannung_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_bleib_ablenkung",
            nameRes = R.string.spiel_si_bleib_ablenkung_name,
            beschreibungRes = R.string.spiel_si_bleib_ablenkung_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 5, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.RUHE_ENTSPANNUNG, SpielFokus.KOPFARBEIT),
            eignung = listOf(SpielEignung.SENIOR_GEEIGNET)
        ),
        SpielAktivitaet(
            id = "si_distanzarbeit_kommandos",
            nameRes = R.string.spiel_si_distanzarbeit_kommandos_name,
            beschreibungRes = R.string.spiel_si_distanzarbeit_kommandos_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.KOPFARBEIT)
        ),
        SpielAktivitaet(
            id = "si_freies_spiel_ohne_ziel",
            nameRes = R.string.spiel_si_freies_spiel_ohne_ziel_name,
            beschreibungRes = R.string.spiel_si_freies_spiel_ohne_ziel_desc,
            orte = listOf(SpielOrt.HAUS, SpielOrt.WOHNUNG),
            minDauerMinuten = 10, maxDauerMinuten = 15,
            material = SpielMaterial.KEINS,
            fokus = listOf(SpielFokus.SOZIALKONTAKT, SpielFokus.BEWEGUNG)
        )
    )

    /** Findet eine Aktivität anhand ihrer stabilen ID (z.B. für Sessions/Details). */
    fun findById(id: String): SpielAktivitaet? = alle.find { it.id == id }

    /**
     * Filtert den Katalog nach Örtlichkeit und optionalen weiteren Parametern.
     * @param ort            Pflicht-Örtlichkeit
     * @param zeitbudget     max. verfügbare Zeit (null = keine Einschränkung)
     * @param verfuegbaresMaterial Set der vorhandenen Materialarten (KEINS ist implizit immer erlaubt)
     * @param gewuenschterFokus Set gewünschter Fokusbereiche (leer = alle)
     * @param welpeOderJunghund true, um Aktivitäten mit Wachstumsfugen-Risiko auszuschließen
     * @param senior         true für zusätzliche Rücksicht (aktuell informativ, kein Hartausschluss)
     * @param gelenkproblem  true, um gelenkbelastende Aktivitäten auszuschließen
     */
    fun filter(
        ort: SpielOrt,
        zeitbudget: SpielZeitbudget? = null,
        verfuegbaresMaterial: Set<SpielMaterial> = SpielMaterial.values().toSet(),
        gewuenschterFokus: Set<SpielFokus> = emptySet(),
        welpeOderJunghund: Boolean = false,
        senior: Boolean = false,
        gelenkproblem: Boolean = false
    ): List<SpielAktivitaet> {
        return alle.filter { aktivitaet ->
            aktivitaet.passtZuOrt(ort) &&
                (zeitbudget == null || aktivitaet.passtZuZeitbudget(zeitbudget)) &&
                aktivitaet.passtZuMaterial(verfuegbaresMaterial) &&
                aktivitaet.passtZuFokus(gewuenschterFokus) &&
                aktivitaet.geeignetFuer(welpeOderJunghund, senior, gelenkproblem)
        }
    }
}
