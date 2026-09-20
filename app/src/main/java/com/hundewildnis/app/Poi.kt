package com.hundewildnis.app

import android.content.Context
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

    fun getSurfaceName(context: Context, code: Int): String = when (code) {
        0  -> context.getString(R.string.surface_unknown)
        1  -> context.getString(R.string.surface_paved)
        2  -> context.getString(R.string.surface_unpaved)
        3  -> context.getString(R.string.surface_asphalt)
        4  -> context.getString(R.string.surface_concrete)
        5  -> context.getString(R.string.surface_cobblestone)
        6  -> context.getString(R.string.surface_metal)
        7  -> context.getString(R.string.surface_wood)
        8  -> context.getString(R.string.surface_gravel_path)
        9  -> context.getString(R.string.surface_fine_gravel)
        10 -> context.getString(R.string.surface_gravel)
        11 -> context.getString(R.string.surface_dirt)
        12 -> context.getString(R.string.surface_ground)
        13 -> context.getString(R.string.surface_ice)
        14 -> context.getString(R.string.surface_paving_stones)
        15 -> context.getString(R.string.surface_sand)
        16 -> context.getString(R.string.surface_wood_chips)
        17 -> context.getString(R.string.surface_grass)
        18 -> context.getString(R.string.surface_grass_paver)
        else -> context.getString(R.string.surface_other)
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

    fun getWayTypeName(context: Context, code: Int): String = when (code) {
        0  -> context.getString(R.string.waytype_unknown)
        1  -> context.getString(R.string.waytype_state_road)
        2  -> context.getString(R.string.waytype_road)
        3  -> context.getString(R.string.waytype_road)
        4  -> context.getString(R.string.waytype_path)
        5  -> context.getString(R.string.waytype_track)
        6  -> context.getString(R.string.waytype_cycleway)
        7  -> context.getString(R.string.waytype_footway)
        8  -> context.getString(R.string.waytype_steps)
        9  -> context.getString(R.string.waytype_ferry)
        10 -> context.getString(R.string.waytype_construction)
        else -> context.getString(R.string.waytype_other)
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
    // Stabiler, sprachunabhängiger Schlüssel für Kategorie-Vergleiche/Logik
    // (z.B. Hundeverbot/Leinenzwang-Hervorhebung). "category" bleibt der
    // übersetzte Anzeigetext, "categoryKey" wird für Vergleiche verwendet.
    val categoryKey: String = "",
    val categoryIcon: String,
    val subtype: String? = null,
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
    val access: String? = null,
    // Für Overpass-"way"/"relation"-Objekte: die tatsächliche Umriss-Geometrie
    // (Reihenfolge der Way-Nodes). Bei einer Relation wird nur der erste
    // brauchbare Way-Ring verwendet (siehe createPOIFromWayOrRelation).
    // null bei "node"-POIs oder wenn keine Geometrie rekonstruierbar war.
    val outline: List<GeoPoint>? = null,
    // true = geschlossener Ring (Fläche, z.B. Naturschutzgebiet) → als Polygon zeichnen
    // false = offene Linie (z.B. Wanderweg) → als Polyline zeichnen
    val outlineIsClosed: Boolean = false
)

// ═══════════════════════════════════════════════════════════
// POI KATEGORIEN
// ═══════════════════════════════════════════════════════════

object POICategories {

    data class Category(
        // Stabiler, sprachunabhängiger Schlüssel für Vergleiche/Lookups
        // (z.B. beim Kachel-Klick oder der Gewitterschutz-Sonderbehandlung).
        val key: String,
        // Deutscher Basistext, als Fallback und Resource-Lookup-Basis genutzt.
        // Für die Anzeige immer displayName(context) verwenden, NIE direkt name.
        val name: String,
        val icon: String,
        val queries: List<String>,
        val color: String = "#424200"
    ) {
        fun displayName(context: Context): String = when (key) {
            "dog_park"            -> context.getString(R.string.poi_cat_dog_park)
            "dog_training"        -> context.getString(R.string.poi_cat_dog_training)
            "leash_required"      -> context.getString(R.string.poi_cat_leash_required)
            "dog_ban"             -> context.getString(R.string.poi_cat_dog_ban)
            "dog_swimming"        -> context.getString(R.string.poi_cat_dog_swimming)
            "dog_waste_disposal"  -> context.getString(R.string.poi_cat_dog_waste_disposal)
            "poop_bag_dispenser"  -> context.getString(R.string.poi_cat_poop_bag_dispenser)
            "veterinarian"        -> context.getString(R.string.poi_cat_veterinarian)
            "pet_supplies"        -> context.getString(R.string.poi_cat_pet_supplies)
            "animal_shelter"      -> context.getString(R.string.poi_cat_animal_shelter)
            "hiking_trail"        -> context.getString(R.string.poi_cat_hiking_trail)
            "summit_cross"        -> context.getString(R.string.poi_cat_summit_cross)
            "viewpoint"           -> context.getString(R.string.poi_cat_viewpoint)
            "signpost"            -> context.getString(R.string.poi_cat_signpost)
            "shelter"             -> context.getString(R.string.poi_cat_shelter)
            "campsite"            -> context.getString(R.string.poi_cat_campsite)
            "picnic_site"         -> context.getString(R.string.poi_cat_picnic_site)
            "bench"               -> context.getString(R.string.poi_cat_bench)
            "firepit"             -> context.getString(R.string.poi_cat_firepit)
            "drinking_water"      -> context.getString(R.string.poi_cat_drinking_water)
            "toilet"              -> context.getString(R.string.poi_cat_toilet)
            "supermarket"         -> context.getString(R.string.poi_cat_supermarket)
            "bakery"              -> context.getString(R.string.poi_cat_bakery)
            "outdoor_shop"        -> context.getString(R.string.poi_cat_outdoor_shop)
            "restaurant"          -> context.getString(R.string.poi_cat_restaurant)
            "parking"             -> context.getString(R.string.poi_cat_parking)
            "pharmacy"            -> context.getString(R.string.poi_cat_pharmacy)
            "doctor"              -> context.getString(R.string.poi_cat_doctor)
            "police"              -> context.getString(R.string.poi_cat_police)
            "fire_department"     -> context.getString(R.string.poi_cat_fire_department)
            "emergency"           -> context.getString(R.string.poi_cat_emergency)
            "nature_reserve"      -> context.getString(R.string.poi_cat_nature_reserve)
            "national_park"       -> context.getString(R.string.poi_cat_national_park)
            "thunderstorm_shelter"-> context.getString(R.string.poi_cat_thunderstorm_shelter)
            else                  -> name
        }
    }

    // Stabiler Key für die Gewitterschutz-Sonderbehandlung (Sicherheitshinweis-Dialog,
    // erweiterter Suchradius, Brücken-Ergänzung). NIEMALS gegen übersetzten Text
    // vergleichen - siehe Category.key oben.
    const val KEY_THUNDERSTORM_SHELTER = "thunderstorm_shelter"

    val ALL_CATEGORIES = listOf(
        Category(key = "dog_park",             name = "Hundepark/-sport",  icon = "🐕", queries = listOf("leisure=dog_park")),
        Category(key = "dog_training",         name = "Hundetraining",     icon = "🐩", queries = listOf("amenity=animal_training", "animal_training=dog")),
        Category(key = "leash_required",       name = "Leinenzwang",       icon = "🦮", queries = listOf("dog=leashed", "dog=leash")),
        Category(key = "dog_ban",              name = "Hundeverbot",       icon = "🚫", queries = listOf("dog=no")),
        Category(key = "dog_swimming",         name = "Hundebadestelle",   icon = "🐾", queries = listOf("dog=designated", "dog=yes")),
        Category(key = "dog_waste_disposal",   name = "Hundekotentsorgung",icon = "🗑️", queries = listOf("amenity=waste_basket", "amenity=dog_waste_bin", "waste=dog_excrement")),
        Category(key = "poop_bag_dispenser",   name = "Kotbeutelspender",  icon = "💩", queries = listOf("vending=excrement_bags")),
        Category(key = "veterinarian",         name = "Tierarzt",          icon = "🏥", queries = listOf("amenity=veterinary"), color = "#aa0000"),
        Category(key = "pet_supplies",         name = "Tierbedarf",        icon = "🛒", queries = listOf("shop=pet")),
        Category(key = "animal_shelter",       name = "Tierheim",          icon = "🏠", queries = listOf("amenity=animal_shelter", "amenity=animal_boarding")),
        Category(key = "hiking_trail",         name = "Wanderweg",         icon = "🥾", queries = listOf("highway=footway", "highway=path", "highway=track"), color = "#ff9800"),
        Category(key = "summit_cross",         name = "Gipfel/Kreuz",      icon = "🏔️", queries = listOf("natural=peak", "man_made=cross", "summit:cross=yes")),
        Category(key = "viewpoint",            name = "Aussichtspunkt",    icon = "🔭", queries = listOf("tourism=viewpoint")),
        Category(key = "signpost",             name = "Wegweiser",         icon = "🪧", queries = listOf("tourism=information", "information=guidepost", "information=board")),
        Category(key = "shelter",              name = "Schutzhütte",       icon = "⛺", queries = listOf("amenity=shelter", "tourism=alpine_hut", "tourism=wilderness_hut")),
        Category(key = "campsite",             name = "Zeltplatz",         icon = "⛺", queries = listOf("tourism=camp_site", "tourism=caravan_site")),
        Category(key = "picnic_site",          name = "Picknickplatz",     icon = "🧺", queries = listOf("leisure=picnic_site")),
        Category(key = "bench",                name = "Sitzbank",          icon = "🪑", queries = listOf("amenity=bench")),
        Category(key = "firepit",              name = "Feuerstelle",       icon = "🔥", queries = listOf("leisure=firepit", "amenity=bbq")),
        Category(key = "drinking_water",       name = "Trinkwasser",       icon = "💧", queries = listOf("amenity=drinking_water", "amenity=water_point", "natural=spring"), color = "#2196F3"),
        Category(key = "toilet",               name = "Toilette",          icon = "🚻", queries = listOf("amenity=toilets")),
        Category(key = "supermarket",          name = "Supermarkt",        icon = "🏪", queries = listOf("shop=supermarket", "shop=convenience")),
        Category(key = "bakery",               name = "Bäckerei",          icon = "🥖", queries = listOf("shop=bakery")),
        Category(key = "outdoor_shop",         name = "Outdoor-Shop",      icon = "🎒", queries = listOf("shop=outdoor")),
        Category(key = "restaurant",           name = "Restaurant",        icon = "🍽️", queries = listOf("amenity=restaurant", "amenity=cafe", "amenity=fast_food", "amenity=pub", "amenity=bar", "amenity=biergarten")),
        Category(key = "parking",              name = "Parkplatz",         icon = "🅿️", queries = listOf("amenity=parking")),
        Category(key = "pharmacy",             name = "Apotheke",          icon = "💊", queries = listOf("amenity=pharmacy", "healthcare=pharmacy"), color = "#aa0000"),
        Category(key = "doctor",               name = "Arzt",              icon = "⚕️", queries = listOf("amenity=doctors", "amenity=hospital", "amenity=clinic", "amenity=dentist", "healthcare"), color = "#aa0000"),
        Category(key = "police",               name = "Polizei",           icon = "🚔", queries = listOf("amenity=police"), color = "#aa0000"),
        Category(key = "fire_department",      name = "Feuerwehr",         icon = "🚒", queries = listOf("amenity=fire_station", "emergency=fire_hydrant"), color = "#aa0000"),
        Category(key = "emergency",            name = "Notfall",           icon = "🆘", queries = listOf("emergency=defibrillator", "emergency=ambulance_station", "emergency=rescue_station", "amenity=rescue_station", "emergency=mountain_rescue", "emergency=water_rescue", "emergency=access_point", "emergency=phone", "emergency=first_aid_kit", "emergency=lifeguard", "healthcare=hospital"), color = "#550000"),
        Category(key = "nature_reserve",       name = "Naturschutzgebiet", icon = "🌲", queries = listOf("leisure=nature_reserve", "boundary=protected_area")),
        Category(key = "national_park",        name = "Nationalpark",      icon = "🏞️", queries = listOf("boundary=national_park")),
        // Nur feste, allseitig geschlossene Gebäude – bewusst OHNE einfache/offene
        // Schutzhütten (amenity=shelter ohne Wände) und OHNE generisches building=*
        // (würde in besiedelten Gebieten tausende irrelevante Wohnhäuser liefern).
        // Ebenfalls bewusst OHNE Brücken/Tunnel/Unterführungen: Der DWD nennt
        // Unterführungen explizit als zu MEIDENDEN Gefahrenbereich (akute
        // Überflutungsgefahr bei Starkregen), und offene Brücken bieten keinen
        // zuverlässigen Blitzschutz. Diese Objekte würden hier ein falsches
        // Sicherheitsgefühl vermitteln.
        Category(key = KEY_THUNDERSTORM_SHELTER, name = "Gewitterschutz", icon = "⛈️", queries = listOf(
            "tourism=alpine_hut", "tourism=wilderness_hut", "building=cabin",
            "amenity=restaurant", "amenity=cafe", "tourism=hotel", "tourism=guest_house",
            "building=chapel", "amenity=place_of_worship",
            "shop=supermarket", "shop=convenience", "shop=mall",
            "amenity=fuel", "railway=station"
        ), color = "#5A6B7A")
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

// ═══════════════════════════════════════════════════════════
// ROUTE-POI-TYPEN (entlang der Route, unabhängig von POICategories)
// Eigenständige POI-Typen wie Bänke, Trinkwasser, Gipfel etc.,
// die entlang der aktiven Route gefunden werden (getRoutePOIInfo in
// LocationActivity.kt). Stabile Keys werden für die Hundeverbot-/
// Leinenzwang-Hervorhebung in der Route-POI-Liste benötigt.
// ═══════════════════════════════════════════════════════════

object RoutePOITypes {

    const val KEY_DOG_BAN = "dog_ban"
    const val KEY_LEASH_REQUIRED = "leash_required"

    fun displayName(context: Context, key: String): String = when (key) {
        "bench"           -> context.getString(R.string.poi_cat_bench)
        "drinking_water"  -> context.getString(R.string.poi_cat_drinking_water)
        "spring"          -> context.getString(R.string.route_poi_spring)
        "toilet"          -> context.getString(R.string.poi_cat_toilet)
        "dog_toilet"      -> context.getString(R.string.route_poi_dog_toilet)
        "dog_training"    -> context.getString(R.string.poi_cat_dog_training)
        KEY_DOG_BAN        -> context.getString(R.string.poi_cat_dog_ban)
        KEY_LEASH_REQUIRED -> context.getString(R.string.poi_cat_leash_required)
        "waste_basket"    -> context.getString(R.string.route_poi_waste_basket)
        "firepit"         -> context.getString(R.string.route_poi_firepit)
        "bbq"             -> context.getString(R.string.route_poi_bbq)
        "viewpoint"       -> context.getString(R.string.poi_cat_viewpoint)
        "peak"            -> context.getString(R.string.route_poi_peak)
        "shelter"         -> context.getString(R.string.poi_cat_shelter)
        "picnic_site"     -> context.getString(R.string.poi_cat_picnic_site)
        "emergency"       -> context.getString(R.string.poi_cat_emergency)
        "nature_reserve"  -> context.getString(R.string.poi_cat_nature_reserve)
        "national_park"   -> context.getString(R.string.poi_cat_national_park)
        else              -> key
    }
}