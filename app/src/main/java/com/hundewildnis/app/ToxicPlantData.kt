package com.hundewildnis.app

import java.util.Calendar

// ════════════════════════════════════════════════════════════════
// GEFAHRENPFLANZEN-SCOUT – Datenbasis
// Datei: ToxicPlantData.kt
// Vollständig lokal / offline – keine API-Abhängigkeit
// ════════════════════════════════════════════════════════════════

// ── Giftigkeitsstufen ────────────────────────────────────────────
enum class PlantToxicityLevel(
    val displayName: String,
    val icon: String,
    val color: String,
    val shortLabel: String
) {
    SLIGHTLY_TOXIC(
        "Leicht giftig",
        "🟡",
        "#F57C00",
        "Leicht"
    ),
    MODERATELY_TOXIC(
        "Giftig",
        "🟠",
        "#E64A19",
        "Giftig"
    ),
    HIGHLY_TOXIC(
        "Stark giftig",
        "🔴",
        "#C62828",
        "Stark"
    ),
    DEADLY(
        "Tödlich",
        "☠️",
        "#7B0000",
        "Tödlich"
    )
}

// ── Betroffene Körpersysteme ─────────────────────────────────────
enum class PlantBodySystem(val displayName: String) {
    DIGESTIVE("Magen-Darm"),
    CARDIOVASCULAR("Herz-Kreislauf"),
    NERVOUS("Nervensystem"),
    LIVER("Leber"),
    KIDNEYS("Nieren"),
    SKIN("Haut / Schleimhaut"),
    RESPIRATORY("Atemwege"),
    BLOOD("Blut")
}

// ── Pflanzendaten-Modell ─────────────────────────────────────────
data class ToxicPlant(
    val id: String,
    val name: String,                        // Hauptname (Deutsch)
    val latinName: String,                   // Lateinischer Name
    val alternativeNames: List<String>,      // Weitere deutsche Namen
    val toxicityLevel: PlantToxicityLevel,
    val toxicParts: List<String>,            // Welche Teile giftig sind
    val affectedSystems: List<PlantBodySystem>,
    val symptoms: List<String>,              // Symptome nach Aufnahme
    val identificationFeatures: List<String>, // Erkennungsmerkmale
    val bloomMonths: List<Int>,              // Monate 1–12 (Blütezeit / Risikozeit)
    val habitat: List<String>,               // Wo vorkommt (Wald, Garten, …)
    val firstAidSteps: List<String>,         // Sofortmaßnahmen
    val mustCallVet: Boolean,                // Immer sofort Tierarzt?
    val confusionRisk: String? = null,       // Verwechslungsgefahr mit
    val note: String? = null                 // Besonderer Hinweis
) {
    /** Ist diese Pflanze im aktuellen Monat besonders relevant? */
    fun isCurrentlySeasonal(): Boolean {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return month in bloomMonths
    }

    /** Alle Namen für die Suche kombiniert */
    fun allSearchNames(): String =
        "$name $latinName ${alternativeNames.joinToString(" ")}".lowercase()
}

// ════════════════════════════════════════════════════════════════
// PFLANZENDATENBANK – 55 Einträge
// ════════════════════════════════════════════════════════════════
object ToxicPlantDatabase {

