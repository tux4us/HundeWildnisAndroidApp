package com.hundewildnis.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MenuItem
import android.view.View
import com.hundewildnis.app.databinding.ActivityWeatherRadarBinding
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Wetterkarte mit den OpenWeatherMap-Kartenebenen „Niederschlag“ und „Wolken“ (Weather Maps 1.0)
 * über OpenStreetMap. Benötigt den OpenWeatherMap-Schlüssel des Nutzers (derselbe wie für die
 * Wetterdaten). Hinweis: Das sind modellbasierte Rasterkacheln des aktuellen Stands, kein gemessenes
 * Radar und ohne Zeitverlauf.
 */
class WeatherRadarActivity : SecureBaseActivity() {

    private enum class Layer(val id: String) {
        PRECIPITATION("precipitation_new"),
        CLOUDS("clouds_new")
    }

    private lateinit var binding: ActivityWeatherRadarBinding
    private var weatherOverlay: TilesOverlay? = null
    private var weatherProvider: MapTileProviderBasic? = null
    private var currentLayer = Layer.PRECIPITATION
    private var apiKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.weather_radar_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        apiKey = AppSettings(this).getOwmApiKey()
        setupMap()

        binding.btnLayerPrecipitation.setOnClickListener { selectLayer(Layer.PRECIPITATION) }
        binding.btnLayerClouds.setOnClickListener { selectLayer(Layer.CLOUDS) }

        if (apiKey.isBlank()) {
            binding.btnLayerPrecipitation.isEnabled = false
            binding.btnLayerClouds.isEnabled = false
            binding.radarStatusText.text = getString(R.string.weather_radar_key_missing)
        } else {
            selectLayer(Layer.PRECIPITATION)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        binding.radarMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.radarMapView.onPause()
    }

    override fun onDestroy() {
        weatherProvider?.detach()
        weatherProvider = null
        binding.radarMapView.onDetach()
        super.onDestroy()
    }

    private fun setupMap() {
        OsmConfig.getInstance().userAgentValue = AppNetwork.USER_AGENT
        var lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        var lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
        if (lat == 0.0 && lon == 0.0) { lat = 51.0; lon = 13.0 }   // noch kein Standort bekannt
        val center = GeoPoint(lat, lon)
        binding.radarMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 3.0
            maxZoomLevel = MAX_ZOOM.toDouble()
            controller.setZoom(6.0)
            controller.setCenter(center)
            // Quellenangabe laut OSM-Richtlinie („© OpenStreetMap contributors“)
            overlays.add(CopyrightOverlay(this@WeatherRadarActivity))
            overlays.add(Marker(this).apply {
                position = center
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            })
        }
    }

    private fun selectLayer(layer: Layer) {
        currentLayer = layer
        binding.btnLayerPrecipitation.alpha = if (layer == Layer.PRECIPITATION) 1f else 0.5f
        binding.btnLayerClouds.alpha = if (layer == Layer.CLOUDS) 1f else 0.5f
        applyWeatherOverlay(layer)
        binding.radarStatusText.text = getString(
            R.string.weather_radar_status,
            getString(if (layer == Layer.PRECIPITATION) R.string.weather_radar_layer_precipitation else R.string.weather_radar_layer_clouds),
            getString(R.string.weather_attribution_owm)
        )
    }

    private fun applyWeatherOverlay(layer: Layer) {
        val map = binding.radarMapView
        weatherOverlay?.let { map.overlays.remove(it) }
        weatherProvider?.detach()

        // Der Name der Quelle bestimmt den Cache-Ordner. Mit 10-Minuten-Zeitscheibe werden
        // keine veralteten Kacheln aus dem Cache wiederverwendet. Der Schlüssel steht nicht im Namen.
        val bucket = System.currentTimeMillis() / REFRESH_MS
        val key = apiKey
        val source = object : OnlineTileSourceBase(
            "OWM_${layer.id}_$bucket", 0, MAX_ZOOM, 256, ".png",
            arrayOf("https://tile.openweathermap.org/map/${layer.id}/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String =
                baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getX(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding + "?appid=" + key
        }

        val provider = MapTileProviderBasic(applicationContext, source)
        provider.tileRequestCompleteHandlers.add(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                binding.radarMapView.invalidate()
            }
        })
        val overlay = TilesOverlay(provider, this).apply {
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
            // Die OWM-Niederschlagskacheln sind sehr blass und halbtransparent: Deckkraft und Sättigung anheben,
            // damit sie auf der hellen Basiskarte sichtbar sind. Wolken bleiben unverändert.
            if (layer == Layer.PRECIPITATION) {
                setColorFilter(android.graphics.ColorMatrixColorFilter(floatArrayOf(
                    1.4f, 0f, 0f, 0f, -30f,
                    0f, 1.4f, 0f, 0f, -30f,
                    0f, 0f, 1.4f, 0f, -30f,
                    0f, 0f, 0f, 3.0f, 0f
                )))
            }
        }
        weatherProvider = provider
        weatherOverlay = overlay
        // Direkt über der Basiskarte, unter Quellenangabe und Standortmarker
        map.overlays.add(0, overlay)
        map.invalidate()
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        private const val MAX_ZOOM = 9
        private const val REFRESH_MS = 10 * 60 * 1000L
    }
}
