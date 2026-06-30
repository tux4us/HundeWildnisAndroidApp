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
// PFLANZENDATENBANK – 40 Einträge
// ════════════════════════════════════════════════════════════════
object ToxicPlantDatabase {

    val plants: List<ToxicPlant> = listOf(

        // ── TÖDLICH ─────────────────────────────────────────────

        ToxicPlant(
            id = "eibe",
            name = "Eibe",
            latinName = "Taxus baccata",
            alternativeNames = listOf("Eibenbaum", "Taxus"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile außer dem roten Fruchtfleisch", "Besonders Nadeln und Samen"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.NERVOUS),
            symptoms = listOf(
                "Zittern und Muskelkrämpfe",
                "Herzrhythmusstörungen bis Herzstillstand",
                "Koordinationsstörungen",
                "Erbrechen",
                "Plötzlicher Tod ohne Vorwarnung möglich"
            ),
            identificationFeatures = listOf(
                "Immergrüner Nadelbaum oder Strauch",
                "Flache, dunkelgrüne Nadeln – oben glänzend, unten matt",
                "Rote, beerenartige Früchte (Arillus) mit schwarzem Samen",
                "Häufig in Gärten, Parks und Friedhöfen"
            ),
            bloomMonths = listOf(1, 2, 3, 4, 9, 10, 11, 12),
            habitat = listOf("Garten", "Park", "Friedhof", "Hecke"),
            firstAidSteps = listOf(
                "Sofort Tierarzt anrufen – keine Zeit verlieren",
                "Erbrochenes oder Pflanzenreste sichern und mitnehmen",
                "Hund ruhig halten, nicht rennen lassen",
                "Kein Erbrechen herbeiführen (Herzbelastung)",
                "Herzfrequenz beobachten"
            ),
            mustCallVet = true,
            note = "Eine der giftigsten Pflanzen für Hunde in Deutschland. Bereits wenige Gramm Nadeln können tödlich sein."
        ),

        ToxicPlant(
            id = "herbstzeitlose",
            name = "Herbstzeitlose",
            latinName = "Colchicum autumnale",
            alternativeNames = listOf("Wiesenzeitlose", "Nackte Jungfer", "Zeitlose"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "Besonders Zwiebel und Samen"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Starker Speichelfluss",
                "Heftiges Erbrechen und Durchfall",
                "Lähmungserscheinungen",
                "Atemnot",
                "Multiorganversagen"
            ),
            identificationFeatures = listOf(
                "Blüht im Herbst (Aug–Okt) ohne Blätter – nur Blüte aus dem Boden",
                "Lila-rosafarbene trichterförmige Blüten",
                "Breite, glänzende Blätter im Frühjahr – dann ohne Blüte",
                "Auf Wiesen und Weiden"
            ),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Wiese", "Weide", "feuchte Wiesen", "Wald"),
            firstAidSteps = listOf(
                "Sofort Tierarzt oder Tierklinik anrufen",
                "Keine Behandlung zu Hause – sofortiger Tierarztbesuch",
                "Aufnahme-Zeitpunkt und Menge notieren"
            ),
            mustCallVet = true,
            confusionRisk = "Bärlauch-Blätter im Frühjahr (riecht nicht nach Knoblauch!)",
            note = "Enthält Colchicin – kein Gegenmittel verfügbar. Jede Minute zählt."
        ),

        ToxicPlant(
            id = "goldregen",
            name = "Goldregen",
            latinName = "Laburnum anagyroides",
            alternativeNames = listOf("Gewöhnlicher Goldregen", "Bohnenbaum"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "Besonders Samen und Blüten"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Starker Speichelfluss",
                "Erbrechen",
                "Muskelzittern und Krämpfe",
                "Herzrasen",
                "Bewusstlosigkeit"
            ),
            identificationFeatures = listOf(
                "Kleiner Baum oder Strauch",
                "Hängende gelbe Blütentrauben (Apr–Jun)",
                "Hülsenfrüchte ähnlich Erbsenschoten",
                "Dreiteilige ovale Blätter"
            ),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Park", "Straße"),
            firstAidSteps = listOf(
                "Sofort Tierarzt anrufen",
                "Aufgenommene Menge notieren",
                "Hund beobachten – Krämpfe sofort melden"
            ),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "oleander",
            name = "Oleander",
            latinName = "Nerium oleander",
            alternativeNames = listOf("Rosenlorbeer"),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicParts = listOf("Alle Teile", "auch das Wasser im Blumentopf"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Erbrechen",
                "Herzrhythmusstörungen",
                "Schwäche und Kollaps",
                "Plötzlicher Herzstillstand"
            ),
            identificationFeatures = listOf(
                "Immergrüner Strauch",
                "Ledrige, lanzettliche Blätter",
                "Weiße, rosa oder rote Blüten",
                "Häufig als Kübelpflanze auf Terrassen"
            ),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Garten", "Terrasse", "Kübelpflanze"),
            firstAidSteps = listOf(
                "Sofort Tierarzt – auch bei kleinen Mengen",
                "Hund von der Pflanze entfernen",
                "Maul mit Wasser ausspülen lassen"
            ),
            mustCallVet = true,
            note = "Bereits ein einziges Blatt kann für einen mittelgroßen Hund tödlich sein."
        ),

        // ── STARK GIFTIG ─────────────────────────────────────────

        ToxicPlant(
            id = "maiglöckchen",
            name = "Maiglöckchen",
            latinName = "Convallaria majalis",
            alternativeNames = listOf("Maienröschen", "Maiblume"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "auch das Blumenwasser"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Erbrechen und Durchfall",
                "Herzrhythmusstörungen",
                "Schwäche",
                "Bewusstlosigkeit"
            ),
            identificationFeatures = listOf(
                "Breite ovale Blätter ähnlich Bärlauch – aber kein Knoblauchgeruch",
                "Kleine weiße Glockenblüten an gebogenem Stiel (Mai)",
                "Rote Beeren im Herbst",
                "Schattige Wälder und Gärten"
            ),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Wald", "Garten", "Schattige Standorte"),
            firstAidSteps = listOf(
                "Tierarzt sofort anrufen",
                "Kein Erbrechen herbeiführen",
                "Herzfrequenz beobachten"
            ),
            mustCallVet = true,
            confusionRisk = "Bärlauch (beide haben ähnlich breite Blätter – Bärlauch riecht nach Knoblauch!)"
        ),

        ToxicPlant(
            id = "blauer-eisenhut",
            name = "Blauer Eisenhut",
            latinName = "Aconitum napellus",
            alternativeNames = listOf("Sturmhut", "Wolfswurz", "Mönchskappe"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders die Wurzel"),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Brennen im Maul",
                "Starker Speichelfluss",
                "Koordinationsstörungen",
                "Herzrhythmusstörungen",
                "Lähmung"
            ),
            identificationFeatures = listOf(
                "Aufrechter Stängel bis 150 cm",
                "Tief eingeschnittene, handförmige Blätter",
                "Charakteristische blau-violette Helmblüten (Jul–Sep)",
                "Bergwiesen, Gebirge, Gärten"
            ),
            bloomMonths = listOf(6, 7, 8, 9),
            habitat = listOf("Bergwiese", "Gebirge", "Garten", "Waldrand"),
            firstAidSteps = listOf(
                "Sofort Tierarzt",
                "Hautkontakt mit Pflanzensaft vermeiden (auch für Menschen giftig!)",
                "Hund ruhig halten"
            ),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "herbstkrokusse",
            name = "Krokus (Herbst)",
            latinName = "Crocus sativus / Colchicum",
            alternativeNames = listOf("Safran-Krokus", "Herbstkrokus"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Zwiebeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Speichelfluss",
                "Schwäche"
            ),
            identificationFeatures = listOf(
                "Kelchförmige Blüten direkt aus dem Boden",
                "Lila bis weiß",
                "Frühjahrs-Krokus (ungiftig) blüht früher"
            ),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Garten", "Park", "Rasen"),
            firstAidSteps = listOf(
                "Tierarzt anrufen",
                "Aufgenommene Menge schätzen und mitteilen"
            ),
            mustCallVet = true,
            confusionRisk = "Ungiftiger Frühlingskrokus (blüht März–April)"
        ),

        ToxicPlant(
            id = "fingerhut",
            name = "Roter Fingerhut",
            latinName = "Digitalis purpurea",
            alternativeNames = listOf("Fingerhut", "Waldglocke"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Erbrechen und Durchfall",
                "Herzrhythmusstörungen",
                "Schwäche und Kollaps"
            ),
            identificationFeatures = listOf(
                "Hohe Stängel bis 150 cm",
                "Charakteristische glockenförmige rote (seltener weiße) Blüten innen gepunktet",
                "Große, weich behaarte Blätter",
                "Waldränder, Lichtungen"
            ),
            bloomMonths = listOf(6, 7, 8),
            habitat = listOf("Waldrand", "Lichtung", "Garten"),
            firstAidSteps = listOf(
                "Sofort Tierarzt",
                "Herzrhythmus beobachten",
                "Ruhe bewahren, Hund nicht belasten"
            ),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "schwarzer-holunder-roh",
            name = "Schwarzer Holunder (roh)",
            latinName = "Sambucus nigra",
            alternativeNames = listOf("Holunderbeere", "Fliederbeere"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Rohe Beeren", "Blätter", "Rinde", "Wurzel"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Starkes Erbrechen",
                "Durchfall",
                "Schwäche"
            ),
            identificationFeatures = listOf(
                "Strauch oder kleiner Baum",
                "Federförmig gefiederte Blätter",
                "Weiße Blütendolden (Mai–Jul)",
                "Schwarze Beerentrauben (Aug–Sep)"
            ),
            bloomMonths = listOf(8, 9, 10),
            habitat = listOf("Waldrand", "Hecke", "Garten"),
            firstAidSteps = listOf(
                "Tierarzt anrufen bei größeren Mengen",
                "Viel frisches Wasser anbieten",
                "Erbrechen beobachten"
            ),
            mustCallVet = false,
            note = "Reife, gekochte Beeren sind ungiftig. Rohe Beeren und alle anderen Teile immer giftig."
        ),

        ToxicPlant(
            id = "jakobskreuzkraut",
            name = "Jakobskreuzkraut",
            latinName = "Jacobaea vulgaris",
            alternativeNames = listOf("Jakobsgreiskraut", "Stinkkraut", "Jakobskraut"),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders getrocknet im Heu"),
            affectedSystems = listOf(PlantBodySystem.LIVER),
            symptoms = listOf(
                "Leberschäden (oft erst Wochen später sichtbar)",
                "Gewichtsverlust",
                "Gelbsucht",
                "Verhaltensänderungen"
            ),
            identificationFeatures = listOf(
                "Gelbe Korbblüten in großen Rispen (Jul–Sep)",
                "Tief eingeschnittene Blätter",
                "Häufig auf Magerweiden, Wegrändern, Bahndämmen"
            ),
            bloomMonths = listOf(7, 8, 9),
            habitat = listOf("Weide", "Wegrand", "Bahndamm", "Magerwiese"),
            firstAidSteps = listOf(
                "Bei Verdacht auf regelmäßige Aufnahme: Tierarzt mit Lebercheck",
                "Hund von betroffenen Weiden fernhalten"
            ),
            mustCallVet = false,
            note = "Wirkt kumulativ – Schäden entstehen durch wiederholte kleine Dosen über Wochen."
        ),

        // ── GIFTIG ───────────────────────────────────────────────

        ToxicPlant(
            id = "bärlauch",
            name = "Bärlauch",
            latinName = "Allium ursinum",
            alternativeNames = listOf("Wilder Knoblauch", "Hexenzwiebel"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Zwiebeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.BLOOD),
            symptoms = listOf(
                "Erbrechen und Durchfall",
                "Hämolytische Anämie",
                "Schwäche",
                "Blasser Zahnfleisch"
            ),
            identificationFeatures = listOf(
                "Breite, ovale Blätter mit deutlichem Knoblauchgeruch",
                "Weiße, sternförmige Blüten in Dolde (Apr–Jun)",
                "Schattige feuchte Wälder"
            ),
            bloomMonths = listOf(3, 4, 5, 6),
            habitat = listOf("Wald", "feuchter Waldrand", "Auwald"),
            firstAidSteps = listOf(
                "Tierarzt anrufen bei größeren Mengen",
                "Hund nicht mehr an Bärlauch-Standorten grasen lassen"
            ),
            mustCallVet = false,
            note = "Bärlauch ist für Hunde und Katzen giftig – auch wenn Menschen ihn bedenkenlos essen. Verwechslungsgefahr mit Maiglöckchen (kein Knoblauchgeruch!).",
            confusionRisk = "Maiglöckchen, Herbstzeitlose (beide ohne Knoblauchgeruch)"
        ),

        ToxicPlant(
            id = "narzisse",
            name = "Narzisse",
            latinName = "Narcissus spp.",
            alternativeNames = listOf("Osterglocke", "Jonquille"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Zwiebeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf(
                "Starker Speichelfluss",
                "Erbrechen",
                "Durchfall",
                "Koordinationsstörungen"
            ),
            identificationFeatures = listOf(
                "Gelbe oder weiße Blüten mit charakteristischer Trompete",
                "Flache, bandförmige Blätter",
                "Typische Frühlingsblume (Feb–Mai)"
            ),
            bloomMonths = listOf(2, 3, 4, 5),
            habitat = listOf("Garten", "Park", "Rasen", "Wiese"),
            firstAidSteps = listOf(
                "Tierarzt anrufen",
                "Speichelfluss und Erbrechen beobachten",
                "Hund von Beeten fernhalten"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "herbst-anemone",
            name = "Buschwindröschen / Anemone",
            latinName = "Anemone nemorosa / Anemone hupehensis",
            alternativeNames = listOf("Windröschen", "Buschwindröschen"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile, besonders Saft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Reizung von Maul und Schleimhäuten",
                "Speichelfluss",
                "Erbrechen",
                "Hautreizung bei Berührung"
            ),
            identificationFeatures = listOf(
                "Weiße (Frühjahr) oder rosa/lila (Herbst) Blüten",
                "Gefingerte Blätter",
                "Wälder, Gärten"
            ),
            bloomMonths = listOf(3, 4, 5, 8, 9, 10),
            habitat = listOf("Wald", "Garten"),
            firstAidSteps = listOf(
                "Maul mit Wasser ausspülen",
                "Tierarzt bei anhaltenden Symptomen"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "stechpalme",
            name = "Stechpalme",
            latinName = "Ilex aquifolium",
            alternativeNames = listOf("Ilex", "Hülse", "Hülsenbaum"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Beeren", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Schwäche"
            ),
            identificationFeatures = listOf(
                "Immergrüner Strauch mit glänzenden, stacheligen Blättern",
                "Rote Beeren im Winter (Nov–Jan)",
                "Typische Weihnachtsdekoration"
            ),
            bloomMonths = listOf(11, 12, 1),
            habitat = listOf("Garten", "Wald", "Hecke"),
            firstAidSteps = listOf(
                "Tierarzt bei mehr als 2–3 Beeren",
                "Viel Wasser anbieten"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "kirschlorbeer",
            name = "Kirschlorbeer",
            latinName = "Prunus laurocerasus",
            alternativeNames = listOf("Lorbeerkirsche"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Blätter", "Samen", "unreife Früchte"),
            affectedSystems = listOf(PlantBodySystem.RESPIRATORY, PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Atemnot",
                "Erbrechen",
                "Schwäche",
                "Zyanose (Bläuliche Schleimhäute)"
            ),
            identificationFeatures = listOf(
                "Immergrüner Strauch, häufige Hecke",
                "Glänzende, ledrige Blätter",
                "Weiße Blütentrauben (Apr–Jun)",
                "Schwarze Früchte"
            ),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Hecke", "Park"),
            firstAidSteps = listOf(
                "Frische Luft",
                "Sofort Tierarzt bei Atemnot"
            ),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "rhododendron",
            name = "Rhododendron",
            latinName = "Rhododendron spp.",
            alternativeNames = listOf("Alpenrose", "Rostblättrige Alpenrose"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Erbrechen",
                "Speichelfluss",
                "Schwäche",
                "Herzrhythmusstörungen bei großen Mengen"
            ),
            identificationFeatures = listOf(
                "Immergrüner Strauch",
                "Große, ledrige Blätter",
                "Auffällige Blütenbüschel: weiß, rosa, rot, lila (Apr–Jun)"
            ),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Park", "Gebirge"),
            firstAidSteps = listOf(
                "Tierarzt anrufen",
                "Erbrechen beobachten"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "tulpe",
            name = "Tulpe",
            latinName = "Tulipa spp.",
            alternativeNames = listOf("Gartentulpe"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Zwiebeln"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Speichelfluss",
                "Hautreizung"
            ),
            identificationFeatures = listOf(
                "Kelchförmige Blüten, alle Farben",
                "Breite, parallele Blätter",
                "Zwiebelpflanze – Frühjahr (Mär–Mai)"
            ),
            bloomMonths = listOf(3, 4, 5),
            habitat = listOf("Garten", "Park"),
            firstAidSteps = listOf(
                "Tierarzt bei Zwiebel-Aufnahme",
                "Hund von Beeten fernhalten"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "clematis",
            name = "Clematis",
            latinName = "Clematis spp.",
            alternativeNames = listOf("Waldrebe", "Kletterpflanze"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Saft"),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Starke Reizung von Maul und Schleimhäuten",
                "Speichelfluss",
                "Erbrechen"
            ),
            identificationFeatures = listOf(
                "Kletterstrauch mit gegenständigen Blättern",
                "Große, gefiederte Blüten (Jun–Sep)",
                "Typische Kletterpflanze an Zäunen und Pergolen"
            ),
            bloomMonths = listOf(5, 6, 7, 8, 9),
            habitat = listOf("Garten", "Zaun", "Pergola"),
            firstAidSteps = listOf(
                "Maul mit Wasser ausspülen",
                "Tierarzt bei anhaltenden Symptomen"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "wisteria",
            name = "Glyzinie",
            latinName = "Wisteria sinensis",
            alternativeNames = listOf("Blauregen"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Samen", "Hülsen", "Rinde"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Bauchschmerzen"
            ),
            identificationFeatures = listOf(
                "Kräftige Kletterpflanze",
                "Hängende lila/weiße Blütentrauben (Apr–Jun)",
                "Hülsenfrüchte ähnlich Bohnen"
            ),
            bloomMonths = listOf(4, 5, 6),
            habitat = listOf("Garten", "Hauswand", "Pergola"),
            firstAidSteps = listOf(
                "Tierarzt bei Hülsen-Aufnahme",
                "Erbrechen beobachten"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "christrose",
            name = "Christrose",
            latinName = "Helleborus niger",
            alternativeNames = listOf("Schwarze Nieswurz", "Schneerose"),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Wurzel"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptoms = listOf(
                "Starker Speichelfluss",
                "Erbrechen",
                "Herzrhythmusstörungen"
            ),
            identificationFeatures = listOf(
                "Weiße Blüten im Winter (Dez–Apr)",
                "Ledrige, gefingerte Blätter",
                "Typische Winterpflanze in Gärten"
            ),
            bloomMonths = listOf(12, 1, 2, 3, 4),
            habitat = listOf("Garten"),
            firstAidSteps = listOf(
                "Tierarzt anrufen",
                "Erbrechen und Herzfrequenz beobachten"
            ),
            mustCallVet = true
        ),

        // ── LEICHT GIFTIG ────────────────────────────────────────

        ToxicPlant(
            id = "efeu",
            name = "Efeu",
            latinName = "Hedera helix",
            alternativeNames = listOf("Gemeiner Efeu"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blätter", "Beeren"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Speichelfluss",
                "Hautreizung"
            ),
            identificationFeatures = listOf(
                "Immergrüne Kletterpflanze",
                "Charakteristisch gelappte Blätter",
                "Schwarze Beeren (Herbst/Winter)",
                "Überall an Mauern, Bäumen, Böden"
            ),
            bloomMonths = listOf(9, 10, 11, 12),
            habitat = listOf("Wald", "Garten", "Mauer", "Baumstamm"),
            firstAidSteps = listOf(
                "Erbrechen beobachten",
                "Bei mehr als wenigen Blättern Tierarzt",
                "Haut mit Wasser abspülen"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "thuja",
            name = "Thuja",
            latinName = "Thuja occidentalis",
            alternativeNames = listOf("Lebensbaum", "Abendländischer Lebensbaum"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Alle Teile"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Koordinationsstörungen bei großen Mengen"
            ),
            identificationFeatures = listOf(
                "Immergrüner Nadelbaum / Strauch",
                "Schuppenartige Blättchen",
                "Häufigste Hecken-Pflanze in deutschen Gärten"
            ),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Garten", "Hecke"),
            firstAidSteps = listOf(
                "Beim Kauen von Ästen: Maul ausspülen",
                "Tierarzt bei größeren Mengen"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "primel",
            name = "Primel",
            latinName = "Primula obconica",
            alternativeNames = listOf("Zimmer-Primel", "Giftprimel"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Blätter", "Stängel (Saft)"),
            affectedSystems = listOf(PlantBodySystem.SKIN),
            symptoms = listOf(
                "Hautreizung und Rötung",
                "Juckreiz",
                "Bei Aufnahme: Erbrechen"
            ),
            identificationFeatures = listOf(
                "Runzlige, behaarte Blätter",
                "Bunte Blüten (rosa, rot, lila, weiß)",
                "Häufige Balkon- und Topfpflanze"
            ),
            bloomMonths = listOf(2, 3, 4, 5),
            habitat = listOf("Garten", "Balkon", "Innenraum"),
            firstAidSteps = listOf(
                "Pfotenkontakt mit Wasser abwaschen",
                "Tierarzt bei Aufnahme größerer Mengen"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "buchsbaum",
            name = "Buchsbaum",
            latinName = "Buxus sempervirens",
            alternativeNames = listOf("Buchs", "Buxbaum"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Alle Teile", "besonders Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall",
                "Muskelzittern bei großen Mengen"
            ),
            identificationFeatures = listOf(
                "Immergrüner Strauch",
                "Kleine, ovale, glänzende Blätter",
                "Typische Formschnitt-Pflanze in Gärten"
            ),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitat = listOf("Garten", "Park"),
            firstAidSteps = listOf(
                "Erbrechen beobachten",
                "Tierarzt bei Kauen größerer Äste"
            ),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "liguster",
            name = "Liguster",
            latinName = "Ligustrum vulgare",
            alternativeNames = listOf("Gewöhnlicher Liguster", "Hartriegel"),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicParts = listOf("Beeren", "Blätter"),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptoms = listOf(
                "Erbrechen",
                "Durchfall"
            ),
            identificationFeatures = listOf(
                "Häufiger Heckenstrauch",
                "Weiße Blütenrispen (Jun–Jul)",
                "Schwarze Beeren im Herbst"
            ),
            bloomMonths = listOf(9, 10, 11),
            habitat = listOf("Hecke", "Garten", "Stadtpark"),
            firstAidSteps = listOf(
                "Erbrechen beobachten",
                "Tierarzt bei mehr als einer Handvoll Beeren"
            ),
            mustCallVet = false
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
        val months = arrayOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun",
                             "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
        return months[Calendar.getInstance().get(Calendar.MONTH)]
    }

    fun currentMonthInt(): Int =
        Calendar.getInstance().get(Calendar.MONTH) + 1
}
