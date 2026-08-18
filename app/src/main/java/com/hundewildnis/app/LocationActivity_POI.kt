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
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.util.Log
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.hundewildnis.app.databinding.ActivityLocationBinding
import kotlinx.coroutines.delay
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

class LocationActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityLocationBinding
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private var isRequestingUpdates = false
    private var isNavigating = false
    private var isMapRotationEnabled = false
    private var isFirstLocation = true

    private val poiList = mutableListOf<POI>()
    private val poiMarkers = mutableListOf<Marker>()

    private var currentPOIMarker: Marker? = null
    private var currentRoutePolyline: Polyline? = null
    private var userLocationMarker: Marker? = null
    private var currentRouteDistance: Double = 0.0
    private var currentRouteDuration: Double = 0.0

    private var selectedCategory: POICategories.Category? = null
    private var searchResultMarkers = mutableListOf<Marker>()
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var navBottomSheet: BottomSheetBehavior<LinearLayout>

    // ── Aktuelle Routendaten für Positions-Tracking ──────────
    private var currentRouteData: RouteData? = null
    private var closestRoutePointIndex: Int = 0

    private var isShowingSearchResults = false

    private var tapMarker: Marker? = null

    private var finalDestination: GeoPoint? = null
    private val waypointQueue: ArrayDeque<POI> = ArrayDeque()
    private var isAtWaypoint: Boolean = false
    private var waypointReachedDialogShown: Boolean = false
    private var lastPOIFetchLocation: Location? = null

    // ✅ NEU: Routing Profile & Rerouting-Schutz
    private var routingProfile = "foot-hiking"
    private var isRerouting = false

    // ✅ NEU: Geländeanalyse – letzter Wetter-Snapshot für die aktive Route
    private data class WeatherSnapshot(
        val tempC: Double,
        val precipProbabilityPercent: Int,
        val windKmh: Double
    )
    private var lastWeatherSnapshot: WeatherSnapshot? = null

    // ✅ NEU: Aktuell für die Geländeanalyse ausgewählter Hund (bei mehreren Profilen)
    private var terrainSelectedDogId: String? = null

    private val waypointMarkers: MutableList<Marker> = mutableListOf()

    private var sunriseRouteMarker: Marker? = null
    private var sunsetRouteMarker: Marker? = null

    private var isShowingFavorites = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val MIN_TIME_BETWEEN_UPDATES = 2000L
        private const val MIN_DISTANCE_CHANGE = 5f
    }

    // ════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.getInstance().userAgentValue = packageName
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Navigation und Orte"
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

        // Bottom Sheet initialisieren
        navBottomSheet = BottomSheetBehavior.from(binding.navigationCardsView)
        navBottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        navBottomSheet.isHideable = true

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            findViewById<View>(R.id.appbar)?.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    // ════════════════════════════════════════════════════════
    // KARTE
    // ════════════════════════════════════════════════════════

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint?): Boolean {
                poiMarkers.forEach { it.closeInfoWindow() }
                if (p != null) {
                    placeTapMarkerAndShowInfo(p.latitude, p.longitude)
                }
                return false
            }
            override fun longPressHelper(p: org.osmdroid.util.GeoPoint?) = false
        }
        val mapEventsOverlay = org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
        binding.mapView.overlays.add(0, mapEventsOverlay)

        val scaleBarOverlay = ScaleBarOverlay(binding.mapView).apply {
            setAlignRight(true)
            setAlignBottom(true)
            setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
            setCentred(false)
            setScaleBarOffset(20, 20)
        }
        binding.mapView.overlays.add(scaleBarOverlay)

        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this).apply {
                setLocationUpdateMinTime(MIN_TIME_BETWEEN_UPDATES)
                setLocationUpdateMinDistance(MIN_DISTANCE_CHANGE)
            },
            binding.mapView
        )
        myLocationOverlay.enableMyLocation()
        binding.mapView.overlays.add(myLocationOverlay)
    }

    private fun setupPOIList() {
        binding.poiRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.poiRecyclerView.adapter = POIAdapter(poiList) { poi -> 
            showPointInfoDialog(poi.lat, poi.lon, poi) 
        }
    }

    private fun setupButtons() {
        styleBackButton()

        binding.btnCurrentLocation.setOnClickListener {
            if (isNavigating) stopNavigation()
            else centerOnCurrentLocation()
        }

        binding.shareButton.setOnClickListener { shareLocation() }
        binding.btnBackToCategories.setOnClickListener { showCategoryView() }
        binding.btnManageFavorites.setOnClickListener { showFavoritesManagement() }

        binding.btnToggleMapRotation.setOnClickListener {
            isMapRotationEnabled = !isMapRotationEnabled
            if (isMapRotationEnabled) {
                binding.btnToggleMapRotation.text = getString(R.string.map_orientation_course)
                currentLocation?.let {
                    if (it.hasBearing()) binding.mapView.setMapOrientation(-it.bearing)
                }
            } else {
                binding.btnToggleMapRotation.text = getString(R.string.map_orientation_north)
                binding.mapView.setMapOrientation(0f)
            }
        }

        // ✅ NEU: Profil-Toggle (Fuß / Fahrrad / Auto)
        binding.toggleRoutingProfile.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                routingProfile = when (checkedId) {
                    R.id.btnProfileFoot -> "foot-hiking"
                    R.id.btnProfileBike -> "cycling-regular"
                    R.id.btnProfileCar  -> "driving-car"
                    else -> "foot-hiking"
                }
                // Bei Profilwechsel Route sofort neu berechnen wenn Nav aktiv
                if (isNavigating) {
                    currentLocation?.let { loc ->
                        finalDestination?.let { dest ->
                            drawRouteOSRM(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
                        }
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // LOCATION UPDATES
    // ════════════════════════════════════════════════════════

    override fun onLocationChanged(location: Location) {
        Log.d("LocationActivity", "📍 Location: ${location.latitude}, ${location.longitude}")
        updateLocation(location)

        if (isNavigating) {
            if (!isAtWaypoint) {
                // Zwischenziel-Ankunft prüfen
                waypointQueue.firstOrNull()?.let { wp ->
                    val distToWp = calculateDistance(location.latitude, location.longitude, wp.lat, wp.lon)
                    if (distToWp < 25.0 && !waypointReachedDialogShown) {
                        waypointReachedDialogShown = true
                        isAtWaypoint = true
                        showWaypointReachedDialog(wp)
                        return
                    }
                }

                // Route nur neu berechnen wenn Abweichung > 30m
                if (isDeviatingFromRoute(location) && !isRerouting) {
                    val dest = finalDestination
                    if (dest != null) {
                        isRerouting = true // Schutz vor Mehrfach-Aufrufen
                        if (waypointQueue.isEmpty()) {
                            drawRouteOSRM(location.latitude, location.longitude,
                                dest.latitude, dest.longitude)
                        } else {
                            lifecycleScope.launch {
                                try {
                                    val routeData = withContext(Dispatchers.IO) {
                                        fetchRouteWithWaypoints(location.latitude, location.longitude,
                                            waypointQueue.toList(), dest.latitude, dest.longitude)
                                    }
                                    applyNewRoute(routeData, dest)
                                } catch (e: Exception) {
                                    Log.e("LocationActivity", "Waypoint-Rerouting Fehler: ${e.message}")
                                } finally {
                                    isRerouting = false
                                }
                            }
                        }
                    }
                }

                // POI-Liste alle 1km aktualisieren
                val distSinceLastFetch = lastPOIFetchLocation?.let {
                    calculateDistance(it.latitude, it.longitude, location.latitude, location.longitude)
                } ?: 1001.0
                if (distSinceLastFetch >= 1000.0) {
                    lastPOIFetchLocation = location
                    currentRouteData?.let { refreshRoutePOIs(it.points, location) }
                }
            }
            if (isAtWaypoint) return

            centerMapOnLocation(location)

            if (isMapRotationEnabled && location.hasBearing() && location.speed > 0.5f) {
                binding.mapView.setMapOrientation(-location.bearing)
            }

            updateElevationMarker(location)
        }
    }

    /**
     * Findet den nächsten Routenpunkt zur aktuellen Position
     * und aktualisiert den blauen Marker im Höhenprofil.
     */
    private fun updateElevationMarker(location: Location) {
        val route = currentRouteData ?: return
        if (route.elevationPoints.isEmpty() || route.points.isEmpty()) return

        var minDist = Double.MAX_VALUE
        var closestIdx = 0
        route.points.forEachIndexed { idx, point ->
            val d = calculateDistance(location.latitude, location.longitude, point.latitude, point.longitude)
            if (d < minDist) { minDist = d; closestIdx = idx }
        }
        if (closestIdx != closestRoutePointIndex) {
            closestRoutePointIndex = closestIdx
            // Höhenprofil-Index ist proportional zur Punktanzahl
            val chartIdx = (closestIdx.toDouble() / route.points.size * route.elevationPoints.size).toInt()
                .coerceIn(0, route.elevationPoints.size - 1)
            binding.elevationChart.setCurrentPosition(chartIdx)
        }
    }

    private fun updateLocation(location: Location) {
        currentLocation = location
        updateUserLocationMarker(location)
        if (isFirstLocation) { centerMapOnLocation(location); isFirstLocation = false }
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
        binding.mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
    }

    // ════════════════════════════════════════════════════════
    // NAVIGATION STARTEN / STOPPEN
    // ════════════════════════════════════════════════════════

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

        isNavigating = true
        startNavigationService(poi.name)
        finalDestination = GeoPoint(poi.lat, poi.lon)
        NavigationForegroundService.savedDestLat = poi.lat
        NavigationForegroundService.savedDestLon = poi.lon
        isAtWaypoint = false
        waypointReachedDialogShown = false
        waypointQueue.clear()
        waypointMarkers.forEach { binding.mapView.overlays.remove(it) }
        waypointMarkers.clear()
        isAtWaypoint = false
        waypointReachedDialogShown = false
        lastPOIFetchLocation = null
        finalDestination = GeoPoint(poi.lat, poi.lon)
        binding.btnCurrentLocation.text = "🧭 Navigation beenden"
        clearPOIMarkers()   // ← NEU: Marker ausblenden
        binding.mapView.invalidate()

        // Navigation Cards einblenden (zunächst mit Platzhaltern)
        showNavigationCardsPanel()

        //Tap-Marker aufräumen
        tapMarker?.let { binding.mapView.overlays.remove(it) }
        tapMarker = null

        currentLocation?.let { location ->
            drawRouteOSRM(location.latitude, location.longitude, poi.lat, poi.lon)

            lifecycleScope.launch {
                delay(5000)
                if (isNavigating) {
                    zoomToFitRoute(location, poiPosition)
                }
            }
        }
    }

    private fun stopNavigation() {
        stopNavigationService()
        NavigationForegroundService.savedDestLat = 0.0
        NavigationForegroundService.savedDestLon = 0.0
        isNavigating = false
        binding.btnCurrentLocation.text = "📍 Standort"
        binding.navigationInfoText.visibility = View.GONE

        // Karte wieder nach Norden ausrichten
        isMapRotationEnabled = false
        binding.btnToggleMapRotation.text = getString(R.string.map_orientation_north)
        binding.mapView.setMapOrientation(0f)

        currentPOIMarker?.let { binding.mapView.overlays.remove(it) }
        currentPOIMarker = null
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        currentRoutePolyline = null
        currentRouteData = null
        closestRoutePointIndex = 0
        finalDestination = null
        isAtWaypoint = false
        waypointReachedDialogShown = false
        // Alle Zwischenziel-Marker entfernen
        waypointMarkers.forEach { binding.mapView.overlays.remove(it) }
        waypointMarkers.clear()
        waypointQueue.clear()
        isAtWaypoint = false
        waypointReachedDialogShown = false
        finalDestination = null
        lastPOIFetchLocation = null

        sunriseRouteMarker?.let { binding.mapView.overlays.remove(it) }
        sunriseRouteMarker = null
        sunsetRouteMarker?.let { binding.mapView.overlays.remove(it) }
        sunsetRouteMarker = null

        // ── NEU: Geländeanalyse zurücksetzen ─────────────────
        lastWeatherSnapshot = null
        binding.terrainWarningsContainer.removeAllViews()
        binding.tvNoWarnings.visibility = View.GONE
        binding.tvDistanceComparison.visibility = View.GONE
        binding.tvRecommendedDistance.text = "—"
        binding.tvSlopeClassBadge.text = "—"
        binding.rowDogSelector.visibility = View.GONE
        // Hinweis: terrainSelectedDogId bewusst NICHT zurücksetzen –
        // die Hundeauswahl bleibt über mehrere Navigationen hinweg erhalten.

        binding.mapView.invalidate()
        currentLocation?.let { centerMapOnLocation(it) }

        // Navigation Cards ausblenden, vorherige Ansicht wiederherstellen
        hideNavigationCardsPanel()

        //Tap-Marker aufräumen
        tapMarker?.let { binding.mapView.overlays.remove(it) }
        tapMarker = null


        // ← NEU: Marker wieder einblenden falls eine Kategorie aktiv ist
        selectedCategory?.let { category ->
            currentLocation?.let { location ->
                lifecycleScope.launch {
                    try {
                        val pois = withContext(Dispatchers.IO) {
                            fetchPOIsForCategory(location.latitude, location.longitude, category, 3000)
                        }
                        showPOIsOnMap(pois.sortedBy { it.distance }, category)
                    } catch (e: Exception) {
                        Log.e("LocationActivity", "POI Reload Fehler: ${e.message}")
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // NAVIGATION CARDS: EINBLENDEN / AUSBLENDEN / BEFÜLLEN
    // ════════════════════════════════════════════════════════

    private fun showNavigationCardsPanel() {
        binding.categoryView.visibility = View.GONE
        binding.poiListView.visibility  = View.GONE

        // Karte auf Vollbild
        TransitionManager.beginDelayedTransition(binding.mainContent)
        val lp = binding.mapFrame.layoutParams as ConstraintLayout.LayoutParams
        lp.matchConstraintPercentHeight = 1.0f
        lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        binding.mapFrame.layoutParams = lp
        binding.bottomCard.visibility = View.GONE

        // Bottom Sheet einblenden (eingeklappt — nur Header sichtbar)
        binding.navigationCardsView.visibility = View.VISIBLE
        navBottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED

        // Platzhalter
        binding.tvAscentValue.text = "…"
        binding.tvDescentValue.text = "…"
        binding.tvMinElevationValue.text = "…"
        binding.tvMaxElevationValue.text = "…"
        binding.surfaceInfoContainer.removeAllViews()
        binding.wayTypeInfoContainer.removeAllViews()

        binding.mainContent.post {
            binding.navigationCardsView.layoutParams.height = binding.mainContent.height
            binding.navigationCardsView.requestLayout()
        }
    }

    private fun hideNavigationCardsPanel() {
        // Bottom Sheet verstecken
        navBottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        binding.navigationCardsView.visibility = View.GONE

        // Karte wieder verkleinern
        TransitionManager.beginDelayedTransition(binding.mainContent)
        val lp = binding.mapFrame.layoutParams as ConstraintLayout.LayoutParams
        lp.matchConstraintPercentHeight = 0.5f
        lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        binding.mapFrame.layoutParams = lp

        binding.bottomCard.visibility = View.VISIBLE

        // Ansicht wiederherstellen (POI-Liste oder Kategorien)
        if (selectedCategory != null || isShowingSearchResults || isShowingFavorites) {
            binding.poiListView.visibility  = View.VISIBLE
            binding.categoryView.visibility = View.GONE
        } else {
            binding.categoryView.visibility = View.VISIBLE
            binding.poiListView.visibility  = View.GONE
        }

        // Sheet-Höhe beim Beenden der Navigation zurücksetzen:
        binding.navigationCardsView.layoutParams.height =
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        binding.navigationCardsView.requestLayout()
    }

    /**
     * Befüllt alle drei Navigations-Cards mit den echten Routendaten.
     * Wird aufgerufen, sobald die Route vom Server zurückgekommen ist.
     */
    private fun updateNavigationCards(routeData: RouteData, destLat: Double, destLon: Double) {
        currentRouteData = routeData

        // ── Card 1: Höhenprofil ──────────────────────────
        binding.elevationChart.setElevationData(routeData.elevationPoints)

        // ── Card 2: Höhenstatistik ───────────────────────
        if (routeData.elevationPoints.isNotEmpty()) {
            binding.tvAscentValue.text       = "+${routeData.totalAscent.toInt()} m"
            binding.tvDescentValue.text      = "−${routeData.totalDescent.toInt()} m"
            binding.tvMinElevationValue.text = "${routeData.minElevation.toInt()} m ü.NN"
            binding.tvMaxElevationValue.text = "${routeData.maxElevation.toInt()} m ü.NN"
        } else {
            binding.tvAscentValue.text       = "—"
            binding.tvDescentValue.text      = "—"
            binding.tvMinElevationValue.text = "—"
            binding.tvMaxElevationValue.text = "—"
        }

        // ── Card 3: Oberfläche & Wegtyp ──────────────────
        updateSurfaceCard(routeData.surfaceSummary, routeData.wayTypeSummary)
        if (lastPOIFetchLocation == null) {
            lastPOIFetchLocation = currentLocation
            currentLocation?.let { loc -> refreshRoutePOIs(routeData.points, loc) }
        }


        // Am Ende der Funktion ergänzen (nach updateSurfaceCard):
        val arrivalTimeMs = System.currentTimeMillis() + (routeData.duration * 1000).toLong()
        lifecycleScope.launch {
            try {
                val weatherData = withContext(Dispatchers.IO) {
                    fetchWeatherAtDestination(destLat, destLon)
                }
                updateWeatherCard(weatherData, arrivalTimeMs)
            } catch (e: Exception) {
                Log.w("LocationActivity", "Wetter am Ziel fehlgeschlagen: ${e.message}")
            }
        }

        // ── Header-Info aktualisieren ────────────────────
        val km   = routeData.distance / 1000.0
        val totalMins = (routeData.duration / 60.0).toInt()

        val timeText = if (totalMins < 60) {
            "$totalMins Min"
        } else {
            val h   = totalMins / 60
            val min = totalMins % 60
            if (min == 0) "${h}h" else "${h}h ${min}min"
        }

        binding.tvNavHeaderInfo.text =
            if (km < 1.0) "${routeData.distance.toInt()} m  •  $timeText"
            else          "${"%.1f".format(km)} km  •  $timeText"

        // ── NEU: Geländeanalyse aktualisieren ────────────────
        updateTerrainAnalysisCard(routeData)
    }

    private fun updateSurfaceCard(
        surfaces: List<SurfaceSummary>,
        wayTypes: List<SurfaceSummary>
    ) {
        binding.surfaceInfoContainer.removeAllViews()
        binding.wayTypeInfoContainer.removeAllViews()

        if (surfaces.isEmpty()) {
            addPlaceholderText(binding.surfaceInfoContainer, "Keine Daten von Routing-Service")
        } else {
            surfaces.sortedByDescending { it.percentage }.take(5).forEach { s ->
                addSurfaceRow(binding.surfaceInfoContainer, s.emoji, s.name, s.percentage, s.distance,
                    RouteInfoHelper.getSurfaceColor(s.code))
            }
        }

        if (wayTypes.isEmpty()) {
            addPlaceholderText(binding.wayTypeInfoContainer, "Keine Daten von Routing-Service")
        } else {
            wayTypes.sortedByDescending { it.percentage }.take(4).forEach { w ->
                addSurfaceRow(binding.wayTypeInfoContainer, w.emoji, w.name, w.percentage, w.distance,
                    RouteInfoHelper.getWayTypeColor(w.code))
            }
        }
    }

    /** Fügt eine Zeile (Label + Fortschrittsbalken) in den Container ein */
    private fun addSurfaceRow(
        container: LinearLayout,
        emoji: String,
        name: String,
        percent: Double,
        distanceM: Double,
        barColor: Int
    ) {
        val dp = resources.displayMetrics.density

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (7 * dp).toInt() }
        }

        // Label-Zeile: Emoji + Name   •   XX% (XXXm)
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvLabel = TextView(this).apply {
            text = "$emoji $name"
            textSize = 11.5f
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvPercent = TextView(this).apply {
            text = "${percent.toInt()}%  ${formatDistance(distanceM)}"
            textSize = 10.5f
            setTextColor(Color.parseColor("#AAAAAA"))
        }

        labelRow.addView(tvLabel)
        labelRow.addView(tvPercent)

        // Fortschrittsbalken
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = percent.toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(barColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (5 * dp).toInt()
            ).also { it.topMargin = (3 * dp).toInt() }
        }

        row.addView(labelRow)
        row.addView(bar)
        container.addView(row)
    }

    private fun addPlaceholderText(container: LinearLayout, text: String) {
        container.addView(TextView(this).apply {
            setText(text)
            textSize = 11f
            setTextColor(Color.parseColor("#777777"))
            setPadding(0, 4, 0, 4)
        })
    }

    // ════════════════════════════════════════════════════════
    // ROUTING
    // ════════════════════════════════════════════════════════

    private fun drawRouteOSRM(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        val appSettings = AppSettings(this)
        if (!appSettings.hasCustomOrsKey()) {
            Toast.makeText(this, "ℹ️ Für präzises Routing bitte ORS-Key in den Einstellungen (Zahnrad oben) hinterlegen.", Toast.LENGTH_LONG).show()
        }
        isRerouting = true
        lifecycleScope.launch {
            try {
                val routeData: RouteData = withContext(Dispatchers.IO) {
                    fetchBestRoute(fromLat, fromLon, toLat, toLon)
                }
                // ... rest of logic

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
                    updateNavigationCards(routeData, toLat, toLon) // ← Cards befüllen
                } else {
                    drawRouteStraightLine(fromLat, fromLon, toLat, toLon)
                }
            } catch (e: Exception) {
                Log.e("LocationActivity", "Routing Fehler: ${e.message}")
                drawRouteStraightLine(fromLat, fromLon, toLat, toLon)
            } finally {
                isRerouting = false
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
// ERSATZ FÜR fetchOpenRouteServiceRoute() UND fetchBestRoute()
// in LocationActivity_POI.kt
//
// Änderungen:
//  - extra_info "waytype" (Singular) statt "waytypes"/"way_types"
//  - Fallback-Kette: ORS voll → ORS nur Elevation → Luftlinie
//    (GraphHopper + OSRM entfernt, da deren öffentliche Server ausgefallen)
// ════════════════════════════════════════════════════════════════

    private fun fetchOpenRouteServiceRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double,
        withExtras: Boolean = true           // false = nur Route + Höhe, kein extra_info
    ): RouteData {

        val apiKey = AppSettings(this).getOrsApiKey()
        val url    = "https://api.openrouteservice.org/v2/directions/$routingProfile/geojson"

        val bodyObj = JSONObject().apply {
            put("coordinates", JSONArray().apply {
                put(JSONArray().apply { put(fromLon); put(fromLat) })
                put(JSONArray().apply { put(toLon);   put(toLat)   })
            })
            put("elevation", true)
// ... rest of function
            if (withExtras) {
                put("extra_info", JSONArray().apply {
                    put("surface")
                    put("waytype")   // ← Singular! Nicht "waytypes" oder "way_types"
                })
            }
            put("language", "de")
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
        conn.setRequestProperty("Accept",        "application/json, application/geo+json")
        conn.setRequestProperty("Authorization", apiKey)
        conn.setRequestProperty("User-Agent",    "HundeWildnisApp/9.7")
        conn.connectTimeout = 15000
        conn.readTimeout    = 20000
        conn.doOutput       = true
        conn.outputStream.use { it.write(bodyObj.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            Log.e("Routing", "ORS Fehler $responseCode (withExtras=$withExtras): $error")
            return RouteData(emptyList(), 0.0, 0.0)
        }

        val json     = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val features = json.getJSONArray("features")
        if (features.length() == 0) return RouteData(emptyList(), 0.0, 0.0)

        val feature    = features.getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val geometry   = feature.getJSONObject("geometry")

        val summary  = properties.getJSONObject("summary")
        val distance = summary.getDouble("distance")
        var duration = summary.getDouble("duration")

        // ✅ NEU: Geschwindigkeit für Fahrrad begrenzen (max 10 km/h = 2.77 m/s)
        if (routingProfile == "cycling-regular") {
            val minDuration = distance / (10.0 / 3.6) // Zeit bei 10 km/h
            if (duration < minDuration) {
                duration = minDuration
            }
        }

        // Auf-/Abstieg
        var totalAscent = 0.0; var totalDescent = 0.0
        if (properties.has("segments")) {
            val segs = properties.getJSONArray("segments")
            for (i in 0 until segs.length()) {
                val seg = segs.getJSONObject(i)
                totalAscent  += seg.optDouble("ascent",  0.0)
                totalDescent += seg.optDouble("descent", 0.0)
            }
        }

        // Koordinaten & Höhenpunkte
        val coordinates     = geometry.getJSONArray("coordinates")
        val points          = mutableListOf<GeoPoint>()
        val elevationPoints = mutableListOf<Double>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
            if (coord.length() >= 3) elevationPoints.add(coord.getDouble(2))
        }

        // Oberfläche & Wegtyp
        val surfaceSummary = mutableListOf<SurfaceSummary>()
        val wayTypeSummary = mutableListOf<SurfaceSummary>()

        if (withExtras && properties.has("extras")) {
            val extras = properties.getJSONObject("extras")

            if (extras.has("surface")) {
                val arr = extras.getJSONObject("surface").getJSONArray("summary")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val code = item.getInt("value")
                    surfaceSummary.add(SurfaceSummary(
                        code = code, name = RouteInfoHelper.getSurfaceName(code),
                        emoji = RouteInfoHelper.getSurfaceEmoji(code),
                        distance = item.getDouble("distance"), percentage = item.getDouble("amount")
                    ))
                }
            }

            if (extras.has("waytype")) {
                val arr = extras.getJSONObject("waytype").getJSONArray("summary")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val code = item.getInt("value")
                    wayTypeSummary.add(SurfaceSummary(
                        code = code, name = RouteInfoHelper.getWayTypeName(code),
                        emoji = RouteInfoHelper.getWayTypeEmoji(code),
                        distance = item.getDouble("distance"), percentage = item.getDouble("amount")
                    ))
                }
            }
        }

        return RouteData(
            points = points, distance = distance, duration = duration,
            elevationPoints = elevationPoints, totalAscent = totalAscent, totalDescent = totalDescent,
            minElevation = elevationPoints.minOrNull() ?: 0.0,
            maxElevation = elevationPoints.maxOrNull() ?: 0.0,
            surfaceSummary = surfaceSummary, wayTypeSummary = wayTypeSummary
        )
    }

// ── Neue fetchBestRoute: nur noch ORS, zwei Stufen ──────────────

    private fun fetchBestRoute(
        fromLat: Double, fromLon: Double,
        toLat: Double,   toLon: Double
    ): RouteData {

        // Stufe 1: ORS mit Höhe + Oberfläche + Wegtyp
        try {
            val route = fetchOpenRouteServiceRoute(fromLat, fromLon, toLat, toLon, withExtras = true)
            if (route.points.isNotEmpty()) {
                Log.i("Routing", "✅ ORS voll (Höhe + Oberfläche)")
                return route
            }
        } catch (e: Exception) {
            Log.w("Routing", "ORS voll fehlgeschlagen: ${e.message}")
        }

        // Stufe 2: ORS nur mit Höhendaten (kein extra_info)
        try {
            val route = fetchOpenRouteServiceRoute(fromLat, fromLon, toLat, toLon, withExtras = false)
            if (route.points.isNotEmpty()) {
                Log.i("Routing", "✅ ORS nur Elevation")
                return route
            }
        } catch (e: Exception) {
            Log.w("Routing", "ORS Elevation fehlgeschlagen: ${e.message}")
        }

        Log.e("Routing", "❌ ORS nicht erreichbar – Luftlinie wird gezeichnet")
        return RouteData(emptyList(), 0.0, 0.0)
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
            val speed = when (routingProfile) {
                "cycling-regular" -> 2.77
                "driving-car"     -> 10.0
                else              -> 1.4
            }
            currentRouteDuration = currentRouteDistance / speed
            updateNavigationInfo()
        }
    }

    private fun updateNavigationInfo() {
        if (!isNavigating) { binding.navigationInfoText.visibility = View.GONE; return }

        val km   = currentRouteDistance / 1000.0
        val totalMins = (currentRouteDuration / 60.0).toInt()

        val distText = if (km < 1.0) "${currentRouteDistance.toInt()} m"
        else          "${"%.1f".format(km)} km"

        val timeText = if (totalMins < 60) {
            "$totalMins Min"
        } else {
            val h   = totalMins / 60
            val min = totalMins % 60
            if (min == 0) "${h}h" else "${h}h ${min}min"
        }

        binding.navigationInfoText.text    = "🧭 $distText  •  $timeText"
        binding.navigationInfoText.visibility = View.VISIBLE

        updateNavNotification("$distText  •  $timeText")
    }

    private fun zoomToFitRoute(userLoc: Location, target: GeoPoint) {
        val points = mutableListOf(GeoPoint(userLoc.latitude, userLoc.longitude), target)
        val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
        binding.mapView.zoomToBoundingBox(boundingBox, true, 200)
    }

    // ════════════════════════════════════════════════════════
    // POI KATEGORIEN & LISTE
    // ════════════════════════════════════════════════════════

    private fun setupCategoryButtons() {
        val tiles = mutableListOf<StartTile>()

        // Favoriten
        tiles.add(StartTile("poi_fav", label = "Favoriten", emoji = "⭐"))

        // Kategorien
        POICategories.ALL_CATEGORIES.forEach { category ->
            tiles.add(StartTile(category.name, label = category.name, emoji = category.icon))
        }

        val colors = StartGroupColors.forGroup(this, 3) // Ocker/Braun
        binding.gridPOICategories.layoutManager = GridLayoutManager(this, 3)
        binding.gridPOICategories.adapter = StartTileAdapter(
            tiles = tiles,
            chipColorRes = colors.chipColorRes,
            accentColorRes = colors.accentColorRes,
            onTileClick = { id ->
                if (id == "poi_fav") {
                    loadFavorites()
                } else {
                    val cat = POICategories.ALL_CATEGORIES.find { it.name == id }
                    cat?.let {
                        selectedCategory = it
                        loadPOIsForCategory(it)
                    }
                }
            }
        )
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
                    val sorted = pois.sortedBy { it.distance }
                    poiList.clear(); poiList.addAll(sorted)
                    binding.poiRecyclerView.adapter = POIAdapter(poiList) { poi -> showPOIOnMap(poi) }
                    binding.poiCountText.text = "${pois.size} POI in 3 km"
                    binding.poiProgressBar.visibility = View.GONE
                    binding.emptyPoiText.visibility = if (pois.isEmpty()) View.VISIBLE else View.GONE
                    if (pois.isEmpty()) binding.emptyPoiText.text = "Keine ${category.name} im Umkreis von 3 km gefunden"
                    showPOIsOnMap(sorted, category)
                } catch (e: Exception) {
                    binding.poiProgressBar.visibility = View.GONE
                    Toast.makeText(this@LocationActivity, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } ?: Toast.makeText(this, "Standort noch nicht verfügbar", Toast.LENGTH_SHORT).show()
    }

    private fun showCategoryView() {
        selectedCategory    = null
        isShowingFavorites  = false          // ← NEU
        binding.categoryView.visibility = View.VISIBLE
        binding.poiListView.visibility  = View.GONE
        poiList.clear()
        binding.poiRecyclerView.adapter?.notifyDataSetChanged()
        clearPOIMarkers()
        stopNavigation()
        binding.btnManageFavorites.visibility = View.GONE  // ← NEU
    }

    private fun showPOIsOnMap(pois: List<POI>, category: POICategories.Category) {
        clearPOIMarkers()
        pois.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title   = poi.name
                snippet = "${formatDistance(poi.distance)} • ${poi.direction}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                var clickCount = 0
                setOnMarkerClickListener { _, _ ->
                    showPointInfoDialog(poi.lat, poi.lon, poi)
                    true
                }
            }
            poiMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
        if (pois.isNotEmpty()) zoomToShowAllPOIs(pois)
    }

    private fun startNavigationToPOI(poi: POI) = showPOIOnMap(poi)

    private fun clearPOIMarkers() {
        poiMarkers.forEach { binding.mapView.overlays.remove(it) }
        poiMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun zoomToShowAllPOIs(pois: List<POI>) {
        val allPoints = mutableListOf<GeoPoint>()
        currentLocation?.let { allPoints.add(GeoPoint(it.latitude, it.longitude)) }
        pois.forEach { allPoints.add(GeoPoint(it.lat, it.lon)) }
        if (allPoints.isEmpty()) return

        var minLat = allPoints[0].latitude; var maxLat = minLat
        var minLon = allPoints[0].longitude; var maxLon = minLon
        allPoints.forEach {
            minLat = minOf(minLat, it.latitude);  maxLat = maxOf(maxLat, it.latitude)
            minLon = minOf(minLon, it.longitude); maxLon = maxOf(maxLon, it.longitude)
        }
        val maxDiff = maxOf(maxLat - minLat, maxLon - minLon)
        val zoom = when {
            maxDiff > 0.1  -> 12.0; maxDiff > 0.05 -> 13.0; maxDiff > 0.02 -> 14.0
            maxDiff > 0.01 -> 15.0; else -> 16.0
        }
        binding.mapView.controller.setCenter(GeoPoint((minLat + maxLat) / 2, (minLon + maxLon) / 2))
        binding.mapView.controller.setZoom(zoom)
    }

    // ════════════════════════════════════════════════════════
    // OVERPASS FETCH
    // ════════════════════════════════════════════════════════

    private suspend fun fetchPOIsForCategory(lat: Double, lon: Double, category: POICategories.Category, radius: Int = 3000): List<POI> {
        val queries = category.queries.flatMap { query ->
            val parts = query.split("=")
            if (parts.size == 2) listOf(
                """node["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                """way["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);""",
                """relation["${parts[0]}"="${parts[1]}"](around:$radius,$lat,$lon);"""
            ) else emptyList()
        }.filter { it.isNotEmpty() }

        val overpassQuery = """
            [out:json][timeout:25];
            (
              ${queries.joinToString("\n  ")}
            );
            out body; >; out skel qt;
        """.trimIndent()

        val json     = OverpassHelper.queryWithRetry(overpassQuery) ?: throw Exception("Overpass API nicht erreichbar")
        val elements = json.getJSONArray("elements")
        val pois     = mutableListOf<POI>()

        for (i in 0 until elements.length()) {
            val el   = elements.getJSONObject(i)
            val type = el.getString("type")
            when (type) {
                "node"            -> createPOIFromElement(el, lat, lon, category)?.let { pois.add(it) }
                "way", "relation" -> createPOIFromWayOrRelation(el, elements, lat, lon, category)?.let { pois.add(it) }
            }
        }
        return pois
    }

    private fun createPOIFromElement(element: JSONObject, userLat: Double, userLon: Double, category: POICategories.Category): POI? {
        val poiLat = element.getDouble("lat")
        val poiLon = element.getDouble("lon")
        val tags   = parseTags(element)
        val name   = tags["name"] ?: tags["ref"] ?: category.name
        val dist   = calculateDistance(userLat, userLon, poiLat, poiLon)
        val bearing = DirectionUtils.calculateBearing(userLat, userLon, poiLat, poiLon)
        return POI(
            id = element.getString("id"), name = name, category = category.name, categoryIcon = category.icon,
            lat = poiLat, lon = poiLon, distance = dist, tags = tags,
            bearing = bearing, direction = DirectionUtils.bearingToDirection(bearing),
            directionArrow = DirectionUtils.bearingToArrow(bearing),
            address = buildAddress(tags), phone = tags["phone"] ?: tags["contact:phone"],
            website = tags["website"] ?: tags["contact:website"] ?: tags["url"],
            openingHours = tags["opening_hours"], description = tags["description"] ?: tags["note"],
            operator = tags["operator"], access = tags["access"] ?: tags["dog"]
        )
    }

    private fun createPOIFromWayOrRelation(element: JSONObject, allElements: JSONArray, userLat: Double, userLon: Double, category: POICategories.Category): POI? {
        val nodeIds = mutableListOf<String>()
        if (element.has("nodes")) {
            val arr = element.getJSONArray("nodes")
            for (i in 0 until arr.length()) nodeIds.add(arr.getString(i))
        } else if (element.has("members")) {
            val arr = element.getJSONArray("members")
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                if (m.getString("type") == "node") nodeIds.add(m.getString("ref"))
            }
        }
        if (nodeIds.isEmpty()) return null

        var sumLat = 0.0; var sumLon = 0.0; var count = 0
        for (i in 0 until allElements.length()) {
            val node = allElements.getJSONObject(i)
            if (node.getString("type") == "node" && nodeIds.contains(node.getString("id"))) {
                sumLat += node.getDouble("lat"); sumLon += node.getDouble("lon"); count++
            }
        }
        if (count == 0) return null

        val centerLat = sumLat / count; val centerLon = sumLon / count
        val tags      = parseTags(element)
        val name      = tags["name"] ?: tags["ref"] ?: category.name
        val dist      = calculateDistance(userLat, userLon, centerLat, centerLon)
        val bearing   = DirectionUtils.calculateBearing(userLat, userLon, centerLat, centerLon)
        return POI(
            id = element.getString("id"), name = name, category = category.name, categoryIcon = category.icon,
            lat = centerLat, lon = centerLon, distance = dist, tags = tags,
            bearing = bearing, direction = DirectionUtils.bearingToDirection(bearing),
            directionArrow = DirectionUtils.bearingToArrow(bearing),
            address = buildAddress(tags), phone = tags["phone"] ?: tags["contact:phone"],
            website = tags["website"] ?: tags["contact:website"] ?: tags["url"],
            openingHours = tags["opening_hours"], description = tags["description"] ?: tags["note"],
            operator = tags["operator"], access = tags["access"] ?: tags["dog"]
        )
    }

    private fun parseTags(element: JSONObject): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        if (element.has("tags")) {
            val tagsObj = element.getJSONObject("tags")
            tagsObj.keys().forEach { tags[it] = tagsObj.getString(it) }
        }
        return tags
    }

    private fun buildAddress(tags: Map<String, String>): String? {
        val parts = mutableListOf<String>()
        tags["addr:street"]?.let { parts.add("$it ${tags["addr:housenumber"] ?: ""}".trim()) }
        tags["addr:postcode"]?.let { parts.add("$it ${tags["addr:city"] ?: ""}".trim()) }
        return parts.joinToString(", ").ifEmpty { null }
    }

    // ════════════════════════════════════════════════════════
    // NOMINATIM SUCHE
    // ════════════════════════════════════════════════════════

    private fun setupPOISearch() {
        binding.etPOISearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.etPOISearch.text.toString().trim()
                if (q.isNotEmpty()) { searchPOI(q); hideKeyboard() }
                true
            } else false
        }

        // Clear-Button ein-/ausblenden je nach Textinhalt
        binding.etPOISearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                binding.btnClearSearch.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        // Clear-Button Klick
        binding.btnClearSearch.setOnClickListener {
            binding.etPOISearch.setText("")
            binding.btnClearSearch.visibility = View.GONE
            clearSearchResults()
            hideKeyboard()
        }

        binding.btnSearchPOI.setOnClickListener {
            val q = binding.etPOISearch.text.toString().trim()
            if (q.isNotEmpty()) { searchPOI(q); hideKeyboard() }
        }
    }

    private fun searchPOI(query: String) {
        val searchLoc = currentLocation?.let { GeoPoint(it.latitude, it.longitude) }
            ?: myLocationOverlay.myLocation
            ?: run {
                val last = try {
                    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    else null
                } catch (e: Exception) { null }
                last?.let { GeoPoint(it.latitude, it.longitude) }
            }

        if (searchLoc == null) {
            Toast.makeText(this, "Standort wird noch ermittelt...", Toast.LENGTH_SHORT).show()
            return
        }

        clearSearchResults()
        binding.progressSearch.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    searchNearbyPOIs(query, searchLoc.latitude, searchLoc.longitude, 20000)
                }
                binding.progressSearch.visibility = View.GONE
                if (results.isEmpty()) {
                    Toast.makeText(this@LocationActivity, "Keine Ergebnisse für '$query' in der Nähe gefunden", Toast.LENGTH_SHORT).show()
                } else {
                    displaySearchResults(results)
                    Toast.makeText(this@LocationActivity, "${results.size} Ergebnis(se) gefunden", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressSearch.visibility = View.GONE
                Toast.makeText(this@LocationActivity, "Suche fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchNearbyPOIs(query: String, lat: Double, lon: Double, radius: Int): List<POI> {
        // Strategie-Wechsel: Nominatim zuerst (schneller für Namen/Adressen/Orte)
        // und mit zwei Stufen (nah & global).
        try {
            // Stufe 1: Nominatim nah am User
            val nearbyResults = searchViaNominatim(query, lat, lon, radius, isBounded = true)
            if (nearbyResults.isNotEmpty()) return nearbyResults

            // Stufe 2: Nominatim global (falls nah nichts gefunden wurde)
            val globalResults = searchViaNominatim(query, lat, lon, radius, isBounded = false)
            if (globalResults.isNotEmpty()) return globalResults

            // Stufe 3: Overpass (für spezifische OSM-Tags, falls Nominatim nichts liefert)
            return searchViaOverpass(query, lat, lon, radius / 2) // Kleinerer Radius für Overpass-Regex (Speed!)
        } catch (e: Exception) {
            Log.e("LocationActivity", "Suche fehlgeschlagen", e)
            return emptyList()
        }
    }

    private fun searchViaOverpass(query: String, lat: Double, lon: Double, radius: Int): List<POI> {
        // Regex-Suche (~".*",i) in Overpass ist bei großem Radius EXTREM langsam.
        // Wir begrenzen den Radius hier auf max 10km für Overpass.
        val safeRadius = radius.coerceAtMost(10000)
        val overpassQuery = """
            [out:json][timeout:10];
            (
              node["name"~"$query",i](around:$safeRadius,$lat,$lon);
              node["brand"~"$query",i](around:$safeRadius,$lat,$lon);
            );
            out center;
        """.trimIndent()
        val url = "https://overpass-api.de/api/interpreter"
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
        conn.doOutput = true
        conn.connectTimeout = 8000; conn.readTimeout = 10000
        conn.outputStream.use { it.write("data=${URLEncoder.encode(overpassQuery, "UTF-8")}".toByteArray()) }
        if (conn.responseCode != 200) return emptyList()
        return parseOverpassResponse(conn.inputStream.bufferedReader().use { it.readText() }, lat, lon)
    }

    private fun searchViaNominatim(query: String, lat: Double, lon: Double, radius: Int, isBounded: Boolean): List<POI> {
        val baseUrl = "https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(query, "UTF-8")}&format=json&addressdetails=1&limit=30"
        
        val url = if (isBounded) {
            // Fokus auf Umkreis (ca. 40km Box)
            val delta = 0.35 
            val viewbox = "${lon - delta},${lat + delta},${lon + delta},${lat - delta}"
            "$baseUrl&viewbox=$viewbox&bounded=1"
        } else {
            // Global, aber lat/lon als Präferenz mitschicken
            "$baseUrl&lat=$lat&lon=$lon"
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        
        if (conn.responseCode != 200) return emptyList()
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        
        // Bei globaler Suche filtern wir nicht nach Radius
        val filterRadius = if (isBounded) radius else Int.MAX_VALUE
        return parseNominatimResponse(response, lat, lon, filterRadius)
    }

    private fun parseOverpassResponse(response: String, userLat: Double, userLon: Double): List<POI> {
        val elements = JSONObject(response).getJSONArray("elements")
        val results  = mutableListOf<POI>()
        val userGeo  = GeoPoint(userLat, userLon)

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val (pLat, pLon) = when (el.getString("type")) {
                "node"           -> el.getDouble("lat") to el.getDouble("lon")
                "way","relation" -> el.optJSONObject("center")?.let { it.getDouble("lat") to it.getDouble("lon") } ?: continue
                else             -> continue
            }
            val tags     = el.optJSONObject("tags") ?: JSONObject()
            val name     = tags.optString("name").ifEmpty { tags.optString("brand").ifEmpty { tags.optString("operator").ifEmpty { "Unbenannt" } } }
            val icon     = when {
                tags.optString("shop") == "supermarket" -> "🛒"
                tags.optString("shop") == "bakery"      -> "🥖"
                tags.optString("amenity") == "pharmacy" -> "💊"
                tags.optString("amenity") == "restaurant" -> "🍽️"
                tags.optString("amenity") == "cafe"     -> "☕"
                tags.has("shop")                        -> "🏪"
                else                                    -> "🔍"
            }
            results.add(POI(id = el.optLong("id", 0).toString(), name = name, category = "Suchergebnis", categoryIcon = icon,
                lat = pLat, lon = pLon, distance = userGeo.distanceToAsDouble(GeoPoint(pLat, pLon))))
        }
        return results.sortedBy { it.distance }
    }

    private fun parseNominatimResponse(response: String, userLat: Double, userLon: Double, radius: Int): List<POI> {
        val arr     = JSONArray(response)
        val results = mutableListOf<POI>()
        val userGeo = GeoPoint(userLat, userLon)
        for (i in 0 until arr.length()) {
            val item  = arr.getJSONObject(i)
            val pLat  = item.optString("lat").toDoubleOrNull() ?: continue
            val pLon  = item.optString("lon").toDoubleOrNull() ?: continue
            val dist  = userGeo.distanceToAsDouble(GeoPoint(pLat, pLon))
            if (dist > radius) continue
            val name  = item.optString("display_name", "").split(",").firstOrNull()?.trim() ?: "Unbenannt"
            results.add(POI(id = item.optString("place_id", i.toString()), name = name, category = "Suchergebnis", categoryIcon = "🔍",
                lat = pLat, lon = pLon, distance = dist, address = item.optString("display_name")))
        }
        return results.sortedBy { it.distance }
    }

    private fun displaySearchResults(results: List<POI>) {
        // Marker auf Karte
        results.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title   = "${poi.categoryIcon} ${poi.name}"
                snippet = "📍 ${"%.0f".format(poi.distance)}m entfernt\n${poi.address ?: ""}"
                icon    = ContextCompat.getDrawable(this@LocationActivity, R.drawable.ic_search_result)
                setOnMarkerClickListener { _, _ -> showPointInfoDialog(poi.lat, poi.lon, poi); true }
            }
            binding.mapView.overlays.add(marker)
            searchResultMarkers.add(marker)
        }
        binding.mapView.invalidate()
        if (results.isNotEmpty()) binding.mapView.controller.animateTo(GeoPoint(results[0].lat, results[0].lon))

        // Ergebnisliste unter der Karte einblenden
        poiList.clear()
        poiList.addAll(results)
        binding.poiRecyclerView.adapter?.notifyDataSetChanged()
        binding.categoryTitleText.text = "🔍 Suchergebnisse"
        binding.poiCountText.text      = "${results.size} Ergebnis(se) in 20 km"
        binding.emptyPoiText.visibility = View.GONE
        binding.categoryView.visibility = View.GONE
        binding.poiListView.visibility  = View.VISIBLE
        isShowingSearchResults = true
    }

    private fun clearSearchResults() {
        searchResultMarkers.forEach { binding.mapView.overlays.remove(it) }
        searchResultMarkers.clear()
        binding.mapView.invalidate()

        // Liste wieder ausblenden und Kategorienansicht zeigen
        poiList.clear()
        binding.poiRecyclerView.adapter?.notifyDataSetChanged()
        binding.poiListView.visibility  = View.GONE
        binding.categoryView.visibility = View.VISIBLE
        isShowingSearchResults = false
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etPOISearch.windowToken, 0)
    }

    private fun showNavigationDialog(poi: POI) {
        val myLoc = myLocationOverlay.myLocation ?: run {
            Toast.makeText(this, "Standort nicht verfügbar", Toast.LENGTH_SHORT).show()
            return
        }
        val isDark      = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) android.graphics.Color.WHITE
        else android.graphics.Color.parseColor("#2d5016")
        val distance    = myLoc.distanceToAsDouble(GeoPoint(poi.lat, poi.lon))
        val alreadyFav  = FavoritesManager.isFavorite(this, poi.lat, poi.lon)

        val dialog = AlertDialog.Builder(this)
            .setTitle("${poi.categoryIcon} ${poi.name}")
            .setMessage(
                "📍 ${"%.0f".format(distance)}m entfernt\n\n" +
                        "${poi.address?.let { "📬 $it\n\n" } ?: ""}" +
                        "Navigation starten?"
            )
            .setPositiveButton("Navigation starten") { _, _ -> startNavigationToPOI(poi) }
            .setNeutralButton(if (alreadyFav) "✓ In Favoriten" else "⭐ Favorit") { _, _ -> }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)

        val neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        if (alreadyFav) {
            neutralBtn.setTextColor(android.graphics.Color.parseColor("#8FC357"))
            neutralBtn.isEnabled = false
        } else {
            neutralBtn.setTextColor(android.graphics.Color.parseColor("#FFA000"))
            neutralBtn.setOnClickListener {
                FavoritesManager.add(
                    this,
                    buildFavoritePOI(poi.lat, poi.lon, poi.name, poi.categoryIcon, poi.address)
                )
                neutralBtn.text = "✓ In Favoriten"
                neutralBtn.setTextColor(android.graphics.Color.parseColor("#8FC357"))
                neutralBtn.isEnabled = false
                Toast.makeText(this, "⭐ Als Favorit gespeichert", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // HILFSFUNKTIONEN
    // ════════════════════════════════════════════════════════

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatDistance(meters: Double) =
        if (meters < 1000) "${meters.toInt()} m" else "${"%.1f".format(meters / 1000)} km"

    private fun centerOnCurrentLocation() {
        currentLocation?.let {
            centerMapOnLocation(it)
            currentPOIMarker?.let { m -> binding.mapView.overlays.remove(m) }; currentPOIMarker = null
            currentRoutePolyline?.let { l -> binding.mapView.overlays.remove(l) }; currentRoutePolyline = null
            binding.mapView.invalidate()
            binding.btnCurrentLocation.text = "📍 Standort"
        }
    }

    private fun shareLocation() {
        currentLocation?.let {
            val text = "Mein aktueller Standort:\nhttps://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=15/${it.latitude}/${it.longitude}"
            startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, text); type = "text/plain" }, "Standort teilen über"))
        } ?: Toast.makeText(this, "Standort noch nicht verfügbar", Toast.LENGTH_SHORT).show()
    }

    private fun setupOSMCopyright() {
        binding.osmCopyrightText.setOnClickListener {
            val webView = WebView(this).apply { settings.javaScriptEnabled = true; webViewClient = WebViewClient(); loadUrl("https://www.openstreetmap.org/copyright") }
            AlertDialog.Builder(this).setTitle("OpenStreetMap Lizenz").setView(webView).setPositiveButton("Schließen", null).show()
        }
    }

    // ════════════════════════════════════════════════════════
    // LOCATION PERMISSION
    // ════════════════════════════════════════════════════════

    private fun checkLocationPermission() {
        val isDark       = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor  = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("📍 Standortdienste aktivieren")
                .setMessage("HUNDEWILDNIS - App benötigt Zugriff auf Deinen Standort für Navigation, Wetteranzeige und weitere Funktionen.\n\nDeine Daten werden nur lokal verarbeitet und nicht an Dritte übermittelt.")
                .setPositiveButton("AKTIVIEREN") { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("ABLEHNEN", null)
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
                }
        } else {
            getLocation()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1002
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) getLocation()
        else Toast.makeText(this, "Standortberechtigung benötigt", Toast.LENGTH_LONG).show()
    }

    private fun getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        binding.progressBar.visibility = View.VISIBLE
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        var bestLocation: Location? = null
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        bestLocation?.let { updateLocation(it) }
        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        isRequestingUpdates = true
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
        } catch (e: Exception) {}
        
        try {
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BETWEEN_UPDATES, MIN_DISTANCE_CHANGE, this)
        } catch (e: Exception) {}
    }

    // ════════════════════════════════════════════════════════
    // STYLING
    // ════════════════════════════════════════════════════════

    private fun styleBackButton() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) Color.parseColor("#5a8038") else Color.parseColor("#2d5016")
        val heightPx = (56 * resources.displayMetrics.density).toInt()
        binding.btnBackToCategories.apply {
            text = "← Zurück"; textSize = 16f
            setTextColor(Color.WHITE); setBackgroundColor(bgColor)
            layoutParams = layoutParams.apply { width = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT; height = heightPx }
            setPadding(24, 12, 24, 12)
        }
    }

    // ════════════════════════════════════════════════════════
    // LIFECYCLE CALLBACKS
    // ════════════════════════════════════════════════════════

    override fun onProviderEnabled(provider: String)  {}
    override fun onProviderDisabled(provider: String) {}
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        if (::myLocationOverlay.isInitialized) myLocationOverlay.disableMyLocation()
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (::myLocationOverlay.isInitialized) myLocationOverlay.enableMyLocation()
        if (!isRequestingUpdates) requestLocationUpdates()

        // Navigation wiederherstellen falls Activity neu erstellt wurde
        if (!isNavigating &&
            NavigationForegroundService.isRunning &&
            NavigationForegroundService.savedDestLat != 0.0) {
            showPOIOnMap(
                POI(
                    id           = "nav_restored",
                    name         = NavigationForegroundService.destinationName,
                    category     = "Navigation",
                    categoryIcon = "🧭",
                    lat          = NavigationForegroundService.savedDestLat,
                    lon          = NavigationForegroundService.savedDestLon,
                    distance     = 0.0
                )
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRequestingUpdates) { locationManager.removeUpdates(this); isRequestingUpdates = false }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings_gear, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val appSettings = AppSettings(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val s1 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        val orsEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOrsApiKey)
        val orsLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutOrsApiKey)
        val btnClearOrs = view.findViewById<View>(R.id.btnClearOrsKey)

        orsLayout.setEndIconOnClickListener {
            showOrsInfoDialog()
        }

        btnClearOrs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("🗑️ Key löschen?")
                .setMessage("Möchtest Du den gespeicherten API-Key wirklich entfernen?")
                .setPositiveButton("Löschen") { _, _ ->
                    appSettings.clearOrsApiKey()
                    orsEdit.setText("")
                    Toast.makeText(this, "Key gelöscht", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)

        if (appSettings.hasCustomOrsKey()) {
            orsEdit.setText("********")
        }

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val csl = android.content.res.ColorStateList.valueOf(color)
        listOf(s1, s2, s3).forEach { it.thumbTintList = csl; it.trackTintList = csl.withAlpha(77) }

        s1.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_enabled", isChecked).apply(); s2.isEnabled = isChecked; s3.isEnabled = isChecked }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply() }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }
        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙️ Einstellungen")
            .setView(view)
            .setPositiveButton("Fertig") { _, _ ->
                val newKey = orsEdit.text?.toString() ?: ""
                if (newKey.isNotBlank() && newKey != "********") {
                    appSettings.saveOrsApiKey(newKey)
                }
            }
            .create()
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
    }

    private fun showOrsInfoDialog() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        AlertDialog.Builder(this)
            .setTitle("ORS API Key")
            .setMessage("Um die Routenberechnung (Routing) nutzen zu können, benötigst Du einen kostenlosen API-Key von OpenRouteService.\n\n" +
                    "Anleitung:\n" +
                    "1. Registriere Dich auf https://openrouteservice.org (HeiGIT gGmbH).\n" +
                    "2. Erstelle unter 'Tokens' einen neuen API-Key.\n" +
                    "3. Kopiere diesen Key und füge ihn hier ein.\n\n" +
                    "Hinweis: Ohne gültigen Key kann bei der Navigation nur die direkte Luftlinie angezeigt werden.")
            .setPositiveButton("OK", null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(color)
            }
    }

    // ── C: Wetter-Fetch Funktion (neu, ans Ende der Klasse) ──────
    private fun fetchWeatherAtDestination(lat: Double, lon: Double): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability," +
                "weather_code,wind_speed_10m,wind_gusts_10m" +
                "&daily=sunrise,sunset" +
                "&forecast_days=2" +
                "&timezone=auto"
        return JSONObject(URL(url).readText())
    }

    // ── D: Wetter-Card befüllen (neu, ans Ende der Klasse) ───────
    private fun updateWeatherCard(data: JSONObject, arrivalTimeMs: Long) {
        try {
            val hourly   = data.getJSONObject("hourly")
            val times    = hourly.getJSONArray("time")
            val temps    = hourly.getJSONArray("temperature_2m")
            val humidity = hourly.getJSONArray("relative_humidity_2m")
            val precipP  = hourly.getJSONArray("precipitation_probability")
            val codes    = hourly.getJSONArray("weather_code")
            val wind     = hourly.getJSONArray("wind_speed_10m")

            // Stunde finden die der Ankunftszeit am nächsten liegt
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.GERMAN)
            var closestIdx = 0
            var minDiff = Long.MAX_VALUE
            for (i in 0 until times.length()) {
                val t = fmt.parse(times.getString(i))?.time ?: continue
                val diff = Math.abs(t - arrivalTimeMs)
                if (diff < minDiff) { minDiff = diff; closestIdx = i }
            }

            val temp       = temps.getDouble(closestIdx)
            val hum        = humidity.getInt(closestIdx)
            val precip     = precipP.getInt(closestIdx)
            val code       = codes.getInt(closestIdx)
            val windKmh    = wind.getDouble(closestIdx)
            val beaufort   = when {
                windKmh < 1   -> 0;  windKmh < 6   -> 1;  windKmh < 12  -> 2
                windKmh < 20  -> 3;  windKmh < 29  -> 4;  windKmh < 39  -> 5
                windKmh < 50  -> 6;  windKmh < 62  -> 7;  windKmh < 75  -> 8
                windKmh < 89  -> 9;  windKmh < 103 -> 10; windKmh < 118 -> 11
                else -> 12
            }

            val (desc, icon) = when (code) {
                0            -> "Klar"               to "☀️"
                1            -> "Leicht bewölkt"     to "🌤️"
                2            -> "Teilw. bewölkt"     to "⛅"
                3            -> "Bewölkt"            to "☁️"
                45, 48       -> "Nebel"              to "🌫️"
                in 51..57    -> "Nieselregen"        to "🌦️"
                in 61..67    -> "Regen"              to "🌧️"
                in 71..77    -> "Schneefall"         to "🌨️"
                in 80..82    -> "Regenschauer"       to "🌦️"
                in 85..86    -> "Schneeschauer"      to "🌨️"
                in 95..99    -> "Gewitter"           to "⛈️"
                else         -> "Wetter $code"       to "🌡️"
            }

            // Ankunftszeit formatieren
            val arrivalTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMAN)
                .format(java.util.Date(arrivalTimeMs))

            binding.tvWeatherIcon.text         = icon
            binding.tvWeatherDesc.text         = desc
            binding.tvWeatherArrivalLabel.text = "Ankunft ~$arrivalTime Uhr"
            binding.tvWeatherTemp.text         = "${"%.1f".format(temp)}°C"
            binding.tvWeatherPrecip.text       = "$precip%"
            binding.tvWeatherWind.text         = "${"%.0f".format(windKmh)} km/h\nBft $beaufort"
            binding.tvWeatherHumidity.text     = "$hum%"

            // ── NEU: Wetter-Snapshot für Geländeanalyse merken + Card aktualisieren ──
            lastWeatherSnapshot = WeatherSnapshot(temp, precip, windKmh)
            currentRouteData?.let { updateTerrainAnalysisCard(it) }

            // Sonnenauf-/untergang
            // NEU — alle verfügbaren Tage auslesen:
            val daily   = data.getJSONObject("daily")
            val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.GERMAN)
            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.GERMAN)

            val sunriseArr = daily.getJSONArray("sunrise")
            val sunsetArr  = daily.getJSONArray("sunset")
            val now = System.currentTimeMillis()

            val sunriseTimes = mutableListOf<Long>()
            val sunsetTimes  = mutableListOf<Long>()
            for (i in 0 until sunriseArr.length()) {
                dateFmt.parse(sunriseArr.getString(i))?.time?.let { sunriseTimes.add(it) }
            }
            for (i in 0 until sunsetArr.length()) {
                dateFmt.parse(sunsetArr.getString(i))?.time?.let { sunsetTimes.add(it) }
            }

