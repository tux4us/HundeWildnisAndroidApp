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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityNasenarbeitMapBinding
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.*

/**
 * NasenarbeitMapActivity – Phase 1
 *
 * Optionaler Schritt in der Nasenarbeit-Beratung zwischen Wetter und Gelände.
 * Nutzer markiert sein Trainingsgebiet auf der Karte:
 *   Verlorensuche / Personensuche → Linienmodus (Wegpunkte → Schritte)
 *   Freisuche                      → Polygonmodus (Fläche → Hektar)
 *
 * Ergebnis wird als Intent-Extra zurückgegeben und füllt
 * gebietSchritte + gebietHa in NasenarbeitBeratungActivity vor.
 */
class NasenarbeitMapActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityNasenarbeitMapBinding
    private lateinit var locationManager: LocationManager
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // Modus
    private var mode = Mode.LINIE         // gesetzt via Intent
    private var disziplin = NasenarbeitDisziplin.VERLORENSUCHE

    // Gezeichnete Punkte
    private val waypoints = mutableListOf<GeoPoint>()
    private val markers   = mutableListOf<Marker>()
    private var routePolyline: Polyline?  = null
    private var areaPolygon: Polygon?     = null

    // Berechnete Werte
    private var calculatedSchritte = 0
    private var calculatedHa       = 0.0

    enum class Mode { LINIE, POLYGON }

    companion object {
        const val EXTRA_DISZIPLIN = "map_disziplin"
        const val RESULT_SCHRITTE = "result_schritte"
        const val RESULT_HA       = "result_ha"
        const val RESULT_USED     = "result_used"
        const val RC_MAP          = 4200
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
        mode = when (disziplin) {
            NasenarbeitDisziplin.FREISUCHE -> Mode.POLYGON
            else                           -> Mode.LINIE
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Karte · ${disziplin.displayName}"

        setupMap()
        setupControls()
        setupButtons()
        setupOSMCopyright()
        requestLocation()
    }

    private fun setupOSMCopyright() {
        binding.tvOsmCopyright.setOnClickListener {
            val webView = WebView(this).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl("https://www.openstreetmap.org/copyright")
            }
            AlertDialog.Builder(this)
                .setTitle("OpenStreetMap Lizenz")
                .setView(webView)
                .setPositiveButton("Schließen", null)
                .show()
        }
    }

    // ════════════════════════════════════════════════════════════
    // MAP SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(17.0)

        // Skalierungsleiste
        val scaleBar = ScaleBarOverlay(binding.mapView).apply {
            setCentred(false); setAlignBottom(true)
        }
        binding.mapView.overlays.add(scaleBar)

        // Eigener Standort
        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(this), binding.mapView
        ).apply { enableMyLocation() }
        binding.mapView.overlays.add(myLocationOverlay)

        // Tap-Empfänger
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
    // CONTROLS (Disziplin-Buttons + Hinweistext)
    // ════════════════════════════════════════════════════════════

    private fun setupControls() {
        updateButtonStates()

        listOf(
            binding.btnModeLinie to NasenarbeitDisziplin.VERLORENSUCHE,
            binding.btnModeTrail to NasenarbeitDisziplin.PERSONENSUCHE,
            binding.btnModePoly  to NasenarbeitDisziplin.FREISUCHE
        ).forEach { (btn, diz) ->
            btn.setOnClickListener {
                if (disziplin == diz) return@setOnClickListener

                disziplin = diz
                mode = if (diz == NasenarbeitDisziplin.FREISUCHE) Mode.POLYGON else Mode.LINIE
                
                updateButtonStates()
                updateHint()
                clearDrawing()
                
                supportActionBar?.title = "Karte · ${disziplin.displayName}"
            }
        }
        updateHint()
    }

    private fun updateButtonStates() {
        val activeColor = Color.parseColor("#4C6238")
        val inactiveColor = Color.parseColor("#555555")

        binding.btnModeLinie.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (disziplin == NasenarbeitDisziplin.VERLORENSUCHE) activeColor else inactiveColor
        )
        binding.btnModeTrail.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (disziplin == NasenarbeitDisziplin.PERSONENSUCHE) activeColor else inactiveColor
        )
        binding.btnModePoly.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (disziplin == NasenarbeitDisziplin.FREISUCHE) activeColor else inactiveColor
        )

        // Zusätzliches haptisches Feedback oder visuelle Tiefe
        binding.btnModeLinie.alpha = if (disziplin == NasenarbeitDisziplin.VERLORENSUCHE) 1.0f else 0.7f
        binding.btnModeTrail.alpha = if (disziplin == NasenarbeitDisziplin.PERSONENSUCHE) 1.0f else 0.7f
        binding.btnModePoly.alpha = if (disziplin == NasenarbeitDisziplin.FREISUCHE) 1.0f else 0.7f
    }

    private fun updateHint() {
        binding.tvMapHint.text = when (mode) {
            Mode.LINIE   -> "Tippe auf die Karte um Wegpunkte zu setzen.\nDer Pfad wird automatisch berechnet."
            Mode.POLYGON -> "Tippe mindestens 3 Punkte um die Suchfläche einzugrenzen.\nLetzten Punkt schließt das Polygon."
        }
        binding.tvResult.text = ""
        binding.btnConfirm.isEnabled = false
    }

    // ════════════════════════════════════════════════════════════
    // TAP HANDLER
    // ════════════════════════════════════════════════════════════

    private fun onMapTap(point: GeoPoint) {
        waypoints.add(point)

        // Marker
        val marker = Marker(binding.mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "${waypoints.size}"
            icon = ContextCompat.getDrawable(
                this@NasenarbeitMapActivity, R.drawable.ic_my_location
            )
        }
        markers.add(marker)
        binding.mapView.overlays.add(marker)

        when (mode) {
            Mode.LINIE   -> drawPolyline()
            Mode.POLYGON -> if (waypoints.size >= 3) drawPolygon()
        }

        binding.mapView.invalidate()
        updateResult()
    }

    // ════════════════════════════════════════════════════════════
    // DRAW
    // ════════════════════════════════════════════════════════════

    private fun drawPolyline() {
        routePolyline?.let { binding.mapView.overlays.remove(it) }
        if (waypoints.size < 2) return
        routePolyline = Polyline(binding.mapView).apply {
            setPoints(waypoints)
            outlinePaint.color = Color.parseColor("#2d5016")
            outlinePaint.strokeWidth = 8f
            outlinePaint.style = Paint.Style.STROKE
        }
        binding.mapView.overlays.add(routePolyline)
    }

    private fun drawPolygon() {
        areaPolygon?.let { binding.mapView.overlays.remove(it) }
        val closedPoints = waypoints + waypoints.first()
        areaPolygon = Polygon(binding.mapView).apply {
            points = closedPoints
            fillPaint.color = Color.parseColor("#402d5016")
            outlinePaint.color = Color.parseColor("#2d5016")
            outlinePaint.strokeWidth = 6f
        }
        binding.mapView.overlays.add(areaPolygon)
    }

    // ════════════════════════════════════════════════════════════
    // BERECHNUNG
    // ════════════════════════════════════════════════════════════

    private fun updateResult() {
        when (mode) {
            Mode.LINIE -> {
                if (waypoints.size >= 2) {
                    val distanceM = calcPolylineDistanceM(waypoints)
                    // 1 Schritt ≈ 0.75m
                    calculatedSchritte = (distanceM / 0.75).toInt()
                    calculatedHa = 0.0
                    val distStr = if (distanceM >= 1000) {
                        String.format("%.1f km", distanceM / 1000)
                    } else {
                        "${distanceM.toInt()} m"
                    }
                    binding.tvResult.text =
                        "📏 $distStr  ·  ~$calculatedSchritte Schritte"
                    binding.btnConfirm.isEnabled = true
                }
            }
            Mode.POLYGON -> {
                if (waypoints.size >= 3) {
                    val areaM2 = calcPolygonAreaM2(waypoints)
                    calculatedHa = areaM2 / 10_000.0
                    calculatedSchritte = 0
                    binding.tvResult.text =
                        "📐 ${String.format("%.2f ha", calculatedHa)}" +
                                "  (${(areaM2).toInt()} m²)"
                    binding.btnConfirm.isEnabled = true
                }
            }
        }
    }

    /**
     * Haversine-Distanz in Metern für eine Polylinie.
     */
    private fun calcPolylineDistanceM(pts: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 1 until pts.size) {
            total += haversine(pts[i - 1].latitude, pts[i - 1].longitude,
                pts[i].latitude,     pts[i].longitude)
        }
        return total
    }

    /**
     * Shoelace-Formel auf sphärischen Koordinaten → Fläche in m².
     * Ausreichend genau für Trainingsgebiete bis ~10 km².
     */
    private fun calcPolygonAreaM2(pts: List<GeoPoint>): Double {
        if (pts.size < 3) return 0.0
        val R = 6_371_000.0    // Erdradius in m
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val lat1 = Math.toRadians(pts[i].latitude)
            val lat2 = Math.toRadians(pts[j].latitude)
            val lon1 = Math.toRadians(pts[i].longitude)
            val lon2 = Math.toRadians(pts[j].longitude)
            area += (lon2 - lon1) * (2 + sin(lat1) + sin(lat2))
        }
        return abs(area) * R * R / 2.0
    }

    private fun haversine(lat1: Double, lon1: Double,
                          lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // ════════════════════════════════════════════════════════════
    // BUTTONS
    // ════════════════════════════════════════════════════════════

    private fun setupButtons() {
        // Letzten Punkt rückgängig
        binding.btnUndo.setOnClickListener {
            if (waypoints.isEmpty()) return@setOnClickListener
            waypoints.removeLastOrNull()
            markers.lastOrNull()?.let { m ->
                binding.mapView.overlays.remove(m)
                markers.removeLastOrNull()
            }
            routePolyline?.let { binding.mapView.overlays.remove(it); routePolyline = null }
            areaPolygon?.let  { binding.mapView.overlays.remove(it);  areaPolygon  = null }
            when (mode) {
                Mode.LINIE   -> if (waypoints.size >= 2) drawPolyline()
                Mode.POLYGON -> if (waypoints.size >= 3) drawPolygon()
            }
            binding.mapView.invalidate()
            updateResult()
            if (waypoints.isEmpty()) { binding.tvResult.text = ""; binding.btnConfirm.isEnabled = false }
        }

        // Alles löschen
        binding.btnClear.setOnClickListener { clearDrawing() }

        // Bestätigen → Ergebnis zurückgeben
        binding.btnConfirm.setOnClickListener {
            val data = Intent().apply {
                putExtra(RESULT_USED,     true)
                putExtra(RESULT_SCHRITTE, calculatedSchritte)
                putExtra(RESULT_HA,       calculatedHa)
                putExtra(EXTRA_DISZIPLIN, disziplin.name)
            }
            setResult(RESULT_OK, data)
            finish()
        }

        // Überspringen → manuell weiter
        binding.btnSkip.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun clearDrawing() {
        waypoints.clear()
        markers.forEach { binding.mapView.overlays.remove(it) }
        markers.clear()
        routePolyline?.let { binding.mapView.overlays.remove(it) }
        areaPolygon?.let  { binding.mapView.overlays.remove(it) }
        routePolyline = null; areaPolygon = null
        calculatedSchritte = 0; calculatedHa = 0.0
        binding.mapView.invalidate()
        binding.tvResult.text = ""; binding.btnConfirm.isEnabled = false
    }

    // ════════════════════════════════════════════════════════════
    // GPS
    // ════════════════════════════════════════════════════════════

    private fun requestLocation() {
        when {
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showLocationRationaleDialog()
            }
            else -> {
                showLocationRationaleDialog()
            }
        }
    }

    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("📍 Standort-Berechtigung")
            .setMessage(
                """
                Die HundeWildnis-App verwendet deinen GPS-Standort für:

                • 🏃🏻‍♂️ Tempo-Trainer – GPS Daten werden zur Geschwindigkeitsmessung erfasst
                • 🗺️ Navigation und Kartenausschnitt für Nasenarbeit – Karte wird auf deinen aktuellen Standort zentriert
                • 🌡️ Wetterdaten – Automatischer Abruf der lokalen Wetterbedingungen
                • 📍 Trainingsstandort – Wird als Kartenmarker im PDF gespeichert

                Der Standort wird nicht gespeichert oder übertragen.
                Alle Daten bleiben auf deinem Gerät.
                """.trimIndent()
            )
            .setPositiveButton("Berechtigung erteilen") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQ_LOCATION
                )
            }
            .setNegativeButton("Ohne Standort fortfahren") { _, _ ->
                // Karte bleibt auf Standard-Ansicht (Deutschland-Mitte)
                binding.mapView.controller.setCenter(GeoPoint(51.163375, 10.447683))
                binding.mapView.controller.setZoom(6.0)
            }
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            // Letzten bekannten Standort sofort nutzen für schnelles Zentrieren
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            last?.let {
                val gp = GeoPoint(it.latitude, it.longitude)
                binding.mapView.controller.setCenter(gp)
                binding.mapView.controller.setZoom(17.0)
            }
            // Live-Update anfordern
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 3000L, 5f, this
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "GPS-Berechtigung fehlt", Toast.LENGTH_SHORT).show()
        }
    }

    private var firstLocationReceived = false

    override fun onLocationChanged(location: Location) {
        val gp = GeoPoint(location.latitude, location.longitude)
        if (!firstLocationReceived) {
            // Erste Ortung: Zoom + Zentrieren
            firstLocationReceived = true
            binding.mapView.controller.setZoom(17.0)
            binding.mapView.controller.animateTo(gp)
        }
        // Danach: Karte nicht mehr automatisch bewegen (Nutzer zeichnet)
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onResume()  { super.onResume();  binding.mapView.onResume() }
    override fun onPause()   { super.onPause();   binding.mapView.onPause() }
    override fun onDestroy() {
        super.onDestroy()
        myLocationOverlay?.disableMyLocation()
        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}