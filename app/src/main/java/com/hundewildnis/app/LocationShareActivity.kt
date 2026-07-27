package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.gson.Gson
import com.hundewildnis.app.databinding.ActivityLocationShareBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.DashPathEffect
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.views.overlay.Polyline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.roundToInt

class LocationShareActivity : SecureBaseActivity() {

    companion object {
        private const val TAG = "LocationShareActivity"
        private const val MAP_REFRESH_INTERVAL_MS = 30_000L
        private const val STALE_THRESHOLD_MS = 10 * 60 * 1000L
    }

    private lateinit var binding: ActivityLocationShareBinding
    private lateinit var prefs: WebDavPreferences
    private val gson = Gson()
    private val locationMarkers = mutableMapOf<String, Marker>()
    private lateinit var myLocationOverlay: MyLocationNewOverlay
    private lateinit var labelOverlay: org.osmdroid.views.overlay.Overlay
    private var devicePayloads = listOf<LocationPayload>()
    private lateinit var deviceAdapter: DeviceAdapter
    private var initialZoomDone = false
    private var currentRoutePolyline: Polyline? = null
    private var isUpdatingSwitch = false

    // Fix 5: routingProfile als persistiertes Property
    private var routingProfile: String
        get() = getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("location_share_routing_profile", "foot-hiking") ?: "foot-hiking"
        set(value) {
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putString("location_share_routing_profile", value).apply()
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) startShareService()
        else {
            binding.switchLocationShare.isChecked = false
            Toast.makeText(this, "Standortberechtigung benötigt", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationShareBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "📍 Standortfreigabe"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }
        prefs = WebDavPreferences(this)
        setupMap()
        setupDeviceList()
        setupListeners()
        loadSavedState()
        checkWebDavConfig()
        startMapRefreshLoop()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        if (hasLocationPermission()) myLocationOverlay.enableMyLocation()
        initialZoomDone = false   // Fix 2: bei jedem Resume erneut zoomen
        updateShareStatus()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        myLocationOverlay.disableMyLocation()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Karte ──────────────────────────────────────────────────────────────

    private fun setupMap() {
        OsmConfig.getInstance().userAgentValue = packageName
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(51.0, 13.0))
        }

