package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.hundewildnis.app.databinding.ActivityNasenarbeitBeratungBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.Locale

class NasenarbeitBeratungActivity : SecureBaseActivity(), LocationListener {

    private lateinit var binding: ActivityNasenarbeitBeratungBinding
    private lateinit var locationManager: LocationManager
    private lateinit var profile: DogProfile
    private lateinit var fortschritt: NasenarbeitFortschritt

    // ── STATE ──────────────────────────────────────────────────
    private var currentScreen = 0      // 0=Loading 1=Wetter 2=Gelände 3=Boden 4=Training 5=Empfehlung
    private var weatherLoaded = false
    private var lastDatesByDiz = mutableMapOf<Int, Long>()

    // Schicht 1
    private var paramTemp = 15.0
    private var paramBf = 3
    private var paramWmo = 2
    private var paramHumidity = 70
    private var paramWindDir = 0       // 0=hinten 1=seite 2=vorn
    private var paramLat = 0.0
    private var paramLon = 0.0

    // Schicht 2
    private val topoSequenz = mutableListOf<String>()
    private val umbSequenz  = mutableListOf<String>()
    private var gebietSchritte = 500
    private var gebietHa = 0.5

    // Schicht 3 – pro Abschnitt
    private data class BodenEntry(
        var bodenTyp: String = "",
        var bodenZustand: String = "",
        var laengeSchritte: Int? = null,
        var flaechenM2: Double? = null,
        var keineAngabeSchr: Boolean = false,
        var keineAngabeM2: Boolean = false
    )
    private val bodenEntries = mutableListOf<BodenEntry>()

    // Schicht 4
    private var perfV = 0; private var perfP = 0; private var perfFl = 0.0
    private var letzteEinheit = "Gestern"
    private var verfassung = "Normal motiviert"

    // Ergebnis
    private var scoringResult: NasenarbeitScorer.ScoringResult? = null
    private var currentB5Tab = "ok"

    private val TOPO_OPTIONS = listOf("Eben","Leichte Steigung","Steiles Gelände","Kuppe","Hanglage","Mulde","Wechselndes Relief","Felsabbrüche / Steilwände")
    private val TOPO_LABELS  = listOf("Eben","Leichte Steigung","Steil","Kuppe","Hanglage","Mulde","Wechselndes Relief","Felsabbrüche")
    private val UMB_OPTIONS  = listOf("Offenes Gelände","Waldgebiet","Grünland","Urbanes Umfeld","Industriegelände","Dörfliche Bebauung","Parkanlage","Mischgebiet")
    private val UMB_LABELS   = listOf("Offen","Waldgebiet","Grünland","Urban","Industrie","Dörflich","Parkanlage","Mischgebiet")

    private val BODEN_TYPEN = listOf(
        "" to "– Bitte wählen –",
        "gl_kurz" to "Grünland – kurz", "gl_lang" to "Grünland – lang", "gl_frisch" to "Grünland – frisch gemäht",
        "wb_laub" to "Waldboden – Laubwald", "wb_nadel" to "Waldboden – Nadelwald",
        "wb_misch" to "Waldboden – Mischwald", "wb_ohne" to "Waldboden – ohne Unterholz", "wb_mit" to "Waldboden – mit Unterholz",
        "ab_grob" to "Ackerboden – grobschollig", "ab_fein" to "Ackerboden – fein geackert",
        "ab_niedrig" to "Ackerboden – niedriger Bewuchs", "ab_hoch" to "Ackerboden – hoher Bewuchs",
        "sb_ohne" to "Sandboden – unbewachsen", "sb_mit" to "Sandboden – mit Bewuchs",
        "asphalt" to "Asphalt / Beton / Granit",
        "ks_ohne" to "Kies/Schotter – ohne Bewuchs", "ks_mit" to "Kies/Schotter – mit Bewuchs",
        "mg_fels" to "Mischgelände – felsig", "mg_erdig" to "Mischgelände – erdig",
        "mg_mineral" to "Mischgelände – mineralisch", "mg_unspez" to "Mischgelände – unspezifisch"
    )
    private val BODEN_ZUSTAND = listOf(
        "" to "– Bitte wählen –",
        "trocken" to "Trocken", "feucht" to "Feucht", "nass" to "Nass",
        "stehend" to "Stehende Nässe", "matschig" to "Matschig", "gefroren" to "Gefroren",
        "schnee_l" to "Schnee leicht (bis 1cm)", "schnee_m" to "Schnee mittel (1–10cm)", "schnee_h" to "Schnee hoch (>10cm)"
    )
    private val NO_MATSCHIG = setOf("asphalt","ks_ohne")
    private val DIZ_NAMES = listOf("Verlorensuche","Personensuche","Freisuche")
    private val DIZ_ICONS = listOf("🐾","🧑","🔍")

    companion object {
        private const val PREFS_CONSENT = "nasenarbeit_consent"
        private const val KEY_CONSENT   = "data_accepted"

        // Startwerte für den ersten Trainingseinsatz (Leistungsdaten = 0)
        private const val START_SCHRITTE_V  = 20    // Verlorensuche
        private const val START_SCHRITTE_P  = 20    // Personensuche
        private const val START_HA_FL       = 0.01  // Freisuche (100 m²)
    }

    // ── Karten-Launcher ──────────────────────────────────────
    private var mapResultPending = false

    // Phase 2+3: Overpass + Topo aus Karte
    private var mapUmgebung = listOf<String>()
    private var mapBoden    = listOf<String>()
    private var mapTopo     = listOf<String>()
    private var mapSegmentValues = doubleArrayOf()
    private var mapWeights = doubleArrayOf()

