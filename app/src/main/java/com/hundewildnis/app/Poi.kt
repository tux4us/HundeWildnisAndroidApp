package com.hundewildnis.app

import org.osmdroid.util.GeoPoint

// ═══════════════════════════════════════════════════════════
// ROUTE DATA - Navigationsdaten inkl. Höhen & Oberfläche
// ═══════════════════════════════════════════════════════════

data class SurfaceSummary(
    val code: Int,
    val name: String,
    val emoji: String,
    val distance: Double,
    val percentage: Double
)

data class RouteData(
    val points: List<GeoPoint>,
    val distance: Double,
    val duration: Double,
    val elevationPoints: List<Double> = emptyList(),
    val totalAscent: Double = 0.0,
    val totalDescent: Double = 0.0,
    val minElevation: Double = 0.0,
    val maxElevation: Double = 0.0,
    val surfaceSummary: List<SurfaceSummary> = emptyList(),
    val wayTypeSummary: List<SurfaceSummary> = emptyList()
)

// ═══════════════════════════════════════════════════════════
// OBERFLÄCHEN- UND WEGTYP-HILFSFUNKTIONEN
// ═══════════════════════════════════════════════════════════

object RouteInfoHelper {

    fun getSurfaceName(code: Int): String = when (code) {
        0  -> "Unbekannt"
        1  -> "Befestigt"
        2  -> "Unbefestigt"
        3  -> "Asphalt"
        4  -> "Beton"
        5  -> "Kopfsteinpflaster"
        6  -> "Metall"
        7  -> "Holz"
        8  -> "Schotterpfad"
        9  -> "Feinschotter"
        10 -> "Schotter"
        11 -> "Erde"
        12 -> "Untergrund"
        13 -> "Eis"
        14 -> "Pflastersteine"
        15 -> "Sand"
        16 -> "Holzspäne"
        17 -> "Gras"
        18 -> "Rasengitter"
        else -> "Sonstige"
    }

    fun getSurfaceEmoji(code: Int): String = when (code) {
        3, 4     -> "🛣️"
        5, 14    -> "🧱"
        7        -> "🪵"
        8, 9, 10 -> "⛰️"
        11, 12   -> "🌿"
        13       -> "🧊"
        15       -> "🏖️"
        17       -> "🌾"
        else     -> "🛤️"
    }

    fun getSurfaceColor(code: Int): Int = when (code) {
        3, 4     -> android.graphics.Color.parseColor("#888888")
        5, 14    -> android.graphics.Color.parseColor("#9E9E9E")
        8, 9, 10 -> android.graphics.Color.parseColor("#A0845C")
        11, 12   -> android.graphics.Color.parseColor("#795548")
        17       -> android.graphics.Color.parseColor("#66BB6A")
        15       -> android.graphics.Color.parseColor("#FFB74D")
        else     -> android.graphics.Color.parseColor("#8FC357")
    }

    fun getWayTypeName(code: Int): String = when (code) {
        0  -> "Unbekannt"
        1  -> "Staatsstraße"
        2  -> "Straße"
        3  -> "Straße"
        4  -> "Pfad"
        5  -> "Wirtschaftsweg"
        6  -> "Radweg"
        7  -> "Fußweg"
        8  -> "Treppen"
        9  -> "Fähre"
        10 -> "Baustelle"
        else -> "Sonstige"
    }

    fun getWayTypeEmoji(code: Int): String = when (code) {
        1, 2, 3 -> "🛣️"
        4       -> "🥾"
        5       -> "🌲"
        6       -> "🚴"
        7       -> "🚶"
        8       -> "🪜"
        9       -> "⛵"
        else    -> "🛤️"
    }

    fun getWayTypeColor(code: Int): Int = when (code) {
        1, 2, 3 -> android.graphics.Color.parseColor("#78909C")
        4, 5    -> android.graphics.Color.parseColor("#8D6E63")
        7       -> android.graphics.Color.parseColor("#8FC357")
        8       -> android.graphics.Color.parseColor("#FF7043")
        else    -> android.graphics.Color.parseColor("#8FC357")
    }
}

// ═══════════════════════════════════════════════════════════
// POI DATENKLASSE
// ═══════════════════════════════════════════════════════════

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
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val openingHours: String? = null,
    val description: String? = null,
    val operator: String? = null,
    val access: String? = null
)

// ═══════════════════════════════════════════════════════════
// POI KATEGORIEN
// ═══════════════════════════════════════════════════════════

object POICategories {

    data class Category(
        val name: String,
        val icon: String,
        val queries: List<String>,
        val color: String = "#424200"
    )