// Card-Anzeige: nächster Sonnenauf-/-untergang ab jetzt
            val displaySunriseMs = sunriseTimes.firstOrNull { it > now } ?: sunriseTimes.firstOrNull()
            val displaySunsetMs  = sunsetTimes.firstOrNull  { it > now } ?: sunsetTimes.firstOrNull()
            displaySunriseMs?.let { binding.tvWeatherSunrise.text = "🌅 ${timeFmt.format(java.util.Date(it))} Uhr" }
            displaySunsetMs?.let  { binding.tvWeatherSunset.text  = "🌇 ${timeFmt.format(java.util.Date(it))} Uhr" }

// Licht-Situation bei Ankunft:
// Welcher Sonnenauf-/-untergang liegt zuletzt VOR der Ankunftszeit?
            val lastSunriseBeforeArrival = sunriseTimes.filter { it <= arrivalTimeMs }.maxOrNull()
            val lastSunsetBeforeArrival  = sunsetTimes.filter  { it <= arrivalTimeMs }.maxOrNull()
            val nextSunriseAfterNow      = sunriseTimes.firstOrNull { it > now }
            val nextSunsetAfterNow       = sunsetTimes.firstOrNull  { it > now }

// Tageslicht bei Ankunft wenn letzter Sonnenaufgang nach letztem Sonnenuntergang
            val isArrivalDaylight = when {
                lastSunriseBeforeArrival == null -> false
                lastSunsetBeforeArrival  == null -> true
                else -> lastSunriseBeforeArrival > lastSunsetBeforeArrival
            }

            val safetyText: String
            val safetyColor: Int

            if (isArrivalDaylight) {
                when {
                    nextSunriseAfterNow != null &&
                            arrivalTimeMs in nextSunriseAfterNow..(nextSunriseAfterNow + 30 * 60000L) -> {
                        safetyText  = "🌅 Ankunft kurz nach Sonnenaufgang"
                        safetyColor = Color.parseColor("#FFA726")
                    }
                    nextSunsetAfterNow != null &&
                            arrivalTimeMs in (nextSunsetAfterNow - 30 * 60000L)..nextSunsetAfterNow -> {
                        safetyText  = "🌇 Ankunft kurz vor Sonnenuntergang"
                        safetyColor = Color.parseColor("#FFA726")
                    }
                    else -> {
                        safetyText  = "☀️ Ankunft bei Tageslicht"
                        safetyColor = Color.parseColor("#8FC357")
                    }
                }
            } else {
                if (nextSunriseAfterNow != null && nextSunriseAfterNow > arrivalTimeMs) {
                    val diff = ((nextSunriseAfterNow - arrivalTimeMs) / 60000).toInt()
                    safetyText  = "🌙 Ankunft $diff Min vor Sonnenaufgang — Dunkel!"
                    safetyColor = Color.parseColor("#FF7043")
                } else if (lastSunsetBeforeArrival != null) {
                    val diff = ((arrivalTimeMs - lastSunsetBeforeArrival) / 60000).toInt()
                    safetyText  = "🌙 Ankunft $diff Min nach Sonnenuntergang — Dunkel!"
                    safetyColor = Color.parseColor("#FF7043")
                } else {
                    safetyText  = "🌙 Ankunft im Dunkeln"
                    safetyColor = Color.parseColor("#FF7043")
                }
            }

            binding.tvWeatherSafetyInfo.text = safetyText
            binding.tvWeatherSafetyInfo.setTextColor(safetyColor)

