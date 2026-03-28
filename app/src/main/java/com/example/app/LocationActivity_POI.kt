package com.hundewildnis.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.DashPathEffect
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false
    private var isNavigating = false
    private var isFirstLocation = true

    private val poiList = mutableListOf<POI>()

    // ✅ NEU: Liste für POI-Marker
    private val poiMarkers = mutableListOf<Marker>()

    private var currentPOIMarker: Marker? = null
    private var currentRoutePolyline: Polyline? = null
    private var userLocationMarker: Marker? = null
    private var currentRouteDistance: Double = 0.0
    private var currentRouteDuration: Double = 0.0

    private var selectedCategory: POICategories.Category? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MIN_TIME_BETWEEN_UPDATES = 2000L
        private const val MIN_DISTANCE_CHANGE = 5f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
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
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        // ✅ FIX: MapEventsOverlay für zuverlässiges Touch-Handling
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
    }

    private fun setupPOIList() {
        binding.poiRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.poiRecyclerView.adapter = POIAdapter(poiList) { poi ->
            showPOIOnMap(poi)
        }
    }

    private fun setupButtons() {
        styleBackButton()  // ✅ FIX 2: Dark Mode Styling

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

    // ✅ FIX 2: Zurück-Button Dark Mode Styling
    // ✅ KORRIGIERTE VERSION - Ersetze die styleBackButton() Funktion in LocationActivity.kt
    // ✅ FINALE KORRIGIERTE VERSION - Ersetze styleBackButton() in LocationActivity.kt
    // ════════════════════════════════════════════════════════════════
    // FIX 2: styleBackButton() - Größer + heller im Dark Mode
    // ════════════════════════════════════════════════════════════════

    private fun styleBackButton() {
        val isDarkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {
            // Dark Mode: Heller + größer
            binding.btnBackToCategories.apply {
                text = "← Zurück"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#5a8038"))

                // ✅ FIX: DP in Pixel umrechnen!
                val heightInPixels = (56 * resources.displayMetrics.density).toInt()
                layoutParams = layoutParams.apply {
                    width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    height = heightInPixels
                }

                // ✅ FIX: Padding damit Text nicht abgeschnitten wird
                setPadding(24, 12, 24, 12)
            }
        } else {
            // Light Mode: Standard
            binding.btnBackToCategories.apply {
                text = "← Zurück"
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#2d5016"))

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
                    android.graphics.Color.parseColor(category.color)
                )
                setTextColor(android.graphics.Color.WHITE)

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

                    // ✅ NEU: POIs auf Karte anzeigen
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

    // ✅ NEU: Zeigt alle POIs einer Kategorie als Marker auf der Karte
    // ════════════════════════════════════════════════════════════════
    // FIX 1: showPOIsOnMap() - Zweiter Klick startet Navigation
    // ════════════════════════════════════════════════════════════════

    private fun showPOIsOnMap(pois: List<POI>, category: POICategories.Category) {
        clearPOIMarkers()

        pois.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title = poi.name
                snippet = "${formatDistance(poi.distance)} • ${poi.direction}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                // ✅ FIX 1: Erster Klick = Info, Zweiter Klick = Navigation
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

    // ✅ NEU: Startet Navigation zu einem POI
    private fun startNavigationToPOI(poi: POI) {
        showPOIOnMap(poi)
    }

    // ✅ NEU: Zoomt Karte so dass alle POIs + User-Position sichtbar sind
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
            maxLat = maxOf(maxLat, point.latitude)
            minLon = minOf(minLon, point.longitude)
            maxLon = maxOf(maxLon, point.longitude)
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

    // ✅ NEU: Entfernt alle POI-Marker von der Karte
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

        // ✅ NEU: Marker entfernen
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
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getLocation()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
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

    data class RouteData(val points: List<GeoPoint>, val distance: Double, val duration: Double)

    private fun drawRouteOSRM(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        lifecycleScope.launch {
            try {
                val routeData = withContext(Dispatchers.IO) {
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
        val apiKey = "***REMOVED***"
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
        Log.d("LocationActivity", "onPause - Updates bleiben aktiv")
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        if (!isRequestingUpdates) {
            Log.d("LocationActivity", "onResume - Updates neu starten")
            requestLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
            Log.d("LocationActivity", "onDestroy - Updates gestoppt")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}