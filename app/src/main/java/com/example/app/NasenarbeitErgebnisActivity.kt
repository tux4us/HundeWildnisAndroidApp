package com.hundewildnis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityNasenarbeitErgebnisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NasenarbeitErgebnisActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityNasenarbeitErgebnisBinding
    private lateinit var profile: DogProfile
    private lateinit var parameter: NasenarbeitParameter
    private lateinit var primaerDisziplin: NasenarbeitDisziplin
    private var scoreV = 0.0; private var scoreP = 0.0; private var scoreF = 0.0
    private var selectedDisziplin: NasenarbeitDisziplin? = null
    private var selectedBewertung = 0

    // ── FOTOS ──────────────────────────────────────────────────
    private val attachedPhotos = mutableListOf<Uri>()
    private var currentPhotoPath: String? = null

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path -> addPhoto(Uri.fromFile(File(path))) }
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris: List<Uri> -> uris.forEach { addPhoto(it) } }

    // ════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNasenarbeitErgebnisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        profile   = intent.getParcelableExtra("profile")!!
        @Suppress("DEPRECATION")
        parameter = intent.getParcelableExtra("parameter")!!
        scoreV    = intent.getDoubleExtra("scoring_v", 0.0)
        scoreP    = intent.getDoubleExtra("scoring_p", 0.0)
        scoreF    = intent.getDoubleExtra("scoring_f", 0.0)
        primaerDisziplin = NasenarbeitDisziplin.valueOf(
            intent.getStringExtra("primaer_disziplin") ?: NasenarbeitDisziplin.VERLORENSUCHE.name
        )

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "🐾 Training erfassen"

        setupEmpfehlung()
        setupDisziplinWahl()
        setupBewertung()
        setupPhotos()
        setupSaveButton()
    }

    // ── EMPFEHLUNG ────────────────────────────────────────────

    private fun setupEmpfehlung() {
        binding.tvEmpfohlen.text = "Empfohlen: ${primaerDisziplin.icon} ${primaerDisziplin.displayName}"
        binding.tvScores.text    = "Score: 🐾 ${fmt(scoreV)}  🧑 ${fmt(scoreP)}  🔍 ${fmt(scoreF)}"
        selectedDisziplin = primaerDisziplin
    }

    private fun fmt(d: Double) = if (d > -999) String.format("%.1f", d) else "❌"

    // ── DISZIPLIN ─────────────────────────────────────────────

    private fun setupDisziplinWahl() {
        val btns = listOf(
            Pair(binding.btnDizV, NasenarbeitDisziplin.VERLORENSUCHE),
            Pair(binding.btnDizP, NasenarbeitDisziplin.PERSONENSUCHE),
            Pair(binding.btnDizF, NasenarbeitDisziplin.FREISUCHE)
        )
        btns.forEach { (btn, diz) ->
            btn.text = "${diz.icon} ${diz.displayName}"
            btn.isSelected = diz == primaerDisziplin
            btn.setOnClickListener {
                selectedDisziplin = diz
                btns.forEach { (b, _) -> b.isSelected = false }
                btn.isSelected = true
                updateDisziplinFields(diz)
            }
        }
        updateDisziplinFields(primaerDisziplin)
    }

    private fun updateDisziplinFields(diz: NasenarbeitDisziplin) {
        binding.layoutSchritte.visibility =
            if (diz != NasenarbeitDisziplin.FREISUCHE) View.VISIBLE else View.GONE
        binding.layoutHa.visibility =
            if (diz == NasenarbeitDisziplin.FREISUCHE) View.VISIBLE else View.GONE
        binding.layoutGegenstaende.visibility =
            if (diz == NasenarbeitDisziplin.VERLORENSUCHE) View.VISIBLE else View.GONE
        binding.layoutPersonenfund.visibility =
            if (diz == NasenarbeitDisziplin.PERSONENSUCHE) View.VISIBLE else View.GONE
    }

    // ── BEWERTUNG ─────────────────────────────────────────────

    private fun setupBewertung() {
        val stars = listOf(binding.btnStar1, binding.btnStar2, binding.btnStar3, binding.btnStar4)
        stars.forEachIndexed { idx, btn ->
            btn.setOnClickListener {
                selectedBewertung = idx + 1
                stars.forEachIndexed { i, b ->
                    b.setImageResource(if (i <= idx) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                    b.alpha = 1.0f
                }
            }
        }
    }

    // ── FOTOS ─────────────────────────────────────────────────

    private fun setupPhotos() {
        binding.btnFotoGalerie.setOnClickListener {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnFotoKamera.setOnClickListener { checkCameraAndOpen() }
    }

    private fun checkCameraAndOpen() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 200)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 200 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else if (requestCode == 200) {
            Toast.makeText(this, "Kamera-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        val photoFile = File.createTempFile(
            "NASENARBEIT_", ".jpg",
            getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        )
        currentPhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        cameraLauncher.launch(intent)
    }

    private fun addPhoto(uri: Uri) {
        if (attachedPhotos.contains(uri)) return
        attachedPhotos.add(uri)
        renderPhotoGrid()
    }

    private fun renderPhotoGrid() {
        binding.layoutFotoGrid.removeAllViews()
        val dp56 = (56 * resources.displayMetrics.density).toInt()
        val dp4  = (4  * resources.displayMetrics.density).toInt()
        attachedPhotos.forEach { uri ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp56, dp56).also {
                    it.marginEnd = dp4; it.bottomMargin = dp4
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                setOnLongClickListener {
                    attachedPhotos.remove(uri); renderPhotoGrid(); true
                }
            }
            binding.layoutFotoGrid.addView(iv)
        }
        binding.tvFotoHint.visibility = if (attachedPhotos.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── SPEICHERN ─────────────────────────────────────────────

    private fun setupSaveButton() {
        binding.btnSpeichern.setOnClickListener { saveSession() }
        binding.btnAbbrechen.setOnClickListener { finish() }
    }

    private fun saveSession() {
        val diz = selectedDisziplin ?: run {
            Toast.makeText(this, "Bitte Disziplin wählen", Toast.LENGTH_SHORT).show()
            return
        }

        val schritte     = binding.etSchritte.text.toString().toIntOrNull()
        val ha           = binding.etHa.text.toString().toDoubleOrNull()
        val dauer        = binding.etDauer.text.toString().toIntOrNull()
        val gegenst      = binding.etGegenstaende.text.toString().toIntOrNull()
        val gegenstGes   = binding.etGegenstaendeGesamt.text.toString().toIntOrNull()
        val personenfund = binding.cbPersonenfund.isChecked
        val notizenTitel = binding.etNotizenTitel.text.toString()
        val notizen      = binding.etNotizen.text.toString()

        // Fotos in internen Speicher kopieren → persistente URIs
        val persistedUris = attachedPhotos.mapNotNull { uri ->
            try {
                val dest = File(filesDir, "nasenarbeit_photos/${UUID.randomUUID()}.jpg")
                    .also { it.parentFile?.mkdirs() }
                contentResolver.openInputStream(uri)?.use { inp ->
                    dest.outputStream().use { inp.copyTo(it) }
                }
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", dest).toString()
            } catch (e: Exception) { null }
        }

        val fortschritt = NasenarbeitManager.loadFortschrittForDog(this, profile.id)

        val session = NasenarbeitSession(
            id = UUID.randomUUID().toString(),
            dogId = profile.id,
            empfohleneDisziplin = primaerDisziplin,
            durchgefuehrteDisziplin = diz,
            scoreVerlorensuche = scoreV,
            scorePersonensuche = scoreP,
            scoreFreisuche = scoreF,
            parameter = parameter,
            levelVerlorensuche = fortschritt.levelVerlorensuche.displayName,
            levelPersonensuche = fortschritt.levelPersonensuche.displayName,
            levelFreisuche = fortschritt.levelFreisuche.displayName,
            durchgefuehrteSchritte = schritte,
            durchgefuehrteHa = ha,
            suchdauerMinuten = dauer,
            gefundeneGegenstaende = gegenst,
            gegenstaendeGesamt = gegenstGes,
            personenfundErfolgreich = if (diz == NasenarbeitDisziplin.PERSONENSUCHE) personenfund else null,
            erfolgsbewertung = selectedBewertung.coerceAtLeast(1),
            notizenTitel = notizenTitel,
            notizen = notizen,
            fotoUris = persistedUris,
            gpsLat = parameter.lat,
            gpsLon = parameter.lon
        )

        NasenarbeitManager.addSession(this, session)
        
        // PDF Export in den öffentlichen Dokumenten-Ordner
        lifecycleScope.launch(Dispatchers.IO) {
            val file = NasenarbeitPDFGenerator.generateSessionPDF(this@NasenarbeitErgebnisActivity, session, profile, saveToPublic = true)
            withContext(Dispatchers.Main) {
                if (file != null) {
                    Toast.makeText(this@NasenarbeitErgebnisActivity, "✅ Session gespeichert in Documents/HundeWildnis", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@NasenarbeitErgebnisActivity, "✅ Session gespeichert", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}