// Nur Sonnen-Events die tatsächlich auf der Route liegen als Marker setzen
            val routeSunriseMs = nextSunriseAfterNow?.takeIf { it in now..arrivalTimeMs }
            val routeSunsetMs  = nextSunsetAfterNow?.takeIf  { it in now..arrivalTimeMs }
            currentRouteData?.let { route ->
                updateSunEventMarkersOnRoute(route, routeSunriseMs, routeSunsetMs, timeFmt)
            }

        } catch (e: Exception) {
            Log.e("LocationActivity", "Wetter-Card Fehler: ${e.message}")
        }
    }

    // 5. Neue Funktion: Tap-Marker setzen + Overpass abfragen:
    private fun placeTapMarkerAndShowInfo(lat: Double, lon: Double) {
        tapMarker?.let { binding.mapView.overlays.remove(it) }
        tapMarker = Marker(binding.mapView).apply {
            position = GeoPoint(lat, lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@LocationActivity, R.drawable.ic_search_result)
                ?.mutate()?.also {
                    it.setTint(Color.parseColor("#FF5722")) // Orange — unterscheidet sich von grünen POI-Markern
                }
        }
        binding.mapView.overlays.add(tapMarker)
        binding.mapView.invalidate()

        lifecycleScope.launch {
            val nearestPoi = withContext(Dispatchers.IO) {
                try { fetchNearestOSMElement(lat, lon) } catch (e: Exception) { null }
            }
            showPointInfoDialog(lat, lon, nearestPoi)
        }
    }

    // 6. Neue Funktion: Nächsten OSM-Punkt suchen:
    private suspend fun fetchNearestOSMElement(lat: Double, lon: Double): POI? {
        val query = """
        [out:json][timeout:10];
        (
          node(around:50,$lat,$lon)["name"];
          way(around:50,$lat,$lon)["name"];
        );
        out center 1;
    """.trimIndent()

        val json     = OverpassHelper.queryWithRetry(query) ?: return null
        val elements = json.getJSONArray("elements")
        if (elements.length() == 0) return null

        val el = elements.getJSONObject(0)
        val elLat: Double; val elLon: Double
        when (el.getString("type")) {
            "node"            -> { elLat = el.getDouble("lat"); elLon = el.getDouble("lon") }
            "way", "relation" -> {
                val center = el.optJSONObject("center") ?: return null
                elLat = center.getDouble("lat"); elLon = center.getDouble("lon")
            }
            else -> return null
        }

        val tags     = parseTags(el)
        val name     = tags["name"] ?: return null
        val dist     = calculateDistance(lat, lon, elLat, elLon)
        val category = POICategories.getCategoryForTags(tags)

        return POI(
            id            = el.optString("id", "tap"),
            name          = name,
            category      = category?.name ?: tags["amenity"] ?: tags["shop"] ?: tags["leisure"] ?: "Ort",
            categoryIcon  = category?.icon ?: "📍",
            lat           = elLat, lon = elLon, distance = dist, tags = tags,
            address       = buildAddress(tags),
            phone         = tags["phone"] ?: tags["contact:phone"],
            website       = tags["website"] ?: tags["contact:website"],
            openingHours  = tags["opening_hours"],
            description   = tags["description"] ?: tags["note"]
        )
    }

    // 7. Neue Funktion: Infobox anzeigen (für Tap UND für POI-Marker):
    private fun showPointInfoDialog(tapLat: Double, tapLon: Double, poi: POI?) {
        val isDark      = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) android.graphics.Color.WHITE
        else android.graphics.Color.parseColor("#2d5016")

        val title: String
        val msg = StringBuilder()

        if (poi != null) {
            title = "${poi.categoryIcon} ${poi.name}"
            msg.append("📂 ${poi.category}\n")
            poi.address?.let      { msg.append("📬 $it\n") }
            poi.phone?.let        { msg.append("📞 $it\n") }
            poi.openingHours?.let { msg.append("🕐 $it\n") }
            poi.description?.let  { msg.append("\nℹ️ $it\n") }
            msg.append("\n📍 ${"%.5f".format(tapLat)}, ${"%.5f".format(tapLon)}")
            msg.append("\n📏 ${formatDistance(poi.distance)} entfernt")
        } else {
            title = "📍 Gewählter Punkt"
            msg.append("Kein benannter Ort in 50 m gefunden.\n\n")
            msg.append("📍 ${"%.5f".format(tapLat)}, ${"%.5f".format(tapLon)}")
            val dist = currentLocation?.let {
                calculateDistance(it.latitude, it.longitude, tapLat, tapLon)
            } ?: 0.0
            msg.append("\n📏 ${formatDistance(dist)} entfernt")
        }

        val navTarget = POI(
            id = "tap_${System.currentTimeMillis()}",
            name = poi?.name ?: "Gewählter Punkt",
            category = poi?.category ?: "Kartenpunkt",
            categoryIcon = poi?.categoryIcon ?: "📍",
            lat = tapLat, lon = tapLon,
            distance = currentLocation?.let {
                calculateDistance(it.latitude, it.longitude, tapLat, tapLon)
            } ?: 0.0
        )

        val alreadyFav = FavoritesManager.isFavorite(this, tapLat, tapLon)

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg.toString())

        if (isNavigating) {
            // Drei-Button-Lösung für Navigation:
            builder.setPositiveButton("🏁 Neues Ziel") { _, _ ->
                stopNavigation()
                startNavigationToPOI(navTarget)
            }
            builder.setNeutralButton("🧭 Zwischenziel") { _, _ ->
                addWaypointToNavigation(navTarget)
            }
            builder.setNegativeButton("Abbrechen", null)
        } else {
            builder.setPositiveButton("Navigation starten") { _, _ -> startNavigationToPOI(navTarget) }
            builder.setNeutralButton(if (alreadyFav) "✓ In Favoriten" else "⭐ Favorit") { _, _ ->
                if (!alreadyFav) {
                    FavoritesManager.add(this, buildFavoritePOI(tapLat, tapLon, navTarget.name, navTarget.categoryIcon, poi?.address))
                    Toast.makeText(this, "⭐ Als Favorit gespeichert", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Abbrechen", null)
        }

        val dialog = builder.create()
        dialog.show()

        // Farben nach dem Anzeigen setzen
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
        
        if (isNavigating) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#9C27B0")) // Lila für Zwischenziel
        } else {
            if (!alreadyFav) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#FFA000"))
            } else {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(Color.parseColor("#8FC357"))
            }
        }
    }

    private fun sampleRoutePoints(
        points: List<GeoPoint>,
        intervalMeters: Double,
        maxPoints: Int = 25
    ): List<GeoPoint> {
        if (points.isEmpty()) return emptyList()
        val sampled = mutableListOf(points.first())
        var accumulated = 0.0
        for (i in 1 until points.size) {
            val d = calculateDistance(
                points[i-1].latitude, points[i-1].longitude,
                points[i].latitude,   points[i].longitude
            )
            accumulated += d
            if (accumulated >= intervalMeters) {
                sampled.add(points[i])
                accumulated = 0.0
                if (sampled.size >= maxPoints) break
            }
        }
        if (sampled.last() != points.last() && sampled.size < maxPoints)
            sampled.add(points.last())
        return sampled
    }

    private suspend fun fetchPOIsAlongRoute(routePoints: List<GeoPoint>): List<POI> {
        if (routePoints.isEmpty()) return emptyList()

        val sampled  = sampleRoutePoints(routePoints, 150.0, 25)
        val coordStr = sampled.joinToString(",") { "${it.latitude},${it.longitude}" }

        val query = """
        [out:json][timeout:25];
        (
          node(around:500,$coordStr)[amenity~"bench|drinking_water|toilets|bbq|dog_toilet|dog_waste_bin|waste_basket"];
          node(around:500,$coordStr)[waste=dog_excrement];
          node(around:500,$coordStr)[leisure=firepit];
          node(around:500,$coordStr)[natural=spring];
          node(around:1000,$coordStr)[tourism=viewpoint];
          node(around:1000,$coordStr)[natural=peak];
          node(around:1000,$coordStr)[amenity=shelter];
          node(around:1000,$coordStr)[leisure=picnic_site];
          node(around:1000,$coordStr)[emergency];
          node(around:1000,$coordStr)[leisure=nature_reserve];
          way(around:1000,$coordStr)[leisure=nature_reserve];
          way(around:1000,$coordStr)[boundary=national_park];
          relation(around:1000,$coordStr)[boundary=national_park];
        );
        out center;
    """.trimIndent()

        val json     = OverpassHelper.queryWithRetry(query) ?: return emptyList()
        val elements = json.getJSONArray("elements")
        val result   = mutableListOf<POI>()
        val seen     = mutableSetOf<String>()

        for (i in 0 until elements.length()) {
            val el   = elements.getJSONObject(i)
            val tags = parseTags(el)
            val (icon, catName) = getRoutePOIInfo(tags) ?: continue

            val elLat: Double; val elLon: Double
            when (el.getString("type")) {
                "node" -> { elLat = el.getDouble("lat"); elLon = el.getDouble("lon") }
                "way", "relation" -> {
                    val center = el.optJSONObject("center") ?: continue
                    elLat = center.getDouble("lat"); elLon = center.getDouble("lon")
                }
                else -> continue
            }

            val name = tags["name"] ?: catName
            val key  = "${"%.4f".format(elLat)},${"%.4f".format(elLon)},$catName"
            if (key in seen) continue
            seen.add(key)

            val distToUser = currentLocation?.let {
                calculateDistance(it.latitude, it.longitude, elLat, elLon)
            } ?: 0.0

            result.add(POI(
                id           = el.optString("id", "$i"),
                name         = name,
                category     = catName,
                categoryIcon = icon,
                lat          = elLat, lon = elLon,
                distance     = distToUser,
                tags         = tags,
                address      = buildAddress(tags),
                phone        = tags["phone"] ?: tags["contact:phone"],
                openingHours = tags["opening_hours"],
                description  = tags["description"] ?: tags["note"]
            ))
        }

        return result.sortedBy { it.distance }
    }

    private fun getRoutePOIInfo(tags: Map<String, String>): Pair<String, String>? = when {
        tags["amenity"] == "bench"           -> "🪑" to "Sitzbank"
        tags["amenity"] == "drinking_water"  -> "💧" to "Trinkwasser"
        tags["natural"] == "spring"          -> "💧" to "Quelle"
        tags["amenity"] == "toilets"         -> "🚻" to "Toilette"
        tags["amenity"] == "dog_toilet"      -> "🚽" to "Hundetoilette"
        tags["amenity"] == "dog_waste_bin"   -> "🚽" to "Hundetoilette"
        tags["waste"] == "dog_excrement"     -> "🚽" to "Hundetoilette"
        tags["amenity"] == "waste_basket"    -> "🗑️" to "Mülleimer"
        tags["leisure"] == "firepit"         -> "🔥" to "Feuerstelle"
        tags["amenity"] == "bbq"             -> "🔥" to "Grillplatz"
        tags["tourism"] == "viewpoint"       -> "🏔️" to "Aussichtspunkt"
        tags["natural"] == "peak"            -> "🏔️" to "Gipfel"
        tags["amenity"] == "shelter"         -> "⛺" to "Schutzhütte"
        tags["leisure"] == "picnic_site"     -> "🧺" to "Picknickplatz"
        tags["emergency"] != null            -> "🆘" to "Notfall"
        tags["leisure"] == "nature_reserve"  -> "🌲" to "Naturschutzgebiet"
        tags["boundary"] == "national_park"  -> "🏞️" to "Nationalpark"
        else -> null
    }

    private fun updateRoutePOIsCard(pois: List<POI>) {
        binding.routePOIsContainer.removeAllViews()
        binding.tvRoutePOIsStatus.visibility = View.GONE

        if (pois.isEmpty()) {
            binding.tvRoutePOIsStatus.visibility = View.VISIBLE
            binding.tvRoutePOIsStatus.text = "Keine POIs in den nächsten 5 km"
            return
        }

// Hinweis-Text oben in der Liste einfügen:
        val dp = resources.displayMetrics.density
        binding.routePOIsContainer.addView(TextView(this).apply {
            text      = "📍 Nächste 5 km ab Standort • aktualisiert alle 1 km"
            textSize  = 9.5f
            setTextColor(Color.parseColor("#666666"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * dp).toInt() }
        })

        pois.forEach { poi ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (6 * dp).toInt() }
                setOnClickListener { showRoutePOIDialog(poi) }
                setPadding(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                isClickable  = true
                isFocusable  = true
            }

            row.addView(TextView(this).apply {
                text   = poi.categoryIcon
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(
                    (30 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            })

            val infoCol = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            infoCol.addView(TextView(this).apply {
                text      = poi.name
                textSize  = 11.5f
                setTextColor(Color.parseColor("#CCCCCC"))
                maxLines  = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            infoCol.addView(TextView(this).apply {
                text     = "${poi.category} • ${formatDistance(poi.distance)}"
                textSize = 10f
                setTextColor(Color.parseColor("#888888"))
            })

            row.addView(infoCol)
            binding.routePOIsContainer.addView(row)

            // Trennlinie
            binding.routePOIsContainer.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.bottomMargin = (4 * dp).toInt() }
                setBackgroundColor(Color.parseColor("#484848"))
            })
        }
    }

    private fun showRoutePOIDialog(poi: POI) {
        val isDark      = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val msg = StringBuilder()
        msg.append("📂 ${poi.category}\n")
        msg.append("📏 ${formatDistance(poi.distance)} von Dir entfernt\n")
        poi.address?.let      { msg.append("📬 $it\n") }
        poi.phone?.let        { msg.append("📞 $it\n") }
        poi.openingHours?.let { msg.append("🕐 $it\n") }
        poi.description?.let  { msg.append("\nℹ️ $it\n") }
        msg.append("\n📍 ${"%.5f".format(poi.lat)}, ${"%.5f".format(poi.lon)}")

        val dialog = AlertDialog.Builder(this)
            .setTitle("${poi.categoryIcon} ${poi.name}")
            .setMessage(msg.toString())
            .setPositiveButton("🧭 Als Zwischenziel") { _, _ ->
                addWaypointToNavigation(poi)
            }
            .setNegativeButton("Schließen", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(buttonColor)
    }

    private fun addWaypointToNavigation(waypoint: POI) {
        val dest = finalDestination ?: return
        val loc  = currentLocation ?: return

        waypointQueue.addLast(waypoint)

        // Marker mit Nummerierung
        val num = waypointQueue.size
        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(waypoint.lat, waypoint.lon)
            title    = "${waypoint.categoryIcon} ${waypoint.name}"
            snippet  = "Zwischenziel $num"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@LocationActivity, R.drawable.ic_search_result)
                ?.mutate()?.also { it.setTint(Color.parseColor("#9C27B0")) }
        }
        waypointMarkers.add(marker)
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()

        lifecycleScope.launch {
            try {
                val routeData = withContext(Dispatchers.IO) {
                    fetchRouteWithWaypoints(loc.latitude, loc.longitude,
                        waypointQueue.toList(), dest.latitude, dest.longitude)
                }
                applyNewRoute(routeData, dest)
                Toast.makeText(this@LocationActivity,
                    "Zwischenziel $num: ${waypoint.name} hinzugefügt ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Marker wieder entfernen bei Fehler
                waypointQueue.removeLast()
                binding.mapView.overlays.remove(waypointMarkers.removeAt(waypointMarkers.lastIndex))
                binding.mapView.invalidate()
                Toast.makeText(this@LocationActivity,
                    "Zwischenziel konnte nicht hinzugefügt werden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWaypointReachedDialog(waypoint: POI) {
        val isDark      = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val nextInfo = when {
            waypointQueue.size > 1 -> "Nächstes Zwischenziel: ${waypointQueue.elementAt(1).name}"
            waypointQueue.size == 1 -> "Weiter zum Ziel"
            else -> "Weiter zum Ziel"
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("${waypoint.categoryIcon} Zwischenziel erreicht!")
            .setMessage("Du hast ${waypoint.name} erreicht.\n\nNavigation pausiert.\n$nextInfo")
            .setPositiveButton("▶️ Weiter") { _, _ -> continueNavigation() }
            .setCancelable(false)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(buttonColor)
    }

    private fun continueNavigation() {
        val dest = finalDestination ?: return
        val loc  = currentLocation ?: return

        // Erstes Zwischenziel aus Queue entfernen + Marker löschen
        if (waypointQueue.isNotEmpty()) {
            waypointQueue.removeFirst()
            if (waypointMarkers.isNotEmpty()) {
                binding.mapView.overlays.remove(waypointMarkers.removeAt(0))
                binding.mapView.invalidate()
            }
        }

        isAtWaypoint               = false
        waypointReachedDialogShown = false

        lifecycleScope.launch {
            try {
                val routeData = withContext(Dispatchers.IO) {
                    fetchRouteWithWaypoints(loc.latitude, loc.longitude,
                        waypointQueue.toList(), dest.latitude, dest.longitude)
                }
                applyNewRoute(routeData, dest)
            } catch (e: Exception) {
                Log.e("LocationActivity", "Weiternavigation Fehler: ${e.message}")
            }
        }
    }

    private fun fetchRouteWithWaypoints(
        fromLat: Double, fromLon: Double,
        waypoints: List<POI>,
        toLat: Double, toLon: Double
    ): RouteData {
        val apiKey = AppSettings(this).getOrsApiKey()
        val url    = "https://api.openrouteservice.org/v2/directions/$routingProfile/geojson"

        val coords = JSONArray().apply {
            put(JSONArray().apply { put(fromLon); put(fromLat) })
            waypoints.forEach { wp -> put(JSONArray().apply { put(wp.lon); put(wp.lat) }) }
            put(JSONArray().apply { put(toLon); put(toLat) })
        }

        val bodyObj = JSONObject().apply {
            put("coordinates", coords)
            put("elevation", true)
            put("extra_info", JSONArray().apply { put("surface"); put("waytype") })
            put("language", "de")
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
        conn.setRequestProperty("Accept",        "application/json, application/geo+json")
        conn.setRequestProperty("Authorization", apiKey)
        conn.setRequestProperty("User-Agent",    "HundeWildnisApp/9.7")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        conn.doOutput = true
        conn.outputStream.use { it.write(bodyObj.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode != 200) return RouteData(emptyList(), 0.0, 0.0)

        val json       = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val features   = json.getJSONArray("features")
        if (features.length() == 0) return RouteData(emptyList(), 0.0, 0.0)

        val feature    = features.getJSONObject(0)
        val properties = feature.getJSONObject("properties")
        val geometry   = feature.getJSONObject("geometry")
        val summary    = properties.getJSONObject("summary")
        val distance   = summary.getDouble("distance")
        var duration   = summary.getDouble("duration")

        // ✅ NEU: Geschwindigkeit für Fahrrad begrenzen (max 10 km/h)
        if (routingProfile == "cycling-regular") {
            val minDuration = distance / (10.0 / 3.6)
            if (duration < minDuration) duration = minDuration
        }

        var totalAscent = 0.0; var totalDescent = 0.0
        if (properties.has("segments")) {
            val segs = properties.getJSONArray("segments")
            for (i in 0 until segs.length()) {
                val seg = segs.getJSONObject(i)
                totalAscent  += seg.optDouble("ascent",  0.0)
                totalDescent += seg.optDouble("descent", 0.0)
            }
        }

        val coordinates     = geometry.getJSONArray("coordinates")
        val points          = mutableListOf<GeoPoint>()
        val elevationPoints = mutableListOf<Double>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            points.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
            if (coord.length() >= 3) elevationPoints.add(coord.getDouble(2))
        }

        // ── Oberfläche & Wegtyp parsen ───────────────────────
        val surfaceSummary = mutableListOf<SurfaceSummary>()
        val wayTypeSummary = mutableListOf<SurfaceSummary>()

        if (properties.has("extras")) {
            val extras = properties.getJSONObject("extras")
            if (extras.has("surface")) {
                val arr = extras.getJSONObject("surface").getJSONArray("summary")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val code = item.getInt("value")
                    surfaceSummary.add(SurfaceSummary(
                        code = code, name = RouteInfoHelper.getSurfaceName(code),
                        emoji = RouteInfoHelper.getSurfaceEmoji(code),
                        distance = item.getDouble("distance"), percentage = item.getDouble("amount")
                    ))
                }
            }
            if (extras.has("waytype")) {
                val arr = extras.getJSONObject("waytype").getJSONArray("summary")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val code = item.getInt("value")
                    wayTypeSummary.add(SurfaceSummary(
                        code = code, name = RouteInfoHelper.getWayTypeName(code),
                        emoji = RouteInfoHelper.getWayTypeEmoji(code),
                        distance = item.getDouble("distance"), percentage = item.getDouble("amount")
                    ))
                }
            }
        }

        return RouteData(
            points          = points,
            distance        = distance,
            duration        = duration,
            elevationPoints = elevationPoints,
            totalAscent     = totalAscent,
            totalDescent    = totalDescent,
            minElevation    = elevationPoints.minOrNull() ?: 0.0,
            maxElevation    = elevationPoints.maxOrNull() ?: 0.0,
            surfaceSummary  = surfaceSummary,   // ← jetzt übergeben
            wayTypeSummary  = wayTypeSummary    // ← jetzt übergeben
        )
    }

    // Route anwenden (wird von mehreren Stellen genutzt)
    private fun applyNewRoute(routeData: RouteData, dest: GeoPoint) {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        if (routeData.points.isNotEmpty()) {
            currentRouteDistance = routeData.distance
            currentRouteDuration = routeData.duration
            currentRoutePolyline = Polyline().apply {
                setPoints(routeData.points)
                outlinePaint.color       = Color.parseColor("#2196F3")
                outlinePaint.strokeWidth = 10f
            }
            binding.mapView.overlays.add(currentRoutePolyline)
            binding.mapView.invalidate()
            updateNavigationInfo()
            updateNavigationCards(routeData, dest.latitude, dest.longitude)
        }
    }

    // Nächste 5km der Route ab aktuellem Standort extrahieren
    private fun getNextRouteSegment(
        routePoints: List<GeoPoint>,
        currentLoc: Location,
        maxDistanceM: Double = 5000.0
    ): List<GeoPoint> {
        if (routePoints.isEmpty()) return emptyList()

        // Nächsten Routenpunkt finden
        var closestIdx = 0
        var minDist    = Double.MAX_VALUE
        routePoints.forEachIndexed { idx, point ->
            val d = calculateDistance(currentLoc.latitude, currentLoc.longitude,
                point.latitude, point.longitude)
            if (d < minDist) { minDist = d; closestIdx = idx }
        }

        // Vorwärts bis 5km akkumulieren
        val segment     = mutableListOf(routePoints[closestIdx])
        var accumulated = 0.0
        for (i in closestIdx + 1 until routePoints.size) {
            val d = calculateDistance(
                routePoints[i-1].latitude, routePoints[i-1].longitude,
                routePoints[i].latitude,   routePoints[i].longitude
            )
            accumulated += d
            segment.add(routePoints[i])
            if (accumulated >= maxDistanceM) break
        }
        return segment
    }

    // POI-Refresh mit 5km-Segment
    private fun refreshRoutePOIs(routePoints: List<GeoPoint>, currentLoc: Location) {
        val segment = getNextRouteSegment(routePoints, currentLoc, 5000.0)
        binding.tvRoutePOIsStatus.visibility = View.VISIBLE
        binding.tvRoutePOIsStatus.text       = "Aktualisiere…"
        binding.routePOIsContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val pois = withContext(Dispatchers.IO) { fetchPOIsAlongRoute(segment) }
                updateRoutePOIsCard(pois)
            } catch (e: Exception) {
                Log.w("LocationActivity", "Route-POI Refresh fehlgeschlagen: ${e.message}")
                binding.tvRoutePOIsStatus.text = "Nicht verfügbar"
            }
        }
    }



    private fun isDeviatingFromRoute(location: Location): Boolean {
        val route = currentRouteData ?: return true
        if (route.points.isEmpty()) return true
        val minDist = route.points.minOf { point ->
            calculateDistance(location.latitude, location.longitude,
                point.latitude, point.longitude)
        }
        return minDist > 30.0  // Neu berechnen erst ab 30m Abweichung
    }

    // Zwei neue Funktionen:

    /**
     * Berechnet die interpolierte Position auf der Route
     * zum Zeitpunkt eines Sonn-Events (Auf-/Untergang).
     */
    private fun getPositionOnRouteAtTime(
        routePoints: List<GeoPoint>,
        routeDistance: Double,
        routeDuration: Double,
        eventTimeMs: Long
    ): GeoPoint? {
        val secondsUntilEvent = (eventTimeMs - System.currentTimeMillis()) / 1000.0
        if (secondsUntilEvent <= 0 || secondsUntilEvent > routeDuration) return null

        val targetDistM = routeDistance * (secondsUntilEvent / routeDuration)
        var accumulated = 0.0

        for (i in 1 until routePoints.size) {
            val d = calculateDistance(
                routePoints[i-1].latitude, routePoints[i-1].longitude,
                routePoints[i].latitude,   routePoints[i].longitude
            )
            if (accumulated + d >= targetDistM) {
                val t   = if (d > 0) (targetDistM - accumulated) / d else 0.0
                val lat = routePoints[i-1].latitude  + t * (routePoints[i].latitude  - routePoints[i-1].latitude)
                val lon = routePoints[i-1].longitude + t * (routePoints[i].longitude - routePoints[i-1].longitude)
                return GeoPoint(lat, lon)
            }
            accumulated += d
        }
        return routePoints.lastOrNull()
    }

    /**
     * Setzt Sonnenaufgang/Sonnenuntergang-Marker
     * an der erwarteten Position auf der Route.
     */
    private fun updateSunEventMarkersOnRoute(
        route: RouteData,
        sunriseMs: Long?,
        sunsetMs: Long?,
        timeFmt: java.text.SimpleDateFormat
    ) {
        // Alte Marker entfernen
        sunriseRouteMarker?.let { binding.mapView.overlays.remove(it) }
        sunsetRouteMarker?.let  { binding.mapView.overlays.remove(it) }
        sunriseRouteMarker = null
        sunsetRouteMarker  = null

        // Sonnenaufgang-Marker
        if (sunriseMs != null) {
            getPositionOnRouteAtTime(route.points, route.distance, route.duration, sunriseMs)?.let { pos ->
                sunriseRouteMarker = Marker(binding.mapView).apply {
                    position = pos
                    title    = "🌅 Sonnenaufgang"
                    snippet  = timeFmt.format(java.util.Date(sunriseMs)) + " Uhr — Du wirst hier sein"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = createSunMarkerDrawable("🌅 Sonnenaufgang", Color.parseColor("#E65100"))
                }
                binding.mapView.overlays.add(sunriseRouteMarker)
            }
        }

        // Sonnenuntergang-Marker
        if (sunsetMs != null) {
            getPositionOnRouteAtTime(route.points, route.distance, route.duration, sunsetMs)?.let { pos ->
                sunsetRouteMarker = Marker(binding.mapView).apply {
                    position = pos
                    title    = "🌇 Sonnenuntergang"
                    snippet  = timeFmt.format(java.util.Date(sunsetMs)) + " Uhr — Du wirst hier sein"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = createSunMarkerDrawable("🌇 Sonnenuntergang", Color.parseColor("#B71C1C"))
                }
                binding.mapView.overlays.add(sunsetRouteMarker)
            }
        }

        binding.mapView.invalidate()
    }

    private fun createSunMarkerDrawable(label: String, color: Int): android.graphics.drawable.Drawable {
        val dp     = resources.displayMetrics.density
        val width  = (130 * dp).toInt()   // breiter für den Text
        val height = (44 * dp).toInt()    // etwas flacher
        val r      = 6 * dp

        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 90% transparent = Alpha 26 (10% von 255)
        val bgColor = Color.argb(26, Color.red(color), Color.green(color), Color.blue(color))

        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = bgColor
            style = android.graphics.Paint.Style.FILL
        }
        val bubbleBottom = height * 0.72f
        canvas.drawRoundRect(android.graphics.RectF(0f, 0f, width.toFloat(), bubbleBottom), r, r, bgPaint)

        // Zeiger-Dreieck
        val path = android.graphics.Path().apply {
            moveTo(width / 2f - 6 * dp, bubbleBottom)
            lineTo(width / 2f + 6 * dp, bubbleBottom)
            lineTo(width / 2f, height.toFloat())
            close()
        }
        canvas.drawPath(path, bgPaint)

        // Text — kleiner damit er passt, mit Schatten für Lesbarkeit
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize   = 9f * dp
            textAlign  = android.graphics.Paint.Align.CENTER
            typeface   = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(2f * dp, 0f, 1f * dp, android.graphics.Color.BLACK)
        }
        val textY = bubbleBottom / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, width / 2f, textY, textPaint)

        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    // ════════════════════════════════════════════════════════════
    // FAVORITEN
    // ════════════════════════════════════════════════════════════

    private fun loadFavorites() {
        selectedCategory       = null
        isShowingSearchResults = false
        isShowingFavorites     = true

        binding.categoryView.visibility       = View.GONE
        binding.poiListView.visibility        = View.VISIBLE
        binding.categoryTitleText.text        = "⭐  Favoriten"
        binding.btnManageFavorites.visibility = View.VISIBLE  // Verwalten-Button einblenden

        val favs = FavoritesManager.getAll(this)
        poiList.clear()
        clearPOIMarkers()

        val loc = currentLocation
        favs.forEach { fav ->
            val dist = loc?.let {
                calculateDistance(it.latitude, it.longitude, fav.lat, fav.lon)
            } ?: 0.0
            poiList.add(
                POI(
                    id           = fav.id,
                    name         = fav.displayName,
                    category     = "Favoriten",
                    categoryIcon = fav.categoryIcon,
                    lat          = fav.lat,
                    lon          = fav.lon,
                    distance     = dist,
                    address      = fav.address
                )
            )
        }

        // Klick → Info-Dialog (nicht direkt Navigation)
        binding.poiRecyclerView.adapter = POIAdapter(poiList) { poi ->
            showPointInfoDialog(poi.lat, poi.lon, poi)
        }

        binding.poiCountText.text = "${favs.size} gespeicherte Favoriten"
        binding.emptyPoiText.visibility =
            if (favs.isEmpty()) View.VISIBLE else View.GONE
        if (favs.isEmpty()) {
            binding.emptyPoiText.text =
                "Noch keine Favoriten.\nNavigationsziele über ⭐ speichern."
        }

        // Marker auf Karte
        poiList.forEach { poi ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(poi.lat, poi.lon)
                title    = "${poi.categoryIcon}  ${poi.name}"
                snippet  = poi.address ?: "%.4f°, %.4f°".format(poi.lat, poi.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    showPointInfoDialog(poi.lat, poi.lon, poi); true
                }
            }
            poiMarkers.add(marker)
            binding.mapView.overlays.add(marker)
        }
        binding.mapView.invalidate()
        if (poiList.isNotEmpty()) zoomToShowAllPOIs(poiList)
    }

    private fun showFavoritesManagement() {
        FavoritesManagementSheet().apply {
            onDismissed = { loadFavorites() }          // Liste nach Verwaltung neu laden
        }.show(supportFragmentManager, "fav_mgmt")
    }

    /** Erstellt FavoritePOI aus einem POI oder einem Kartenklick-Punkt */
    private fun buildFavoritePOI(
        lat:  Double, lon: Double,
        name: String, icon: String,
        address: String?
    ) = FavoritePOI(
        name         = name,
        lat          = lat,
        lon          = lon,
        categoryIcon = icon,
        address      = address
    )

    private fun startNavigationService(destinationName: String) {
        ContextCompat.startForegroundService(
            this,
            Intent(this, NavigationForegroundService::class.java).apply {
                putExtra("destination", destinationName)
            }
        )
    }

    private fun stopNavigationService() {
        stopService(Intent(this, NavigationForegroundService::class.java))
    }

    private fun updateNavNotification(infoText: String) {
        if (!NavigationForegroundService.isRunning) return
        NavigationForegroundService.notificationInfo = infoText
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, LocationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            NavigationForegroundService.NOTIFICATION_ID,
            NotificationCompat.Builder(this, NavigationForegroundService.CHANNEL_ID)
                .setContentTitle("🧭 ${NavigationForegroundService.destinationName}")
                .setContentText(infoText)
                .setSmallIcon(R.drawable.ic_my_location)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )
    }

    // ════════════════════════════════════════════════════════════
    // GELÄNDEANALYSE
    //
    // Reine Auswertungsschicht über bereits vorhandene RouteData-
    // und Wetterwerte. Löst KEINEN eigenen API-Call aus.
    // ════════════════════════════════════════════════════════════

    private fun updateTerrainAnalysisCard(routeData: RouteData) {
        // Steigungsklasse aus Distanz + Höhenmetern ableiten
        val slopeClass = SlopeClass.fromRoute(
            routeData.distance, routeData.totalAscent, routeData.totalDescent
        )

        // Badge aktualisieren
        binding.tvSlopeClassBadge.text = "${slopeClass.icon} ${slopeClass.displayName}"
        binding.cardSlopeClassBadge.setCardBackgroundColor(
            Color.parseColor(slopeClass.color)
        )

        // Asphaltanteil aus surfaceSummary ermitteln (Name-basiert,
        // robust gegen Code-Änderungen im RouteInfoHelper)
        val asphaltPercent = routeData.surfaceSummary
            .filter { it.name.contains("Asphalt", ignoreCase = true) ||
                    it.name.contains("Beton", ignoreCase = true) }
            .sumOf { it.percentage }

        // ✅ Hundeauswahl: Dropdown bei mehreren Profilen, sonst automatisch
        val selectedDog = setupTerrainDogSelector()
        val character   = selectedDog?.toCharacter()
        // Gewicht ist als freier String gespeichert (z.B. "12,5 kg" oder "12.5")
        // → robust per Regex extrahieren statt direktem toDoubleOrNull()
        val weightKg = selectedDog?.weight
            ?.let { Regex("""[\d.,]+""").find(it)?.value }
            ?.replace(",", ".")
            ?.toDoubleOrNull()

        // Empfohlene Maximaldistanz
        val recommendedM = TerrainRecommendationEngine.recommendMaxDistance(
            character, weightKg, slopeClass
        )
        binding.tvRecommendedDistance.text = if (recommendedM >= 1000)
            "${"%.1f".format(recommendedM / 1000.0)} km"
        else
            "$recommendedM m"

        // Vergleich Routendistanz vs. Empfehlung
        val isCar = (routingProfile == "driving-car")
        val dogName = selectedDog?.name ?: "Deinen Hund"

        if (routeData.distance > recommendedM * 1.10) {
            val overPercent = ((routeData.distance / recommendedM - 1.0) * 100).toInt()
            val baseText = "⚠️ Diese Route ist ca. $overPercent% länger als die Empfehlung für $dogName."
            val suitabilityText = "Diese Strecke ist für $dogName am Stück nicht geeignet."
            val carText = if (isCar) "\n\nℹ️ Diese Analyse ist rein theoretisch, da die Anreise per Auto geplant wird." else ""
            
            binding.tvDistanceComparison.visibility = View.VISIBLE
            binding.tvDistanceComparison.text = "$baseText\n$suitabilityText$carText"
        } else {
            if (isCar) {
                binding.tvDistanceComparison.visibility = View.VISIBLE
                binding.tvDistanceComparison.text = "ℹ️ Diese Analyse ist rein theoretisch, da die Anreise per Auto geplant wird."
            } else {
                binding.tvDistanceComparison.visibility = View.GONE
            }
        }

        // Warnungen aus Steigung + Asphalt + Wetter-Snapshot
        val snapshot = lastWeatherSnapshot
        val warnings = TerrainRecommendationEngine.buildWarnings(
            slopeClass                = slopeClass,
            asphaltPercent            = asphaltPercent,
            tempC                     = snapshot?.tempC,
            precipProbabilityPercent  = snapshot?.precipProbabilityPercent,
            windKmh                   = snapshot?.windKmh
        )

        renderTerrainWarnings(warnings)
    }

    /**
     * Befüllt/aktualisiert den Hunde-Auswahl-Spinner der Geländeanalyse-Card.
     * - 0 Hunde: Spinner ausgeblendet, keine Empfehlungsanpassung
     * - 1 Hund: Spinner ausgeblendet, dieser Hund wird automatisch verwendet
     * - 2+ Hunde: Spinner eingeblendet, Auswahl wird in terrainSelectedDogId gemerkt
     *
     * @return Das aktuell für die Empfehlung zu verwendende DogProfile (oder null)
     */
    private fun setupTerrainDogSelector(): DogProfile? {
        val profiles = DogProfileManager.getAllProfiles(this)

        if (profiles.isEmpty()) {
            binding.rowDogSelector.visibility = View.GONE
            return null
        }

        if (profiles.size == 1) {
            binding.rowDogSelector.visibility = View.GONE
            terrainSelectedDogId = profiles.first().id
            return profiles.first()
        }

        // Mehrere Hunde → Spinner einblenden und befüllen
        binding.rowDogSelector.visibility = View.VISIBLE

        // Falls noch keine Auswahl getroffen wurde oder der gewählte Hund
        // nicht mehr existiert: ersten Hund vorauswählen
        if (terrainSelectedDogId == null || profiles.none { it.id == terrainSelectedDogId }) {
            terrainSelectedDogId = profiles.first().id
        }

        val names = profiles.map { it.name }
        val currentAdapter = binding.spinnerTerrainDog.adapter
        val needsRebuild = currentAdapter == null ||
                currentAdapter.count != names.size ||
                (0 until names.size).any { currentAdapter.getItem(it) != names[it] }

        if (needsRebuild) {
            val adapter = android.widget.ArrayAdapter(
                this, android.R.layout.simple_spinner_item, names
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerTerrainDog.adapter = adapter

            val selectedIndex = profiles.indexOfFirst { it.id == terrainSelectedDogId }.coerceAtLeast(0)
            binding.spinnerTerrainDog.setSelection(selectedIndex, false)

            binding.spinnerTerrainDog.onItemSelectedListener =
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long
                    ) {
                        val newSelection = profiles.getOrNull(pos) ?: return
                        if (terrainSelectedDogId != newSelection.id) {
                            terrainSelectedDogId = newSelection.id
                            // Card mit neuer Auswahl sofort neu berechnen
                            currentRouteData?.let { updateTerrainAnalysisCard(it) }
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
        }

        return profiles.firstOrNull { it.id == terrainSelectedDogId }
    }

    private fun renderTerrainWarnings(warnings: List<TerrainWarning>) {
        binding.terrainWarningsContainer.removeAllViews()

        if (warnings.isEmpty()) {
            binding.tvNoWarnings.visibility = View.VISIBLE
            return
        }
        binding.tvNoWarnings.visibility = View.GONE

        warnings.forEach { warning ->
            val view = layoutInflater.inflate(
                R.layout.item_terrain_warning, binding.terrainWarningsContainer, false
            )
            view.findViewById<TextView>(R.id.tvWarningIcon).text = warning.level.icon
            view.findViewById<TextView>(R.id.tvWarningTitle).apply {
                text = warning.title
                setTextColor(Color.parseColor(warning.level.color))
            }
            view.findViewById<TextView>(R.id.tvWarningMessage).text = warning.message
            binding.terrainWarningsContainer.addView(view)
        }
    }
}