package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.DashPathEffect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.Context
import android.os.Bundle
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityLocationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import java.net.URLEncoder
import android.text.Editable
import android.text.TextWatcher

class LocationActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false
    private var isNavigating = false
    private var isFirstLocation = true

    private val poiList = mutableListOf<POI>()

    // ✅ Liste für POI-Marker
    private val poiMarkers = mutableListOf<Marker>()

    private var currentPOIMarker: Marker? = null
    private var currentRoutePolyline: Polyline? = null
    private var userLocationMarker: Marker? = null
    private var currentRouteDistance: Double = 0.0
    private var currentRouteDuration: Double = 0.0

    private var selectedCategory: POICategories.Category? = null

    private var searchResultMarkers = mutableListOf<Marker>()
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MIN_TIME_BETWEEN_UPDATES = 2000L
        private const val MIN_DISTANCE_CHANGE = 5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.getInstance().userAgentValue = packageName
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Standort & Ziele"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        setupMap()
        setupPOIList()
        setupButtons()
        setupOSMCopyright()
        checkLocationPermission()
        setupCategoryButtons()
        setupPOISearch()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        // ✅ MapEventsOverlay für zuverlässiges Touch-Handling
        val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                // Bei jedem Tap auf die Karte → Info-Fenster schließen
                poiMarkers.forEach { marker ->
                    marker.closeInfoWindow()
                }
                return false // Event nicht consumed, damit Map weiter funktioniert
            }

            override fun longPressHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                return false
            }
        }

        val mapEventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
        binding.mapView.overlays.add(0, mapEventsOverlay)  // Als erstes Overlay hinzufügen

        // ✅ Maßstabsbalken (ScaleBar) hinzufügen
        val scaleBarOverlay = ScaleBarOverlay(binding.mapView)
        scaleBarOverlay.setAlignRight(true)
        scaleBarOverlay.setAlignBottom(true)
        
        // WICHTIG: Metrisches System explizit setzen
        scaleBarOverlay.setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
        
        // Zentrierung und Genauigkeit verbessern
        scaleBarOverlay.setCentred(false)

        // Offset für Abstand zum Rand (in Pixeln)
        scaleBarOverlay.setScaleBarOffset(20, 20)
        binding.mapView.overlays.add(scaleBarOverlay)

        // ✅ MyLocationOverlay initialisieren
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        myLocationOverlay.enableMyLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    private fun setupPOIList() {
        binding.poiRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.poiRecyclerView.adapter = POIAdapter(poiList) { poi ->
            showPOIOnMap(poi)
        }
    }

    private fun setupButtons() {
        styleBackButton()

        binding.btnCurrentLocation.setOnClickListener {
            if (isNavigating) {
                stopNavigation()
            } else {
                centerOnCurrentLocation()
            }
        }

        binding.shareButton.setOnClickListener {
            shareLocation()
        }

        binding.btnBackToCategories.setOnClickListener {
            showCategoryView()
        }
    }

    private fun styleBackButton() {
        val isDarkTheme = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {
            // Dark Mode: Heller + größer
            binding.btnBackToCategories.apply {
                text = "← Zurück"
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#5a8038"))

                // DP in Pixel umrechnen!
                val heightInPixels = (56 * resources.displayMetrics.density).toInt()
                layoutParams = layoutParams.apply {
                    width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    height = heightInPixels
                }

                // Padding damit Text nicht abgeschnitten wird
                setPadding(24, 12, 24, 12)
            }
        } else {
            // Light Mode: Standard
            binding.btnBackToCategories.apply {
                text = "← Zurück"
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2d5016"))

                val heightInPixels = (56 * resources.displayMetrics.density).toInt()
                layoutParams = layoutParams.apply {
                    width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    height = heightInPixels
                }

                setPadding(24, 12, 24, 12)
            }
        }
    }

    private fun setupCategoryButtons() {
        binding.categoryGrid.removeAllViews()

        POICategories.ALL_CATEGORIES.forEach { category ->
            val button = com.google.android.material.button.MaterialButton(this).apply {
                text = "${category.icon}\n${category.name}"
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setPadding(8, 16, 8, 16)

                cornerRadius = 12
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    Color.parseColor(category.color)
                )
                setTextColor(Color.WHITE)

                val params = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                layoutParams = params

                setOnClickListener {
                    selectedCategory = category
                    loadPOIsForCategory(category)
                }
            }

            binding.categoryGrid.addView(button)
        }
    }

    private fun loadPOIsForCategory(category: POICategories.Category) {
        currentLocation?.let { location ->
            binding.poiProgressBar.visibility = View.VISIBLE
            binding.categoryView.visibility = View.GONE
            binding.poiListView.visibility = View.VISIBLE

            binding.categoryTitleText.text = "${category.icon} ${category.name}"

            lifecycleScope.launch {
                try {
                    val pois = withContext(Dispatchers.IO) {
                        fetchPOIsForCategory(location.latitude, location.longitude, category, 3000)
                    }

                    val sortedPOIs = pois.sortedBy { it.distance }

                    poiList.clear()
                    poiList.addAll(sortedPOIs)

                    binding.poiRecyclerView.adapter?.notifyDataSetChanged()
                    binding.poiCountText.text = "${pois.size} POI in 3 km"
                    binding.poiProgressBar.visibility = View.GONE

                    if (pois.isEmpty()) {
                        binding.emptyPoiText.visibility = View.VISIBLE
                        binding.emptyPoiText.text = "Keine ${category.name} im Umkreis von 3 km gefunden"
                    } else {
                        binding.emptyPoiText.visibility = View.GONE
                    }

                    // POIs auf Karte anzeigen
                    showPOIsOnMap(sortedPOIs, category)

                } catch (e: Exception) {
                    binding.poiProgressBar.visibility = View.GONE
                    Toast.makeText(this@LocationActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Standort noch nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPOIsOnMap(pois: List<POI>, category: POICategories.Category) {
        clearPOIMarkers()

        pois.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title = poi.name
                snippet = "${formatDistance(poi.distance)} • ${poi.direction}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // Erster Klick = Info, Zweiter Klick = Navigation
                var clickCount = 0
                setOnMarkerClickListener { clickedMarker, _ ->
                    clickCount++

                    if (clickCount == 1) {
                        // Erster Klick: Alle anderen schließen, dieses öffnen
                        poiMarkers.forEach { it.closeInfoWindow() }
                        clickedMarker.showInfoWindow()
                    } else {
                        // Zweiter Klick: Navigation starten
                        clickCount = 0  // Reset für nächstes Mal
                        startNavigationToPOI(poi)
                    }

                    true // Event consumed
                }
            }

            poiMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }

        binding.mapView.invalidate()

        if (pois.isNotEmpty()) {
            zoomToShowAllPOIs(pois)
        }
    }

    private fun startNavigationToPOI(poi: POI) {
        showPOIOnMap(poi)
    }

    private fun zoomToShowAllPOIs(pois: List<POI>) {
        if (pois.isEmpty()) return

        val allPoints = mutableListOf<GeoPoint>()

        // User-Position hinzufügen
        currentLocation?.let {
            allPoints.add(GeoPoint(it.latitude, it.longitude))
        }

        // Alle POI-Positionen hinzufügen
        pois.forEach { poi ->
            allPoints.add(GeoPoint(poi.lat, poi.lon))
        }

        if (allPoints.isEmpty()) return

        // Bounding Box berechnen
        var minLat = allPoints[0].latitude
        var maxLat = allPoints[0].latitude
        var minLon = allPoints[0].longitude
        var maxLon = allPoints[0].longitude

        allPoints.forEach { point ->
            minLat = minOf(minLat, point.latitude)
            maxLat = maxLat.coerceAtLeast(point.latitude)
            minLon = minLon.coerceAtMost(point.longitude)
            maxLon = maxLon.coerceAtLeast(point.longitude)
        }

        // Zentrum berechnen
        val centerLat = (minLat + maxLat) / 2
        val centerLon = (minLon + maxLon) / 2
        val center = GeoPoint(centerLat, centerLon)

        // Zoom-Level berechnen (je größer die Box, desto weiter rauszoomen)
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon
        val maxDiff = maxOf(latDiff, lonDiff)

        val zoomLevel = when {
            maxDiff > 0.1 -> 12.0  // > 10 km
            maxDiff > 0.05 -> 13.0 // 5-10 km
            maxDiff > 0.02 -> 14.0 // 2-5 km
            maxDiff > 0.01 -> 15.0 // 1-2 km
            else -> 16.0           // < 1 km
        }

        binding.mapView.controller.setCenter(center)
        binding.mapView.controller.setZoom(zoomLevel)
    }

    private fun clearPOIMarkers() {
        poiMarkers.forEach { marker ->
            binding.mapView.overlays.remove(marker)
        }
        poiMarkers.clear()
        binding.mapView.invalidate()
    }

    private suspend fun fetchPOIsForCategory(
        lat: Double,
        lon: Double,
        category: POICategories.Category,
        radius: Int = 3000
    ): List<POI> {
        val queries = category.queries.flatMap { query ->
            val parts = query.split("=")
            if (parts.size == 2) {
                listOf(
                    """node["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                    """way["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                    """relation["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);"""
                )
            } else {
                emptyList()
            }
        }.filter { it.isNotEmpty() }

        val overpassQuery = """
            [out:json][timeout:25];
            (
              ${queries.joinToString("\n  ")}
            );
            out body;
            >;
            out skel qt;
        """.trimIndent()

        val json = OverpassHelper.queryWithRetry(overpassQuery)
            ?: throw Exception("Overpass API nicht erreichbar")

        val elements = json.getJSONArray("elements")
        val pois = mutableListOf<POI>()

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)
            val elementType = element.getString("type")

            when (elementType) {
                "node" -> {
                    createPOIFromElement(element, lat, lon, category)?.let { poi ->
                        pois.add(poi)
                    }
                }
                "way", "relation" -> {
                    createPOIFromWayOrRelation(element, elements, lat, lon, category)?.let { poi ->
                        pois.add(poi)
                    }
                }
            }
        }

        return pois
    }

    private fun createPOIFromElement(
        element: JSONObject,
        userLat: Double,
        userLon: Double,
        category: POICategories.Category
    ): POI? {
        val poiLat = element.getDouble("lat")
        val poiLon = element.getDouble("lon")

        val tags = mutableMapOf<String, String>()
        if (element.has("tags")) {
            val tagsObj = element.getJSONObject("tags")
            tagsObj.keys().forEach { key ->
                tags[key] = tagsObj.getString(key)
            }
        }

        val name = tags["name"] ?: tags["ref"] ?: category.name
        val distance = calculateDistance(userLat, userLon, poiLat, poiLon)

        val bearing = DirectionUtils.calculateBearing(userLat, userLon, poiLat, poiLon)
        val direction = DirectionUtils.bearingToDirection(bearing)
        val arrow = DirectionUtils.bearingToArrow(bearing)

        val address = buildAddress(tags)
        val phone = tags["phone"] ?: tags["contact:phone"]
        val website = tags["website"] ?: tags["contact:website"] ?: tags["url"]
        val openingHours = tags["opening_hours"]
        val description = tags["description"] ?: tags["note"]
        val operator = tags["operator"]
        val access = tags["access"] ?: tags["dog"]

        return POI(
            id = element.getString("id"),
            name = name,
            category = category.name,
            categoryIcon = category.icon,
            lat = poiLat,
            lon = poiLon,
            distance = distance,
            tags = tags,
            bearing = bearing,
            direction = direction,
            directionArrow = arrow,
            address = address,
            phone = phone,
            website = website,
            openingHours = openingHours,
            description = description,
            operator = operator,
            access = access
        )
    }

    private fun createPOIFromWayOrRelation(
        element: JSONObject,
        allElements: JSONArray,
        userLat: Double,
        userLon: Double,
        category: POICategories.Category
    ): POI? {
        val nodeIds = mutableListOf<String>()

        if (element.has("nodes")) {
            val nodesArray = element.getJSONArray("nodes")
            for (i in 0 until nodesArray.length()) {
                nodeIds.add(nodesArray.getString(i))
            }
        } else if (element.has("members")) {
            val membersArray = element.getJSONArray("members")
            for (i in 0 until membersArray.length()) {
                val member = membersArray.getJSONObject(i)
                if (member.getString("type") == "node") {
                    nodeIds.add(member.getString("ref"))
                }
            }
        }

        if (nodeIds.isEmpty()) return null

        var sumLat = 0.0
        var sumLon = 0.0
        var count = 0

        for (i in 0 until allElements.length()) {
            val node = allElements.getJSONObject(i)
            if (node.getString("type") == "node" && nodeIds.contains(node.getString("id"))) {
                sumLat += node.getDouble("lat")
                sumLon += node.getDouble("lon")
                count++
            }
        }

        if (count == 0) return null

        val centerLat = sumLat / count
        val centerLon = sumLon / count

        val tags = mutableMapOf<String, String>()
        if (element.has("tags")) {
            val tagsObj = element.getJSONObject("tags")
            tagsObj.keys().forEach { key ->
                tags[key] = tagsObj.getString(key)
            }
        }

        val name = tags["name"] ?: tags["ref"] ?: category.name
        val distance = calculateDistance(userLat, userLon, centerLat, centerLon)

        val bearing = DirectionUtils.calculateBearing(userLat, userLon, centerLat, centerLon)
        val direction = DirectionUtils.bearingToDirection(bearing)
        val arrow = DirectionUtils.bearingToArrow(bearing)

        val address = buildAddress(tags)
        val phone = tags["phone"] ?: tags["contact:phone"]
        val website = tags["website"] ?: tags["contact:website"] ?: tags["url"]
        val openingHours = tags["opening_hours"]
        val description = tags["description"] ?: tags["note"]
        val operator = tags["operator"]
        val access = tags["access"] ?: tags["dog"]

        return POI(
            id = element.getString("id"),
            name = name,
            category = category.name,
            categoryIcon = category.icon,
            lat = centerLat,
            lon = centerLon,
            distance = distance,
            tags = tags,
            bearing = bearing,
            direction = direction,
            directionArrow = arrow,
            address = address,
            phone = phone,
            website = website,
            openingHours = openingHours,
            description = description,
            operator = operator,
            access = access
        )
    }

    private fun buildAddress(tags: Map<String, String>): String? {
        val parts = mutableListOf<String>()

        tags["addr:street"]?.let { street ->
            val houseNumber = tags["addr:housenumber"] ?: ""
            parts.add("$street $houseNumber".trim())
        }

        tags["addr:postcode"]?.let { postcode ->
            val city = tags["addr:city"] ?: ""
            parts.add("$postcode $city".trim())
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ") else null
    }

    private fun showCategoryView() {
        selectedCategory = null
        binding.categoryView.visibility = View.VISIBLE
        binding.poiListView.visibility = View.GONE
        poiList.clear()
        binding.poiRecyclerView.adapter?.notifyDataSetChanged()

        // Marker entfernen
        clearPOIMarkers()

        stopNavigation()
    }

    private fun setupOSMCopyright() {
        binding.osmCopyrightText.setOnClickListener {
            showOSMLicense()
        }
    }

    private fun shareLocation() {
        currentLocation?.let { location ->
            val shareText = "Mein aktueller Standort:\n" +
                    "https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=15/${location.latitude}/${location.longitude}"

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            startActivity(Intent.createChooser(shareIntent, "Standort teilen über"))
        } ?: run {
            Toast.makeText(this, "Standort noch nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. SETUP-FUNKTION FÜR SUCHFELD (in onCreate aufrufen)
    // ──────────────────────────────────────────────────────────────
    private fun setupPOISearch() {
        binding.etPOISearch.apply {
            // Enter-Taste drücken = Suche starten
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = text.toString().trim()
                    if (query.isNotEmpty()) {
                        searchPOI(query)
                        hideKeyboard()
                    }
                    true
                } else {
                    false
                }
            }

            // X-Button zum Löschen
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    if (event.rawX >= (right - compoundDrawables[2].bounds.width())) {
                        setText("")
                        clearSearchResults()
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }

        // Such-Button
        binding.btnSearchPOI.setOnClickListener {
            val query = binding.etPOISearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchPOI(query)
                hideKeyboard()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 4. POI-SUCHE FUNKTION (OSM Nominatim API)
    // ──────────────────────────────────────────────────────────────
    private fun searchPOI(query: String) {
        // Standort abrufen (Overlay oder Fallback auf Manager)
        val currentLocation = myLocationOverlay.myLocation ?: run {
            val lastKnown = try {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } else null
            } catch (e: Exception) { null }

            lastKnown?.let { org.osmdroid.util.GeoPoint(it.latitude, it.longitude) }
        }

        if (currentLocation == null) {
            Toast.makeText(this, "Standort wird noch ermittelt...", Toast.LENGTH_SHORT).show()
            return
        }

        // Alte Suchergebnisse löschen
        clearSearchResults()

        // Loading anzeigen
        binding.progressSearch.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    searchNearbyPOIs(
                        query = query,
                        lat = currentLocation.latitude,
                        lon = currentLocation.longitude,
                        radius = 3000 // 3km Radius für die Suche
                    )
                }

                binding.progressSearch.visibility = View.GONE

                if (results.isEmpty()) {
                    Toast.makeText(
                        this@LocationActivity,
                        "Keine Ergebnisse für '$query' in der Nähe gefunden",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    displaySearchResults(results)
                    Toast.makeText(
                        this@LocationActivity,
                        "${results.size} Ergebnis(se) gefunden",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                binding.progressSearch.visibility = View.GONE
                Log.e("POI_SEARCH", "Suche fehlgeschlagen", e)
                Toast.makeText(
                    this@LocationActivity,
                    "Suche fehlgeschlagen: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
// OPTIMIERTE POI-SUCHE - OVERPASS MIT NOMINATIM FALLBACK
//
// STRATEGIE:
// 1. Versuche Overpass (bessere Ergebnisse)
// 2. Bei Timeout/Fehler: Fallback auf Nominatim (schneller)
// ════════════════════════════════════════════════════════════════

    private fun searchNearbyPOIs(
        query: String,
        lat: Double,
        lon: Double,
        radius: Int
    ): List<POI> {
        Log.d("POI_SEARCH", "searchNearbyPOIs()")
        Log.d("POI_SEARCH", "  Query: $query")
        Log.d("POI_SEARCH", "  Location: $lat, $lon")
        Log.d("POI_SEARCH", "  Radius: ${radius}m")

        // STRATEGIE 1: Versuche Overpass (bessere Ergebnisse)
        try {
            val results = searchViaOverpass(query, lat, lon, radius)
            if (results.isNotEmpty()) {
                Log.i("POI_SEARCH", "✅ Overpass erfolgreich: ${results.size} Treffer")
                return results
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w("POI_SEARCH", "⏱️ Overpass Timeout - Fallback auf Nominatim")
        } catch (e: Exception) {
            Log.w("POI_SEARCH", "⚠️ Overpass Fehler: ${e.message} - Fallback auf Nominatim")
        }

        // STRATEGIE 2: Fallback auf Nominatim (schneller)
        try {
            val results = searchViaNominatim(query, lat, lon, radius)
            Log.i("POI_SEARCH", "✅ Nominatim Fallback: ${results.size} Treffer")
            return results
        } catch (e: Exception) {
            Log.e("POI_SEARCH", "❌ Auch Nominatim fehlgeschlagen: ${e.message}")
            throw e
        }
    }

    // ──────────────────────────────────────────────────────────────
// OVERPASS-SUCHE (OPTIMIERT - weniger Tags)
// ──────────────────────────────────────────────────────────────
    private fun searchViaOverpass(
        query: String,
        lat: Double,
        lon: Double,
        radius: Int
    ): List<POI> {

        // ✅ OPTIMIERT: Nur die wichtigsten Tags, kürzeres Timeout
        val overpassQuery = """
        [out:json][timeout:15];
        (
          node["name"~"$query",i](around:$radius,$lat,$lon);
          node["brand"~"$query",i](around:$radius,$lat,$lon);
          node["operator"~"$query",i](around:$radius,$lat,$lon);
        );
        out center;
    """.trimIndent()

        val url = "https://overpass-api.de/api/interpreter"

        Log.d("POI_SEARCH", "Overpass: ${overpassQuery.take(100)}...")

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
        connection.doOutput = true

        // ✅ KÜRZERE Timeouts für schnelleren Fallback
        connection.connectTimeout = 10000  // 10s (war 30s)
        connection.readTimeout = 15000     // 15s (war 30s)

        // Query senden
        connection.outputStream.use { os ->
            val data = "data=${URLEncoder.encode(overpassQuery, "UTF-8")}"
            os.write(data.toByteArray())
        }

        val responseCode = connection.responseCode
        Log.d("POI_SEARCH", "Overpass Response: $responseCode")

        if (responseCode != 200) {
            Log.e("POI_SEARCH", "Overpass HTTP Error: $responseCode")
            return emptyList()
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }

        return parseOverpassResponse(response, lat, lon)
    }

    // ──────────────────────────────────────────────────────────────
// NOMINATIM-SUCHE (FALLBACK - schnell und zuverlässig)
// ──────────────────────────────────────────────────────────────
    private fun searchViaNominatim(
        query: String,
        lat: Double,
        lon: Double,
        radius: Int
    ): List<POI> {

        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        // Viewbox für lokale Suche
        val delta = 0.05  // ca. 5km
        val viewbox = "${lon - delta},${lat + delta},${lon + delta},${lat - delta}"

        val url = "https://nominatim.openstreetmap.org/search?" +
                "q=$encodedQuery&" +
                "format=json&" +
                "viewbox=$viewbox&" +
                "bounded=1&" +  // ✅ NUR innerhalb viewbox
                "addressdetails=1&" +
                "limit=30"

        Log.d("POI_SEARCH", "Nominatim: $url")

        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val response = connection.inputStream.bufferedReader().use { it.readText() }

        return parseNominatimResponse(response, lat, lon, radius)
    }

    // ──────────────────────────────────────────────────────────────
// OVERPASS RESPONSE PARSEN
// ──────────────────────────────────────────────────────────────
    private fun parseOverpassResponse(response: String, userLat: Double, userLon: Double): List<POI> {
        val jsonObject = JSONObject(response)
        val elements = jsonObject.getJSONArray("elements")

        Log.d("POI_SEARCH", "Overpass: ${elements.length()} Elemente")

        val results = mutableListOf<POI>()
        val currentGeo = GeoPoint(userLat, userLon)

        for (i in 0 until elements.length()) {
            val element = elements.getJSONObject(i)

            val poiLat: Double
            val poiLon: Double

            when (element.getString("type")) {
                "node" -> {
                    poiLat = element.getDouble("lat")
                    poiLon = element.getDouble("lon")
                }
                "way", "relation" -> {
                    val center = element.optJSONObject("center") ?: continue
                    poiLat = center.getDouble("lat")
                    poiLon = center.getDouble("lon")
                }
                else -> continue
            }

            val poiGeo = GeoPoint(poiLat, poiLon)
            val distance = currentGeo.distanceToAsDouble(poiGeo)

            val tags = element.optJSONObject("tags") ?: JSONObject()

            val name = tags.optString("name").ifEmpty {
                tags.optString("brand").ifEmpty {
                    tags.optString("operator").ifEmpty { "Unbenannt" }
                }
            }

            val categoryIcon = when {
                tags.has("shop") && tags.getString("shop") == "supermarket" -> "🛒"
                tags.has("shop") && tags.getString("shop") == "bakery" -> "🥖"
                tags.has("amenity") && tags.getString("amenity") == "pharmacy" -> "💊"
                tags.has("amenity") && tags.getString("amenity") == "restaurant" -> "🍽️"
                tags.has("amenity") && tags.getString("amenity") == "cafe" -> "☕"
                tags.has("shop") -> "🏪"
                else -> "🔍"
            }

            val address = buildString {
                tags.optString("addr:street").let { if (it.isNotEmpty()) append("$it ") }
                tags.optString("addr:housenumber").let { if (it.isNotEmpty()) append(it) }
                tags.optString("addr:postcode").let { if (it.isNotEmpty()) append(", $it") }
                tags.optString("addr:city").let { if (it.isNotEmpty()) append(" $it") }
            }.trim()

            results.add(POI(
                id = element.optLong("id", 0).toString(),
                name = name,
                category = "Suchergebnis",
                categoryIcon = categoryIcon,
                lat = poiLat,
                lon = poiLon,
                distance = distance,
                address = address.ifEmpty { null },
                tags = (0 until tags.length()).associate {
                    val key = tags.names()?.getString(it) ?: ""
                    key to tags.optString(key, "")
                }
            ))
        }

        return results.sortedBy { it.distance }
    }

    // ──────────────────────────────────────────────────────────────
// NOMINATIM RESPONSE PARSEN
// ──────────────────────────────────────────────────────────────
    private fun parseNominatimResponse(
        response: String,
        userLat: Double,
        userLon: Double,
        radius: Int
    ): List<POI> {

        val jsonArray = JSONArray(response)
        val results = mutableListOf<POI>()
        val currentGeo = GeoPoint(userLat, userLon)

        Log.d("POI_SEARCH", "Nominatim: ${jsonArray.length()} Ergebnisse")

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)

            val poiLat = item.optString("lat").toDoubleOrNull() ?: continue
            val poiLon = item.optString("lon").toDoubleOrNull() ?: continue

            val poiGeo = GeoPoint(poiLat, poiLon)
            val distance = currentGeo.distanceToAsDouble(poiGeo)

            // Filter: Nur im Radius
            if (distance > radius) continue

            val displayName = item.optString("display_name", "")
            val shortName = displayName.split(",").firstOrNull()?.trim() ?: "Unbenannt"

            results.add(POI(
                id = item.optString("place_id", i.toString()),
                name = shortName,
                category = "Suchergebnis",
                categoryIcon = "🔍",
                lat = poiLat,
                lon = poiLon,
                distance = distance,
                address = displayName
            ))
        }

        return results.sortedBy { it.distance }
    }


    // ──────────────────────────────────────────────────────────────
    // 6. SUCHERGEBNISSE ALS MARKER ANZEIGEN
    // ──────────────────────────────────────────────────────────────
    private fun displaySearchResults(results: List<POI>) {
        results.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title = "${poi.categoryIcon} ${poi.name}"
                snippet = buildString {
                    append("📍 ${String.format("%.0f", poi.distance)}m entfernt\n")
                    poi.address?.let { append("📬 $it") }
                }

                // Icon
                icon = ContextCompat.getDrawable(
                    this@LocationActivity,
                    R.drawable.ic_search_result // Du musst dieses Icon erstellen
                )

                // Klick-Listener für Navigation
                setOnMarkerClickListener { clickedMarker, _ ->
                    showNavigationDialog(poi)
                    true
                }
            }

            binding.mapView.overlays.add(marker)
            searchResultMarkers.add(marker)
        }

        // Karte aktualisieren
        binding.mapView.invalidate()

        // Zoom auf erstes Ergebnis
        if (results.isNotEmpty()) {
            binding.mapView.controller.animateTo(
                GeoPoint(results[0].lat, results[0].lon)
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 7. ALTE SUCHERGEBNISSE LÖSCHEN
    // ──────────────────────────────────────────────────────────────
    private fun clearSearchResults() {
        searchResultMarkers.forEach { marker ->
            binding.mapView.overlays.remove(marker)
        }
        searchResultMarkers.clear()
        binding.mapView.invalidate()
    }

    // ──────────────────────────────────────────────────────────────
// 8. TASTATUR VERSTECKEN
// ──────────────────────────────────────────────────────────────
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etPOISearch.windowToken, 0)
    }

    // ──────────────────────────────────────────────────────────────
// 9. NAVIGATION-DIALOG (bereits vorhanden erweitern)
// ──────────────────────────────────────────────────────────────
    private fun showNavigationDialog(poi: POI) {
        val myLocation = myLocationOverlay.myLocation ?: run {
            Toast.makeText(this, "Standort nicht verfügbar", Toast.LENGTH_SHORT).show()
            return
        }

        val distance = myLocation.distanceToAsDouble(GeoPoint(poi.lat, poi.lon))

        // ✅ NEU: Dark Mode Erkennung
        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

        val dialog = AlertDialog.Builder(this)
            .setTitle("${poi.categoryIcon} ${poi.name}")
            .setMessage(buildString {
                append("📍 ${String.format("%.0f", distance)}m entfernt\n\n")
                poi.address?.let { append("📬 $it\n\n") }
                append("Navigation starten?")
            })
            .setPositiveButton("Navigation starten") { _, _ ->
                startNavigationToPOI(poi)
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        // ✅ Dialog anzeigen
        dialog.show()

        // ✅ WICHTIG: Button-Farben NACH show() setzen!
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
    }


    private fun showOSMLicense() {
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl("https://www.openstreetmap.org/copyright")

        AlertDialog.Builder(this)
            .setTitle("OpenStreetMap Lizenz")
            .setView(webView)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun checkLocationPermission() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle("📍 Standortdienste aktivieren")
                .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für folgende Zwecke:\n\n" +
                        "• NOTFALLMELDUNG:\nUm Deine Sicherheit und die Deines Hundes zu erhöhen, kann HundeWildnis Deinen aktuellen GPS-Standort erfassen und in die Notfallmeldung (PDF) einfügen.\n" +
                        "Dies ermöglicht es Tierärzten oder Rettungskräften Euch im Ernstfall schneller zu finden.\n" +
                        "• Die Erfassung erfolgt NUR, wenn Du diesen Schalter aktivierst.\n" +
                        "• Die Daten werden NUR lokal in das PDF-Dokument geschrieben und dem Empfänger der Notfallmeldung übermittelt.\n" +
                        "• Es erfolgt KEINE Übermittlung an HundeWildnis oder Dritte.\n\n\n" +
                        "• TEMPO-MESSUNG:\nUm Deine Geh-Geschwindigkeit im Training präzise zu ermitteln und Dir zu Deiner Auswertung zur Verfügung zu stellen.\n\n" +
                        "• NAVIGATION:\nDamit Du Standort auf der Karte, Routenberechnung zum Ziel und Anzeige von relevanten Orten in der Nähe aufrufen und nutzen kannst.\n\n" +
                        "• STANDORT WETTER:\nDamit Du Wetterdaten und Wettervorhersage an Deinem aktuellen Standort aufrufen und nutzen kannst.\n\n" +
                        "• TAGESNOTIZEN:\nDu kannst Deine Tagesnotizen optional mit Wetterdaten und optional mit den GPS-Daten Deines Standortes ergänzen, um später nachvollziehen zu können, unter welchen Bedingungen Du Deine Beobachtungen zum Training gemacht hast.\n\n" +
                        "WICHTIG:\nDamit die Messung auch bei ausgeschaltetem Bildschirm, wenn die App in den Hintergrund geschoben ist oder in der Tasche ist, stabil funktioniert weil Du während des Trainings und bei Aktivität im Gelände die Hände frei haben musst, nutzt die App einen dauerhaften Dienst (Foreground Service). Dieser wird durch die Benachrichtigung in Deiner Statusleiste angezeigt.\n\n" +
                        "Ohne Aktivierung der Standortdienste, als technische Voraussetzung, kannst Du die Funktionen nicht wie beschrieben nutzen.\n\nDATENSCHUTZ:\nDeine Daten werden nur lokal verarbeitet, ausschliesslich auf Deinem Gerät gespeichert und nur von Dir allein verwaltet und nur auf deinen Wunsch zum Teilenfunktion freigegeben.\n\nZur Nutzung von Navigation und Standortwetter wird der aktuelle Standort anonymisiert an freie Server mitgeteilt, um die gewünschten Daten abzurufen und Dir zur Verfügung zu stellen.\n\nDu kannst die Berechtigungen für Standort jederzeit überprüfen in den Einstellungen:\n\nDein Gerät - Einstellungen - Apps - HundeWildnis - Berechtigungen - Standort verwalten - Zugriff nur während der Nutzung der App zulassen.\n\nDort kann die Berechtigung zum Standort jederzeit dauerhaft geändert, aktiviert oder deaktiviert werden")
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton("ABLEHNEN", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                }
        } else {
            getLocation()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getLocation()
                } else {
                    Toast.makeText(this, "Standortberechtigung benötigt", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        var bestLocation: Location? = null

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        if (bestLocation != null) {
            updateLocation(bestLocation)
        }

        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        isRequestingUpdates = true

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
            Log.d("LocationActivity", "GPS Updates gestartet")
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
            Log.d("LocationActivity", "Network Updates gestartet")
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d("LocationActivity", "📍 Location Update: ${location.latitude}, ${location.longitude}")

        updateLocation(location)

        if (isNavigating) {
            centerMapOnLocation(location)
            currentPOIMarker?.let {
                drawRouteOSRM(location.latitude, location.longitude, it.position.latitude, it.position.longitude)
            }
        }
    }

    private fun updateLocation(location: Location) {
        currentLocation = location
        updateUserLocationMarker(location)

        if (isFirstLocation) {
            centerMapOnLocation(location)
            isFirstLocation = false
        }

        binding.progressBar.visibility = View.GONE
        binding.coordinatesText.text = String.format("📍 %.4f°, %.4f°", location.latitude, location.longitude)
        binding.shareButton.isEnabled = true
    }

    private fun updateUserLocationMarker(location: Location) {
        val position = GeoPoint(location.latitude, location.longitude)

        if (userLocationMarker == null) {
            userLocationMarker = Marker(binding.mapView).apply {
                this.position = position
                title = "Dein Standort"
                icon = resources.getDrawable(R.drawable.ic_my_location, null)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            binding.mapView.overlays.add(userLocationMarker)
        } else {
            userLocationMarker?.position = position
        }

        binding.mapView.invalidate()
    }

    private fun centerMapOnLocation(location: Location) {
        val point = GeoPoint(location.latitude, location.longitude)
        binding.mapView.controller.animateTo(point)
    }

    private fun centerOnCurrentLocation() {
        currentLocation?.let { location ->
            centerMapOnLocation(location)
            currentPOIMarker?.let { binding.mapView.overlays.remove(it) }
            currentPOIMarker = null
            currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
            currentRoutePolyline = null
            binding.mapView.invalidate()
            binding.btnCurrentLocation.text = "📍 Standort"
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun showPOIOnMap(poi: POI) {
        currentPOIMarker?.let { binding.mapView.overlays.remove(it) }

        val poiPosition = GeoPoint(poi.lat, poi.lon)
        currentPOIMarker = Marker(binding.mapView).apply {
            position = poiPosition
            title = "${poi.categoryIcon} ${poi.name}"
            snippet = "${poi.category} • ${formatDistance(poi.distance)}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        binding.mapView.overlays.add(currentPOIMarker)

        currentLocation?.let { location ->
            drawRouteOSRM(location.latitude, location.longitude, poi.lat, poi.lon)
        }

        binding.mapView.controller.animateTo(poiPosition)
        binding.btnCurrentLocation.text = "🧭 Navigation beenden"
        isNavigating = true
        binding.mapView.invalidate()
    }

    private fun drawRouteOSRM(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        lifecycleScope.launch {
            try {
                val routeData: RouteData = withContext(Dispatchers.IO) {
                    fetchBestRoute(fromLat, fromLon, toLat, toLon)
                }

                currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }

                if (routeData.points.isNotEmpty()) {
                    currentRouteDistance = routeData.distance
                    currentRouteDuration = routeData.duration

                    currentRoutePolyline = Polyline().apply {
                        setPoints(routeData.points)
                        outlinePaint.color = Color.parseColor("#2196F3")
                        outlinePaint.strokeWidth = 10f
                    }

                    binding.mapView.overlays.add(currentRoutePolyline)
                    binding.mapView.invalidate()
                    updateNavigationInfo()
                } else {
                    drawRouteStraightLine(fromLat, fromLon, toLat, toLon)
                }
            } catch (e: Exception) {
                Log.e("LocationActivity", "Routing Fehler: ${e.message}")
                drawRouteStraightLine(fromLat, fromLon, toLat, toLon)
            }
        }
    }

    private fun fetchOSRMRouteWithDetails(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData {
        val url = "https://router.project-osrm.org/route/v1/foot/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=geojson"
        val response = URL(url).readText()
        val json = JSONObject(response)

        if (json.getString("code") != "Ok") return RouteData(emptyList(), 0.0, 0.0)

        val routes = json.getJSONArray("routes")
        if (routes.length() == 0) return RouteData(emptyList(), 0.0, 0.0)

        val route = routes.getJSONObject(0)
        val distance = route.getDouble("distance")
        val duration = route.getDouble("duration")
        val geometry = route.getJSONObject("geometry")
        val coordinates = geometry.getJSONArray("coordinates")

        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
        }

        return RouteData(points, distance, duration)
    }

    private fun fetchGraphhopperRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData {
        val url = "https://graphhopper.com/api/1/route?point=$fromLat,$fromLon&point=$toLat,$toLon&vehicle=foot&locale=de&points_encoded=false&profile=foot"
        val response = URL(url).readText()
        val json = JSONObject(response)

        if (!json.has("paths")) return RouteData(emptyList(), 0.0, 0.0)

        val paths = json.getJSONArray("paths")
        if (paths.length() == 0) return RouteData(emptyList(), 0.0, 0.0)

        val path = paths.getJSONObject(0)
        val distance = path.getDouble("distance")
        val duration = path.getDouble("time") / 1000.0
        val coordinates = path.getJSONObject("points").getJSONArray("coordinates")

        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
        }

        return RouteData(points, distance, duration)
    }

    private fun fetchOpenRouteServiceRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData {
        val apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjBkODg2NmJmMGM4NTRlNDlhZWI2MjliMWM5YTJmYzM1IiwiaCI6Im11cm11cjY0In0="
        val url = "https://api.openrouteservice.org/v2/directions/foot-hiking?api_key=$apiKey&start=$fromLon,$fromLat&end=$toLon,$toLat"
        val response = URL(url).readText()
        val json = JSONObject(response)

        val features = json.getJSONArray("features")
        if (features.length() == 0) return RouteData(emptyList(), 0.0, 0.0)

        val feature = features.getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val geometry = feature.getJSONObject("geometry")

        val distance = properties.getJSONObject("summary").getDouble("distance")
        val duration = properties.getJSONObject("summary").getDouble("duration")
        val coordinates = geometry.getJSONArray("coordinates")

        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
        }

        return RouteData(points, distance, duration)
    }

    private fun fetchBestRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData {
        try {
            val route = fetchOpenRouteServiceRoute(fromLat, fromLon, toLat, toLon)
            if (route.points.isNotEmpty()) {
                Log.i("Routing", "✅ OpenRouteService")
                return route
            }
        } catch (e: Exception) {
            Log.w("Routing", "OpenRouteService fehlgeschlagen: ${e.message}")
        }

        try {
            val route = fetchGraphhopperRoute(fromLat, fromLon, toLat, toLon)
            if (route.points.isNotEmpty()) {
                Log.i("Routing", "✅ Graphhopper")
                return route
            }
        } catch (e: Exception) {
            Log.w("Routing", "Graphhopper fehlgeschlagen: ${e.message}")
        }

        try {
            val route = fetchOSRMRouteWithDetails(fromLat, fromLon, toLat, toLon)
            if (route.points.isNotEmpty()) {
                Log.i("Routing", "⚠️ OSRM Fallback")
                return route
            }
        } catch (e: Exception) {
            Log.w("Routing", "OSRM fehlgeschlagen: ${e.message}")
        }

        Log.e("Routing", "❌ Alle Routing-Services fehlgeschlagen")
        return RouteData(emptyList(), 0.0, 0.0)
    }

    private fun updateNavigationInfo() {
        if (!isNavigating) {
            binding.navigationInfoText.visibility = View.GONE
            return
        }

        val distanceKm = currentRouteDistance / 1000.0
        val durationMin = (currentRouteDuration / 60.0).toInt()

        val infoText = if (distanceKm < 1.0) {
            "${currentRouteDistance.toInt()} m • $durationMin Min"
        } else {
            "${String.format("%.1f", distanceKm)} km • $durationMin Min"
        }

        binding.navigationInfoText.text = "🧭 $infoText"
        binding.navigationInfoText.visibility = View.VISIBLE
    }

    private fun drawRouteStraightLine(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }

        val points = listOf(GeoPoint(fromLat, fromLon), GeoPoint(toLat, toLon))

        currentRoutePolyline = Polyline().apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#FF9800")
            outlinePaint.strokeWidth = 10f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }

        binding.mapView.overlays.add(currentRoutePolyline)
        binding.mapView.invalidate()

        if (isNavigating) {
            currentRouteDistance = calculateDistance(fromLat, fromLon, toLat, toLon)
            currentRouteDuration = (currentRouteDistance / 1.4)
            updateNavigationInfo()
        }
    }

    private fun stopNavigation() {
        isNavigating = false
        binding.btnCurrentLocation.text = "📍 Standort"
        binding.navigationInfoText.visibility = View.GONE
        currentPOIMarker?.let { binding.mapView.overlays.remove(it) }
        currentPOIMarker = null
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        currentRoutePolyline = null
        binding.mapView.invalidate()
        currentLocation?.let { centerMapOnLocation(it) }
    }

    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> "${String.format("%.1f", meters / 1000)} km"
        }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("LocationActivity", "Provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("LocationActivity", "Provider disabled: $provider")
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.disableMyLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        if (::myLocationOverlay.isInitialized) {
            myLocationOverlay.enableMyLocation()
        }

        if (!isRequestingUpdates) {
            requestLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