    val ALL_CATEGORIES = listOf(
        Category(name = "Hundepark/-sport",      icon = "🐕", queries = listOf("leisure=dog_park")),
        Category(name = "Hundetraining",     icon = "🐩", queries = listOf("amenity=animal_training", "animal_training=dog")),
        Category(name = "Leinenzwang",       icon = "🦮", queries = listOf("dog=leashed", "dog=leash")),
        Category(name = "Hundeverbot",       icon = "🚫", queries = listOf("dog=no")),
        Category(name = "Hundebadestelle",   icon = "🐾", queries = listOf("dog=designated", "dog=yes")),
        Category(name = "Abfalleimer",       icon = "🗑️", queries = listOf("amenity=waste_basket", "amenity=dog_waste_bin", "waste=dog_excrement")),
        Category(name = "Hundekotbeutel",    icon = "💩", queries = listOf("vending=excrement_bags")),
        Category(name = "Tierarzt",          icon = "🏥", queries = listOf("amenity=veterinary"), color = "#aa0000"),
        Category(name = "Tierbedarf",        icon = "🛒", queries = listOf("shop=pet")),
        Category(name = "Tierheim",          icon = "🏠", queries = listOf("amenity=animal_shelter", "amenity=animal_boarding")),
        Category(name = "Wanderweg",         icon = "🥾", queries = listOf("highway=footway", "highway=path", "highway=track"), color = "#ff9800"),
        Category(name = "Gipfel/Kreuz",      icon = "🏔️", queries = listOf("natural=peak", "man_made=cross", "summit:cross=yes")),
        Category(name = "Aussichtspunkt",    icon = "🔭", queries = listOf("tourism=viewpoint")),
        Category(name = "Wegweiser",         icon = "🪧", queries = listOf("tourism=information", "information=guidepost", "information=board")),
        Category(name = "Schutzhütte",       icon = "⛺", queries = listOf("amenity=shelter", "tourism=alpine_hut", "tourism=wilderness_hut")),
        Category(name = "Zeltplatz",         icon = "⛺", queries = listOf("tourism=camp_site", "tourism=caravan_site")),
        Category(name = "Picknickplatz",     icon = "🧺", queries = listOf("leisure=picnic_site")),
        Category(name = "Sitzbank",          icon = "🪑", queries = listOf("amenity=bench")),
        Category(name = "Feuerstelle",       icon = "🔥", queries = listOf("leisure=firepit", "amenity=bbq")),
        Category(name = "Trinkwasser",       icon = "💧", queries = listOf("amenity=drinking_water", "amenity=water_point", "natural=spring"), color = "#2196F3"),
        Category(name = "Toilette",          icon = "🚻", queries = listOf("amenity=toilets")),
        Category(name = "Supermarkt",        icon = "🏪", queries = listOf("shop=supermarket", "shop=convenience")),
        Category(name = "Bäckerei",          icon = "🥖", queries = listOf("shop=bakery")),
        Category(name = "Outdoor-Shop",      icon = "🎒", queries = listOf("shop=outdoor")),
        Category(name = "Restaurant",        icon = "🍽️", queries = listOf("amenity=restaurant", "amenity=cafe", "amenity=fast_food", "amenity=pub", "amenity=bar", "amenity=biergarten")),
        Category(name = "Parkplatz",         icon = "🅿️", queries = listOf("amenity=parking")),
        Category(name = "Apotheke",          icon = "💊", queries = listOf("amenity=pharmacy", "healthcare=pharmacy"), color = "#aa0000"),
        Category(name = "Arzt",              icon = "⚕️", queries = listOf("amenity=doctors", "amenity=hospital", "amenity=clinic", "amenity=dentist", "healthcare"), color = "#aa0000"),
        Category(name = "Polizei",           icon = "🚔", queries = listOf("amenity=police"), color = "#aa0000"),
        Category(name = "Feuerwehr",         icon = "🚒", queries = listOf("amenity=fire_station"), color = "#aa0000"),
        Category(name = "Notfall",           icon = "🆘", queries = listOf("emergency=defibrillator", "emergency=ambulance_station", "amenity=rescue_station", "emergency=mountain_rescue", "emergency=access_point", "emergency=first_aid_kit", "emergency=lifeguard", "healthcare=hospital", "emergency"), color = "#550000"),
        Category(name = "Naturschutzgebiet", icon = "🌲", queries = listOf("leisure=nature_reserve", "boundary=protected_area")),
        Category(name = "Nationalpark",      icon = "🏞️", queries = listOf("boundary=national_park"))
    )

    fun generateOverpassQuery(lat: Double, lon: Double, radius: Int = 10000): String {
        val queries = ALL_CATEGORIES.flatMap { category ->
            category.queries.flatMap { query ->
                val parts = query.split("=")
                if (parts.size == 2) {
                    listOf(
                        """node["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                        """way["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                        """relation["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);"""
                    )
                } else if (query.isNotEmpty()) {
                    listOf(
                        """node["$query"](around:$radius,$lat,$lon);""",
                        """way["$query"](around:$radius,$lat,$lon);""",
                        """relation["$query"](around:$radius,$lat,$lon);"""
                    )
                } else emptyList()
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

    fun getCategoryForTags(tags: Map<String, String>): Category? {
        return ALL_CATEGORIES.firstOrNull { category ->
            category.queries.any { query ->
                val parts = query.split("=")
                if (parts.size == 2) tags[parts[0]] == parts[1]
                else tags.containsKey(query)
            }
        }
    }
}