    val plants: List<ToxicPlant> = listOf(

        // ── TÖDLICH (DEADLY) ─────────────────────────────────────

        ToxicPlant(
            id = "eibe",
            name = "Eibe",
            latinName = "Taxus baccata",
            alternativeNames = listOf("Eibenbaum", "Taxus"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Nadeln", "Samen (Kern)", "Rinde", "Holz"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Speichelfluss", "Herzrhythmusstörungen", "Krämpfe", "Tod durch Herzstillstand"),
            identificationFeatures = listOf("Immergrüner Nadelbaum", "Rote beerenartige Früchte", "Flache Nadeln"),
            bloomMonths = listOf(1, 2, 3, 4, 9, 10, 11, 12),
            habitat = listOf("Garten", "Park", "Friedhof", "Wald"),
            firstAidSteps = listOf("Sofort Tierarzt", "Kein Erbrechen induzieren", "Ruhe bewahren"),
            mustCallVet = true,
            note = "Sehr hochgiftig. Bereits wenige Gramm Nadeln sind tödlich."
        ),

        ToxicPlant(
            id = "oleander",
            name = "Oleander",
            latinName = "Nerium oleander",
            alternativeNames = listOf("Rosenlorbeer"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "auch Blüten und Wasser"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "blutiger Durchfall", "Herzrhythmusstörungen", "Koma"),
            identificationFeatures = listOf("Immergrüner Strauch", "Lanzettliche ledrige Blätter", "Rosa/Weiße Blüten"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Garten", "Terrasse", "Kübelpflanze"),
            firstAidSteps = listOf("Sofort Tierarzt", "Aktivkohle", "Herzüberwachung"),
            mustCallVet = true,
            note = "Sehr hochgiftig. Schon wenige Blätter können tödlich sein."
        ),

        ToxicPlant(
            id = "eisenhut",
            name = "Blauer Eisenhut",
            latinName = "Aconitum napellus",
            alternativeNames = listOf("Sturmhut", "Wolfswurz"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "besonders Wurzel"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Brennen im Mund", "Speichelfluss", "Taubheit", "Atemlähmung", "Herzstillstand"),
            identificationFeatures = listOf("Hohe Staude", "Blau-violette Helmblüten", "Handförmige Blätter"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Gebirge", "Wiese", "Wald", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Intensivmedizin nötig", "Hautkontakt vermeiden"),
            mustCallVet = true,
            note = "Eine der giftigsten Pflanzen Europas. Kleinste Mengen lebensgefährlich."
        ),

        ToxicPlant(
            id = "goldregen",
            name = "Goldregen",
            latinName = "Laburnum anagyroides",
            alternativeNames = listOf("Bohnenbaum"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Samen (in Hülsen)", "Rinde", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Übelkeit", "Erbrechen", "Krämpfe", "Lähmungen", "Atemstillstand"),
            identificationFeatures = listOf("Strauch/Baum", "Hängende gelbe Blütentrauben", "Hülsenfrüchte"),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Park", "Straße"),
            firstAidSteps = listOf("Sofort Tierarzt", "Aktivkohle", "Beatmung bei Bedarf"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "herbstzeitlose",
            name = "Herbstzeitlose",
            latinName = "Colchicum autumnale",
            alternativeNames = listOf("Wiesenzeitlose", "Zeitlose"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "besonders Zwiebel und Samen"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Erbrechen", "blutiger Durchfall", "Koliken", "Atemlähmung (verzögert)"),
            identificationFeatures = listOf("Blüte im Herbst ohne Blätter", "Rosa-lila Blüten", "Ähnelt Krokus"),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Wiese", "Weide", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Aktivkohle", "Supportive Care"),
            mustCallVet = true,
            note = "Extrem toxisch. Symptome oft erst nach Stunden oder Tagen."
        ),

        ToxicPlant(
            id = "schierling",
            name = "Gefleckter Schierling",
            latinName = "Conium maculatum",
            alternativeNames = listOf("Schierling"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "besonders junge Triebe und Samen"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Brennen im Mund", "aufsteigende Lähmung", "Krämpfe", "Atemlähmung"),
            identificationFeatures = listOf("Rote Flecken am Stängel", "Fein gefiederte Blätter", "Mäuseuringeruch"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Wegrand", "Brachland", "Ufer"),
            firstAidSteps = listOf("Sofort Tierarzt", "Aktivkohle", "Beatmung"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "tollkirsche",
            name = "Schwarze Tollkirsche",
            latinName = "Atropa belladonna",
            alternativeNames = listOf("Belladonna"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Beeren", "Blätter", "Wurzel"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Trockener Mund", "weite Pupillen", "Erregung", "Krämpfe", "Atemlähmung"),
            identificationFeatures = listOf("Glänzend schwarze Beeren", "Violett-braune Glockenblüten"),
            bloomMonths = listOf(6, 7, 8),
            habitat = listOf("Wald", "Lichtung", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Physostigmin als Antidot möglich"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "wunderbaum",
            name = "Wunderbaum",
            latinName = "Ricinus communis",
            alternativeNames = listOf("Rizinus"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Samen (Rizin)"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Schwere Magen-Darm-Blutungen", "Organversagen", "Krämpfe", "Tod"),
            identificationFeatures = listOf("Große handförmige Blätter", "Stachelige Früchte"),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Garten", "Zierpflanze"),
            firstAidSteps = listOf("Sofort Tierarzt", "Intensivmedizin", "Magenspülung"),
            mustCallVet = true,
            note = "Rizin ist eines der stärksten Gifte. Zerkaute Samen sind extrem gefährlich."
        ),

        ToxicPlant(
            id = "stechapfel",
            name = "Stechapfel",
            latinName = "Datura stramonium",
            alternativeNames = listOf("Dornapfel"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "besonders Samen"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Halluzinationen", "Herzrasen", "Trockene Schleimhäute", "Lähmung"),
            identificationFeatures = listOf("Weiße Trichterblüten", "Stachelige Kapselfrüchte"),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitat = listOf("Brachland", "Garten", "Wegrand"),
            firstAidSteps = listOf("Sofort Tierarzt", "Supportive Care"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "adonisroeschen",
            name = "Adonisröschen",
            latinName = "Adonis vernalis",
            alternativeNames = listOf("Frühlings-Adonisröschen"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Herzrhythmusstörungen", "Benommenheit", "Krämpfe"),
            identificationFeatures = listOf("Gelbe Blüten", "Fein geschlitzte Blätter"),
            bloomMonths = listOf(4, 5),
            habitat = listOf("Steppe", "Trockenrasen", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Herzmonitoring"),
            mustCallVet = true
        ),

        // ── STARK GIFTIG (HIGHLY TOXIC) ──────────────────────────

        ToxicPlant(
            id = "maigloeckchen",
            name = "Maiglöckchen",
            latinName = "Convallaria majalis",
            alternativeNames = listOf("Maienröschen"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "auch Vasenwasser"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Durchfall", "Herzrhythmusstörungen", "Schwäche"),
            identificationFeatures = listOf("Weiße Glockenblüten", "Rote Beeren", "Ovale Blätter"),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Wald", "Garten"),
            firstAidSteps = listOf("Tierarzt sofort", "Kein Erbrechen induzieren", "EKG"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "fingerhut",
            name = "Roter Fingerhut",
            latinName = "Digitalis purpurea",
            alternativeNames = listOf("Fingerhut"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Blätter und Samen"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "blutiger Durchfall", "Herzrhythmusstörungen", "Tod möglich"),
            identificationFeatures = listOf("Purpurne Glockenblüten", "Hoher Blütenstand", "Behaarte Blätter"),
            bloomMonths = listOf(6, 7, 8),
            habitat = listOf("Waldrand", "Lichtung", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "EKG", "Aktivkohle"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "kirschlorbeer",
            name = "Kirschlorbeer",
            latinName = "Prunus laurocerasus",
            alternativeNames = listOf("Lorbeerkirsche"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Blätter", "Samen/Kerne", "Rinde"),
            affectedSystems = listOf(PlantBodySystem.RESPIRATORY, PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Speichelfluss", "Atemnot", "Zellatmungshemmung", "Atemlähmung"),
            identificationFeatures = listOf("Immergrüner Strauch", "Glänzende ledrige Blätter", "Schwarze Früchte"),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Hecke", "Garten", "Park"),
            firstAidSteps = listOf("Sofort Tierarzt", "Blausäure-Antidot möglich", "Frische Luft"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "rhododendron",
            name = "Rhododendron / Azalee",
            latinName = "Rhododendron spp.",
            alternativeNames = listOf("Alpenrose", "Azalee"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Blätter und Blüten"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Erbrechen", "Speichelfluss", "Schwäche", "Bradykardie", "Krämpfe"),
            identificationFeatures = listOf("Große ledrige Blätter", "Bunte Blütendolden"),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Park", "Gebirge"),
            firstAidSteps = listOf("Tierarzt", "Aktivkohle", "Infusionen"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "buchsbaum",
            name = "Buchsbaum",
            latinName = "Buxus sempervirens",
            alternativeNames = listOf("Buchs"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Blätter", "Rinde"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Erbrechen", "Durchfall", "Zittern", "Lähmungen", "Atemlähmung"),
            identificationFeatures = listOf("Kleine ovale Blätter", "Immergrüner Strauch"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Garten", "Park", "Hecke"),
            firstAidSteps = listOf("Tierarzt", "Krampftherapie", "Atemsupport"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "seidelbast",
            name = "Seidelbast",
            latinName = "Daphne mezereum",
            alternativeNames = listOf("Kellerhals"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Beeren", "Rinde", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptoms = listOf("Brennen im Mund", "blutiger Durchfall", "Krämpfe", "Lähmungen"),
            identificationFeatures = listOf("Rosa Blüten vor Laub", "Rote Beeren am Stängel"),
            bloomMonths = listOf(2, 3, 4),
            habitat = listOf("Wald", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Aktivkohle"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "engelstrompete",
            name = "Engelstrompete",
            latinName = "Brugmansia spp.",
            alternativeNames = listOf("Brugmansia"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Herzrhythmusstörungen", "Halluzinationen", "Koma"),
            identificationFeatures = listOf("Große hängende Trompetenblüten", "Starker Duft nachts"),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitat = listOf("Garten", "Terrasse", "Kübelpflanze"),
            firstAidSteps = listOf("Sofort Tierarzt", "Physostigmin möglich"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "bilsenkraut",
            name = "Schwarzes Bilsenkraut",
            latinName = "Hyoscyamus niger",
            alternativeNames = listOf("Bilsenkraut"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Samen", "Wurzel", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Pupillenerweiterung", "Herzrasen", "Erregung", "Krämpfe"),
            identificationFeatures = listOf("Gelblich-violette Blüten", "Klebrige Blätter"),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitat = listOf("Brachland", "Wegrand", "Garten"),
            firstAidSteps = listOf("Sofort Tierarzt", "Supportive Care"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "christrose",
            name = "Christrose",
            latinName = "Helleborus niger",
            alternativeNames = listOf("Schneerose"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Wurzel"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Herzprobleme", "Lähmung"),
            identificationFeatures = listOf("Weiße Blüten im Winter", "Ledrige gefingerte Blätter"),
            bloomMonths = listOf(12, 1, 2, 3, 4),
            habitat = listOf("Garten", "Alpen"),
            firstAidSteps = listOf("Sofort Tierarzt", "Herzmonitoring"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "dieffenbachia",
            name = "Dieffenbachia",
            latinName = "Dieffenbachia seguine",
            alternativeNames = listOf("Giftwurz"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Milchsaft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Massive Schwellung im Mund", "Speichelfluss", "Atemnot durch Schwellung"),
            identificationFeatures = listOf("Große grün-weiß gemusterte Blätter"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Zimmerpflanze"),
            firstAidSteps = listOf("Mund sofort spülen", "Tierarzt wegen Erstickungsgefahr"),
            mustCallVet = true,
            note = "Enthält Calciumoxalat-Nadeln, die sich in Schleimhäute bohren."
        ),

        ToxicPlant(
            id = "rittersporn",
            name = "Rittersporn",
            latinName = "Delphinium spp.",
            alternativeNames = listOf("Lerchensporn"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Samen"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Speichelfluss", "Krämpfe", "Lähmungen"),
            identificationFeatures = listOf("Hohe Blütenkerzen", "Blau/Rosa/Weiße Blüten"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Garten"),
            firstAidSteps = listOf("Tierarzt", "Krampftherapie"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "robinie",
            name = "Robinie",
            latinName = "Robinia pseudoacacia",
            alternativeNames = listOf("Falsche Akazie"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Rinde", "Blätter", "Samen"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Erbrechen", "Durchfall", "Schwäche", "Krämpfe"),
            identificationFeatures = listOf("Gefiederte Blätter", "Dornen", "Weiße Blütentrauben"),
            bloomMonths = listOf(5, 6),
            habitat = listOf("Park", "Garten", "Wald"),
            firstAidSteps = listOf("Tierarzt", "Aktivkohle"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "jakobskreuzkraut",
            name = "Jakobskreuzkraut",
            latinName = "Jacobaea vulgaris",
            alternativeNames = listOf("Jakobsgreiskraut"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders getrocknet"),
            affectedSystems = listOf(PlantBodySystem.LIVER),
            symptoms = listOf("Leberschäden (oft spät)", "Gelbsucht", "Gewichtsverlust"),
            identificationFeatures = listOf("Gelbe Korbblüten", "Tief eingeschnittene Blätter"),
            bloomMonths = listOf(7, 8, 9),
            habitat = listOf("Weide", "Wegrand", "Magerwiese"),
            firstAidSteps = listOf("Bei Verdacht: Lebercheck beim Tierarzt"),
            mustCallVet = false,
            note = "Wirkt kumulativ über Wochen."
        ),

        ToxicPlant(
            id = "schwarzer-holunder-roh",
            name = "Schwarzer Holunder (roh)",
            latinName = "Sambucus nigra",
            alternativeNames = listOf("Holunderbeere"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Rohe Beeren", "Blätter", "Rinde"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Heftiges Erbrechen", "Durchfall", "Atemnot bei großen Mengen"),
            identificationFeatures = listOf("Weiße Blütendolden", "Schwarze Beeren"),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Waldrand", "Hecke", "Garten"),
            firstAidSteps = listOf("Tierarzt bei größeren Mengen", "Viel Wasser"),
            mustCallVet = false
        ),

        // ── GIFTIG (MODERATELY TOXIC) ────────────────────────────

        ToxicPlant(
            id = "baerlauch",
            name = "Bärlauch",
            latinName = "Allium ursinum",
            alternativeNames = listOf("Wilder Knoblauch"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Zwiebeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.BLOOD),
            symptoms = listOf("Erbrechen", "Durchfall", "Anämie (Blutarmut)", "Schwäche"),
            identificationFeatures = listOf("Knoblauchgeruch", "Breite ovale Blätter"),
            bloomMonths = listOf(3, 4, 5, 6),
            habitat = listOf("Wald", "Auwald"),
            firstAidSteps = listOf("Tierarzt bei größeren Mengen"),
            mustCallVet = false,
            confusionRisk = "Maiglöckchen, Herbstzeitlose (beide ohne Knoblauchgeruch)"
        ),

        ToxicPlant(
            id = "narzisse",
            name = "Narzisse",
            latinName = "Narcissus spp.",
            alternativeNames = listOf("Osterglocke"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Zwiebeln", "Blätter", "Blüten"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Speichelfluss", "Erbrechen", "Durchfall", "Krämpfe bei großen Mengen"),
            identificationFeatures = listOf("Gelbe/Weiße Blüten mit Trompete"),
            bloomMonths = listOf(2, 3, 4, 5),
            habitat = listOf("Garten", "Park", "Rasen"),
            firstAidSteps = listOf("Tierarzt anrufen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "buschwindroeschen",
            name = "Buschwindröschen",
            latinName = "Anemone nemorosa",
            alternativeNames = listOf("Windröschen"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf("Reizung", "Speichelfluss", "Erbrechen", "Durchfall"),
            identificationFeatures = listOf("Weiße Blüten im Frühling"),
            bloomMonths = listOf(3, 4, 5),
            habitat = listOf("Wald", "Garten"),
            firstAidSteps = listOf("Maul ausspülen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "stechpalme",
            name = "Stechpalme",
            latinName = "Ilex aquifolium",
            alternativeNames = listOf("Ilex"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Beeren", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Durchfall", "Bauchschmerzen"),
            identificationFeatures = listOf("Stachelige glänzende Blätter", "Rote Beeren"),
            bloomMonths = listOf(11, 12, 1),
            habitat = listOf("Wald", "Garten", "Hecke"),
            firstAidSteps = listOf("Tierarzt bei mehr als 2-3 Beeren"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "tulpe",
            name = "Tulpe",
            latinName = "Tulipa spp.",
            alternativeNames = listOf("Gartentulpe"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Zwiebeln (besonders)", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf("Erbrechen", "Durchfall", "Speichelfluss", "Hautreizung"),
            identificationFeatures = listOf("Kelchförmige Blüten", "Breite parallele Blätter"),
            bloomMonths = listOf(3, 4, 5),
            habitat = listOf("Garten", "Park"),
            firstAidSteps = listOf("Tierarzt bei Zwiebelaufnahme"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "clematis",
            name = "Clematis",
            latinName = "Clematis spp.",
            alternativeNames = listOf("Waldrebe"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Saft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Schleimhautreizung", "Speichelfluss", "Erbrechen"),
            identificationFeatures = listOf("Kletterpflanze", "Große gefiederte Blüten"),
            bloomMonths = listOf(5, 6, 7, 8, 9),
            habitat = listOf("Garten", "Zaun"),
            firstAidSteps = listOf("Maul ausspülen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "wisteria",
            name = "Glyzinie",
            latinName = "Wisteria sinensis",
            alternativeNames = listOf("Blauregen"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Samen", "Hülsen", "Rinde"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Bauchschmerzen", "Krämpfe"),
            identificationFeatures = listOf("Hängende lila Blütentrauben", "Bohnenähnliche Hülsen"),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Hauswand"),
            firstAidSteps = listOf("Tierarzt bei Hülsen-Aufnahme"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "efeu",
            name = "Efeu",
            latinName = "Hedera helix",
            alternativeNames = listOf("Gemeiner Efeu"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blätter", "Beeren", "Saft"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Hautreizung", "Krämpfe bei großen Mengen"),
            identificationFeatures = listOf("Kletterpflanze", "Gelappte Blätter", "Schwarze Beeren"),
            bloomMonths = listOf(9, 10, 11, 12),
            habitat = listOf("Wald", "Garten", "Mauer"),
            firstAidSteps = listOf("Haut abwaschen", "Tierarzt bei Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "aronstab",
            name = "Aronstab",
            latinName = "Arum maculatum",
            alternativeNames = listOf("Gefleckter Aronstab"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Beeren", "Knolle", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.RESPIRATORY),
            symptoms = listOf("Brennen im Mund", "Schwellung im Rachen", "Erbrechen", "Atemnot"),
            identificationFeatures = listOf("Pfeilförmige Blätter", "Blütenkolben", "Rote Beeren"),
            bloomMonths = listOf(4, 5, 8, 9, 10),
            habitat = listOf("Laubwald", "Hecke", "Park"),
            firstAidSteps = listOf("Mund spülen", "Tierarzt bei Schwellung"),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "adlerfarn",
            name = "Adlerfarn",
            latinName = "Pteridium aquilinum",
            alternativeNames = listOf("Farn"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders junge Wedel"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.BLOOD),
            symptoms = listOf("Erbrechen", "Durchfall", "Thiaminmangel", "neurologische Störungen"),
            identificationFeatures = listOf("Großer Farn", "Eingekringelte junge Wedel"),
            bloomMonths = listOf(5, 6, 7, 8, 9),
            habitat = listOf("Wald", "Heide", "Garten"),
            firstAidSteps = listOf("Aktivkohle", "Thiamin-Supplementation (Vet)"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "rainfarn",
            name = "Rainfarn",
            latinName = "Tanacetum vulgare",
            alternativeNames = listOf("Wurmkraut"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blüten", "Blätter", "Stängel"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Erbrechen", "Entzündung Magen/Darm", "Krämpfe", "Herzrhythmusstörungen"),
            identificationFeatures = listOf("Gelbe knopfartige Blüten", "Fiederige Blätter"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Wegrand", "Wiese", "Brachland"),
            firstAidSteps = listOf("Tierarzt bei Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "pfaffenhuetchen",
            name = "Pfaffenhütchen",
            latinName = "Euonymus europaeus",
            alternativeNames = listOf("Spindelstrauch"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Samen", "Früchte"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Bauchschmerzen", "Übelkeit", "Erbrechen", "herztoxische Effekte"),
            identificationFeatures = listOf("Rosa-rote vierlappige Früchte", "Orange Samen"),
            bloomMonths = listOf(5, 6, 9, 10),
            habitat = listOf("Wald", "Hecke", "Garten"),
            firstAidSteps = listOf("Tierarzt bei größeren Mengen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "nachtschatten",
            name = "Bittersüßer Nachtschatten",
            latinName = "Solanum dulcamara",
            alternativeNames = listOf("Nachtschatten"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders unreife Früchte"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Lähmungen bei großen Mengen"),
            identificationFeatures = listOf("Violette Blüten", "Rote Beeren", "Herzförmige Blätter"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Wald", "Ufer", "Garten"),
            firstAidSteps = listOf("Tierarzt bei Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "hortensie",
            name = "Hortensie",
            latinName = "Hydrangea macrophylla",
            alternativeNames = listOf("Gartenhortensie"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blätter", "Blüten"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Krämpfe bei großen Mengen"),
            identificationFeatures = listOf("Große Blütenballen", "Gezackte Blätter"),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Garten", "Kübelpflanze"),
            firstAidSteps = listOf("Tierarzt bei Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "hyazinthe",
            name = "Hyazinthe",
            latinName = "Hyacinthus orientalis",
            alternativeNames = listOf("Gartenhyazinthe"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Zwiebeln (besonders)"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "blutiger Durchfall bei großen Mengen"),
            identificationFeatures = listOf("Dichte Blütentrauben", "Starker Duft"),
            bloomMonths = listOf(3, 4, 5),
            habitat = listOf("Garten", "Topfpflanze"),
            firstAidSteps = listOf("Tierarzt bei Zwiebelaufnahme"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "alpenveilchen",
            name = "Alpenveilchen",
            latinName = "Cyclamen persicum",
            alternativeNames = listOf("Zyklame"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Knolle", "Blätter", "Blüten"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Erbrechen", "Durchfall", "Speichelfluss", "Herzprobleme"),
            identificationFeatures = listOf("Herzförmige gemusterte Blätter", "Zurückgeschlagene Blütenblätter"),
            bloomMonths = listOf(9, 10, 11, 12, 1, 2, 3),
            habitat = listOf("Zimmerpflanze", "Garten"),
            firstAidSteps = listOf("Tierarzt"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "amaryllis",
            name = "Amaryllis",
            latinName = "Hippeastrum spp.",
            alternativeNames = listOf("Ritterstern"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Zwiebeln (besonders)"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf("Erbrechen", "Durchfall", "Herzrhythmusstörungen"),
            identificationFeatures = listOf("Große trichterförmige Blüten", "Dicker hohler Stängel"),
            bloomMonths = listOf(11, 12, 1, 2),
            habitat = listOf("Zimmerpflanze", "Schnittblume"),
            firstAidSteps = listOf("Tierarzt bei Zwiebelaufnahme"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "clivia",
            name = "Clivia",
            latinName = "Clivia miniata",
            alternativeNames = listOf("Riemenblatt"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Wurzelstock"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Schweißausbrüche", "neurologische Störungen"),
            identificationFeatures = listOf("Riemenförmige Blätter", "Orange Blütendolden"),
            bloomMonths = listOf(2, 3, 4, 5),
            habitat = listOf("Zimmerpflanze"),
            firstAidSteps = listOf("Tierarzt"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "philodendron",
            name = "Philodendron",
            latinName = "Philodendron spp.",
            alternativeNames = listOf("Baumfreund"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blätter", "Stängel", "Saft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Brennen", "Speichelfluss", "Erbrechen", "Schluckbeschwerden"),
            identificationFeatures = listOf("Kletterpflanze", "Oft herzförmige Blätter"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Zimmerpflanze"),
            firstAidSteps = listOf("Mund spülen", "Tierarzt bei starken Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "chrysantheme",
            name = "Chrysantheme",
            latinName = "Chrysanthemum spp.",
            alternativeNames = listOf("Winteraster"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blüten", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptoms = listOf("Durchfall", "Atemnot", "Gleichgewichtsstörungen", "Hautreizung"),
            identificationFeatures = listOf("Dichte Blütenköpfe", "Typischer Herbstblüher"),
            bloomMonths = listOf(8, 9, 10, 11),
            habitat = listOf("Garten", "Park", "Balkon"),
            firstAidSteps = listOf("Tierarzt bei größeren Mengen"),
            mustCallVet = false
        ),

        // ── LEICHT GIFTIG (SLIGHTLY TOXIC) ───────────────────────

        ToxicPlant(
            id = "thuja",
            name = "Thuja",
            latinName = "Thuja occidentalis",
            alternativeNames = listOf("Lebensbaum"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Alle Teile", "ätherische Öle"),
            affectedSystems = listOf(PlantBodySystem.KIDNEYS, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "Nierenschäden bei Daueraufnahme"),
            identificationFeatures = listOf("Immergrün", "Schuppenartige Blätter"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Hecke", "Garten"),
            firstAidSteps = listOf("Maul ausspülen", "Tierarzt bei größeren Mengen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "primel",
            name = "Primel",
            latinName = "Primula obconica",
            alternativeNames = listOf("Giftprimel"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blätter", "Stängelsaft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Hautreizung", "Juckreiz", "Erbrechen bei Aufnahme"),
            identificationFeatures = listOf("Runzlige behaarte Blätter", "Bunte Blüten"),
            bloomMonths = listOf(2, 3, 4, 5),
            habitat = listOf("Balkon", "Garten", "Zimmer"),
            firstAidSteps = listOf("Haut abwaschen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "liguster",
            name = "Liguster",
            latinName = "Ligustrum vulgare",
            alternativeNames = listOf("Hartriegel"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Beeren", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Durchfall"),
            identificationFeatures = listOf("Heckenstrauch", "Schwarze Beeren im Herbst"),
            bloomMonths = listOf(6, 7, 9, 10, 11),
            habitat = listOf("Hecke", "Garten", "Park"),
            firstAidSteps = listOf("Viel Wasser anbieten"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "ficus",
            name = "Ficus / Gummibaum",
            latinName = "Ficus elastica / benjamina",
            alternativeNames = listOf("Birkenfeige"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Milchsaft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Reizung", "Erbrechen", "Durchfall", "Hautreizung"),
            identificationFeatures = listOf("Glänzende Blätter", "Milchiger Saft"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Zimmerpflanze"),
            firstAidSteps = listOf("Spülen", "Waschen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "weihnachtsstern",
            name = "Weihnachtsstern",
            latinName = "Euphorbia pulcherrima",
            alternativeNames = listOf("Adventsstern"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Milchsaft"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptoms = listOf("Erbrechen", "Durchfall", "neurologische Störungen bei großen Mengen"),
            identificationFeatures = listOf("Rote Hochblätter im Winter"),
            bloomMonths = listOf(11, 12, 1),
            habitat = listOf("Zimmerpflanze"),
            firstAidSteps = listOf("Tierarzt bei Symptomen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "aloevera",
            name = "Aloe Vera",
            latinName = "Aloe vera",
            alternativeNames = listOf("Wüstenlilie"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blattsaft (Aloin)"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Erbrechen", "Durchfall", "Koliken"),
            identificationFeatures = listOf("Fleischige gezackte Blätter"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Zimmerpflanze", "Heilpflanze"),
            firstAidSteps = listOf("Tierarzt bei größeren Mengen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "geranie",
            name = "Geranie",
            latinName = "Pelargonium spp.",
            alternativeNames = listOf("Pelargonie"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blätter", "Stängel", "Blüten"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf("Leichte Magen-Darm-Beschwerden", "Hautreizung"),
            identificationFeatures = listOf("Balkonblume", "Behaarte Blätter"),
            bloomMonths = listOf(5, 6, 7, 8, 9, 10),
            habitat = listOf("Balkon", "Garten"),
            firstAidSteps = listOf("Maul spülen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "agave",
            name = "Agave",
            latinName = "Agave spp.",
            alternativeNames = listOf("Hundertjährige Agave"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blattsaft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf("Reizung", "Erbrechen"),
            identificationFeatures = listOf("Große Rosetten", "Spitze fleischige Blätter"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Garten", "Topfpflanze"),
            firstAidSteps = listOf("Spülen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "begonie",
            name = "Begonie",
            latinName = "Begonia spp.",
            alternativeNames = listOf("Schiefblatt"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf("Leichte Reizung", "Erbrechen", "Durchfall"),
            identificationFeatures = listOf("Asymmetrische Blätter", "Fleischige Stängel"),
            bloomMonths = listOf(5, 6, 7, 8, 9, 10),
            habitat = listOf("Garten", "Zimmer"),
            firstAidSteps = listOf("Maul spülen"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "wacholder",
            name = "Wacholder",
            latinName = "Juniperus communis",
            alternativeNames = listOf("Feuerbaum"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Beeren", "Nadeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.KIDNEYS),
            symptoms = listOf("Leichte Magen-Darm-Beschwerden", "Nierenreizung bei großen Mengen"),
            identificationFeatures = listOf("Immergrüner Nadelstrauch", "Blaue Scheinbeeren"),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Heide", "Wald", "Garten"),
            firstAidSteps = listOf("Viel Wasser"),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "riesenbaerenklau",
            name = "Riesenbärenklau",
            latinName = "Heracleum mantegazzianum",
            alternativeNames = listOf("Herkulesstaude"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Saft der Blätter und Stängel"),
            affectedSystems = listOf(PlantBodySystem.SKIN),
            symptoms = listOf("Phototoxische Verbrennungen", "Rötung", "Blasen bei Sonnenlicht"),
            identificationFeatures = listOf("Sehr große Staude (bis 3-4m)", "Große weiße Dolden"),
            bloomMonths = listOf(6, 7, 8),
            habitat = listOf("Wegrand", "Ufer", "Waldrand"),
            firstAidSteps = listOf("Sofort mit Wasser und Seife waschen", "Vor Sonne schützen"),
            mustCallVet = true,
            note = "Dermatotoxisch. Kontakt mit Saft + UV-Licht führt zu schweren Verbrennungen."
        )
    )

    // ── Suchfunktionen ───────────────────────────────────────────

    fun search(query: String): List<ToxicPlant> {
        if (query.length < 2) return plants
        val q = query.lowercase().trim()
        return plants.filter { it.allSearchNames().contains(q) }
    }

    fun getBySeason(): List<ToxicPlant> {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return plants.filter { month in it.bloomMonths }
            .sortedByDescending { it.toxicityLevel.ordinal }
    }

    fun getByToxicity(level: PlantToxicityLevel): List<ToxicPlant> =
        plants.filter { it.toxicityLevel == level }
            .sortedBy { it.name }

    fun getById(id: String): ToxicPlant? =
        plants.firstOrNull { it.id == id }

    fun currentMonth(): String {
        val months = arrayOf("Januar", "Februar", "März", "April", "Mai", "Juni",
                             "Juli", "August", "September", "Oktober", "November", "Dezember")
        return months[Calendar.getInstance().get(Calendar.MONTH)]
    }

    fun currentMonthInt(): Int =
        Calendar.getInstance().get(Calendar.MONTH) + 1
}