    private val mapLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        mapResultPending = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.let { data ->
                val schritte = data.getIntExtra(NasenarbeitMapActivity.RESULT_SCHRITTE, 0)
                val ha       = data.getDoubleExtra(NasenarbeitMapActivity.RESULT_HA, 0.0)
                val dizName  = data.getStringExtra(NasenarbeitMapActivity.EXTRA_DISZIPLIN)
                    ?: disziplin.name

                if (schritte > 0) gebietSchritte = schritte
                if (ha > 0.0)     gebietHa       = ha
                disziplin = NasenarbeitDisziplin.valueOf(dizName)

                // Phase 2+3: Overpass + Topo speichern
                mapUmgebung = data.getStringArrayListExtra(
                    NasenarbeitMapActivity.RESULT_UMGEBUNG) ?: emptyList()
                mapBoden    = data.getStringArrayListExtra(
                    NasenarbeitMapActivity.RESULT_BODEN) ?: emptyList()
                mapTopo     = data.getStringArrayListExtra(
                    NasenarbeitMapActivity.RESULT_TOPO) ?: emptyList()

                mapSegmentValues = data.getDoubleArrayExtra(
                    NasenarbeitMapActivity.RESULT_SEGMENT_VALUES) ?: doubleArrayOf()
                mapWeights = data.getDoubleArrayExtra(
                    NasenarbeitMapActivity.RESULT_WEIGHTS) ?: doubleArrayOf()

                showScreen(2)
                buildGelaendeScreen()

                val msg = buildString {
                    append(getString(R.string.nasenarbeit_beratung_map_data_applied))
                    if (mapUmgebung.isNotEmpty()) append(" · " + getString(R.string.nasenarbeit_beratung_map_data_terrain))
                    if (mapTopo.isNotEmpty())     append(" · " + getString(R.string.nasenarbeit_beratung_map_data_topo))
                    if (mapBoden.isNotEmpty())    append(" · " + getString(R.string.nasenarbeit_beratung_map_data_ground))
                }
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            showScreen(2)
            buildGelaendeScreen()
        }
    }

    private var disziplin = NasenarbeitDisziplin.VERLORENSUCHE

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchWeather()
        else Toast.makeText(this, getString(R.string.nasenarbeit_map_error_no_gps), Toast.LENGTH_LONG).show()
    }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitBeratungBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        profile = intent.getParcelableExtra("profile")!!
        fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = profile.name + getString(R.string.nasenarbeit_beratung_title_suffix)

        setupScreenNavigation()
        loadLastDates()
        checkConsent()
    }

    private fun loadLastDates() {
        val allSessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)
        lastDatesByDiz.clear()
        NasenarbeitDisziplin.values().forEach { diz ->
            allSessions.filter { it.durchgefuehrteDisziplin == diz }
                .maxOfOrNull { it.datum }?.let { lastDatesByDiz[diz.ordinal] = it }
        }
    }

    // ════════════════════════════════════════════════════════════
    // CONSENT
    // ════════════════════════════════════════════════════════════

    private fun checkConsent() {
        if (getSharedPreferences(PREFS_CONSENT, MODE_PRIVATE).getBoolean(KEY_CONSENT, false)) {
            requestLocationAndFetch()
        } else {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.nasenarbeit_beratung_consent_title))
                .setMessage(getString(R.string.nasenarbeit_beratung_consent_message))
                .setPositiveButton(getString(R.string.nasenarbeit_beratung_consent_btn_continue)) { _, _ ->
                    getSharedPreferences(PREFS_CONSENT, MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, true).apply()
                    requestLocationAndFetch()
                }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }

    private fun requestLocationAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            fetchWeather()
        }
    }

    // ════════════════════════════════════════════════════════════
    // WEATHER
    // ════════════════════════════════════════════════════════════

    private fun fetchWeather() {
        showScreen(0)
        try {
            val last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) { paramLat = last.latitude; paramLon = last.longitude; doWeatherFetch() }
            else locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, this, null)
        } catch (e: SecurityException) { doWeatherFetch() } // fallback ohne GPS
    }

    override fun onLocationChanged(location: Location) {
        paramLat = location.latitude; paramLon = location.longitude; doWeatherFetch()
    }

    private fun doWeatherFetch() {
        lifecycleScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$paramLat&longitude=$paramLon" +
                            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                    JSONObject(URL(url).readText()).getJSONObject("current")
                }
                paramTemp     = data.getDouble("temperature_2m")
                paramHumidity = data.getInt("relative_humidity_2m")
                paramWmo      = data.getInt("weather_code")
                paramBf       = kmhToBeaufort(data.getDouble("wind_speed_10m"))
                weatherLoaded = true
                buildWetterScreen()
                showScreen(1)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@NasenarbeitBeratungActivity, getString(R.string.nasenarbeit_beratung_weather_not_available), Toast.LENGTH_LONG).show()
                    paramTemp = 15.0; paramBf = 3; paramWmo = 2; paramHumidity = 70
                    buildWetterScreen()
                    showScreen(1)
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN 1 – WETTER
    // ════════════════════════════════════════════════════════════

    private fun buildWetterScreen() {
        binding.tvTemp.text     = String.format(Locale.GERMAN, "%.1f°C", paramTemp)
        binding.tvBeaufort.text = "Bf $paramBf – ${BF_LABELS[paramBf]}"
        binding.tvHumidity.text = "$paramHumidity%"
        binding.tvWetterIcon.text  = wmoToIcon(paramWmo)
        binding.tvWetterLabel.text = wmoToLabel(paramWmo)

        val hardExcl = paramTemp > 30 || paramTemp < -10 || paramWmo in listOf(95,96,99) || paramBf >= 9
        if (hardExcl) {
            binding.cardExclusion.visibility = View.VISIBLE
            val reason = when {
                paramTemp > 30  -> getString(R.string.nasenarbeit_beratung_exclusion_too_hot_reason)
                paramTemp < -10 -> getString(R.string.nasenarbeit_beratung_exclusion_too_cold_reason)
                paramBf >= 9    -> getString(R.string.nasenarbeit_beratung_exclusion_storm_reason, paramBf)
                else            -> getString(R.string.nasenarbeit_beratung_exclusion_thunderstorm_reason)
            }
            binding.tvExclusionReason.text = reason
            binding.layoutWetterEignung.visibility = View.GONE
            binding.cardWindDir.visibility = View.GONE

            // ✅ NEU: Button aktiv lassen für Gewitter, aber mit Warn-Dialog
            if (paramWmo in listOf(95, 96, 99)) {
                binding.btnWetterWeiter.isEnabled = true
                binding.btnWetterWeiter.setOnClickListener {
                    showThunderstormWarningDialog()
                }
            } else {
                binding.btnWetterWeiter.isEnabled = false
            }
        } else {
            binding.cardExclusion.visibility = View.GONE
            updateWetterEignung()
            updateWindDirEignung()
            binding.layoutWetterEignung.visibility = View.VISIBLE
            binding.cardWindDir.visibility = View.VISIBLE
            binding.btnWetterWeiter.isEnabled = true
            binding.btnWetterWeiter.setOnClickListener {
                openMapActivity()
            }
        }

        // ✅ Wind-Richtung Listener (außerhalb des if/else – immer registrieren)
        binding.rgWindDir.setOnCheckedChangeListener { _, _ ->
            paramWindDir = when (binding.rgWindDir.checkedRadioButtonId) {
                R.id.rbWindSeite -> 1
                R.id.rbWindVorn  -> 2
                else             -> 0
            }
            updateWindDirEignung()
        }
    }

    /**
     * ✅ NEU: Warn-Dialog bei Gewitter
     */
    private fun showThunderstormWarningDialog() {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorNormal = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.nasenarbeit_beratung_thunderstorm_dialog_title))
            .setMessage(getString(R.string.nasenarbeit_beratung_thunderstorm_dialog_message))
            .setPositiveButton(getString(R.string.nasenarbeit_beratung_thunderstorm_btn_risk)) { _, _ ->
                openMapActivity()
            }
            .setNegativeButton(getString(R.string.nasenarbeit_beratung_thunderstorm_btn_agree)) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.YELLOW)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorNormal)
            }
    }

    private fun updateWetterEignung() {
        val wmoS = WMO_SUIT[paramWmo] ?: intArrayOf(0,0,0)
        val bfSuits = intArrayOf(
            NasenarbeitScorer.BF_SUIT_V[paramBf.coerceIn(0,12)][fortschritt.levelVerlorensuche.ordinal],
            NasenarbeitScorer.BF_SUIT_P[paramBf.coerceIn(0,12)][fortschritt.levelPersonensuche.ordinal],
            NasenarbeitScorer.BF_SUIT_F[paramBf.coerceIn(0,12)][fortschritt.levelFreisuche.ordinal]
        )
        val suits = IntArray(3) { i -> maxOf(wmoS[i], bfSuits[i]) }
        binding.layoutWetterBadges.removeAllViews()
        suits.forEachIndexed { i, s -> binding.layoutWetterBadges.addView(makeDizRow(i, s)) }
    }

    private fun updateWindDirEignung() {
        val dirMatrix = arrayOf(
            intArrayOf(0,0,0,0), intArrayOf(0,0,0,0), intArrayOf(2,2,0,0),  // Verlorensuche
            intArrayOf(2,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0),  // Personensuche
            intArrayOf(2,0,0,0), intArrayOf(0,0,0,0), intArrayOf(0,0,0,0)   // Freisuche
        )
        val suits = intArrayOf(
            dirMatrix[paramWindDir][fortschritt.levelVerlorensuche.ordinal],
            dirMatrix[3 + paramWindDir][fortschritt.levelPersonensuche.ordinal],
            dirMatrix[6 + paramWindDir][fortschritt.levelFreisuche.ordinal]
        )
        binding.layoutWindDirBadges.removeAllViews()
        suits.forEachIndexed { i, s -> binding.layoutWindDirBadges.addView(makeDizRow(i, s)) }
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN 2 – GELÄNDE
    // ════════════════════════════════════════════════════════════

    private fun buildGelaendeScreen() {
        // ── Karten-Daten ZUERST einfüllen, dann erst rendern ──────

        // Phase 3: Topografie aus Karte pre-fill (vor renderTopoSel!)
        if (mapTopo.isNotEmpty() && topoSequenz.isEmpty()) {
            mapTopo.forEach { topo ->
                val normalized = if (TOPO_OPTIONS.contains(topo)) topo else "Eben"
                topoSequenz.add(normalized)
            }
            android.widget.Toast.makeText(
                this,
                "⛰️ Topografie: ${topoSequenz.joinToString(" → ")}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } else if (mapTopo.isEmpty() && mapUmgebung.isNotEmpty()) {
            repeat(mapUmgebung.size) { topoSequenz.add("Eben") }
        }

        // Phase 2: Umgebung aus Karte pre-fill (vor renderUmbSel!)
        if (mapUmgebung.isNotEmpty() && umbSequenz.isEmpty()) {
            mapUmgebung.forEach { umb ->
                val normalized = if (UMB_OPTIONS.contains(umb)) umb else "Offenes Gelände"
                umbSequenz.add(normalized)
            }
            bodenEntries.clear()
        }

        // ── Jetzt Options-Chips aufbauen und mit gefüllten Sequenzen rendern ──

        // Topografie
        binding.chipGroupTopoOpts.removeAllViews()
        TOPO_OPTIONS.forEachIndexed { idx, opt ->
            binding.chipGroupTopoOpts.addView(makeSelectChip(TOPO_LABELS[idx]) {
                topoSequenz.add(opt); renderTopoSel(); updateGelaendeEignung()
            })
        }
        renderTopoSel()   // ← jetzt NACH pre-fill: zeigt bereits gefüllte Chips

        // Umgebung
        binding.chipGroupUmbOpts.removeAllViews()
        UMB_OPTIONS.forEachIndexed { idx, opt ->
            binding.chipGroupUmbOpts.addView(makeSelectChip(UMB_LABELS[idx]) {
                umbSequenz.add(opt); renderUmbSel(); updateGelaendeEignung()
                bodenEntries.clear()
            })
        }
        renderUmbSel()
        if (mapUmgebung.isNotEmpty()) {
            android.widget.Toast.makeText(
                this,
                "🗺️ Umgebung: ${mapUmgebung.distinct().joinToString(" → ")}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }

        // Gebiet sliders
        // Schritte: 0→50 steps of 50 = 50..3000
        // Slider-Position auf vorhandene Werte setzen (z.B. aus Karte)
        val initSchrProgress = ((gebietSchritte - 50) / 50).coerceIn(0, 59)
        binding.sbGebietSchritte.progress = initSchrProgress
        binding.tvGebietSchritte.text = "$gebietSchritte Schritte"

        binding.sbGebietSchritte.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                gebietSchritte = 50 + p * 50
                binding.tvGebietSchritte.text = "$gebietSchritte Schritte"
                updateGebietHint()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        // Ha: 0→99 steps of 0.1 = 0.1..10.0
        val initHaProgress = ((gebietHa / 0.1).toInt() - 1).coerceIn(0, 99)
        binding.sbGebietHa.progress = initHaProgress
        binding.tvGebietHa.text = String.format(Locale.GERMAN, "%.1f ha", gebietHa)

        binding.sbGebietHa.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                gebietHa = (p + 1) * 0.1
                binding.tvGebietHa.text = String.format(Locale.GERMAN, "%.1f ha", gebietHa)
                updateGebietHint()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        updateGebietHint()
        updateGelaendeEignung()

        binding.btnGelaendeWeiter.setOnClickListener {
            showScreen(3); buildBodenScreen()
        }
    }

    private fun renderTopoSel() {
        binding.chipGroupTopoSel.removeAllViews()
        if (topoSequenz.isEmpty()) {
            binding.chipGroupTopoSel.addView(makeInfoText(getString(R.string.nasenarbeit_beratung_topo_not_selected)))
        } else {
            topoSequenz.forEachIndexed { idx, v ->
                binding.chipGroupTopoSel.addView(makeSeqChip("${idx+1}. $v") {
                    topoSequenz.removeAt(idx); renderTopoSel(); updateGelaendeEignung()
                })
            }
        }
        updateOptionsHighlighting()
    }

    private fun renderUmbSel() {
        binding.chipGroupUmbSel.removeAllViews()
        if (umbSequenz.isEmpty()) {
            binding.chipGroupUmbSel.addView(makeInfoText(getString(R.string.nasenarbeit_beratung_topo_not_selected)))
        } else {
            umbSequenz.forEachIndexed { idx, v ->
                binding.chipGroupUmbSel.addView(makeSeqChip("${idx+1}. $v") {
                    umbSequenz.removeAt(idx); renderUmbSel(); updateGelaendeEignung()
                    bodenEntries.clear()
                })
            }
        }
        updateOptionsHighlighting()
    }

    private fun updateGebietHint() {
        val levV = fortschritt.levelVerlorensuche
        val levFl = fortschritt.levelFreisuche
        val hints = mutableListOf<String>()
        if (gebietSchritte < levV.maxSchritte_V) hints.add(getString(R.string.nasenarbeit_beratung_area_limit_v, gebietSchritte))
        if (gebietSchritte < fortschritt.levelPersonensuche.maxSchritte_P) hints.add(getString(R.string.nasenarbeit_beratung_area_limit_p))
        if (gebietHa < levFl.maxHa_F) hints.add(getString(R.string.nasenarbeit_beratung_area_limit_f, String.format(Locale.US, "%.1f", gebietHa)))
        binding.tvGebietHint.text = if (hints.isEmpty()) getString(R.string.nasenarbeit_beratung_area_sufficient) else hints.joinToString(" · ")
    }

    private fun updateGelaendeEignung() {
        val TOPO_SUIT = mapOf(getString(R.string.nasenarbeit_opt_topo_eben) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_topo_steigung) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_topo_steil) to intArrayOf(1,1,0),getString(R.string.nasenarbeit_opt_topo_kuppe) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_topo_hang) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_topo_mulde) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_topo_relief) to intArrayOf(0,0,1),getString(R.string.nasenarbeit_opt_topo_fels) to intArrayOf(1,1,2))
        val UMB_SUIT  = mapOf(getString(R.string.nasenarbeit_opt_umb_offen) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_umb_wald) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_umb_gruen) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_umb_urban) to intArrayOf(0,0,1),getString(R.string.nasenarbeit_opt_umb_industrie) to intArrayOf(0,0,1),getString(R.string.nasenarbeit_opt_umb_dorf) to intArrayOf(0,0,1),getString(R.string.nasenarbeit_opt_umb_park) to intArrayOf(0,0,0),getString(R.string.nasenarbeit_opt_umb_misch) to intArrayOf(0,0,1))
        val suit = IntArray(3)
        (topoSequenz + umbSequenz).forEach { item ->
            (TOPO_SUIT[item] ?: UMB_SUIT[item] ?: intArrayOf(0,0,0)).forEachIndexed { i, s -> if (s > suit[i]) suit[i] = s }
        }
        binding.layoutGelaendeEignung.removeAllViews()
        if (topoSequenz.isNotEmpty() || umbSequenz.isNotEmpty()) {
            val card = MaterialCardView(this).apply { radius = 12f * resources.displayMetrics.density; cardElevation = 2f * resources.displayMetrics.density }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)) }
            val title = TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_topo_eval_title); textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(0,0,0,dp(10)) }
            inner.addView(title)
            suit.forEachIndexed { i, s -> inner.addView(makeDizRow(i, s)) }
            card.addView(inner)
            binding.layoutGelaendeEignung.addView(card)
        }
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN 3 – BODEN
    // ════════════════════════════════════════════════════════════

    private fun buildBodenScreen() {
        val abschnitte = if (umbSequenz.isNotEmpty()) umbSequenz else listOf("Allgemein")
        // Sync bodenEntries list size
        while (bodenEntries.size < abschnitte.size) bodenEntries.add(BodenEntry())
        while (bodenEntries.size > abschnitte.size) bodenEntries.removeLastOrNull()

        // ── Automatische Aufteilung der Gesamtwerte ─────────────
        val count = abschnitte.size
        val avgSchritte = if (count > 0) gebietSchritte / count else 0
        val avgM2       = if (count > 0) (gebietHa * 10_000.0) / count else 0.0

        // Phase 2: Overpass-Bodendaten für Abschnitte vorausfüllen
        // mapBoden hat so viele Einträge wie mapUmgebung (1 pro Abschnitt)
        bodenEntries.forEachIndexed { idx, entry ->
            val bodenTyp = mapBoden.getOrNull(idx) ?: ""
            if (bodenTyp.isNotEmpty()) {
                entry.bodenTyp = bodenTyp
            }

            // ✅ NEU: Korrekte Verteilung der Fläche bei Freisuche
            if (disziplin == NasenarbeitDisziplin.FREISUCHE) {
                if (entry.flaechenM2 == null) {
                    if (mapSegmentValues.size == 1 && count > 1 && mapWeights.size == count) {
                        // FALL: Ein Polygon, aber mehrere Bodentypen mit GEWICHTUNG
                        entry.flaechenM2 = mapSegmentValues[0] * mapWeights[idx]
                    } else if (mapSegmentValues.size == 1 && count > 1) {
                        // Fallback: gleichmäßig
                        entry.flaechenM2 = mapSegmentValues[0] / count
                    } else {
                        val mapValue = mapSegmentValues.getOrNull(idx)
                        entry.flaechenM2 = mapValue ?: avgM2
                    }
                }
            } else {
                if (entry.laengeSchritte == null) {
                    if (mapSegmentValues.size == 1 && count > 1 && mapWeights.size == count) {
                        entry.laengeSchritte = (mapSegmentValues[0] * mapWeights[idx]).toInt()
                    } else if (mapSegmentValues.isNotEmpty()) {
                        val mapValue = mapSegmentValues.getOrNull(idx)
                        entry.laengeSchritte = mapValue?.toInt() ?: (mapSegmentValues.sum() / count).toInt()
                    } else {
                        entry.laengeSchritte = avgSchritte
                    }
                }
            }
            // Bodenzustand bleibt leer → Nutzer wählt manuell
        }

        binding.layoutBodenEntries.removeAllViews()
        abschnitte.forEachIndexed { idx, name -> binding.layoutBodenEntries.addView(buildBodenEntry(idx, name)) }
        checkBodenComplete()
        binding.btnBodenWeiter.setOnClickListener { showScreen(4); buildTrainingScreen() }
    }

    private fun buildBodenEntry(idx: Int, name: String): View {
        val dp = { n: Int -> (n * resources.displayMetrics.density).toInt() }
        val card = MaterialCardView(this).apply {
            radius = 10f * resources.displayMetrics.density
            cardElevation = 2f * resources.displayMetrics.density
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(10); layoutParams = lp
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)) }

        // Header
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        hdr.addView(TextView(this).apply {
            text = "${idx+1}"; textSize = 11f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            background = ContextCompat.getDrawable(this@NasenarbeitBeratungActivity, R.drawable.icon_circle_bg)
            setPadding(dp(8),dp(4),dp(8),dp(4))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginEnd = dp(10); layoutParams = lp
        })
        hdr.addView(TextView(this).apply { text = name; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) })
        val lphdr = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lphdr.bottomMargin = dp(12)
        hdr.layoutParams = lphdr
        root.addView(hdr)

        // Bodentyp Spinner
        root.addView(TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_ground_type_label); textSize = 11f; setPadding(0,0,0,dp(4)) })
        val spBoden = Spinner(this)
        val btLabels = BODEN_TYPEN.map { it.second }
        spBoden.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, btLabels)
        spBoden.setSelection(0)
        val lpSpin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpSpin.bottomMargin = dp(10); spBoden.layoutParams = lpSpin
        root.addView(spBoden)

        // Bodenzustand Spinner
        root.addView(TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_ground_condition_label); textSize = 11f; setPadding(0,0,0,dp(4)) })
        val spZustand = Spinner(this)
        val bzLabels = BODEN_ZUSTAND.map { it.second }
        val zustandAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bzLabels)
        spZustand.adapter = zustandAdapter
        val lpSpin2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpSpin2.bottomMargin = dp(10); spZustand.layoutParams = lpSpin2
        root.addView(spZustand)

        // Länge + Fläche row
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // Schritte
        val colSchr = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lp2.marginEnd = dp(8); layoutParams = lp2 }
        colSchr.addView(TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_ground_steps_label); textSize = 10f; setPadding(0,0,0,dp(4)) })
        val etSchr = EditText(this).apply { hint = "z.B. 200"; inputType = InputType.TYPE_CLASS_NUMBER; textSize = 12f }
        colSchr.addView(etSchr)
        val cbSchr = CheckBox(this).apply { text = getString(R.string.nasenarbeit_beratung_topo_not_selected); textSize = 11f }
        colSchr.addView(cbSchr)
        row.addView(colSchr)
        // Prozent
        val colPct = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 }
        colPct.addView(TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_ground_area_pct_label); textSize = 10f; setPadding(0,0,0,dp(4)) })
        val etPct = EditText(this).apply { hint = "z.B. 30"; inputType = InputType.TYPE_CLASS_NUMBER; textSize = 12f }
        colPct.addView(etPct)
        val cbPct = CheckBox(this).apply { text = getString(R.string.nasenarbeit_beratung_topo_not_selected); textSize = 11f }
        colPct.addView(cbPct)
        row.addView(colPct)
        val lpRow = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpRow.bottomMargin = dp(10); row.layoutParams = lpRow
        root.addView(row)

        // Eignung label
        val tvEignung = TextView(this).apply { textSize = 12f; setPadding(0,dp(4),0,0) }
        root.addView(tvEignung)

        card.addView(root)

        // Listeners
        val entry = bodenEntries[idx]

        // UI-Felder mit voreingetragenen Werten füllen
        if (entry.laengeSchritte != null) etSchr.setText(entry.laengeSchritte.toString())
        if (entry.flaechenM2 != null) etPct.setText(String.format(Locale.US, "%.0f", entry.flaechenM2))

        cbSchr.setOnCheckedChangeListener { _, checked -> etSchr.isEnabled = !checked; entry.keineAngabeSchr = checked; if(checked) entry.laengeSchritte = null; updateBodenEignung(idx, tvEignung) }
        cbPct.setOnCheckedChangeListener  { _, checked -> etPct.isEnabled  = !checked; entry.keineAngabeM2  = checked; if(checked) entry.flaechenM2  = null; updateBodenEignung(idx, tvEignung) }

        etSchr.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { entry.laengeSchritte = s.toString().toIntOrNull(); updateBodenEignung(idx, tvEignung) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        etPct.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { entry.flaechenM2 = s.toString().toDoubleOrNull(); updateBodenEignung(idx, tvEignung) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        spBoden.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                entry.bodenTyp = BODEN_TYPEN[pos].first
                // Disable matschig if needed
                val noMatsch = NO_MATSCHIG.contains(entry.bodenTyp)
                val allZustand = BODEN_ZUSTAND.map { if (noMatsch && it.first == "matschig") getString(R.string.nasenarbeit_beratung_ground_not_available) else it.second }
                spZustand.adapter = ArrayAdapter(this@NasenarbeitBeratungActivity, android.R.layout.simple_spinner_dropdown_item, allZustand)
                if (noMatsch && entry.bodenZustand == "matschig") { entry.bodenZustand = ""; spZustand.setSelection(0) }
                updateBodenEignung(idx, tvEignung); checkBodenComplete()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spZustand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                entry.bodenZustand = BODEN_ZUSTAND[pos].first
                updateBodenEignung(idx, tvEignung); checkBodenComplete()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        return card
    }

    private fun updateBodenEignung(idx: Int, tv: TextView) {
        val entry = bodenEntries.getOrNull(idx) ?: return
        if (entry.bodenTyp.isEmpty() || entry.bodenZustand.isEmpty()) { tv.text = ""; return }
        val suits = NasenarbeitScorer.getBodenSuit(entry.bodenTyp, entry.bodenZustand)
        tv.text = suits?.let { s ->
            DIZ_ICONS.mapIndexed { i, ic -> "$ic ${SUIT_SHORT[s[i]]}" }.joinToString("  ")
        } ?: ""
    }

    private fun checkBodenComplete() {
        val all = bodenEntries.all { it.bodenTyp.isNotEmpty() && it.bodenZustand.isNotEmpty() }
        binding.btnBodenWeiter.isEnabled = all
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN 4 – TRAININGSSTAND
    // ════════════════════════════════════════════════════════════

    private fun buildTrainingScreen() {
        // Level display – auto-loaded
        binding.layoutLevelDisplay.removeAllViews()
        listOf(
            Triple("🐾 " + getString(R.string.nasenarbeit_disziplin_verlorensuche), fortschritt.sessionsVerlorensuche, fortschritt.levelVerlorensuche),
            Triple("🧑 " + getString(R.string.nasenarbeit_disziplin_personensuche), fortschritt.sessionsPersonensuche, fortschritt.levelPersonensuche),
            Triple("🔍 " + getString(R.string.nasenarbeit_disziplin_freisuche),     fortschritt.sessionsFreisuche,     fortschritt.levelFreisuche)
        ).forEach { (label, sessions, level) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(8); layoutParams = lp }
            row.addView(TextView(this).apply { text = label; textSize = 13f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
            row.addView(TextView(this).apply { text = getString(R.string.nasenarbeit_beratung_level_sessions, level.displayName(this@NasenarbeitBeratungActivity), sessions); textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) })
            binding.layoutLevelDisplay.addView(row)
        }

        // Letzte Einheit
        binding.rgLetzte.setOnCheckedChangeListener { _, id ->
            letzteEinheit = when(id) {
                R.id.rbHeute      -> getString(R.string.today)
                R.id.rb7Tage      -> getString(R.string.nasenarbeit_beratung_last_7days)
                R.id.rb14Tage     -> getString(R.string.nasenarbeit_beratung_last_14days)
                R.id.rbNochKeine  -> getString(R.string.nasenarbeit_beratung_last_none)
                else              -> getString(R.string.yesterday)
            }
            // Leistungsdaten auf 0 setzen wenn noch kein Training stattgefunden hat
            if (id == R.id.rbNochKeine) {
                perfV = 0; perfP = 0; perfFl = 0.0
                binding.sbPerfV.progress  = 0
                binding.sbPerfP.progress  = 0
                binding.sbPerfFl.progress = 0
                binding.tvPerfV.text  = "0 " + getString(R.string.nasenarbeit_map_result_steps_unit)
                binding.tvPerfP.text  = "0 " + getString(R.string.nasenarbeit_map_result_steps_unit)
                binding.tvPerfFl.text = "0.0 ha"
                binding.sbPerfV.isEnabled  = false
                binding.sbPerfP.isEnabled  = false
                binding.sbPerfFl.isEnabled = false
            } else {
                binding.sbPerfV.isEnabled  = true
                binding.sbPerfP.isEnabled  = true
                binding.sbPerfFl.isEnabled = true
            }
        }
        // Tagesverfassung
        binding.rgVerfassung.setOnCheckedChangeListener { _, id ->
            verfassung = when(id) {
                R.id.rbSehr   -> getString(R.string.nasenarbeit_beratung_condition_very)
                R.id.rbExtrem -> getString(R.string.nasenarbeit_beratung_condition_extreme)
                else          -> getString(R.string.nasenarbeit_beratung_condition_normal)
            }
        }

        // Perf sliders – Schritte 0..200 * 10 = 0..2000
        binding.sbPerfV.setOnSeekBarChangeListener(seekListener { perfV = it * 10; binding.tvPerfV.text = "$perfV " + getString(R.string.nasenarbeit_map_result_steps_unit) })
        binding.sbPerfP.setOnSeekBarChangeListener(seekListener { perfP = it * 10; binding.tvPerfP.text = "$perfP " + getString(R.string.nasenarbeit_map_result_steps_unit) })
        binding.sbPerfFl.setOnSeekBarChangeListener(seekListener { perfFl = it * 0.1; binding.tvPerfFl.text = String.format(Locale.US,"%.1f ha", perfFl) })

        // Auto-select "Noch keine" if no sessions exist yet → Leistungsdaten sperren
        if (fortschritt.gesamtSessions == 0) {
            binding.rgLetzte.check(R.id.rbNochKeine)
            letzteEinheit = getString(R.string.nasenarbeit_beratung_last_none)
            perfV = 0; perfP = 0; perfFl = 0.0
            binding.sbPerfV.isEnabled  = false
            binding.sbPerfP.isEnabled  = false
            binding.sbPerfFl.isEnabled = false
        }

        // Pre-fill Leistungsdaten getrennt pro Disziplin aus jeweils letzter Session
        val allSessions = NasenarbeitManager.loadSessionsForDog(this, profile.id)

        // Verlorensuche – letzte Session dieser Disziplin
        allSessions.firstOrNull { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.VERLORENSUCHE }
            ?.durchgefuehrteSchritte?.let { schr ->
                if (schr > 0) {
                    perfV = schr
                    binding.sbPerfV.progress = (schr / 10).coerceIn(0, 200)
                }
            }

        // Personensuche – letzte Session dieser Disziplin
        allSessions.firstOrNull { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.PERSONENSUCHE }
            ?.durchgefuehrteSchritte?.let { schr ->
                if (schr > 0) {
                    perfP = schr
                    binding.sbPerfP.progress = (schr / 10).coerceIn(0, 200)
                }
            }

        // Freisuche – letzte Session dieser Disziplin
        allSessions.firstOrNull { it.durchgefuehrteDisziplin == NasenarbeitDisziplin.FREISUCHE }
            ?.durchgefuehrteHa?.let { ha ->
                if (ha > 0.0) {
                    perfFl = ha
                    binding.sbPerfFl.progress = (ha / 0.1).toInt().coerceIn(0, 50)
                }
            }

        // Anzeige aktualisieren
        binding.tvPerfV.text  = getString(R.string.nasenarbeit_beratung_steps, perfV)
        binding.tvPerfP.text  = getString(R.string.nasenarbeit_beratung_steps, perfP)
        binding.tvPerfFl.text = getString(R.string.nasenarbeit_beratung_hectares, perfFl)

        binding.btnTrainingWeiter.setOnClickListener {
            // Recommendation triggered BEFORE finalizing screen data to auto-set RadioButtons
            val result = NasenarbeitScorer.calculate(this, buildParam(), fortschritt)
            updateLetzteEinheitForDiz(result.primaer.ordinal)
            buildAndShowResult()
        }
    }

    private fun updateLetzteEinheitForDiz(dizOrdinal: Int) {
        val lastDate = lastDatesByDiz[dizOrdinal]
        if (lastDate == null) {
            binding.rgLetzte.check(R.id.rbNochKeine)
            return
        }

        val diffDays = (System.currentTimeMillis() - lastDate) / (1000 * 60 * 60 * 24)
        when {
            diffDays <= 0 -> binding.rgLetzte.check(R.id.rbHeute)
            diffDays <= 1 -> binding.rgLetzte.check(R.id.rbGestern)
            diffDays <= 7 -> binding.rgLetzte.check(R.id.rb7Tage)
            diffDays <= 14 -> binding.rgLetzte.check(R.id.rb14Tage)
            else -> binding.rgLetzte.check(R.id.rbNochKeine)
        }
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN 5 – EMPFEHLUNG (BUILD)
    // ════════════════════════════════════════════════════════════

    private fun buildAndShowResult() {
        val param = buildParam()
        scoringResult = NasenarbeitScorer.calculate(this, param, fortschritt)
        val result = scoringResult!!

        // Block 1
        if (result.isHardExcluded) {
            binding.tvEmpfehlungDiz.text = getString(R.string.nasenarbeit_beratung_training_not_possible)
            binding.tvEmpfehlungGrund.text = result.hardExclusionReason
            binding.tvEmpfehlungScore.text = ""
            binding.btnTrainingStarten.isEnabled = false
        } else {
            val p = result.primaer
            binding.tvEmpfehlungDiz.text = "${p.icon} ${p.displayName(this)}"
            binding.tvEmpfehlungGrund.text = result.begruendung
            binding.tvEmpfehlungScore.text = getString(
                R.string.nasenarbeit_beratung_score_line,
                DIZ_ICONS[0], fmt1(result.scores[0]),
                DIZ_ICONS[1], fmt1(result.scores[1]),
                DIZ_ICONS[2], fmt1(result.scores[2])
            )
            if (result.empfohlen.size > 1) {
                binding.tvEmpfehlungAlt.visibility = View.VISIBLE
                binding.tvEmpfehlungAlt.text = getString(
                    R.string.nasenarbeit_beratung_also_suitable,
                    result.empfohlen.filter { it != p }.joinToString(", ") { "${it.icon} ${it.displayName(this)}" }
                )
            }
            binding.btnTrainingStarten.isEnabled = true
        }

        // Block 2
        buildBlock2(result, param)
        // Block 3
        buildBlock3(param)
        // Block 5
        buildBlock5(result, param)
        // Block 6
        buildBlock6(result)

        // Button listeners
        // Tab listener for Block 5
        binding.tabLayoutB5.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val t = when(tab?.position) {
                    1 -> "warn"
                    2 -> "stop"
                    3 -> "wx"
                    else -> "ok"
                }
                showB5Tab(t)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
        showB5Tab("ok")

        binding.btnTrainingStarten.setOnClickListener {
            val intent = Intent(this, NasenarbeitErgebnisActivity::class.java)
            intent.putExtra("profile", profile)
            intent.putExtra("parameter", param)
            intent.putExtra("scoring_v", result.scores[0])
            intent.putExtra("scoring_p", result.scores[1])
            intent.putExtra("scoring_f", result.scores[2])
            intent.putExtra("primaer_disziplin", result.primaer.name)
            startActivity(intent)
        }
        binding.btnNeueBeratung.setOnClickListener { resetAndRestart() }

        showScreen(5)
    }

    private fun buildBlock2(result: NasenarbeitScorer.ScoringResult, param: NasenarbeitParameter) {
        binding.layoutParamGrid.removeAllViews()
        if (result.isHardExcluded) return
        val best = result.primaer.ordinal
        val levels = listOf(fortschritt.levelVerlorensuche, fortschritt.levelPersonensuche, fortschritt.levelFreisuche)
        val lev = levels[best]
        // Startwerte wenn noch keine Leistungsdaten vorhanden
        val baseV  = if (perfV  <= 0) START_SCHRITTE_V  else perfV
        val baseP  = if (perfP  <= 0) START_SCHRITTE_P  else perfP
        val baseFl = if (perfFl <= 0) START_HA_FL        else perfFl

        // Multiplikator: bei "Noch keine Trainingseinheit" neutral (1.0), keine Steigerung
        var mult = if (letzteEinheit == "Noch keine Trainingseinheit") 1.0 else 1.0 + lev.steigerung
        if (letzteEinheit == "Heute") mult *= 0.7
        if (letzteEinheit == "Innerhalb 14 Tage") mult *= 0.85
        if (verfassung == "Sehr motiviert") mult *= 1.1
        if (verfassung == "Extrem motiviert") mult *= 1.2

        val recV  = (baseV  * mult).toInt().coerceAtMost(gebietSchritte)
        val recP  = (baseP  * mult).toInt().coerceAtMost(gebietSchritte)
        val recFl = (baseFl * mult).coerceAtMost(gebietHa)

        val isFirstSession = letzteEinheit == getString(R.string.nasenarbeit_beratung_last_none) ||
                (perfV <= 0 && perfP <= 0 && perfFl <= 0)
        val starterHint = if (isFirstSession) " (Startwert)" else ""

        val recStr = when (result.primaer) {
            NasenarbeitDisziplin.VERLORENSUCHE -> "$recV " + getString(R.string.nasenarbeit_map_result_steps_unit) + starterHint
            NasenarbeitDisziplin.PERSONENSUCHE -> "$recP " + getString(R.string.nasenarbeit_map_result_steps_unit) + starterHint
            NasenarbeitDisziplin.FREISUCHE     -> String.format(Locale.US, "%.2f ha", recFl) + starterHint
        }

        val spuralter = calcSpuralter(lev.ordinal, param.beaufort, param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: "")

        listOf(
            getString(R.string.nasenarbeit_beratung_label_recommended_length) to recStr,
            getString(R.string.nasenarbeit_beratung_label_difficulty) to lev.schwierigkeit(this),
            if (result.primaer != NasenarbeitDisziplin.FREISUCHE) getString(R.string.nasenarbeit_beratung_label_spuralter) to spuralter else null,
            getString(R.string.nasenarbeit_beratung_label_letzte_einheit) to letzteEinheit,
            getString(R.string.nasenarbeit_beratung_label_tagesverfassung) to verfassung,
            getString(R.string.nasenarbeit_beratung_label_steigerungsfaktor) to "×${String.format(Locale.GERMAN,"%.2f", mult)}"
        ).filterNotNull().forEach { (label, value) -> binding.layoutParamGrid.addView(makeParamRow(label, value)) }
    }

    private fun buildBlock3(param: NasenarbeitParameter) {
        binding.layoutVorbereitung.removeAllViews()
        listOf(
            getString(R.string.nasenarbeit_beratung_prep_check_gear),
            getString(R.string.nasenarbeit_beratung_prep_water),
            getString(R.string.nasenarbeit_beratung_prep_rewards)
        ).forEach { binding.layoutVorbereitung.addView(makeBullet("✅", it)) }
        val zustand = param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: ""
        if (zustand in listOf("gefroren","schnee_l","schnee_m","schnee_h"))
            binding.layoutVorbereitung.addView(makeBullet("⚠️", getString(R.string.nasenarbeit_beratung_prep_paws)))
        if (param.beaufort >= 6)
            binding.layoutVorbereitung.addView(makeBullet("⚠️", getString(R.string.nasenarbeit_beratung_prep_wind)))
        if (param.temperaturCelsius > 25)
            binding.layoutVorbereitung.addView(makeBullet("⚠️", getString(R.string.nasenarbeit_beratung_prep_heat)))
    }

    private fun buildBlock5(result: NasenarbeitScorer.ScoringResult, param: NasenarbeitParameter) {
        if (result.isHardExcluded) return
        val diz = result.primaer.ordinal
        val lev = listOf(fortschritt.levelVerlorensuche.ordinal, fortschritt.levelPersonensuche.ordinal, fortschritt.levelFreisuche.ordinal)[diz]
        val zustand = param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: ""

        val signals = NasenarbeitSignals.getSignals(diz, lev, param.beaufort, zustand)
        val b5Data = mapOf("ok" to signals.ok, "warn" to signals.warn, "stop" to signals.stop, "wx" to signals.wx)
        binding.layoutB5Content.tag = b5Data
    }

    private fun showB5Tab(tab: String) {
        currentB5Tab = tab
        val tabIndex = when(tab) {
            "warn" -> 1
            "stop" -> 2
            "wx"   -> 3
            else   -> 0
        }
        if (binding.tabLayoutB5.selectedTabPosition != tabIndex) {
            binding.tabLayoutB5.getTabAt(tabIndex)?.select()
        }
        @Suppress("UNCHECKED_CAST")
        val data = binding.layoutB5Content.tag as? Map<String, List<String>> ?: return
        binding.layoutB5Content.removeAllViews()
        data[tab]?.forEach { binding.layoutB5Content.addView(makeBullet(if (tab == "ok") "✅" else if (tab == "stop") "🚫" else "⚠️", it)) }
    }

    private fun buildParam(): NasenarbeitParameter {
        val abschnitte = (if (umbSequenz.isNotEmpty()) umbSequenz else listOf("Allgemein")).mapIndexed { idx, name ->
            val e = bodenEntries.getOrNull(idx) ?: BodenEntry()
            GelaendeAbschnitt(
                umgebung = name,
                bodenTyp = e.bodenTyp,
                bodenZustand = e.bodenZustand,
                laengeSchritte = e.laengeSchritte,
                flaechenM2 = e.flaechenM2
            )
        }
        return NasenarbeitParameter(
            temperaturCelsius = paramTemp, beaufort = paramBf, wmoCode = paramWmo,
            luftfeuchtigkeit = paramHumidity, windrichtung = paramWindDir,
            lat = paramLat, lon = paramLon,
            topoSequenz = topoSequenz.toList(), gelaendeAbschnitte = abschnitte,
            gebietSchritte = gebietSchritte, gebietHa = gebietHa,
            letzteEinheit = letzteEinheit, tagesverfassung = verfassung,
            letzteLeistungSchritte = perfV, letzteLeistungHa = perfFl
        )
    }

    private fun buildBlock6(result: NasenarbeitScorer.ScoringResult) {
        binding.layoutNachTraining.removeAllViews()
        if (result.isHardExcluded) return
        val best = result.primaer.ordinal
        val lev  = listOf(fortschritt.levelVerlorensuche, fortschritt.levelPersonensuche, fortschritt.levelFreisuche)[best]
        listOf(
            getString(R.string.nasenarbeit_beratung_after_diary),
            getString(R.string.nasenarbeit_beratung_after_easy, (lev.steigerung * 100).toInt()),
            getString(R.string.nasenarbeit_beratung_after_hard)
        ).forEach { binding.layoutNachTraining.addView(makeBullet("📊", it)) }
    }

    private fun openMapActivity() {
        val intent = android.content.Intent(this, NasenarbeitMapActivity::class.java)
        intent.putExtra(NasenarbeitMapActivity.EXTRA_DISZIPLIN, disziplin.name)
        mapResultPending = true
        mapLauncher.launch(intent)
    }

    // ════════════════════════════════════════════════════════════
    // SCREEN NAVIGATION
    // ════════════════════════════════════════════════════════════

    private val screens by lazy { listOf(
        binding.screenLoading, binding.screenWetter, binding.screenGelande,
        binding.screenBoden, binding.screenTraining, binding.screenEmpfehlung
    )}

    private fun showScreen(idx: Int) {
        currentScreen = idx
        screens.forEachIndexed { i, v -> v.visibility = if (i == idx) View.VISIBLE else View.GONE }
        binding.progressBar.progress = idx + 1
        supportActionBar?.title = when(idx) {
            0 -> "${profile.name} – " + getString(R.string.training_category_nasenarbeit)
            1 -> getString(R.string.nasenarbeit_beratung_tab_weather)
            2 -> getString(R.string.nasenarbeit_beratung_tab_gelaende)
            3 -> getString(R.string.nasenarbeit_beratung_tab_boden)
            4 -> getString(R.string.nasenarbeit_beratung_tab_training)
            5 -> getString(R.string.nasenarbeit_beratung_tab_empfehlung)
            else -> getString(R.string.training_category_nasenarbeit)
        }
    }

    private fun setupScreenNavigation() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (currentScreen > 1) { showScreen(currentScreen - 1); return true }
        finish(); return true
    }

    private fun updateOptionsHighlighting() {
        val activeColor = Color.parseColor("#004c6f")
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        val inactiveColor = if (isDark) Color.parseColor("#333333") else Color.parseColor("#E0E0E0")
        val activeTextColor = Color.WHITE
        val inactiveTextColor = if (isDark) Color.WHITE else Color.BLACK

        // Topo
        for (i in 0 until binding.chipGroupTopoOpts.childCount) {
            val chip = binding.chipGroupTopoOpts.getChildAt(i) as? Chip ?: continue
            val opt = TOPO_OPTIONS.getOrNull(i) ?: continue
            val selected = topoSequenz.contains(opt)
            chip.setChipBackgroundColor(ColorStateList.valueOf(if (selected) activeColor else inactiveColor))
            chip.setTextColor(if (selected) activeTextColor else inactiveTextColor)
        }
        // Umb
        for (i in 0 until binding.chipGroupUmbOpts.childCount) {
            val chip = binding.chipGroupUmbOpts.getChildAt(i) as? Chip ?: continue
            val opt = UMB_OPTIONS.getOrNull(i) ?: continue
            val selected = umbSequenz.contains(opt)
            chip.setChipBackgroundColor(ColorStateList.valueOf(if (selected) activeColor else inactiveColor))
            chip.setTextColor(if (selected) activeTextColor else inactiveTextColor)
        }
    }

    private fun resetAndRestart() {
        topoSequenz.clear(); umbSequenz.clear(); bodenEntries.clear()
        scoringResult = null; currentB5Tab = "ok"
        disziplin = NasenarbeitDisziplin.VERLORENSUCHE
        gebietSchritte = 500; gebietHa = 0.5
        mapUmgebung = emptyList(); mapBoden = emptyList(); mapTopo = emptyList()
        fetchWeather()
    }

    // ════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════

    private fun kmhToBeaufort(kmh: Double): Int = when {
        kmh < 1 -> 0; kmh < 6 -> 1; kmh < 12 -> 2; kmh < 20 -> 3
        kmh < 29 -> 4; kmh < 39 -> 5; kmh < 50 -> 6; kmh < 62 -> 7
        kmh < 75 -> 8; kmh < 89 -> 9; kmh < 103 -> 10; kmh < 118 -> 11; else -> 12
    }

    private fun wmoToIcon(c: Int) = when(c) { 0->"☀️"; 1,2,3->"⛅"; 45,48->"🌫️"; in 51..57->"🌦️"; in 61..67->"🌧️"; in 71..77->"🌨️"; in 80..82->"🌦️"; 85,86->"🌨️"; 95,96,99->"⛈️"; else->"🌤️" }
    private fun wmoToLabel(c: Int) = when(c) {
        0->getString(R.string.nasenarbeit_beratung_wmo_0)
        1->getString(R.string.nasenarbeit_beratung_wmo_1)
        2->getString(R.string.nasenarbeit_beratung_wmo_2)
        3->getString(R.string.nasenarbeit_beratung_wmo_3)
        45->getString(R.string.nasenarbeit_beratung_wmo_45)
        48->getString(R.string.nasenarbeit_beratung_wmo_48)
        in 51..55->getString(R.string.nasenarbeit_beratung_wmo_51)
        in 61..65->getString(R.string.nasenarbeit_beratung_wmo_61)
        in 71..75->getString(R.string.nasenarbeit_beratung_wmo_71)
        95->getString(R.string.nasenarbeit_beratung_wmo_95)
        96,99->getString(R.string.nasenarbeit_beratung_wmo_96)
        else->getString(R.string.nasenarbeit_beratung_wmo_fallback)
    }

    private fun calcSpuralter(lev: Int, bf: Int, zustand: String): String {
        val isFeucht = zustand in listOf("feucht","nass","stehend","matschig","schnee_l","schnee_m","schnee_h")
        val isStrong = bf >= 6
        return when(lev) {
            0 -> "2–10 " + getString(R.string.nasenarbeit_session_detail_duration_unit)
            1 -> "10–30 " + getString(R.string.nasenarbeit_session_detail_duration_unit)
            2 -> if (isFeucht && !isStrong) "30–120 " + getString(R.string.nasenarbeit_session_detail_duration_unit) else "30–90 " + getString(R.string.nasenarbeit_session_detail_duration_unit)
            else -> if (isStrong) "90 " + getString(R.string.nasenarbeit_session_detail_duration_unit) + "–12h" else if (isFeucht) "2h–24h" else "2h–18h"
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun fmt1(d: Double) = if (d > -999) String.format(Locale.US,"%.1f", d) else "❌"

    private val SUIT_SHORT = arrayOf("✅","⚠️","❌")
    private val BF_LABELS by lazy { arrayOf(
        getString(R.string.nasenarbeit_beratung_bf_0),
        getString(R.string.nasenarbeit_beratung_bf_1),
        getString(R.string.nasenarbeit_beratung_bf_2),
        getString(R.string.nasenarbeit_beratung_bf_3),
        getString(R.string.nasenarbeit_beratung_bf_4),
        getString(R.string.nasenarbeit_beratung_bf_5),
        getString(R.string.nasenarbeit_beratung_bf_6),
        getString(R.string.nasenarbeit_beratung_bf_7),
        getString(R.string.nasenarbeit_beratung_bf_8),
        getString(R.string.nasenarbeit_beratung_bf_9),
        getString(R.string.nasenarbeit_beratung_bf_10),
        getString(R.string.nasenarbeit_beratung_bf_11),
        getString(R.string.nasenarbeit_beratung_bf_12)
    ) }
    private val WMO_SUIT   = mapOf(0 to intArrayOf(0,0,0),1 to intArrayOf(0,0,0),2 to intArrayOf(0,0,0),3 to intArrayOf(0,0,0),45 to intArrayOf(0,0,2),48 to intArrayOf(0,0,2),65 to intArrayOf(2,0,0),75 to intArrayOf(2,0,0),86 to intArrayOf(2,1,0),95 to intArrayOf(2,2,2),96 to intArrayOf(2,2,2),99 to intArrayOf(2,2,2))

    // View factory helpers
    private fun makeDizRow(diz: Int, suit: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(4); layoutParams = lp }
        row.addView(TextView(this).apply { text = "${DIZ_ICONS[diz]} ${DIZ_NAMES[diz]}"; textSize = 13f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
        row.addView(TextView(this).apply {
            text = when(suit){ 0->getString(R.string.nasenarbeit_beratung_suit_suitable); 1->getString(R.string.nasenarbeit_beratung_suit_limited); else->getString(R.string.nasenarbeit_beratung_suit_not_suitable) }
            textSize = 12f
            setTextColor(when(suit){ 0->0xFF4ade80.toInt(); 1->0xFFfbbf24.toInt(); else->0xFFf87171.toInt() })
        })
        return row
    }

    private fun makeSelectChip(label: String, onClick: () -> Unit): Chip =
        Chip(this).apply { text = label; isCheckable = false; setOnClickListener { onClick() }; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginEnd = dp(4); layoutParams = lp }

    private fun makeSeqChip(label: String, onRemove: () -> Unit): Chip =
        Chip(this).apply { text = label; isCloseIconVisible = true; isCheckable = false; setOnCloseIconClickListener { onRemove() }; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.marginEnd = dp(4); layoutParams = lp }

    private fun makeInfoText(text: String) = TextView(this).apply { this.text = text; textSize = 11f; setPadding(0, dp(4), 0, dp(4)) }

    private fun makeParamRow(label: String, value: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(8); layoutParams = lp }
        row.addView(TextView(this).apply { text = label; textSize = 12f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
        row.addView(TextView(this).apply { text = value; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) })
        return row
    }

    private fun makeBullet(icon: String, text: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(8); layoutParams = lp }
        row.addView(TextView(this).apply { this.text = icon; textSize = 14f; val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.marginEnd = dp(8); layoutParams = lp2 })
        row.addView(TextView(this).apply { this.text = text; textSize = 13f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
        return row
    }

    private fun seekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) = onChange(p)
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
    }

    override fun onDestroy() { super.onDestroy(); try { locationManager.removeUpdates(this) } catch (_: Exception) {} }
}