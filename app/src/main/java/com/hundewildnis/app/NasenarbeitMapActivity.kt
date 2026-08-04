package com.hundewildnis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.content.res.Configuration
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityNasenarbeitMapBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import androidx.core.content.ContextCompat
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.*

/**
 * NasenarbeitMapActivity – Phase 1 + 2
 *
 * Phase 1: Karte zeichnen → gebietSchritte / gebietHa berechnen
 * Phase 2: Overpass API → Umgebung + Bodentyp automatisch erkennen
 * Snap-to-way: Abschnitte optional auf ORS foot-hiking einrasten
 */
class NasenarbeitMapActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityNasenarbeitMapBinding
    private lateinit var locationManager: LocationManager
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // ── MODUS ──────────────────────────────────────────────────
    private var mode = Mode.LINIE
    private var disziplin = NasenarbeitDisziplin.VERLORENSUCHE
    private var firstLocationReceived = false

    enum class Mode { LINIE, POLYGON }

    // ── WEGPUNKTE ──────────────────────────────────────────────
    // Jeder Abschnitt besteht aus mehreren GeoPoints (direkt ODER geroutet)
    private val segments   = mutableListOf<List<GeoPoint>>()   // pro Abschnitt
    private val markers    = mutableListOf<Marker>()            // Tap-Marker
    private val segmentPolylines = mutableListOf<Polyline>()    // pro Abschnitt

    // Polygon-Punkte (Freisuche)
    private val polyPoints = mutableListOf<GeoPoint>()
    private var areaPolygon: Polygon? = null

    // ── ERGEBNISSE ─────────────────────────────────────────────
    private var calculatedSchritte = 0
    private var calculatedHa       = 0.0

    // Phase 2: Overpass-Erkennungen
    private var detectedUmgebung = mutableListOf<String>()
    private var detectedBodenTyp = mutableListOf<String>()
    private var detectedWeights  = mutableListOf<Double>()
    private var detectedTopo     = mutableListOf<String>()
    private var overpassQueried  = false
    private var isRequestingUpdates = false

    // ── CONSTANTS ──────────────────────────────────────────────
    companion object {
        const val EXTRA_DISZIPLIN     = "map_disziplin"
        const val RESULT_SCHRITTE     = "result_schritte"
        const val RESULT_HA           = "result_ha"
        const val RESULT_USED         = "result_used"
        const val RESULT_UMGEBUNG     = "result_umgebung"    // ArrayList<String>
        const val RESULT_BODEN        = "result_boden"       // ArrayList<String>
        const val RESULT_TOPO         = "result_topo"        // ArrayList<String>
        const val RESULT_SEGMENT_VALUES = "result_segment_values" // DoubleArray
        const val RESULT_WEIGHTS      = "result_weights"     // DoubleArray
        const val RC_MAP              = 4200
        private const val REQ_LOCATION = 4201
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmConfig.getInstance().userAgentValue = packageName
        binding = ActivityNasenarbeitMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        disziplin = NasenarbeitDisziplin.valueOf(
            intent.getStringExtra(EXTRA_DISZIPLIN) ?: NasenarbeitDisziplin.VERLORENSUCHE.name
        )
        mode = if (disziplin == NasenarbeitDisziplin.FREISUCHE) Mode.POLYGON else Mode.LINIE

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Karte · ${disziplin.displayName}"

        setupMap()
        setupControls()
        setupButtons()
        requestLocation()
    }

    // ════════════════════════════════════════════════════════════
    // MAP SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(17.0)

        val scaleBar = ScaleBarOverlay(binding.mapView).apply {
            setCentred(false); setAlignBottom(true)
        }
        binding.mapView.overlays.add(scaleBar)

        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this).apply {
                setLocationUpdateMinTime(3000L)
                setLocationUpdateMinDistance(5f)
            },
            binding.mapView
        ).apply { enableMyLocation() }
        binding.mapView.overlays.add(myLocationOverlay)

        val receiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { onMapTap(it) }
                return true
            }
            override fun longPressHelper(p: GeoPoint?) = false
        }
        binding.mapView.overlays.add(0, MapEventsOverlay(receiver))
    }

    // ════════════════════════════════════════════════════════════
    // CONTROLS
    // ════════════════════════════════════════════════════════════

    private fun setupControls() {
        val dizButtons = listOf(
            binding.btnModeLinie  to NasenarbeitDisziplin.VERLORENSUCHE,
            binding.btnModeTrail  to NasenarbeitDisziplin.PERSONENSUCHE,
            binding.btnModePoly   to NasenarbeitDisziplin.FREISUCHE
        )

        fun highlightActiveButton() {
            dizButtons.forEach { (btn, diz) ->
                if (diz == disziplin) {
                    btn.setBackgroundColor(android.graphics.Color.parseColor("#004c6f")) // Blau = aktiv
                    btn.strokeWidth = 3
                } else {
                    btn.setBackgroundColor(android.graphics.Color.parseColor("#4C6238")) // dunkel = inaktiv
                    btn.strokeWidth = 0
                }
            }
        }

        dizButtons.forEach { (btn, diz) ->
            btn.setOnClickListener {
                disziplin = diz
                mode = if (diz == NasenarbeitDisziplin.FREISUCHE) Mode.POLYGON else Mode.LINIE
                highlightActiveButton()
                updateHint()
                clearDrawing()
            }
        }
        highlightActiveButton()
        updateHint()
    }

    private fun updateHint() {
        binding.tvMapHint.text = when (mode) {
            Mode.LINIE   -> "Tippe Wegpunkte · Pro Abschnitt: Weg oder Gelände wählen"
            Mode.POLYGON -> "Tippe ≥ 3 Punkte für die Suchfläche"
        }
        binding.tvResult.text = ""
        binding.btnConfirm.isEnabled = false
    }

    // ════════════════════════════════════════════════════════════
    // TAP HANDLER
    // ════════════════════════════════════════════════════════════

    private fun onMapTap(point: GeoPoint) {
        val marker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "${markers.size + 1}"
            try {
                icon = androidx.core.content.ContextCompat.getDrawable(
                    this@NasenarbeitMapActivity, R.drawable.ic_my_location
                )
            } catch (_: Exception) {}
        }
        markers.add(marker)
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()

        when (mode) {
            Mode.LINIE -> {
                val allPoints = allWaypoints()
                if (allPoints.isEmpty()) {
                    // Erster Punkt – einfach hinzufügen
                    segments.add(listOf(point))
                } else {
                    val prevPoint = allPoints.last()
                    // Ab dem zweiten Punkt: Fragen ob Weg oder Gelände
                    askSnapOrDirect(prevPoint, point)
                    return // Warten auf Dialog-Ergebnis
                }
                updateResult()
            }
            Mode.POLYGON -> {
                polyPoints.add(point)
                if (polyPoints.size >= 3) drawPolygon()
                updateResult()
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // SNAP-TO-WAY DIALOG
    // ════════════════════════════════════════════════════════════

    private fun askSnapOrDirect(from: GeoPoint, to: GeoPoint) {
        val distM = haversine(from.latitude, from.longitude, to.latitude, to.longitude)
        val distStr = if (distM >= 1000) String.format("%.1f km", distM / 1000)
        else "${distM.toInt()} m"

        AlertDialog.Builder(this)
            .setTitle("Abschnitt ${segments.size + 1} · $distStr")
            .setMessage(
                "Wie verläuft dieser Abschnitt?\n\n" +
                        "🛤️  Auf dem Weg\n" +
                        "Wird auf den nächstliegenden Fuß-/Wanderweg eingerastet.\n\n" +
                        "🌿  Quer durch das Gelände\n" +
                        "Direkte Linie, unabhängig von Wegen."
            )
            .setPositiveButton("🛤️ Auf dem Weg") { _, _ ->
                snapSegmentToRoute(from, to)
            }
            .setNegativeButton("🌿 Durch Gelände") { _, _ ->
                addDirectSegment(from, to)
            }
            .setCancelable(false)
            .show()
    }

    private fun addDirectSegment(from: GeoPoint, to: GeoPoint) {
        segments.add(listOf(from, to))
        redrawPolylines()
        updateResult()
    }

    private fun snapSegmentToRoute(from: GeoPoint, to: GeoPoint) {
        val appSettings = AppSettings(this)
        if (!appSettings.hasCustomOrsKey()) {
            Toast.makeText(this, "ℹ️ Für präzises Routing bitte ORS-Key in den Einstellungen (Zahnrad oben) hinterlegen.", Toast.LENGTH_LONG).show()
        }
        binding.tvResult.text = "⏳ Route wird berechnet …"
        lifecycleScope.launch {
            try {
                val routePoints = withContext(Dispatchers.IO) {
                    fetchOrsRoute(from, to)
                }
                if (routePoints.isNotEmpty()) {
                    segments.add(routePoints)
                    withContext(Dispatchers.Main) {
                        redrawPolylines()
                        updateResult()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@NasenarbeitMapActivity,
                            "⚠️ Kein Weg gefunden – direkte Linie verwendet",
                            Toast.LENGTH_SHORT
                        ).show()
                        addDirectSegment(from, to)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@NasenarbeitMapActivity,
                        "Routing-Fehler – direkte Linie verwendet",
                        Toast.LENGTH_SHORT
                    ).show()
                    addDirectSegment(from, to)
                }
            }
        }
    }

    /**
     * Exakt gleicher ORS-Aufruf wie in LocationActivity (getesteter, funktionierender Code).
     * Accept-Header, User-Agent-Version und Timeouts sind identisch.
     */
    private suspend fun fetchOrsRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val url = "https://api.openrouteservice.org/v2/directions/foot-hiking/geojson"

        val body = JSONObject().apply {
            put("coordinates", JSONArray().apply {
                put(JSONArray().apply { put(from.longitude); put(from.latitude) })
                put(JSONArray().apply { put(to.longitude);   put(to.latitude)   })
            })
            put("elevation", true)
            put("language", "de")
        }

        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
        conn.setRequestProperty("Accept",        "application/json, application/geo+json")
        conn.setRequestProperty("Authorization", AppSettings(this).getOrsApiKey())
        conn.setRequestProperty("User-Agent",    "HundeWildnisApp/9.7")
        conn.connectTimeout = 15000
        conn.readTimeout    = 20000
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            android.util.Log.e("NasenarbeitMap", "ORS Fehler $responseCode: $error")
            // Fallback: zweiter Versuch ohne elevation
            return fetchOrsRouteFallback(from, to)
        }

        return try {
            val json   = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val coords = json.getJSONArray("features")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")
            (0 until coords.length()).map { i ->
                val c = coords.getJSONArray(i)
                GeoPoint(c.getDouble(1), c.getDouble(0))
            }
        } catch (e: Exception) {
            android.util.Log.e("NasenarbeitMap", "ORS Parse-Fehler: ${e.message}")
            emptyList()
        }
    }

    /** Fallback: minimaler Request ohne optionale Felder */
    private suspend fun fetchOrsRouteFallback(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        return try {
            val url  = "https://api.openrouteservice.org/v2/directions/foot-hiking/geojson"
            val body = JSONObject().apply {
                put("coordinates", JSONArray().apply {
                    put(JSONArray().apply { put(from.longitude); put(from.latitude) })
                    put(JSONArray().apply { put(to.longitude);   put(to.latitude)   })
                })
            }
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
            conn.setRequestProperty("Accept",        "application/json, application/geo+json")
            conn.setRequestProperty("Authorization", AppSettings(this).getOrsApiKey())
            conn.setRequestProperty("User-Agent",    "HundeWildnisApp/9.7")
            conn.connectTimeout = 15000
            conn.readTimeout    = 20000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) {
                android.util.Log.e("NasenarbeitMap", "ORS Fallback auch Fehler: ${conn.responseCode}")
                return emptyList()
            }
            val json   = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val coords = json.getJSONArray("features")
                .getJSONObject(0).getJSONObject("geometry")
                .getJSONArray("coordinates")
            (0 until coords.length()).map { i ->
                val c = coords.getJSONArray(i)
                GeoPoint(c.getDouble(1), c.getDouble(0))
            }
        } catch (e: Exception) {
            android.util.Log.e("NasenarbeitMap", "ORS Fallback Exception: ${e.message}")
            emptyList()
        }
    }

    // ════════════════════════════════════════════════════════════
    // DRAW
    // ════════════════════════════════════════════════════════════

    private fun redrawPolylines() {
        segmentPolylines.forEach { binding.mapView.overlays.remove(it) }
        segmentPolylines.clear()
        segments.forEachIndexed { idx, pts ->
            if (pts.size >= 2) {
                val pl = Polyline(binding.mapView).apply {
                    setPoints(pts)
                    outlinePaint.color = Color.parseColor("#2d5016")
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.style = Paint.Style.STROKE
                }
                segmentPolylines.add(pl)
                binding.mapView.overlays.add(pl)
            }
        }
        binding.mapView.invalidate()
    }

    private fun drawPolygon() {
        areaPolygon?.let { binding.mapView.overlays.remove(it) }
        val closed = polyPoints + polyPoints.first()
        areaPolygon = Polygon(binding.mapView).apply {
            points = closed
            fillPaint.color = Color.parseColor("#402d5016")
            outlinePaint.color = Color.parseColor("#2d5016")
            outlinePaint.strokeWidth = 6f
        }
        binding.mapView.overlays.add(areaPolygon)
        binding.mapView.invalidate()
    }

    // ════════════════════════════════════════════════════════════
    // BERECHNUNG
    // ════════════════════════════════════════════════════════════

    private fun allWaypoints(): List<GeoPoint> = segments.flatten()

    private fun updateResult() {
        when (mode) {
            Mode.LINIE -> {
                val allPts = allWaypoints()
                if (allPts.size >= 2) {
                    val distM = calcPolylineDistanceM(allPts)
                    calculatedSchritte = (distM / 0.75).toInt()
                    val distStr = if (distM >= 1000) String.format("%.1f km", distM / 1000)
                    else "${distM.toInt()} m"
                    binding.tvResult.text = "📏 $distStr  ·  ~$calculatedSchritte Schritte"
                    binding.btnConfirm.isEnabled = true
                }
            }
            Mode.POLYGON -> {
                if (polyPoints.size >= 3) {
                    val areaM2 = calcPolygonAreaM2(polyPoints)
                    calculatedHa = areaM2 / 10_000.0
                    binding.tvResult.text = "📐 ${String.format("%.2f ha", calculatedHa)}  " +
                            "(${areaM2.toInt()} m²)"
                    binding.btnConfirm.isEnabled = true
                }
            }
        }
    }

    private fun calcPolylineDistanceM(pts: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 1 until pts.size)
            total += haversine(pts[i-1].latitude, pts[i-1].longitude,
                pts[i].latitude,   pts[i].longitude)
        return total
    }

    private fun calcPolygonAreaM2(pts: List<GeoPoint>): Double {
        if (pts.size < 3) return 0.0
        val R = 6_371_000.0
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val lat1 = Math.toRadians(pts[i].latitude);  val lat2 = Math.toRadians(pts[j].latitude)
            val lon1 = Math.toRadians(pts[i].longitude); val lon2 = Math.toRadians(pts[j].longitude)
            area += (lon2 - lon1) * (2 + sin(lat1) + sin(lat2))
        }
        return abs(area) * R * R / 2.0
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ════════════════════════════════════════════════════════════
    // PHASE 2 – OVERPASS API
    // ════════════════════════════════════════════════════════════

    /**
     * Wertet Gelände + Topografie PRO SEGMENT in Routenreihenfolge aus.
     * Jedes Segment bekommt eigene Overpass-Abfrage → korrekte Abfolge.
     * Elevation-API wird mit allen Routenpunkten in Reihenfolge abgefragt.
     */
    private fun queryOverpassForArea() {
        val segList = if (mode == Mode.LINIE) segments else listOf(polyPoints)
        if (segList.isEmpty()) return

        binding.tvResult.text = "🗺️ Geländedaten werden abgerufen …"
        binding.btnConfirm.isEnabled = false

        lifecycleScope.launch {
            try {
                // ── Topografie: Elevation in Routenreihenfolge ───────
                val allPts = allWaypoints().ifEmpty { polyPoints }
                val realSegments = if (mode == Mode.LINIE) segments.filter { it.size >= 2 } else segList
                val topoFromSlope = withContext(Dispatchers.IO) {
                    fetchElevationTopoPerSegment(realSegments)
                }

                // ── Umgebung + Boden: pro Segment in Routenreihenfolge
                val umgebungOrdered = mutableListOf<String>()
                val bodenOrdered    = mutableListOf<String>()
                val weightsOrdered  = mutableListOf<Double>()
                val topoFeatOrdered = mutableListOf<String>()

                withContext(Dispatchers.IO) {
                    segList.forEachIndexed { segIdx, segPts ->
                        if (segPts.size < 2) return@forEachIndexed
                        val bbox = buildBbox(segPts, paddingDeg = 0.0008)

                        // Timeout pro Segment: max 8 Sek, sonst Fallback
                        try {
                            val result = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                                fetchOverpassTerrainWithWeights(bbox)
                            }

                            if (mode == Mode.LINIE) {
                                val (umb, bod, w) = result ?: Triple(emptyList(), emptyList(), emptyList())
                                // 1:1 Zuordnung pro Segment
                                val umbVal = umb.firstOrNull() ?: ""
                                val bodVal = bod.firstOrNull() ?: ""

                                if (umbVal.isNotEmpty()) {
                                    umgebungOrdered.add(umbVal)
                                    bodenOrdered.add(bodVal)
                                    weightsOrdered.add(1.0)
                                } else {
                                    umgebungOrdered.add(umgebungOrdered.lastOrNull() ?: "Offenes Gelände")
                                    bodenOrdered.add(bodenOrdered.lastOrNull() ?: "")
                                    weightsOrdered.add(1.0)
                                }
                            } else {
                                // POLYGON: Alle gefundenen Geländetypen übernehmen
                                val (umb, bod, w) = result ?: Triple(listOf("Offenes Gelände"), listOf(""), listOf(1.0))
                                umb.forEachIndexed { i, u ->
                                    umgebungOrdered.add(u)
                                    bodenOrdered.add(bod[i])
                                    weightsOrdered.add(w[i])
                                }
                            }

                            val topoFeats = kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                                fetchOverpassTopoFeatures(bbox)
                            } ?: emptyList()
                            topoFeats.forEach { f -> if (f !in topoFeatOrdered) topoFeatOrdered.add(f) }

                        } catch (e: Exception) {
                            android.util.Log.w("NasenarbeitMap", "Seg $segIdx Overpass-Fehler: ${e.message}")
                            // Segment überspringen – kein Absturz
                            if (umgebungOrdered.isEmpty()) {
                                umgebungOrdered.add("Offenes Gelände")
                                bodenOrdered.add("")
                                weightsOrdered.add(1.0)
                            }
                        }
                    }
                }

                // ── Topografie zusammenführen in Routenreihenfolge ────
                val topoOrdered = buildTopoSequenzOrdered(topoFromSlope, topoFeatOrdered)

                detectedUmgebung.clear(); detectedUmgebung.addAll(umgebungOrdered)
                detectedBodenTyp.clear(); detectedBodenTyp.addAll(bodenOrdered)
                detectedWeights.clear();  detectedWeights.addAll(weightsOrdered)
                detectedTopo.clear();     detectedTopo.addAll(topoOrdered)
                overpassQueried = true

                withContext(Dispatchers.Main) {
                    updateResult()
                    val info = buildString {
                        if (detectedUmgebung.isNotEmpty())
                            append(" · 🗺️ ${detectedUmgebung.take(3).joinToString(" → ")}")
                        if (detectedTopo.isNotEmpty())
                            append(" · ⛰️ ${detectedTopo.take(2).joinToString(", ")}")
                    }
                    binding.tvResult.text = binding.tvResult.text.toString() + info
                    binding.btnConfirm.isEnabled = true
                    val topoStr = if (detectedTopo.isEmpty()) "–"
                    else detectedTopo.joinToString(" → ")
                    Toast.makeText(this@NasenarbeitMapActivity,
                        "✅ Gelände erkannt · Topo: $topoStr",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("NasenarbeitMap", "Terrain-Fehler: ${e.message}")
                withContext(Dispatchers.Main) {
                    updateResult()
                    binding.btnConfirm.isEnabled = true
                    Toast.makeText(this@NasenarbeitMapActivity,
                        "⚠️ Geländedaten nicht verfügbar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    data class BoundingBox(val south: Double, val west: Double,
                           val north: Double, val east: Double)

    private fun buildBbox(pts: List<GeoPoint>, paddingDeg: Double): BoundingBox {
        val lats = pts.map { it.latitude }
        val lons = pts.map { it.longitude }
        return BoundingBox(
            south = lats.min() - paddingDeg,
            west  = lons.min() - paddingDeg,
            north = lats.max() + paddingDeg,
            east  = lons.max() + paddingDeg
        )
    }

    /**
     * Fragt Overpass nach landuse / natural / surface-Tags im Bounding-Box-Bereich.
     * Gibt Tripel zurück: (List<Umgebungsname>, List<BodenTypKey>, List<Gewichtung>)
     */
    private suspend fun fetchOverpassTerrainWithWeights(bb: BoundingBox): Triple<List<String>, List<String>, List<Double>> {
        val locale = java.util.Locale.US
        val bbox = String.format(locale, "%.6f,%.6f,%.6f,%.6f", bb.south, bb.west, bb.north, bb.east)
        val query = """
            [out:json][timeout:8];
            (
              way["landuse"](${bbox});
              way["natural"](${bbox});
              way["surface"](${bbox});
              way["highway"](${bbox});
              relation["landuse"](${bbox});
            );
            out tags;
        """.trimIndent()

        val json = OverpassHelper.queryWithRetry(query, maxRetries = 1) ?: return Triple(emptyList(), emptyList(), emptyList())
        val elements = json.getJSONArray("elements")

        val landuseCounts  = mutableMapOf<String, Int>()
        val naturalCounts  = mutableMapOf<String, Int>()
        val surfaceCounts  = mutableMapOf<String, Int>()
        val highwayCounts  = mutableMapOf<String, Int>()

        for (i in 0 until elements.length()) {
            val el   = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            tags.optString("landuse").takeIf { it.isNotEmpty() }?.let {
                landuseCounts[it] = (landuseCounts[it] ?: 0) + 1
            }
            tags.optString("natural").takeIf { it.isNotEmpty() }?.let {
                naturalCounts[it] = (naturalCounts[it] ?: 0) + 1
            }
            tags.optString("surface").takeIf { it.isNotEmpty() }?.let {
                surfaceCounts[it] = (surfaceCounts[it] ?: 0) + 1
            }
            val hw = tags.optString("highway").takeIf { it.isNotEmpty() }
            if (hw != null) {
                highwayCounts[hw] = (highwayCounts[hw] ?: 0) + 1
                tags.optString("surface").takeIf { it.isNotEmpty() }?.let { surf ->
                    surfaceCounts[surf] = (surfaceCounts[surf] ?: 0) + 1
                }
            }
        }

        val umgebung = mutableListOf<String>()
        val boden    = mutableListOf<String>()
        val weights  = mutableListOf<Double>()

        val dominantLanduse = landuseCounts.maxByOrNull { it.value }?.key ?: ""
        val dominantNatural = naturalCounts.maxByOrNull { it.value }?.key ?: ""

        data class TerrainEntry(val key: String, val type: String, val count: Int)
        val allTerrainSorted: List<TerrainEntry> =
            (landuseCounts.entries.map { TerrainEntry(it.key, "landuse",  it.value) } +
                    naturalCounts.entries.map  { TerrainEntry(it.key, "natural",  it.value) } +
                    highwayCounts.entries.map  { TerrainEntry(it.key, "highway",  it.value) })
                .sortedByDescending { it.count }

        val maxSections = if (mode == Mode.LINIE) segments.size.coerceIn(1, 6) else 5
        var totalCount = 0
        val tempEntries = mutableListOf<Pair<String, Int>>()

        for (entry in allTerrainSorted) {
            val mapped = when (entry.type) {
                "landuse" -> mapToUmgebung(entry.key, "")
                "natural" -> mapToUmgebung("", entry.key)
                "highway" -> mapHighwayToUmgebung(entry.key)
                else      -> ""
            }
            if (mapped.isNotEmpty() && !umgebung.contains(mapped)) {
                umgebung.add(mapped)
                tempEntries.add(mapped to entry.count)
                totalCount += entry.count
            }
            if (umgebung.size >= maxSections) break
        }

        val surfacesSorted  = surfaceCounts.entries.sortedByDescending { it.value }
        val highwaysSorted  = highwayCounts.entries.sortedByDescending { it.value }

        umgebung.forEachIndexed { idx, umb ->
            val surface  = surfacesSorted.getOrNull(idx)?.key ?: ""
            val fromSurf = mapToBodenTyp(surface, dominantLanduse, dominantNatural)
            val hw       = highwaysSorted.getOrNull(idx)?.key ?: ""
            val fromHw   = mapHighwayToBoden(hw)
            val fromUmb  = umbToBodenFallback(umb)

            val bodenResult = when {
                fromSurf.isNotEmpty() -> fromSurf
                fromHw.isNotEmpty()   -> fromHw
                else                  -> fromUmb
            }
            boden.add(bodenResult)

            // Gewichtung berechnen
            val count = tempEntries.find { it.first == umb }?.second ?: 1
            weights.add(if (totalCount > 0) count.toDouble() / totalCount else 1.0 / umgebung.size)
        }

        return Triple(umgebung, boden, weights)
    }

    /** Leitet aus dem Umgebungstyp einen plausiblen Boden-Fallback ab */
    private fun umbToBodenFallback(umgebung: String): String = when (umgebung) {
        "Waldgebiet"        -> "wb_misch"
        "Grünland"          -> "gl_kurz"
        "Offenes Gelände"   -> "mg_erdig"
        "Urbanes Umfeld"    -> "asphalt"
        "Industriegelände"  -> "asphalt"
        "Dörfliche Bebauung"-> "asphalt"
        "Parkanlage"        -> "gl_kurz"
        "Mischgebiet"       -> "asphalt"
        else -> ""
    }

    /** Leitet aus highway-Tag einen Bodentyp ab (wenn kein surface-Tag vorhanden) */
    private fun mapHighwayToBoden(highway: String): String = when (highway) {
        "motorway","trunk","primary","secondary","tertiary",
        "residential","living_street","service","pedestrian" -> "asphalt"
        "unclassified"                                        -> "ks_mit"
        "track"                                               -> "mg_erdig"
        "path","footway","bridleway"                          -> "mg_erdig"
        else                                                  -> ""
    }

    /** Leitet aus einem OSM-highway-Tag die passende Umgebungskategorie ab */
    private fun mapHighwayToUmgebung(highway: String): String = when (highway) {
        "motorway","trunk","primary","secondary","tertiary",
        "residential","living_street","pedestrian","service" -> "Urbanes Umfeld"
        "unclassified"                                        -> "Mischgebiet"
        "track","path","bridleway","cycleway","footway"       -> ""   // kein eigener Umgebungstyp
        "steps"                                               -> "Steiles Gelände" // Topo, nicht Umgebung
        else                                                  -> ""
    }

    private fun mapToUmgebung(landuse: String, natural: String): String = when {
        natural in listOf("wood","scrub")           -> "Waldgebiet"
        natural in listOf("grassland","heath")      -> "Grünland"
        natural in listOf("sand","beach","dune")    -> "Offenes Gelände"
        landuse in listOf("forest")                 -> "Waldgebiet"
        landuse in listOf("grass","meadow","greenfield") -> "Grünland"
        landuse in listOf("farmland","farmyard","orchard") -> "Offenes Gelände"
        landuse in listOf("residential","commercial","retail") -> "Urbanes Umfeld"
        landuse in listOf("industrial","quarry")    -> "Industriegelände"
        landuse == "village_green"                  -> "Dörfliche Bebauung"
        landuse == "recreation_ground"              -> "Parkanlage"
        natural in listOf("fell","bare_rock","scree") -> "Offenes Gelände"
        else -> ""
    }

    private fun mapToBodenTyp(surface: String, landuse: String, natural: String): String = when {
        // Wenn kein surface-Tag vorhanden, aus landuse/highway ableiten
        surface.isEmpty() && landuse in listOf("residential","commercial","retail","industrial") -> "asphalt"
        surface in listOf("grass","grass_paver")    -> "gl_kurz"
        surface in listOf("dirt","earth","ground")  -> "mg_erdig"
        surface in listOf("sand")                   -> "sb_ohne"
        surface in listOf("asphalt","paved","concrete") -> "asphalt"
        surface in listOf("gravel","fine_gravel","pebblestone") -> "ks_ohne"
        surface in listOf("compacted","wood","woodchips") -> "mg_mineral"
        surface in listOf("unpaved","rock","stone") -> "mg_fels"
        natural == "wood" || landuse == "forest"    -> "wb_misch"
        natural == "grassland" || landuse == "grass" -> "gl_kurz"
        natural == "sand"                            -> "sb_ohne"
        else -> ""
    }

    // ════════════════════════════════════════════════════════════
    // BUTTONS
    // ════════════════════════════════════════════════════════════

    private fun setupButtons() {
        binding.btnUndo.setOnClickListener {
            if (mode == Mode.POLYGON) {
                if (polyPoints.isEmpty()) return@setOnClickListener
                polyPoints.removeLastOrNull()
                markers.lastOrNull()?.let { binding.mapView.overlays.remove(it); markers.removeLastOrNull() }
                areaPolygon?.let { binding.mapView.overlays.remove(it); areaPolygon = null }
                if (polyPoints.size >= 3) drawPolygon()
            } else {
                if (segments.isEmpty() && markers.isEmpty()) return@setOnClickListener
                // Letzten Abschnitt entfernen
                if (segments.isNotEmpty()) segments.removeLastOrNull()
                segmentPolylines.lastOrNull()?.let { binding.mapView.overlays.remove(it); segmentPolylines.removeLastOrNull() }
                markers.lastOrNull()?.let { binding.mapView.overlays.remove(it); markers.removeLastOrNull() }
                redrawPolylines()
            }
            binding.mapView.invalidate()
            updateResult()
            if (allWaypoints().size < 2 && polyPoints.size < 3) {
                binding.tvResult.text = ""; binding.btnConfirm.isEnabled = false
            }
        }

        binding.btnClear.setOnClickListener { clearDrawing() }

        binding.btnConfirm.setOnClickListener {
            if (!overpassQueried) {
                // Phase 2: Overpass-Abfrage vor Bestätigung
                queryOverpassForArea()
                // Nach Abfrage wird confirm erneut aktiviert → Nutzer klickt nochmal
                return@setOnClickListener
            }
            returnResult()
        }

        binding.btnSkip.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun returnResult() {
        val segmentValues = mutableListOf<Double>()

        if (mode == Mode.LINIE) {
            // ✅ Korrektur: Nur Segmente mit mindestens 2 Punkten (echte Strecken) zählen
            segments.filter { it.size >= 2 }.forEach { pts ->
                val distM = calcPolylineDistanceM(pts)
                segmentValues.add(distM / 0.75) // Schritte (0.75m pro Schritt)
            }
        } else {
            // Quadratmeter pro Segment (im Polygon-Modus meist nur 1 Segment)
            if (polyPoints.size >= 3) {
                val areaM2 = calcPolygonAreaM2(polyPoints)
                segmentValues.add(areaM2) // ✅ NEU: m2 statt Hektar übergeben
            } else {
                segmentValues.add(0.0)
            }
        }

        val data = Intent().apply {
            putExtra(RESULT_USED,     true)
            putExtra(RESULT_SCHRITTE, calculatedSchritte)
            putExtra(RESULT_HA,       calculatedHa)
            putExtra(EXTRA_DISZIPLIN, disziplin.name)
            putStringArrayListExtra(RESULT_UMGEBUNG, ArrayList(detectedUmgebung))
            putStringArrayListExtra(RESULT_BODEN,    ArrayList(detectedBodenTyp))
            putStringArrayListExtra(RESULT_TOPO,     ArrayList(detectedTopo))
            putExtra(RESULT_SEGMENT_VALUES, segmentValues.toDoubleArray())
            putExtra(RESULT_WEIGHTS, detectedWeights.toDoubleArray())
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun clearDrawing() {
        segments.clear()
        segmentPolylines.forEach { binding.mapView.overlays.remove(it) }
        segmentPolylines.clear()
        markers.forEach { binding.mapView.overlays.remove(it) }
        markers.clear()
        polyPoints.clear()
        areaPolygon?.let { binding.mapView.overlays.remove(it) }
        areaPolygon = null
        detectedUmgebung.clear(); detectedBodenTyp.clear()
        detectedTopo.clear(); overpassQueried = false
        detectedWeights.clear(); calculatedSchritte = 0; calculatedHa = 0.0
        binding.mapView.invalidate()
        binding.tvResult.text = ""; binding.btnConfirm.isEnabled = false
    }

    // ════════════════════════════════════════════════════════════
    // GPS + PERMISSIONS
    // ════════════════════════════════════════════════════════════

    private fun requestLocation() {
        when {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED -> startLocationUpdates()
            else -> showLocationRationaleDialog()
        }
    }

    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("📍 Standort-Berechtigung")
            .setMessage(
                "Die HundeWildnis-App verwendet deinen GPS-Standort für:\n\n" +
                        "• 🗺️ Karte zentrieren – automatisch auf deinen Standort\n" +
                        "• 🌡️ Wetterdaten – lokale Bedingungen abrufen\n" +
                        "• 📍 Trainingsort – Marker im PDF speichern\n\n" +
                        "Der Standort wird nicht übertragen. Alle Daten bleiben auf deinem Gerät."
            )
            .setPositiveButton("Berechtigung erteilen") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQ_LOCATION
                )
            }
            .setNegativeButton("Ohne Standort") { _, _ ->
                // Fallback: Deutschland-Mitte
                binding.mapView.controller.setCenter(GeoPoint(51.163375, 10.447683))
                binding.mapView.controller.setZoom(6.0)
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            last?.let {
                binding.mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude))
                binding.mapView.controller.setZoom(17.0)
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 3000L, 5f, this
            )
            isRequestingUpdates = true
        } catch (e: SecurityException) {
            Toast.makeText(this, "GPS-Berechtigung fehlt", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!firstLocationReceived) {
            firstLocationReceived = true
            binding.mapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
            binding.mapView.controller.setZoom(17.0)
        }
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onResume()  {
        super.onResume()
        binding.mapView.onResume()
        if (!isRequestingUpdates && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }
    override fun onPause()   {
        super.onPause()
        binding.mapView.onPause()
        if (isRequestingUpdates) {
            locationManager.removeUpdates(this)
            isRequestingUpdates = false
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        myLocationOverlay?.disableMyLocation()
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
    }

    // ════════════════════════════════════════════════════════════
    // PHASE 3 – TOPOGRAFIE via Höhenprofil + Overpass Features
    // ════════════════════════════════════════════════════════════

    /**
     * Fragt Open-Meteo Elevation API PER SEGMENT in Routenreihenfolge.
     * Gibt pro Segment eine Topo-Kategorie zurück → geordnete Sequenz.
     */
    private suspend fun fetchElevationTopoPerSegment(
        segs: List<List<GeoPoint>>
    ): List<String> {
        if (segs.isEmpty()) return emptyList()

        // Alle Segmentpunkte sammeln, Index-Grenzen merken
        val allPts     = mutableListOf<GeoPoint>()
        val segBounds  = mutableListOf<IntRange>()  // Start- und End-Index pro Segment
        segs.forEach { seg ->
            val start = allPts.size
            allPts.addAll(seg)
            segBounds.add(start until allPts.size)
        }

        // Max 100 Punkte gesamt (API-Limit beachten), gleichmäßig verteilt
        val maxTotal = 100
        val step     = maxOf(1, allPts.size / maxTotal)
        val samples  = allPts.filterIndexed { i, _ -> i % step == 0 }
            .let { if (it.lastOrNull() != allPts.last()) it + allPts.last() else it }

        val latStr = samples.joinToString(",") { String.format(java.util.Locale.US, "%.6f", it.latitude)  }
        val lonStr = samples.joinToString(",") { String.format(java.util.Locale.US, "%.6f", it.longitude) }
        val url    = "https://api.open-meteo.com/v1/elevation?latitude=$latStr&longitude=$lonStr"

        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.setRequestProperty("User-Agent", "HundeWildnisApp/9.7")
            conn.connectTimeout = 10000
            conn.readTimeout    = 12000

            if (conn.responseCode != 200) {
                android.util.Log.e("NasenarbeitMap", "Elevation API ${conn.responseCode}")
                return segs.map { "Eben" }  // Fallback
            }

            val json       = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val elevArr    = json.getJSONArray("elevation")
            val elevations = (0 until elevArr.length()).map { elevArr.getDouble(it) }

            // Pro Segment Topo klassifizieren
            val result = mutableListOf<String>()
            val samplesPerSeg = samples.size.toDouble() / segs.size
            segs.forEachIndexed { segIdx, _ ->
                val fromSample = (segIdx * samplesPerSeg).toInt()
                val toSample   = minOf(((segIdx + 1) * samplesPerSeg).toInt(), samples.size - 1)
                if (fromSample >= toSample) {
                    result.add("Eben")
                    return@forEachIndexed
                }
                val segSamples = samples.subList(fromSample, toSample + 1)
                val segElevs   = elevations.subList(fromSample, minOf(toSample + 1, elevations.size))
                val topo       = classifyTopoFromSlope(segSamples, segElevs)
                result.add(topo.firstOrNull() ?: "Eben")
            }
            result
        } catch (e: Exception) {
            android.util.Log.e("NasenarbeitMap", "Elevation fetch error: ${e.message}")
            segs.map { "Eben" }  // Fallback
        }
    }

    /**
     * Klassifiziert Topografie aus Höhenprofil.
     */
    private fun classifyTopoFromSlope(
        pts: List<GeoPoint>,
        elevations: List<Double>
    ): List<String> {
        if (pts.size < 2 || elevations.size < 2) return listOf("Eben")

        val slopeCounts = mutableMapOf<String, Int>()
        var prevElev    = elevations.first()

        for (i in 1 until minOf(pts.size, elevations.size)) {
            val distM    = haversine(pts[i-1].latitude, pts[i-1].longitude,
                pts[i].latitude,   pts[i].longitude)
            val elevDiff = (elevations[i] - prevElev).absoluteValue
            val slopeDeg = if (distM > 0.5)
                Math.toDegrees(atan(elevDiff / distM)) else 0.0

            val cat = when {
                slopeDeg >= 5.0  -> "Steiles Gelände"
                slopeDeg >= 1.2  -> "Leichte Steigung"
                else             -> "Eben"
            }
            slopeCounts[cat] = (slopeCounts[cat] ?: 0) + 1
            prevElev = elevations[i]
        }

        val totalDistM = calcPolylineDistanceM(pts)
        val totalGain  = elevations.zipWithNext().sumOf { (a, b) -> maxOf(0.0, b - a) }
        val totalDrop  = elevations.zipWithNext().sumOf { (a, b) -> maxOf(0.0, a - b) }
        val maxElevDiff = (elevations.max() - elevations.min())

        val gainPerM = if (totalDistM > 0) (totalGain + totalDrop) / totalDistM else 0.0
        val gainDeg  = Math.toDegrees(atan(gainPerM))

        if (gainDeg >= 5.0) {
            slopeCounts["Steiles Gelände"] = (slopeCounts["Steiles Gelände"] ?: 0) + 3
        } else if (gainDeg >= 1.2 || maxElevDiff >= 6.0) {
            slopeCounts["Leichte Steigung"] = (slopeCounts["Leichte Steigung"] ?: 0) + 3
        }

        // Spezial-Erkennung
        val midElev  = elevations[elevations.size / 2]
        val startEnd = (elevations.first() + elevations.last()) / 2.0
        if (midElev < startEnd - 4.5)
            slopeCounts["Mulde"] = (slopeCounts["Mulde"] ?: 0) + 2
        if (midElev > startEnd + 4.5)
            slopeCounts["Kuppe"] = (slopeCounts["Kuppe"] ?: 0) + 2

        val ascents  = elevations.zipWithNext().count { (a,b) -> b > a + 0.5 }
        val descents = elevations.zipWithNext().count { (a,b) -> b < a - 0.5 }
        val total    = elevations.size - 1
        if (total > 0) {
            if (ascents  > total * 0.70) slopeCounts["Hanglage"] = (slopeCounts["Hanglage"] ?: 0) + 2
            if (descents > total * 0.70) slopeCounts["Hanglage"] = (slopeCounts["Hanglage"] ?: 0) + 2
        }

        return slopeCounts.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it != "Eben" || slopeCounts.size == 1 }
            .take(3)
            .ifEmpty { listOf("Eben") }
    }

    private suspend fun fetchOverpassTopoFeatures(bb: BoundingBox): List<String> {
        val locale = java.util.Locale.US
        val bbox = String.format(locale, "%.6f,%.6f,%.6f,%.6f", bb.south, bb.west, bb.north, bb.east)
        val query = """
            [out:json][timeout:6];
            (
              node["natural"~"^(cliff|peak|ridge|saddle|valley|depression|rock|scree)$"](${bbox});
              way["natural"~"^(cliff|ridge|valley)$"](${bbox});
              way["highway"="steps"](${bbox});
            );
            out tags;
        """.trimIndent()

        return try {
            val json     = OverpassHelper.queryWithRetry(query, maxRetries = 1) ?: return emptyList()
            val elements = json.getJSONArray("elements")
            val features = mutableListOf<String>()
            for (i in 0 until elements.length()) {
                val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
                val nat  = tags.optString("natural", "")
                val hw   = tags.optString("highway", "")
                val mapped = when {
                    nat == "cliff" || nat == "rock"   -> "Felsabbrüche / Steilwände"
                    nat == "peak"  || nat == "ridge"  -> "Kuppe"
                    nat == "saddle"                   -> "Hanglage"
                    nat == "valley"|| nat == "depression" -> "Mulde"
                    nat == "scree"                    -> "Steiles Gelände"
                    hw == "steps"                     -> "Steiles Gelände"
                    else -> ""
                }
                if (mapped.isNotEmpty() && !features.contains(mapped)) features.add(mapped)
            }
            features
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildTopoSequenzOrdered(
        slopeTopoPerSeg: List<String>,
        overpassFeatures: List<String>
    ): List<String> {
        if (slopeTopoPerSeg.isEmpty()) return if (overpassFeatures.isNotEmpty()) overpassFeatures else listOf("Eben")
        val result = slopeTopoPerSeg.toMutableList()
        overpassFeatures.forEach { f -> if (!result.contains(f)) result.add(f) }
        return result.take(8)
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

        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
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
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
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
}
