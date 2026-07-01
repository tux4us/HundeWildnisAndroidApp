package com.hundewildnis.app

import android.Manifest
import android.content.Context
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
import android.view.Menu
import android.view.MenuItem
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.hundewildnis.app.databinding.ActivityLocationShareBinding
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.net.URL
import kotlin.math.*

/**
 * LocationShareActivity – In-App Navigation und Kartenansicht für Standortfreigabe.
 * Orientiert am Design und der Funktionalität von LocationActivity_POI.
 * Nutzt interne Routing-Logik (OpenRouteService) ohne externe Apps.
 */
class LocationShareActivity : SecureBaseActivity(), LocationListener {

    companion object {
        private const val TAG = "LocationShareActivity"
        private const val MAP_REFRESH_INTERVAL_MS = 60_000L
        private const val STALE_THRESHOLD_MS = 10 * 60 * 1000L
    }

    private lateinit var binding: ActivityLocationShareBinding
    private lateinit var prefs: WebDavPreferences
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private val gson = Gson()

    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private val locationMarkers = mutableMapOf<String, Marker>()
    private var currentRoutePolyline: Polyline? = null
    private var currentRouteData: RouteData? = null
    private var finalDestination: GeoPoint? = null

    private var refreshJob: Job? = null
    private var routingProfile = "foot-hiking"
    private var isNavigating = false
    private var isRerouting = false
    private var isFirstLocation = true

    private var lastUserInteractionTime = 0L
    private var autoReCenterJob: Job? = null

    private lateinit var deviceAdapter: DeviceAdapter
    private val deviceList = mutableListOf<LocationPayload>()

