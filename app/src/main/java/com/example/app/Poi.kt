package com.hundewildnis.app

// ✅ ERWEITERTE POI Datenklasse mit allen OSM-Details
data class POI(
    val id: String,
    val name: String,
    val category: String,
    val categoryIcon: String,
    val lat: Double,
    val lon: Double,
    val distance: Double = 0.0,
    val tags: Map<String, String> = emptyMap(),
    val bearing: Double = 0.0,
    val direction: String = "",
    val directionArrow: String = "",

    // ✅ NEU: Erweiterte Details aus OSM
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val openingHours: String? = null,
    val description: String? = null,
    val operator: String? = null,
    val access: String? = null
)

// POI Kategorien mit Icons
object POICategories {

    // Kategorie-Definition
    data class Category(
        val name: String,
        val icon: String,
        val queries: List<String>,
        val color: String = "#8fad6f"
    )

    val ALL_CATEGORIES = listOf(

        // 🐕 HUND & TIER
        Category(
            name = "Hundeauslauf",
            icon = "🐕",
            queries = listOf("leisure=dog_park"),
            color = "#8fad6f"
        ),
        Category(
            name = "Tierarzt",
            icon = "🏥",
            queries = listOf("amenity=veterinary")
        ),
        Category(
            name = "Tierbedarf",
            icon = "🛒",
            queries = listOf("shop=pet")
        ),
        Category(
            name = "Tierheim",
            icon = "🏠",
            queries = listOf("amenity=animal_shelter", "amenity=animal_boarding")
        ),

        // 🥾 WANDERN & WEGE
        Category(
            name = "Wanderweg",
            icon = "🥾",
            queries = listOf(
                "highway=footway",
                "highway=path",
                "highway=track"
            ),
            color = "#ff9800"
        ),
        Category(
            name = "Aussichtspunkt",
            icon = "🏔️",
            queries = listOf("tourism=viewpoint", "natural=peak")
        ),
        Category(
            name = "Wegweiser",
            icon = "🪧",
            queries = listOf(
                "tourism=information",
                "information=guidepost",
                "information=board"
            )
        ),

        // 🏕️ RAST & UNTERSTAND
        Category(
            name = "Schutzhütte",
            icon = "⛺",
            queries = listOf("amenity=shelter")
        ),
        Category(
            name = "Picknickplatz",
            icon = "🧺",
            queries = listOf("leisure=picnic_site")
        ),
        Category(
            name = "Sitzbank",
            icon = "🪑",
            queries = listOf("amenity=bench")
        ),
        Category(
            name = "Feuerstelle",
            icon = "🔥",
            queries = listOf("leisure=firepit", "amenity=bbq")
        ),

        // 💧 WASSER & VERSORGUNG
        Category(
            name = "Trinkwasser",
            icon = "💧",
            queries = listOf(
                "amenity=drinking_water",
                "amenity=water_point",
                "natural=spring"
            ),
            color = "#2196F3"
        ),
        Category(
            name = "Toilette",
            icon = "🚻",
            queries = listOf("amenity=toilets")
        ),
        Category(
            name = "Supermarkt",
            icon = "🏪",
            queries = listOf("shop=supermarket", "shop=convenience")
        ),
        Category(
            name = "Bäckerei",
            icon = "🥖",
            queries = listOf("shop=bakery")
        ),
        Category(
            name = "Outdoor-Shop",
            icon = "🎒",
            queries = listOf("shop=outdoor")
        ),
        Category(
            name = "Restaurant",
            icon = "🍽️",
            queries = listOf("amenity=restaurant", "amenity=cafe")
        ),

        // 🅿️ PARKEN
        Category(
            name = "Parkplatz",
            icon = "🅿️",
            queries = listOf("amenity=parking")
        ),

        // 🚨 NOTFALL & GESUNDHEIT
        Category(
            name = "Apotheke",
            icon = "💊",
            queries = listOf("amenity=pharmacy", "healthcare=pharmacy"),
            color = "#f44336"
        ),
        Category(
            name = "Arzt",
            icon = "⚕️",
            queries = listOf(
                "amenity=doctors", 
                "amenity=hospital", 
                "amenity=clinic",
                "amenity=dentist",
                "healthcare" // ✅ Alle Healthcare-Einträge einschließen
            )
        ),
        Category(
            name = "Polizei",
            icon = "🚔",
            queries = listOf("amenity=police")
        ),
        Category(
            name = "Feuerwehr",
            icon = "🚒",
            queries = listOf("amenity=fire_station")
        ),
        Category(
            name = "Notfall",
            icon = "🆘",
            queries = listOf("amenity=emergency")
        ),

        // 🌲 NATURSCHUTZ
        Category(
            name = "Naturschutzgebiet",
            icon = "🌲",
            queries = listOf(
                "leisure=nature_reserve",
                "boundary=protected_area"
            ),
            color = "#4CAF50"
        ),
        Category(
            name = "Nationalpark",
            icon = "🏞️",
            queries = listOf("boundary=national_park")
        )
    )

    // Overpass Query für alle Kategorien generieren
    fun generateOverpassQuery(lat: Double, lon: Double, radius: Int = 10000): String {
        val queries = ALL_CATEGORIES.flatMap { category ->
            category.queries.map { query ->
                val parts = query.split("=")
                if (parts.size == 2) {
                    """node["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);"""
                } else if (query.isNotEmpty()) {
                    // ✅ Unterstützt nun auch einfache Keys (z.B. "healthcare")
                    """node["$query"](around:$radius,$lat,$lon);"""
                } else {
                    ""
                }
            }
        }.filter { it.isNotEmpty() }

        return """
            [out:json][timeout:25];
            (
              ${queries.joinToString("\n  ")}
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()
    }

    // Kategorie aus OSM-Tags bestimmen
    fun getCategoryForTags(tags: Map<String, String>): Category? {
        return ALL_CATEGORIES.firstOrNull { category ->
            category.queries.any { query ->
                val parts = query.split("=")
                if (parts.size == 2) {
                    tags[parts[0]] == parts[1]
                } else {
                    // ✅ Prüft ob der Key existiert
                    tags.containsKey(query)
                }
            }
        }
    }
}
