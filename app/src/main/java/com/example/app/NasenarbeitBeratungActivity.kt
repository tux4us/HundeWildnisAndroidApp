package com.hundewildnis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
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
        var flaechenPct: Double? = null,
        var keineAngabeSchr: Boolean = false,
        var keineAngabePct: Boolean = false
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

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) fetchWeather()
        else Toast.makeText(this, "Standort-Berechtigung erforderlich", Toast.LENGTH_LONG).show()
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
        supportActionBar?.title = "${profile.name} – Nasenarbeit Beratung"

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
                .setTitle("Nasenarbeit – Trainingsberatung")
                .setMessage("Für eine optimale Trainingsempfehlung benötigt die App:\n\n📍 GPS-Standort – temporär für Wetterdaten, wird nicht gespeichert\n🌡️ Wetterdaten – automatisch via Open-Meteo API\n\nAlle Daten bleiben auf deinem Gerät.")
                .setPositiveButton("Verstanden & Weiter") { _, _ ->
                    getSharedPreferences(PREFS_CONSENT, MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, true).apply()
                    requestLocationAndFetch()
                }
                .setNegativeButton("Ablehnen") { _, _ -> finish() }
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
                    Toast.makeText(this@NasenarbeitBeratungActivity, "Wetter nicht verfügbar – manuelle Eingabe", Toast.LENGTH_LONG).show()
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
            binding.tvExclusionReason.text = when {
                paramTemp > 30  -> "Temperatur über 30°C"
                paramTemp < -10 -> "Temperatur unter −10°C"
                paramBf >= 9    -> "Windstärke Beaufort $paramBf – Sturm"
                else            -> "Gewitter aktiv"
            }
            binding.layoutWetterEignung.visibility = View.GONE
            binding.cardWindDir.visibility = View.GONE
            binding.btnWetterWeiter.isEnabled = false
        } else {
            binding.cardExclusion.visibility = View.GONE
            updateWetterEignung()
            updateWindDirEignung()
            binding.layoutWetterEignung.visibility = View.VISIBLE
            binding.cardWindDir.visibility = View.VISIBLE
            binding.btnWetterWeiter.isEnabled = true
        }

        // Wind direction radio listener
        binding.rgWindDir.setOnCheckedChangeListener { _, _ ->
            paramWindDir = when (binding.rgWindDir.checkedRadioButtonId) {
                R.id.rbWindSeite -> 1
                R.id.rbWindVorn  -> 2
                else             -> 0
            }
            updateWindDirEignung()
        }

        binding.btnWetterWeiter.setOnClickListener {
            showScreen(2); buildGelaendeScreen()
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
        // Topografie
        binding.chipGroupTopoOpts.removeAllViews()
        TOPO_OPTIONS.forEachIndexed { idx, opt ->
            binding.chipGroupTopoOpts.addView(makeSelectChip(TOPO_LABELS[idx]) {
                topoSequenz.add(opt); renderTopoSel(); updateGelaendeEignung()
            })
        }
        renderTopoSel()

        // Umgebung
        binding.chipGroupUmbOpts.removeAllViews()
        UMB_OPTIONS.forEachIndexed { idx, opt ->
            binding.chipGroupUmbOpts.addView(makeSelectChip(UMB_LABELS[idx]) {
                umbSequenz.add(opt); renderUmbSel(); updateGelaendeEignung()
                bodenEntries.clear()
            })
        }
        renderUmbSel()

        // Gebiet sliders
        // Schritte: 0→50 steps of 50 = 50..3000
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
            binding.chipGroupTopoSel.addView(makeInfoText("Noch nichts gewählt …"))
        } else {
            topoSequenz.forEachIndexed { idx, v ->
                binding.chipGroupTopoSel.addView(makeSeqChip("${idx+1}. $v") {
                    topoSequenz.removeAt(idx); renderTopoSel(); updateGelaendeEignung()
                })
            }
        }
    }

    private fun renderUmbSel() {
        binding.chipGroupUmbSel.removeAllViews()
        if (umbSequenz.isEmpty()) {
            binding.chipGroupUmbSel.addView(makeInfoText("Noch nichts gewählt …"))
        } else {
            umbSequenz.forEachIndexed { idx, v ->
                binding.chipGroupUmbSel.addView(makeSeqChip("${idx+1}. $v") {
                    umbSequenz.removeAt(idx); renderUmbSel(); updateGelaendeEignung()
                    bodenEntries.clear()
                })
            }
        }
    }

    private fun updateGebietHint() {
        val levV = fortschritt.levelVerlorensuche
        val levFl = fortschritt.levelFreisuche
        val hints = mutableListOf<String>()
        if (gebietSchritte < levV.maxSchritte_V) hints.add("⚠️ Verlorensuche begrenzt auf $gebietSchritte Schritte")
        if (gebietSchritte < fortschritt.levelPersonensuche.maxSchritte_P) hints.add("⚠️ Personensuche begrenzt")
        if (gebietHa < levFl.maxHa_F) hints.add("⚠️ Freisuche begrenzt auf ${String.format("%.1f", gebietHa)} ha")
        binding.tvGebietHint.text = if (hints.isEmpty()) "✅ Gebiet ausreichend für aktuelles Level" else hints.joinToString(" · ")
    }

    private fun updateGelaendeEignung() {
        val TOPO_SUIT = mapOf("Eben" to intArrayOf(0,0,0),"Leichte Steigung" to intArrayOf(0,0,0),"Steiles Gelände" to intArrayOf(1,1,0),"Kuppe" to intArrayOf(0,0,0),"Hanglage" to intArrayOf(0,0,0),"Mulde" to intArrayOf(0,0,0),"Wechselndes Relief" to intArrayOf(0,0,1),"Felsabbrüche / Steilwände" to intArrayOf(1,1,2))
        val UMB_SUIT  = mapOf("Offenes Gelände" to intArrayOf(0,0,0),"Waldgebiet" to intArrayOf(0,0,0),"Grünland" to intArrayOf(0,0,0),"Urbanes Umfeld" to intArrayOf(0,0,1),"Industriegelände" to intArrayOf(0,0,1),"Dörfliche Bebauung" to intArrayOf(0,0,1),"Parkanlage" to intArrayOf(0,0,0),"Mischgebiet" to intArrayOf(0,0,1))
        val suit = IntArray(3)
        (topoSequenz + umbSequenz).forEach { item ->
            (TOPO_SUIT[item] ?: UMB_SUIT[item] ?: intArrayOf(0,0,0)).forEachIndexed { i, s -> if (s > suit[i]) suit[i] = s }
        }
        binding.layoutGelaendeEignung.removeAllViews()
        if (topoSequenz.isNotEmpty() || umbSequenz.isNotEmpty()) {
            val card = MaterialCardView(this).apply { radius = 12f * resources.displayMetrics.density; cardElevation = 2f * resources.displayMetrics.density }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),dp(14),dp(14),dp(14)) }
            val title = TextView(this).apply { text = "Geländebewertung"; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0,0,0,dp(10)) }
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
        root.addView(TextView(this).apply { text = "Bodentyp"; textSize = 11f; setPadding(0,0,0,dp(4)) })
        val spBoden = Spinner(this)
        val btLabels = BODEN_TYPEN.map { it.second }
        spBoden.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, btLabels)
        spBoden.setSelection(0)
        val lpSpin = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lpSpin.bottomMargin = dp(10); spBoden.layoutParams = lpSpin
        root.addView(spBoden)

        // Bodenzustand Spinner
        root.addView(TextView(this).apply { text = "Bodenzustand"; textSize = 11f; setPadding(0,0,0,dp(4)) })
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
        colSchr.addView(TextView(this).apply { text = "Schritte · Verloren + Person"; textSize = 10f; setPadding(0,0,0,dp(4)) })
        val etSchr = EditText(this).apply { hint = "z.B. 200"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; textSize = 12f }
        colSchr.addView(etSchr)
        val cbSchr = CheckBox(this).apply { text = "Keine Angabe"; textSize = 11f }
        colSchr.addView(cbSchr)
        row.addView(colSchr)
        // Prozent
        val colPct = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 }
        colPct.addView(TextView(this).apply { text = "Flächenanteil % · Freisuche"; textSize = 10f; setPadding(0,0,0,dp(4)) })
        val etPct = EditText(this).apply { hint = "z.B. 30"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; textSize = 12f }
        colPct.addView(etPct)
        val cbPct = CheckBox(this).apply { text = "Keine Angabe"; textSize = 11f }
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

        cbSchr.setOnCheckedChangeListener { _, checked -> etSchr.isEnabled = !checked; entry.keineAngabeSchr = checked; if(checked) entry.laengeSchritte = null; updateBodenEignung(idx, tvEignung) }
        cbPct.setOnCheckedChangeListener  { _, checked -> etPct.isEnabled  = !checked; entry.keineAngabePct  = checked; if(checked) entry.flaechenPct  = null; updateBodenEignung(idx, tvEignung) }

        etSchr.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { entry.laengeSchritte = s.toString().toIntOrNull(); updateBodenEignung(idx, tvEignung) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        etPct.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { entry.flaechenPct = s.toString().toDoubleOrNull(); updateBodenEignung(idx, tvEignung) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        spBoden.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                entry.bodenTyp = BODEN_TYPEN[pos].first
                // Disable matschig if needed
                val noMatsch = NO_MATSCHIG.contains(entry.bodenTyp)
                val allZustand = BODEN_ZUSTAND.map { if (noMatsch && it.first == "matschig") "– nicht verfügbar –" else it.second }
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
            Triple("🐾 Verlorensuche", fortschritt.sessionsVerlorensuche, fortschritt.levelVerlorensuche),
            Triple("🧑 Personensuche", fortschritt.sessionsPersonensuche, fortschritt.levelPersonensuche),
            Triple("🔍 Freisuche",     fortschritt.sessionsFreisuche,     fortschritt.levelFreisuche)
        ).forEach { (label, sessions, level) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(8); layoutParams = lp }
            row.addView(TextView(this).apply { text = label; textSize = 13f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
            row.addView(TextView(this).apply { text = "${level.displayName} · $sessions Sessions"; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD) })
            binding.layoutLevelDisplay.addView(row)
        }

        // Letzte Einheit
        binding.rgLetzte.setOnCheckedChangeListener { _, id ->
            letzteEinheit = when(id) {
                R.id.rbHeute      -> "Heute"
                R.id.rb7Tage      -> "Innerhalb 7 Tage"
                R.id.rb14Tage     -> "Innerhalb 14 Tage"
                R.id.rbNochKeine  -> "Noch keine Trainingseinheit"
                else              -> "Gestern"
            }
            // Leistungsdaten auf 0 setzen wenn noch kein Training stattgefunden hat
            if (letzteEinheit == "Noch keine Trainingseinheit") {
                perfV = 0; perfP = 0; perfFl = 0.0
                binding.sbPerfV.progress  = 0
                binding.sbPerfP.progress  = 0
                binding.sbPerfFl.progress = 0
                binding.tvPerfV.text  = "0 Schritte"
                binding.tvPerfP.text  = "0 Schritte"
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
                R.id.rbSehr   -> "Sehr motiviert"
                R.id.rbExtrem -> "Extrem motiviert"
                else          -> "Normal motiviert"
            }
        }

        // Perf sliders – Schritte 0..200 * 10 = 0..2000
        binding.sbPerfV.setOnSeekBarChangeListener(seekListener { perfV = it * 10; binding.tvPerfV.text = "$perfV Schritte" })
        binding.sbPerfP.setOnSeekBarChangeListener(seekListener { perfP = it * 10; binding.tvPerfP.text = "$perfP Schritte" })
        binding.sbPerfFl.setOnSeekBarChangeListener(seekListener { perfFl = it * 0.1; binding.tvPerfFl.text = String.format(Locale.GERMAN,"%.1f ha", perfFl) })

        // Auto-select "Noch keine" if no sessions exist yet → Leistungsdaten sperren
        if (fortschritt.gesamtSessions == 0) {
            binding.rgLetzte.check(R.id.rbNochKeine)
            letzteEinheit = "Noch keine Trainingseinheit"
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
        binding.tvPerfV.text  = if (perfV > 0) "$perfV Schritte" else "0 Schritte"
        binding.tvPerfP.text  = if (perfP > 0) "$perfP Schritte" else "0 Schritte"
        binding.tvPerfFl.text = if (perfFl > 0.0) String.format(Locale.GERMAN, "%.1f ha", perfFl) else "0.0 ha"

        binding.btnTrainingWeiter.setOnClickListener {
            // Recommendation triggered BEFORE finalizing screen data to auto-set RadioButtons
            val result = NasenarbeitScorer.calculate(buildParam(), fortschritt)
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
        scoringResult = NasenarbeitScorer.calculate(param, fortschritt)
        val result = scoringResult!!

        // Block 1
        if (result.isHardExcluded) {
            binding.tvEmpfehlungDiz.text = "Training nicht möglich"
            binding.tvEmpfehlungGrund.text = result.hardExclusionReason
            binding.tvEmpfehlungScore.text = ""
            binding.btnTrainingStarten.isEnabled = false
        } else {
            val p = result.primaer
            binding.tvEmpfehlungDiz.text = "${p.icon} ${p.displayName}"
            binding.tvEmpfehlungGrund.text = result.begruendung
            binding.tvEmpfehlungScore.text = "Score: ${DIZ_ICONS[0]}${fmt1(result.scores[0])}  ${DIZ_ICONS[1]}${fmt1(result.scores[1])}  ${DIZ_ICONS[2]}${fmt1(result.scores[2])}"
            if (result.empfohlen.size > 1) {
                binding.tvEmpfehlungAlt.visibility = View.VISIBLE
                binding.tvEmpfehlungAlt.text = "Auch geeignet: " + result.empfohlen.filter { it != p }.joinToString(", ") { "${it.icon} ${it.displayName}" }
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

        val isFirstSession = letzteEinheit == "Noch keine Trainingseinheit" ||
                (perfV <= 0 && perfP <= 0 && perfFl <= 0)
        val starterHint = if (isFirstSession) " (Startwert)" else ""

        val recStr = when (result.primaer) {
            NasenarbeitDisziplin.VERLORENSUCHE -> "$recV Schritte$starterHint"
            NasenarbeitDisziplin.PERSONENSUCHE -> "$recP Schritte$starterHint"
            NasenarbeitDisziplin.FREISUCHE     -> String.format(Locale.GERMAN, "%.2f ha", recFl) + starterHint
        }

        val spuralter = calcSpuralter(lev.ordinal, param.beaufort, param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: "")

        listOf(
            "Empfohlene Länge/Fläche" to recStr,
            "Schwierigkeit" to lev.schwierigkeit,
            if (result.primaer != NasenarbeitDisziplin.FREISUCHE) "Spuralter" to spuralter else null,
            "Letzte Einheit" to letzteEinheit,
            "Tagesverfassung" to verfassung,
            "Steigerungsfaktor" to "×${String.format(Locale.GERMAN,"%.2f", mult)}"
        ).filterNotNull().forEach { (label, value) -> binding.layoutParamGrid.addView(makeParamRow(label, value)) }
    }

    private fun buildBlock3(param: NasenarbeitParameter) {
        binding.layoutVorbereitung.removeAllViews()
        listOf(
            "Leine, Geschirr und Suchleine prüfen",
            "Ausreichend Trinkwasser für den Hund",
            "Belohnungsmittel vorbereiten"
        ).forEach { binding.layoutVorbereitung.addView(makeBullet("✅", it)) }
        val zustand = param.gelaendeAbschnitte.firstOrNull()?.bodenZustand ?: ""
        if (zustand in listOf("gefroren","schnee_l","schnee_m","schnee_h"))
            binding.layoutVorbereitung.addView(makeBullet("⚠️", "Pfotenschutz prüfen – Untergrund gefroren/Schnee"))
        if (param.beaufort >= 6)
            binding.layoutVorbereitung.addView(makeBullet("⚠️", "Starker Wind – Windrichtung für Spurlegung beachten"))
        if (param.temperaturCelsius > 25)
            binding.layoutVorbereitung.addView(makeBullet("⚠️", "Hitze – Trinkpausen planen, schattige Bereiche bevorzugen"))
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
            GelaendeAbschnitt(umgebung = name, bodenTyp = e.bodenTyp, bodenZustand = e.bodenZustand, laengeSchritte = e.laengeSchritte, flaechenanteilProzent = e.flaechenPct)
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
            "Ergebnisse im Tagebuch erfassen: Länge, Funde, Dauer",
            "War die Einheit zu einfach? → Nächste Session +${(lev.steigerung * 100).toInt()}%",
            "Schwierigkeiten? → Steigerung pausieren"
        ).forEach { binding.layoutNachTraining.addView(makeBullet("📊", it)) }
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
            0 -> "${profile.name} – Nasenarbeit"
            1 -> "Daten zum Wetter"
            2 -> "Daten zum Gelände"
            3 -> "Daten zum Boden"
            4 -> "Daten zum Trainingsstand"
            5 -> "Trainingsempfehlung"
            else -> "Nasenarbeit"
        }
    }

    private fun setupScreenNavigation() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (currentScreen > 1) { showScreen(currentScreen - 1); return true }
        finish(); return true
    }

    private fun resetAndRestart() {
        topoSequenz.clear(); umbSequenz.clear(); bodenEntries.clear()
        scoringResult = null; currentB5Tab = "ok"
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
    private fun wmoToLabel(c: Int) = when(c) { 0->"Klarer Himmel"; 1->"Überwiegend klar"; 2->"Leicht bewölkt"; 3->"Bewölkt"; 45->"Nebel"; 48->"Gefrierender Nebel"; in 51..55->"Nieselregen"; in 61..65->"Regen"; in 71..75->"Schneefall"; 95->"Gewitter"; 96,99->"Gewitter m.Hagel"; else->"Wechselhaft" }

    private fun calcSpuralter(lev: Int, bf: Int, zustand: String): String {
        val isFeucht = zustand in listOf("feucht","nass","stehend","matschig","schnee_l","schnee_m","schnee_h")
        val isStrong = bf >= 6
        return when(lev) {
            0 -> "2–10 Min."
            1 -> "10–30 Min."
            2 -> if (isFeucht && !isStrong) "30–120 Min." else "30–90 Min."
            else -> if (isStrong) "90 Min.–12h" else if (isFeucht) "2h–24h" else "2h–18h"
        }
    }

    private fun fmt1(d: Double) = if (d > -999) String.format(Locale.GERMAN,"%.1f", d) else "❌"
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private val SUIT_SHORT = arrayOf("✅","⚠️","❌")
    private val BF_LABELS  = arrayOf("Windstille","Leiser Zug","Leichte Brise","Schwache Brise","Mäßige Brise","Frische Brise","Starker Wind","Steifer Wind","Stürmischer Wind","Sturm","Schwerer Sturm","Orkanartiger Sturm","Orkan")
    private val WMO_SUIT   = mapOf(0 to intArrayOf(0,0,0),1 to intArrayOf(0,0,0),2 to intArrayOf(0,0,0),3 to intArrayOf(0,0,0),45 to intArrayOf(0,0,2),48 to intArrayOf(0,0,2),65 to intArrayOf(2,0,0),75 to intArrayOf(2,0,0),86 to intArrayOf(2,1,0),95 to intArrayOf(2,2,2),96 to intArrayOf(2,2,2),99 to intArrayOf(2,2,2))

    // View factory helpers
    private fun makeDizRow(diz: Int, suit: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL; val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.bottomMargin = dp(4); layoutParams = lp }
        row.addView(TextView(this).apply { text = "${DIZ_ICONS[diz]} ${DIZ_NAMES[diz]}"; textSize = 13f; val lp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); layoutParams = lp2 })
        row.addView(TextView(this).apply {
            text = when(suit){ 0->"✅ Geeignet"; 1->"⚠️ Eingeschränkt"; else->"❌ Nicht geeignet" }
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