    // ── Berechtigungs-Launcher ─────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startShareService()
        } else {
            binding.switchLocationShare.isChecked = false
            Toast.makeText(this, "Standortberechtigung benötigt", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.getInstance().userAgentValue = packageName
        binding = ActivityLocationShareBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "📍 Standortfreigabe"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        prefs = WebDavPreferences(this)

        setupMap()
        setupDeviceList()
        setupListeners()
        loadSavedState()
        checkWebDavConfig()
        centerOnLastKnownLocation()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        myLocationOverlay.enableMyLocation()
        updateShareStatus()
        startMapRefreshLoop()
        startLocationUpdates()
        startAutoReCenterTimer()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        myLocationOverlay.disableMyLocation()
        refreshJob?.cancel()
        autoReCenterJob?.cancel()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_location_share, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_webdav -> {
                startActivity(Intent(this, WebDavSyncActivity::class.java))
                true
            }
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
        AlertDialog.Builder(this)
            .setTitle("ORS API Key")
            .setMessage("Um die Routenberechnung (Routing) nutzen zu können, benötigst Du einen kostenlosen API-Key von OpenRouteService.\n\n" +
                    "Anleitung:\n" +
                    "1. Registriere Dich auf https://openrouteservice.org (HeiGIT gGmbH).\n" +
                    "2. Erstelle unter 'Tokens' einen neuen API-Key.\n" +
                    "3. Kopiere diesen Key und füge ihn hier ein.\n\n" +
                    "Hinweis: Ohne gültigen Key kann bei der Navigation nur die direkte Luftlinie angezeigt werden.")
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun setupMap() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            
            // Nutzerinteraktion erkennen
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_DOWN) {
                    lastUserInteractionTime = System.currentTimeMillis()
                }
                false
            }
        }

        // Maßstab
        val scaleBar = ScaleBarOverlay(binding.mapView).apply {
            setAlignRight(true)
            setAlignBottom(true)
            setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
        }
        binding.mapView.overlays.add(scaleBar)

        // Eigener Standort (Blauer Punkt)
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), binding.mapView)
        
        // Standortpfeil besser sichtbar machen (Grün statt Weiß)
        try {
            val icon = ContextCompat.getDrawable(this, R.drawable.ic_my_location)
            if (icon != null) {
                val iconBitmap = android.graphics.Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(iconBitmap)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                
                myLocationOverlay.setPersonIcon(iconBitmap)
                myLocationOverlay.setDirectionArrow(iconBitmap, iconBitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Anpassen der Standort-Icons", e)
        }

        myLocationOverlay.enableMyLocation()
        myLocationOverlay.runOnFirstFix {
            runOnUiThread {
                if (isFirstLocation) {
                    val location = myLocationOverlay.myLocation
                    if (location != null) {
                        binding.mapView.controller.animateTo(location)
                        isFirstLocation = false
                    }
                }
            }
        }
        binding.mapView.overlays.add(myLocationOverlay)
    }

    private fun setupDeviceList() {
        deviceAdapter = DeviceAdapter(deviceList) { payload ->
            binding.mapView.controller.animateTo(GeoPoint(payload.lat, payload.lon))
            showPointInfoDialog(payload)
        }
        binding.rvDevices.adapter = deviceAdapter
    }

    private fun setupListeners() {
        // Fix: ClickListener statt CheckedChangeListener verhindert "Double Tap" Problem
        // durch programmgesteuertes Update des Switch-Status.
        binding.switchLocationShare.setOnClickListener {
            val isChecked = binding.switchLocationShare.isChecked
            if (isChecked) onShareEnabled() else onShareDisabled()
        }

        binding.etLocationLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveLabelFromInput()
        }

        binding.btnRefreshMap.setOnClickListener { refreshMap() }

        binding.btnOpenWebDavSettings?.setOnClickListener {
            startActivity(Intent(this, WebDavSyncActivity::class.java))
        }

        binding.osmCopyrightText.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.openstreetmap.org/copyright"))
            startActivity(intent)
        }

        binding.btnCenterLocation.setOnClickListener {
            if (isNavigating) {
                stopNavigation()
            } else {
                // Sofort zurück zur Automatik-Ansicht (Zentrieren & Zoom)
                lastUserInteractionTime = 0L
                autoCenterAndZoom()
            }
        }

        binding.toggleRoutingProfile.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                routingProfile = when (checkedId) {
                    R.id.btnProfileFoot -> "foot-hiking"
                    R.id.btnProfileBike -> "cycling-regular"
                    else -> "foot-hiking"
                }
                if (isNavigating) recalculateRoute()
            }
        }
    }

    private fun loadSavedState() {
        binding.switchLocationShare.isChecked = LocationShareService.isServiceRunning
        val savedLabel = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(LocationShareService.PREF_LOCATION_LABEL, android.os.Build.MODEL) ?: ""
        binding.etLocationLabel.setText(savedLabel)
    }

    // ── Standort & Navigation ──────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 5f, this)
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        currentLocation = location
        if (isFirstLocation) {
            binding.mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
            isFirstLocation = false
        }
        
        // Automatisches Zentrieren/Zoomen triggern
        autoCenterAndZoom()
        
        if (isNavigating && !isRerouting) {
            checkRerouting(location)
            updateNavigationInfo()
        }
    }

    private fun centerOnLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                last?.let {
                    currentLocation = it
                    binding.mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                    isFirstLocation = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Konnte letzten Standort nicht abrufen: ${e.message}")
            }
        }
    }

    private fun checkRerouting(location: Location) {
        val route = currentRouteData ?: return
        val minDist = route.points.minOfOrNull { calculateDistance(location.latitude, location.longitude, it.latitude, it.longitude) } ?: 100.0
        if (minDist > 40.0) recalculateRoute()
    }

    private fun startNavigationTo(target: GeoPoint, label: String) {
        isNavigating = true
        finalDestination = target
        binding.btnCenterLocation.text = "🧭 Stopp"
        binding.btnCenterLocation.isChecked = true
        myLocationOverlay.enableFollowLocation()
        
        startNavigationService(label)
        recalculateRoute()
    }

    private fun stopNavigation() {
        isNavigating = false
        finalDestination = null
        currentRouteData = null
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        currentRoutePolyline = null
        
        binding.btnCenterLocation.text = "📍 Standort"
        binding.btnCenterLocation.isChecked = false
        binding.navigationInfoText.visibility = View.GONE
        stopNavigationService()
        binding.mapView.invalidate()
    }

    private fun recalculateRoute() {
        val from = currentLocation ?: myLocationOverlay.myLocation?.let {
            Location("osm").apply { latitude = it.latitude; longitude = it.longitude }
        }

        if (from == null) {
            Log.d(TAG, "recalculateRoute: Standort noch nicht bekannt")
            return
        }
        val to = finalDestination ?: return
        drawRouteORS(from.latitude, from.longitude, to.latitude, to.longitude)
    }

    private fun drawRouteORS(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        val appSettings = AppSettings(this)
        if (!appSettings.hasCustomOrsKey()) {
            Toast.makeText(this, "ℹ️ Für präzises Routing bitte ORS-Key in den Einstellungen (Zahnrad oben) hinterlegen.", Toast.LENGTH_LONG).show()
        }
        isRerouting = true
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { fetchORS(fromLat, fromLon, toLat, toLon) }
                currentRouteData = data
                
                currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
                if (data.points.isNotEmpty()) {
                    currentRoutePolyline = Polyline().apply {
                        setPoints(data.points)
                        outlinePaint.color = Color.parseColor("#2196F3")
                        outlinePaint.strokeWidth = 12f
                    }
                    binding.mapView.overlays.add(currentRoutePolyline)
                    updateNavigationInfo()
                } else {
                    drawStraightLine(fromLat, fromLon, toLat, toLon)
                }
            } catch (e: Exception) {
                drawStraightLine(fromLat, fromLon, toLat, toLon)
            } finally {
                isRerouting = false
                binding.mapView.invalidate()
            }
        }
    }

    private fun drawStraightLine(fLat: Double, fLon: Double, tLat: Double, tLon: Double) {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        val pts = listOf(GeoPoint(fLat, fLon), GeoPoint(tLat, tLon))
        currentRoutePolyline = Polyline().apply {
            setPoints(pts)
            outlinePaint.color = Color.parseColor("#FF9800")
            outlinePaint.strokeWidth = 10f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
        binding.mapView.overlays.add(currentRoutePolyline)
    }

    private fun updateNavigationInfo() {
        val data = currentRouteData ?: return
        val km = data.distance / 1000.0
        val mins = (data.duration / 60.0).toInt()
        
        val dText = if (km < 1.0) "${data.distance.toInt()} m" else "%.1f km".format(km)
        val tText = if (mins < 60) "$mins Min" else "${mins/60}h ${mins%60}m"
        
        binding.navigationInfoText.text = "🧭 $dText • $tText"
        binding.navigationInfoText.visibility = View.VISIBLE
        updateNavNotification("$dText • $tText")
    }

    // ── Backend (ORS) ──────────────────────────────────────────────────────

    private fun fetchORS(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): RouteData {
        val apiKey = AppSettings(this).getOrsApiKey()
        val url = "https://api.openrouteservice.org/v2/directions/$routingProfile/geojson"
        
        val body = JSONObject().apply {
            put("coordinates", JSONArray().apply {
                put(JSONArray().apply { put(fromLon); put(fromLat) })
                put(JSONArray().apply { put(toLon);   put(toLat)   })
            })
        }

        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", apiKey)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode != 200) return RouteData(emptyList(), 0.0, 0.0)

        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val feat = json.getJSONArray("features").getJSONObject(0)
        val prop = feat.getJSONObject("properties").getJSONObject("summary")
        val geom = feat.getJSONObject("geometry").getJSONArray("coordinates")
        
        val pts = mutableListOf<GeoPoint>()
        for (i in 0 until geom.length()) {
            val c = geom.getJSONArray(i)
            pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
        }
        
        return RouteData(pts, prop.getDouble("distance"), prop.getDouble("duration"))
    }

    // ── Marker Verwaltung ──────────────────────────────────────────────────

    private fun updateMarkersOnMap(payloads: List<LocationPayload>) {
        val now = System.currentTimeMillis()
        val seenIds = mutableSetOf<String>()

        // Geräteliste aktualisieren
        deviceList.clear()
        deviceList.addAll(payloads.sortedByDescending { it.timestamp })
        deviceAdapter.notifyDataSetChanged()
        binding.tvMapStatus.text = if (payloads.isEmpty()) "Keine Geräte gefunden" else "Gefundene Geräte (${payloads.size}):"

        for (payload in payloads) {
            seenIds.add(payload.deviceId)
            val isStale = (now - payload.timestamp) > STALE_THRESHOLD_MS
            val pos = GeoPoint(payload.lat, payload.lon)

            val marker = locationMarkers[payload.deviceId] ?: Marker(binding.mapView).also {
                it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                binding.mapView.overlays.add(it)
                locationMarkers[payload.deviceId] = it
            }

            marker.position = pos
            marker.title = if (isStale) "⏸ ${payload.label}" else "📍 ${payload.label}"
            marker.snippet = "Zuletzt: ${formatTime(payload.timestamp)}"
            
            // Permanentes Label durch Custom Icon mit Text
            marker.icon = buildLabeledMarkerIcon(payload.label, isStale)
            
            marker.setOnMarkerClickListener { m, _ ->
                showPointInfoDialog(payload)
                true
            }
        }

        val toRemove = locationMarkers.keys.filter { it !in seenIds }
        toRemove.forEach { id -> binding.mapView.overlays.remove(locationMarkers[id]); locationMarkers.remove(id) }
        
        // Automatisches Zoomen triggern nach Marker-Update
        autoCenterAndZoom()
        
        binding.mapView.invalidate()
    }

    private fun showPointInfoDialog(payload: LocationPayload) {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val btnColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val dist = currentLocation?.let { calculateDistance(it.latitude, it.longitude, payload.lat, payload.lon) } ?: 0.0
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("📍 ${payload.label}")
            .setMessage("Entfernung: ${formatDistance(dist)}\nZuletzt gesehen: ${formatTime(payload.timestamp)}")
            .setPositiveButton("🧭 Navigation") { _, _ ->
                startNavigationTo(GeoPoint(payload.lat, payload.lon), payload.label)
            }
            .setNegativeButton("Schließen", null)
            .create()
            
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(btnColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(btnColor)
    }

    // ── Auto-Zentrierung & Zoom ──────────────────────────────────────────

    private fun startAutoReCenterTimer() {
        autoReCenterJob?.cancel()
        autoReCenterJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                if (System.currentTimeMillis() - lastUserInteractionTime >= 5000L) {
                    withContext(Dispatchers.Main) {
                        autoCenterAndZoom()
                    }
                }
            }
        }
    }

    private fun autoCenterAndZoom() {
        // Wenn Nutzer gerade interagiert hat, 5 Sek warten
        if (System.currentTimeMillis() - lastUserInteractionTime < 5000L) return

        val userLocation = currentLocation ?: myLocationOverlay.myLocation?.let {
            Location("osm").apply { latitude = it.latitude; longitude = it.longitude }
        } ?: return

        val userGeoPoint = GeoPoint(userLocation.latitude, userLocation.longitude)

        // Punkte sammeln, die sichtbar sein müssen
        val pointsToInclude = mutableListOf<GeoPoint>()
        pointsToInclude.add(userGeoPoint)

        if (isNavigating) {
            // Bei Navigation: Route und Ziel einschließen
            currentRouteData?.points?.let { pointsToInclude.addAll(it) }
            finalDestination?.let { pointsToInclude.add(it) }
        } else {
            // Sonst: Alle anderen Geräte einschließen
            locationMarkers.values.forEach { pointsToInclude.add(it.position) }
        }

        if (pointsToInclude.size > 1) {
            // Symmetrische BoundingBox um den User berechnen, damit er im Zentrum bleibt
            var maxLatDiff = 0.0
            var maxLonDiff = 0.0

            for (p in pointsToInclude) {
                maxLatDiff = max(maxLatDiff, abs(p.latitude - userGeoPoint.latitude))
                maxLonDiff = max(maxLonDiff, abs(p.longitude - userGeoPoint.longitude))
            }

            // Puffer hinzufügen (20%)
            maxLatDiff *= 1.2
            maxLonDiff *= 1.2
            
            // Minimale Ausdehnung sicherstellen (ca. 500m), damit nicht zu nah gezoomt wird
            maxLatDiff = max(maxLatDiff, 0.005)
            maxLonDiff = max(maxLonDiff, 0.005)

            val boundingBox = org.osmdroid.util.BoundingBox(
                userGeoPoint.latitude + maxLatDiff,
                userGeoPoint.longitude + maxLonDiff,
                userGeoPoint.latitude - maxLatDiff,
                userGeoPoint.longitude - maxLonDiff
            )

            // animateTo zentriert, zoomToBoundingBox setzt den Zoom
            // Wir nutzen zoomToBoundingBox mit animate=true
            binding.mapView.zoomToBoundingBox(boundingBox, true, 100)
            // Sicherheitshalber nochmal das Zentrum forcieren (OSM BoundingBox zentriert sonst auf BB-Mitte)
            binding.mapView.controller.animateTo(userGeoPoint)
        } else {
            // Nur der User allein: Standard-Zoom und Zentrierung
            binding.mapView.controller.animateTo(userGeoPoint)
            if (binding.mapView.zoomLevelDouble < 15.0) {
                binding.mapView.controller.setZoom(17.0)
            }
        }
    }

    // ── Hilfsmethoden (Analog LocationActivity) ────────────────────────────

    private fun buildLabeledMarkerIcon(label: String, isStale: Boolean): android.graphics.drawable.Drawable {
        val baseIcon = buildMarkerIcon(isStale) ?: return android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStale) Color.GRAY else Color.parseColor("#2d5016")
            textSize = 32f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(label, 0, label.length, textBounds)
        
        val padding = 12
        val width = maxOf(baseIcon.intrinsicWidth, textBounds.width() + padding * 2)
        val height = baseIcon.intrinsicHeight + textBounds.height() + padding + 5
        
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Background Pill für Text
        val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isStale) Color.parseColor("#88000000") else Color.parseColor("#CC2d5016")
            style = android.graphics.Paint.Style.FILL
        }
        val pillRect = android.graphics.RectF(
            (width / 2 - textBounds.width() / 2 - padding).toFloat(),
            0f,
            (width / 2 + textBounds.width() / 2 + padding).toFloat(),
            (textBounds.height() + padding).toFloat()
        )
        canvas.drawRoundRect(pillRect, 8f, 8f, bgPaint)
        
        // Text
        canvas.drawText(label, (width / 2).toFloat(), (textBounds.height() + padding / 2).toFloat(), paint.apply { color = Color.WHITE })
        
        // Icon darunter
        baseIcon.setBounds(width / 2 - baseIcon.intrinsicWidth / 2, textBounds.height() + padding + 5, width / 2 + baseIcon.intrinsicWidth / 2, height)
        baseIcon.draw(canvas)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun buildMarkerIcon(isStale: Boolean) = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default)
        ?.mutate()?.apply { setTint(if (isStale) Color.GRAY else Color.parseColor("#4CAF50")) }
        ?.mutate()?.apply { setTint(if (isStale) Color.GRAY else Color.parseColor("#4CAF50")) }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatDistance(m: Double) = if (m < 1000) "${m.toInt()} m" else "%.1f km".format(m / 1000)
    private fun formatTime(t: Long) = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(t))

    private fun startNavigationService(dest: String) {
        ContextCompat.startForegroundService(this, Intent(this, NavigationForegroundService::class.java).apply { putExtra("destination", dest) })
    }

    private fun stopNavigationService() = stopService(Intent(this, NavigationForegroundService::class.java))

    private fun updateNavNotification(info: String) {
        if (!NavigationForegroundService.isRunning) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val pi = android.app.PendingIntent.getActivity(this, 0, Intent(this, LocationShareActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        nm.notify(NavigationForegroundService.NOTIFICATION_ID, androidx.core.app.NotificationCompat.Builder(this, NavigationForegroundService.CHANNEL_ID).setContentTitle("🧭 ${NavigationForegroundService.destinationName}").setContentText(info).setSmallIcon(R.drawable.ic_my_location).setContentIntent(pi).setOngoing(true).setSilent(true).setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW).build())
    }

    // ── Freigabe Logik ──────────────────────────────────────────────────────

    private fun onShareEnabled() {
        saveLabelFromInput()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)); return
        }
        startShareService()
    }

    private fun onShareDisabled() {
        stopService(Intent(this, LocationShareService::class.java))
        // Das Flag wird im Service.onDestroy auf false gesetzt. 
        // Wir triggern ein verzögertes UI-Update.
        binding.root.postDelayed({ updateShareStatus() }, 500)
    }

    private fun startShareService() {
        ContextCompat.startForegroundService(this, Intent(this, LocationShareService::class.java))
        // Das Flag wird im Service.onStartCommand auf true gesetzt.
        binding.root.postDelayed({ updateShareStatus() }, 500)
    }

    private fun updateShareStatus() {
        val running = LocationShareService.isServiceRunning
        if (binding.switchLocationShare.isChecked != running) {
            binding.switchLocationShare.isChecked = running
        }
        binding.tvShareStatus.text = if (running) "✅ Freigabe aktiv" else "Freigabe inaktiv"
    }

    // ── Adapter für die Geräteliste ──────────────────────────────────────

    private inner class DeviceAdapter(
        private val items: List<LocationPayload>,
        private val onClick: (LocationPayload) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
            val label: android.widget.TextView = view.findViewById(R.id.tvDeviceLabel)
            val status: android.widget.TextView = view.findViewById(R.id.tvDeviceStatus)
            val distance: android.widget.TextView = view.findViewById(R.id.tvDeviceDistance)
            val icon: android.widget.TextView = view.findViewById(R.id.tvDeviceIcon)
            val coords: android.widget.TextView = view.findViewById(R.id.tvDeviceCoords)
            val battery: android.widget.TextView = view.findViewById(R.id.tvDeviceBattery)
            val btnRoute: View = view.findViewById(R.id.btnItemRoute)
            val btnShare: View = view.findViewById(R.id.btnItemShare)

            init {
                view.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        onClick(items[pos])
                    }
                }
                btnRoute.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        val item = items[pos]
                        startNavigationTo(GeoPoint(item.lat, item.lon), item.label)
                    }
                }
                btnShare.setOnClickListener {
                    val pos = adapterPosition
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        shareDeviceLocation(items[pos])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_location_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isStale = (System.currentTimeMillis() - item.timestamp) > STALE_THRESHOLD_MS
            
            holder.label.text = item.label
            holder.status.text = if (isStale) "⏸ Inaktiv (${formatTime(item.timestamp)})" else "Vor ${formatTimeDifference(item.timestamp)}"
            holder.icon.text = if (isStale) "⏸" else "📍"
            holder.coords.text = "Lat: %.6f, Lon: %.6f".format(item.lat, item.lon)
            
            if (item.batteryLevel != null && item.batteryLevel != -1) {
                holder.battery.visibility = View.VISIBLE
                holder.battery.text = "🔋 ${item.batteryLevel}%"
                holder.battery.setTextColor(when {
                    item.batteryLevel > 50 -> Color.parseColor("#8fad6f")
                    item.batteryLevel > 20 -> Color.parseColor("#FFC107")
                    else -> Color.parseColor("#F44336")
                })
            } else {
                holder.battery.visibility = View.GONE
            }
            
            val dist = currentLocation?.let { calculateDistance(it.latitude, it.longitude, item.lat, item.lon) } ?: 0.0
            holder.distance.text = formatDistance(dist)
            
            holder.label.setTextColor(if (isStale) Color.GRAY else Color.WHITE)
        }

        override fun getItemCount() = items.size

        private fun formatTimeDifference(t: Long): String {
            val diff = (System.currentTimeMillis() - t) / 1000
            return when {
                diff < 60 -> "$diff sek"
                diff < 3600 -> "${diff / 60} min"
                else -> "${diff / 3600} std"
            }
        }

        private fun shareDeviceLocation(payload: LocationPayload) {
            val osmPreview = "https://www.openstreetmap.org/?mlat=${payload.lat}&mlon=${payload.lon}#map=17/${payload.lat}/${payload.lon}"
            val geoUri = "geo:${payload.lat},${payload.lon}?q=${payload.lat},${payload.lon}(${payload.label})"
            
            val text = "📍 Standort von ${payload.label}:\n\n" +
                    "Karte (OSM): $osmPreview\n\n" +
                    "Direkt-Link für Navigations-Apps:\n$geoUri\n\n" +
                    "Koordinaten:\nLat: ${payload.lat}\nLon: ${payload.lon}"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis Standort")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Standort teilen via"))
        }
    }

    private fun saveLabelFromInput() {
        val label = binding.etLocationLabel.text?.toString()?.trim()
        if (!label.isNullOrBlank()) getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(LocationShareService.PREF_LOCATION_LABEL, label).apply()
    }

    private fun checkWebDavConfig() {
        val has = prefs.hasCredentials()
        binding.cardNoWebDav.isVisible = !has
        binding.switchLocationShare.isEnabled = has
    }

    private fun startMapRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) { fetchAndUpdateMarkers(); delay(MAP_REFRESH_INTERVAL_MS) }
        }
    }

    private fun refreshMap() = lifecycleScope.launch { fetchAndUpdateMarkers() }

    private suspend fun fetchAndUpdateMarkers() {
        val config = prefs.loadConfig() ?: return
        val client = WebDavClient(config.serverUrl, config.username, config.password)
        val own = LocationShareService.remoteFileName(this)

        withContext(Dispatchers.IO) {
            val files = client.listFiles(WebDavSyncManager.REMOTE_DIR).getOrNull()?.filter { it.startsWith("location_") && it.endsWith(".json") && it != own } ?: return@withContext
            val payloads = mutableListOf<LocationPayload>()
            val tempDir = File(cacheDir, "loc_dl").apply { mkdirs() }

            for (f in files) {
                val file = File(tempDir, f)
                if (client.downloadFile("${WebDavSyncManager.REMOTE_DIR}/$f", file).isSuccess) {
                    try { payloads.add(gson.fromJson(file.readText(), LocationPayload::class.java)) } catch (e: Exception) {}
                    file.delete()
                }
            }
            withContext(Dispatchers.Main) { updateMarkersOnMap(payloads) }
        }
    }
}