        val provider = GpsMyLocationProvider(this).apply {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER))
                addLocationSource(LocationManager.NETWORK_PROVIDER)
        }

        myLocationOverlay = MyLocationNewOverlay(provider, binding.mapView).apply {
            enableMyLocation()
            setDrawAccuracyEnabled(true)
            // Eigenes Icon statt Standard-Pfeil
            val personIcon = ContextCompat.getDrawable(this@LocationShareActivity, R.drawable.ic_my_location)
            if (personIcon != null) {
                val bmp = android.graphics.Bitmap.createBitmap(
                    personIcon.intrinsicWidth.takeIf { it > 0 } ?: 96,
                    personIcon.intrinsicHeight.takeIf { it > 0 } ?: 96,
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                personIcon.setBounds(0, 0, canvas.width, canvas.height)
                personIcon.draw(canvas)
                setPersonIcon(bmp)
                setDirectionIcon(bmp)
            }
            runOnFirstFix {
                binding.mapView.post {
                    if (locationMarkers.isEmpty()) {
                        myLocation?.let {
                            binding.mapView.controller.animateTo(it)
                            binding.mapView.controller.setZoom(15.0)
                        }
                    }
                }
            }
        }
        binding.mapView.overlays.add(myLocationOverlay)

        // Fix 3: dp-basierte Offsets und textSize statt hardcodierter px-Werte
        val dp = resources.displayMetrics.density

        labelOverlay = object : org.osmdroid.views.overlay.Overlay() {
            private val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#1B3A0A")
                textSize = 12f * dp
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.WHITE)
            }
            private val markerOffsetPx = 36f * dp
            private val selfOffsetPx   = 26f * dp

            override fun draw(
                canvas: android.graphics.Canvas,
                mapView: org.osmdroid.views.MapView,
                shadow: Boolean
            ) {
                if (shadow) return
                val proj = mapView.projection
                val screenPoint = android.graphics.Point()
                try {
                    for (marker in locationMarkers.values) {
                        proj.toPixels(marker.position, screenPoint)
                        val label = marker.title?.substringAfter(" ") ?: ""
                        if (label.isNotEmpty()) {
                            canvas.drawText(
                                label,
                                screenPoint.x.toFloat(),
                                screenPoint.y.toFloat() - markerOffsetPx,
                                paint
                            )
                        }
                    }
                    myLocationOverlay.myLocation?.let {
                        proj.toPixels(it, screenPoint)
                        canvas.drawText(
                            "ICH",
                            screenPoint.x.toFloat(),
                            screenPoint.y.toFloat() - selfOffsetPx,
                            paint
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Label drawing error", e)
                }
            }
        }
        binding.mapView.overlays.add(labelOverlay)
        binding.mapView.invalidate()
    }

    // ── Setup ──────────────────────────────────────────────────────────────

    private fun setupDeviceList() {
        deviceAdapter = DeviceAdapter(
            onClick = { payload ->
                binding.mapView.controller.animateTo(GeoPoint(payload.lat, payload.lon))
                binding.mapView.controller.setZoom(16.0)
                binding.mapView.parent.requestChildFocus(binding.mapView, binding.mapView)
            },
            onDeleteClick = { payload -> confirmAndDeleteDevice(payload) }
        )
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(this@LocationShareActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupListeners() {
        binding.switchLocationShare.setOnCheckedChangeListener { _, isChecked ->
            if (!isUpdatingSwitch) { if (isChecked) onShareEnabled() else onShareDisabled() }
        }
        binding.etLocationLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveLabelFromInput()
        }
        binding.btnRefreshMap.setOnClickListener { refreshMap() }
        binding.btnStopRouting.setOnClickListener { stopRouting() }
        binding.btnOpenWebDavSettings.setOnClickListener {
            startActivity(Intent(this, WebDavSyncActivity::class.java))
        }
    }

    private fun loadSavedState() {
        binding.switchLocationShare.isChecked = LocationShareService.isServiceRunning
        binding.etLocationLabel.setText(
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString(LocationShareService.PREF_LOCATION_LABEL, android.os.Build.MODEL)
        )
    }

    private fun checkWebDavConfig() {
        val hasConfig = prefs.hasCredentials()
        binding.cardNoWebDav.isVisible = !hasConfig
        binding.switchLocationShare.isEnabled = hasConfig
    }

    /**
     * Prüft ob die App von der Batterieoptimierung ausgenommen ist.
     * Nur relevant ab Android 6 (Doze Mode). Wird beim Aktivieren der
     * Standortfreigabe einmalig angefragt.
     */
    private fun requestBatteryOptimizationExemption() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        val pm = getSystemService(android.content.Context.POWER_SERVICE)
                as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return  // bereits ausgenommen

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚡ Akkuoptimierung deaktivieren?")
            .setMessage(
                "Damit die Standortfreigabe auch bei gesperrtem Bildschirm " +
                        "zuverlässig funktioniert, empfehlen wir, die Akkuoptimierung " +
                        "für HundeWildnis zu deaktivieren." +
                "Android schränkt Hintergrundprozesse sonst nach einiger Zeit ein." +
                "Du oder Dein Gerät könnten somit im Notfall nicht gefunden werden."
            )
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                try {
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    // Fallback: allgemeine Akku-Einstellungen öffnen
                    startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        )
                    )
                }
            }
            .setNegativeButton("Überspringen", null)
            .show()
    }

    // ── Freigabe ───────────────────────────────────────────────────────────

    private fun onShareEnabled() {
        saveLabelFromInput()
        if (!hasLocationPermission()) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            return
        }
        // Akkuoptimierung prüfen bevor der Service startet
        requestBatteryOptimizationExemption()
        startShareService()
    }

    private fun onShareDisabled() { stopShareService(); updateShareStatus() }

    private fun startShareService() {
        LocationShareService.isServiceRunning = true
        ContextCompat.startForegroundService(this, Intent(this, LocationShareService::class.java))
        updateShareStatus()
        Toast.makeText(this, "Standortfreigabe gestartet", Toast.LENGTH_SHORT).show()
    }

    private fun stopShareService() {
        LocationShareService.isServiceRunning = false
        stopService(Intent(this, LocationShareService::class.java))
        updateShareStatus()
    }

    private fun updateShareStatus() {
        if (isUpdatingSwitch) return
        isUpdatingSwitch = true
        if (LocationShareService.isServiceRunning) {
            binding.switchLocationShare.isChecked = true
            binding.tvShareStatus.text = "✅ Freigabe aktiv – Standort wird alle 30 Sek. gesendet."
            binding.tvShareStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            binding.switchLocationShare.isChecked = false
            binding.tvShareStatus.text = "Freigabe inaktiv."
            binding.tvShareStatus.setTextColor(Color.parseColor("#666666"))
        }
        isUpdatingSwitch = false
    }

    private fun saveLabelFromInput() {
        val label = binding.etLocationLabel.text?.toString()?.trim()
        if (!label.isNullOrBlank()) {
            getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putString(LocationShareService.PREF_LOCATION_LABEL, label).apply()
        }
    }

    // ── Karten-Refresh ─────────────────────────────────────────────────────

    private fun startMapRefreshLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    fetchAndUpdateMarkers()
                    delay(MAP_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    private fun refreshMap() {
        lifecycleScope.launch {
            binding.btnRefreshMap.isEnabled = false
            binding.tvMapStatus.text = "🔄 Aktualisiere Standortdaten..."
            fetchAndUpdateMarkers()
            binding.btnRefreshMap.isEnabled = true
            Toast.makeText(this@LocationShareActivity, "Standorte aktualisiert", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun fetchAndUpdateMarkers() {
        val config = prefs.loadConfig() ?: run {
            withContext(Dispatchers.Main) { binding.tvMapStatus.text = "Keine WebDAV-Zugangsdaten." }
            return
        }
        val client = WebDavClient(config.serverUrl, config.username, config.password)
        val ownFileName = LocationShareService.remoteFileName(this).trim()

        withContext(Dispatchers.IO) {
            val filesResult = client.listFiles(WebDavSyncManager.REMOTE_DIR)
            if (filesResult.isFailure) {
                withContext(Dispatchers.Main) {
                    binding.tvMapStatus.text = "⚠️ Verbindung fehlgeschlagen."
                }
                return@withContext
            }

            // Fix 4: robuster Dateinamen-Filter (startsWith/endsWith + exakter Ausschluss)
            val ownFileNameClean = ownFileName.lowercase()
            val locationFiles = filesResult.getOrDefault(emptyList()).filter { raw ->
                val name = raw.trim().lowercase()
                name.startsWith(LocationShareService.LOCATION_FILE_PREFIX.lowercase()) &&
                        name.endsWith(LocationShareService.LOCATION_FILE_SUFFIX.lowercase()) &&
                        name != ownFileNameClean
            }

            if (locationFiles.isEmpty()) {
                withContext(Dispatchers.Main) {
                    clearAllMarkers()
                    devicePayloads = emptyList()
                    deviceAdapter.submitList(emptyList())
                    val all = filesResult.getOrDefault(emptyList())
                    val firstRaw = all.firstOrNull()
                        ?: "Der Ordner '${WebDavSyncManager.REMOTE_DIR}' ist absolut leer."
                    binding.tvMapStatus.text =
                        "Eigene ID: $ownFileName\nDateien im Ordner: ${all.size}\nBeispiel-Name: $firstRaw"
                }
                return@withContext
            }

            val payloads = mutableListOf<LocationPayload>()
            val tempDir = File(cacheDir, "location_share_dl").apply { mkdirs() }
            val errors  = mutableListOf<String>()

            for (fileName in locationFiles) {
                val tempFile = File(tempDir, fileName)
                val dl = client.downloadFile("${WebDavSyncManager.REMOTE_DIR}/$fileName", tempFile)
                if (dl.isSuccess) {
                    try {
                        val payload = gson.fromJson(tempFile.readText(), LocationPayload::class.java)
                        // Dateinamen-Filter (Fix 4) schließt eigene Datei bereits aus.
                        // Kein zusätzlicher deviceId-Vergleich – dieser filterte fälschlich
                        // fremde Geräte mit ähnlicher Geräte-ID heraus (Bug 2).
                        if (payload != null) payloads.add(payload)
                    } catch (e: Exception) { errors.add("Fehler in $fileName") }
                    tempFile.delete()
                } else {
                    errors.add("Download fehlgeschlagen: $fileName")
                }
            }
            tempDir.deleteRecursively()

            withContext(Dispatchers.Main) {
                updateMarkersOnMap(payloads)
                binding.tvMapStatus.text = if (errors.isNotEmpty())
                    "Gefunden: ${locationFiles.size} andere, Fehler: ${errors.size}\n${errors.take(1).joinToString()}"
                else
                    "Gefunden: ${locationFiles.size} andere Geräte."
            }
        }
    }

    // ── Marker ─────────────────────────────────────────────────────────────

    private fun updateMarkersOnMap(payloads: List<LocationPayload>) {
        val now = System.currentTimeMillis()
        val seenIds = mutableSetOf<String>()
        devicePayloads = payloads

        for (payload in payloads) {
            seenIds.add(payload.deviceId)
            val isStale  = (now - payload.timestamp) > STALE_THRESHOLD_MS
            val position = GeoPoint(payload.lat, payload.lon)
            locationMarkers[payload.deviceId]?.let {
                it.position = position
                it.title    = buildMarkerTitle(payload, isStale)
                it.snippet  = buildMarkerSnippet(payload, isStale)
                it.icon     = buildMarkerIcon(isStale)
            } ?: run {
                val marker = Marker(binding.mapView).apply {
                    this.position = position
                    title   = buildMarkerTitle(payload, isStale)
                    snippet = buildMarkerSnippet(payload, isStale)
                    icon    = buildMarkerIcon(isStale)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                binding.mapView.overlays.add(marker)
                locationMarkers[payload.deviceId] = marker
            }
        }

        locationMarkers.keys.filter { it !in seenIds }.forEach {
            binding.mapView.overlays.remove(locationMarkers[it])
            locationMarkers.remove(it)
        }

        binding.mapView.invalidate()
        deviceAdapter.submitList(payloads)
        binding.tvDeviceListHeader.text = "Geräteliste (${payloads.size})"

        if (payloads.isNotEmpty() && !initialZoomDone) {
            zoomToShowAllMarkers(payloads)
            initialZoomDone = true
        }

        val stale = payloads.count { (now - it.timestamp) > STALE_THRESHOLD_MS }
        binding.tvMapStatus.text = buildStatusText(payloads.size - stale, stale)
    }

    private fun clearAllMarkers() {
        locationMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        locationMarkers.clear()
        binding.mapView.invalidate()
    }

    private fun buildMarkerTitle(p: LocationPayload, s: Boolean) =
        if (s) "⏸ ${p.label}" else "📍 ${p.label}"

    private fun buildMarkerSnippet(p: LocationPayload, s: Boolean) =
        "Zuletzt: ${formatTimestamp(p.timestamp)}${if (s) " (veraltet)" else ""}"

    private fun buildMarkerIcon(s: Boolean) =
        ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default)
            ?.mutate()?.apply {
                setColorFilter(android.graphics.PorterDuffColorFilter(
                    Color.parseColor(if (s) "#9E9E9E" else "#4CAF50"),
                    android.graphics.PorterDuff.Mode.SRC_IN
                ))
            }

    private fun zoomToShowAllMarkers(payloads: List<LocationPayload>) {
        val now = System.currentTimeMillis()
        val zoomActiveOnly = binding.switchZoomActiveOnly.isChecked
        val candidates = if (zoomActiveOnly)
            payloads.filter { (now - it.timestamp) <= STALE_THRESHOLD_MS } else payloads
        val allPoints = mutableListOf<GeoPoint>()
        myLocationOverlay.myLocation?.let { allPoints.add(it) }
        candidates.forEach { allPoints.add(GeoPoint(it.lat, it.lon)) }
        if (allPoints.size < 2) {
            allPoints.firstOrNull()?.let {
                binding.mapView.controller.animateTo(it)
                binding.mapView.controller.setZoom(15.0)
            }
            return
        }
        val minLat = allPoints.minOf { it.latitude };  val maxLat = allPoints.maxOf { it.latitude }
        val minLon = allPoints.minOf { it.longitude }; val maxLon = allPoints.maxOf { it.longitude }
        binding.mapView.zoomToBoundingBox(
            org.osmdroid.util.BoundingBox(maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001),
            true, 200
        )
    }

    private fun buildStatusText(f: Int, s: Int) = when {
        f == 0 && s == 0 -> "Keine Geräte gefunden."
        s == 0           -> "$f Gerät(e) aktiv."
        f == 0           -> "$s Gerät(e) veraltet."
        else             -> "$f aktiv, $s veraltet."
    }

    // ── Gerät löschen ──────────────────────────────────────────────────────

    private fun confirmAndDeleteDevice(payload: LocationPayload) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Eintrag entfernen?")
            .setMessage("Möchtest du '${payload.label}' wirklich vom Server löschen?")
            .setPositiveButton("Löschen") { _, _ -> deleteDeviceFromServer(payload) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun deleteDeviceFromServer(payload: LocationPayload) {
        val currentList = devicePayloads.toMutableList()
            .apply { removeAll { it.deviceId == payload.deviceId } }
        updateMarkersOnMap(currentList)
        lifecycleScope.launch {
            val config = prefs.loadConfig() ?: return@launch
            val client = WebDavClient(config.serverUrl, config.username, config.password)
            val fileName = "${LocationShareService.LOCATION_FILE_PREFIX}${payload.deviceId}${LocationShareService.LOCATION_FILE_SUFFIX}"
            if (withContext(Dispatchers.IO) {
                    client.deleteFile("${WebDavSyncManager.REMOTE_DIR}/$fileName")
                }.isSuccess) {
                Toast.makeText(this@LocationShareActivity, "Eintrag entfernt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@LocationShareActivity, "Fehler beim Löschen", Toast.LENGTH_SHORT).show()
            }
            refreshMap()
        }
    }

    // ── Routing ────────────────────────────────────────────────────────────

    private fun drawRouteOSRM(fLat: Double, fLon: Double, tLat: Double, tLon: Double) {
        lifecycleScope.launch {
            try {
                // Fix 1b: HTTP-Call explizit auf IO-Thread
                val route = withContext(Dispatchers.IO) { fetchBestRoute(fLat, fLon, tLat, tLon) }
                currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
                if (route.points.isNotEmpty()) {
                    currentRoutePolyline = Polyline().apply {
                        setPoints(route.points)
                        outlinePaint.color       = Color.parseColor("#2196F3")
                        outlinePaint.strokeWidth = 10f
                    }
                    binding.mapView.overlays.add(currentRoutePolyline)
                    binding.btnStopRouting.isVisible = true
                    binding.mapView.invalidate()
                } else drawRouteStraightLine(fLat, fLon, tLat, tLon)
            } catch (e: Exception) {
                Log.w(TAG, "Routing fehlgeschlagen, Luftlinie: ${e.message}")
                drawRouteStraightLine(fLat, fLon, tLat, tLon)
            }
        }
    }

    private fun fetchBestRoute(fLat: Double, fLon: Double, tLat: Double, tLon: Double): RouteData {
        return try { fetchOpenRouteServiceRoute(fLat, fLon, tLat, tLon) }
        catch (e: Exception) { Log.w(TAG, "ORS: ${e.message}"); RouteData(emptyList(), 0.0, 0.0) }
    }

    private fun fetchOpenRouteServiceRoute(fLat: Double, fLon: Double, tLat: Double, tLon: Double): RouteData {
        val apiKey = AppSettings(this).getOrsApiKey()

        // Fix 1a: Koordinaten-Reihenfolge korrekt (Start → Ziel)
        val body = JSONObject().apply {
            put("coordinates", JSONArray().apply {
                put(JSONArray().apply { put(fLon); put(fLat) })
                put(JSONArray().apply { put(tLon); put(tLat) })
            })
            put("elevation", true)
        }

        val conn = java.net.URL(
            "https://api.openrouteservice.org/v2/directions/$routingProfile/geojson"
        ).openConnection() as java.net.HttpURLConnection

        // Fix 1c: korrekte Header + Timeouts
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
        conn.setRequestProperty("Accept",        "application/json, application/geo+json")
        conn.setRequestProperty("Authorization", apiKey)
        conn.setRequestProperty("User-Agent",    "HundeWildnisApp/9.7")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 20_000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode != 200) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            Log.e(TAG, "ORS Fehler ${conn.responseCode}: $err")
            return RouteData(emptyList(), 0.0, 0.0)
        }

        val json   = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val coords = json.getJSONArray("features")
            .getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")

        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            points.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
        }
        return RouteData(points, 0.0, 0.0)
    }

    private fun drawRouteStraightLine(fLat: Double, fLon: Double, tLat: Double, tLon: Double) {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        currentRoutePolyline = Polyline().apply {
            setPoints(listOf(GeoPoint(fLat, fLon), GeoPoint(tLat, tLon)))
            outlinePaint.color       = Color.parseColor("#FF9800")
            outlinePaint.strokeWidth = 10f
            outlinePaint.pathEffect  = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
        binding.mapView.overlays.add(currentRoutePolyline)
        binding.btnStopRouting.isVisible = true
        binding.mapView.invalidate()
    }

    private fun stopRouting() {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
        currentRoutePolyline = null
        binding.btnStopRouting.isVisible = false
        binding.mapView.invalidate()
    }

    // ── Hilfsfunktionen ────────────────────────────────────────────────────

    private fun hasLocationPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun formatTimestamp(t: Long) =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(t))

    // ── DeviceAdapter (Fix 6: DiffUtil statt notifyDataSetChanged) ─────────

    private inner class DeviceAdapter(
        private val onClick: (LocationPayload) -> Unit,
        private val onDeleteClick: (LocationPayload) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.VH>() {

        private var items = listOf<LocationPayload>()

        fun submitList(newList: List<LocationPayload>) {
            val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(
                object : androidx.recyclerview.widget.DiffUtil.Callback() {
                    override fun getOldListSize() = items.size
                    override fun getNewListSize() = newList.size

                    override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                        items[oldPos].deviceId == newList[newPos].deviceId

                    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                        val o = items[oldPos]; val n = newList[newPos]
                        return o.label        == n.label        &&
                                o.lat          == n.lat          &&
                                o.lon          == n.lon          &&
                                o.timestamp    == n.timestamp    &&
                                o.batteryLevel == n.batteryLevel
                    }
                }
            )
            items = newList
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int) =
            VH(android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_location_device, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val b = com.hundewildnis.app.databinding.ItemLocationDeviceBinding.bind(view)

            fun bind(p: LocationPayload) {
                val stale = (System.currentTimeMillis() - p.timestamp) > STALE_THRESHOLD_MS
                b.tvDeviceLabel.text   = p.label
                b.tvDeviceIcon.text    = if (stale) "⏸" else "📍"
                b.tvDeviceStatus.text  = "Zuletzt: ${formatTimestamp(p.timestamp)}"
                b.tvDeviceCoords.text  = String.format("%.5f, %.5f", p.lat, p.lon)
                b.tvDeviceBattery.isVisible = p.batteryLevel != null
                b.tvDeviceBattery.text = "🔋 ${p.batteryLevel}%"

                // Distanzanzeige: Snapshot beim Binden (nur Anzeigezweck)
                val ownAtBind = myLocationOverlay.myLocation
                b.tvDeviceDistance.text = if (ownAtBind != null) {
                    val d = ownAtBind.distanceToAsDouble(GeoPoint(p.lat, p.lon))
                    if (d < 1000) "${d.roundToInt()} m"
                    else String.format("%.2f km", d / 1000.0)
                } else "-- km"

                itemView.setOnClickListener { onClick(p) }

                b.btnItemRoute.setOnClickListener {
                    // Fix: myLocation frisch abfragen statt bind()-Snapshot nutzen
                    val currentLoc = myLocationOverlay.myLocation
                    if (currentLoc != null) {
                        drawRouteOSRM(currentLoc.latitude, currentLoc.longitude, p.lat, p.lon)
                        binding.mapView.parent.requestChildFocus(binding.mapView, binding.mapView)
                    } else {
                        Toast.makeText(
                            this@LocationShareActivity,
                            "Warte auf GPS-Fix…",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                b.btnItemShare.setOnClickListener {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Standort von ${p.label}: " +
                                        "https://www.google.com/maps/search/?api=1&query=${p.lat},${p.lon}"
                            )
                        }, "Teilen"
                    ))
                }

                b.btnItemDelete.setOnClickListener { onDeleteClick(p) }
            }
        }
    }
}