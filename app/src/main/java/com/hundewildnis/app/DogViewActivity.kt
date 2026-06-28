package com.hundewildnis.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.hundewildnis.app.databinding.ActivityDogViewBinding

class DogViewActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityDogViewBinding

    // Profil-ID für Reload nach Rückkehr aus HeatCycleActivity
    private var profileId: String = ""

    // ════════════════════════════════════════════════════════════════
    // LAUNCHER für HeatCycleActivity
    // Profil nach Speichern neu laden → Stats + Liste aktualisieren
    // ════════════════════════════════════════════════════════════════
    private val heatCycleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadAndRefresh()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDogViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ID merken — beim Resume per DogProfileManager neu laden
        val profile = intent.getParcelableExtra<DogProfile>("profile")
        profileId = profile?.id ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🐕 ${profile?.name ?: "Hund"}"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        profile?.let { displayProfile(it) }
    }

    override fun onResume() {
        super.onResume()
        // Profil neu laden damit Läufigkeits- und Vitalwerte-Daten
        // nach Rückkehr aus anderen Activities aktuell sind
        if (profileId.isNotEmpty()) {
            DogProfileManager.getProfile(this, profileId)?.let { fresh ->
                displayVitalSection(fresh)
                displayHeatCycleSection(fresh)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HAUPTANZEIGE (unverändert aus deiner Version)
    // ════════════════════════════════════════════════════════════════

    private fun displayProfile(p: DogProfile) {
        // Foto
        if (p.photoUri.isNotEmpty()) {
            try {
                binding.ivPhoto.setImageURI(Uri.parse(p.photoUri))
            } catch (e: Exception) {
                binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            }
        } else {
            binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        // Basis-Daten
        binding.tvName.text = p.name
        binding.tvBreed.text = p.breed
        binding.tvGender.text = p.gender
        binding.tvNeutered.text = "Kastriert: ${p.neutered}"
        binding.tvBirthDate.text = "Geboren: ${p.birthDate}"
        binding.tvOriginCountry.text = "Herkunft: ${p.originCountry.ifEmpty { "-" }}"
        binding.tvColor.text = "Farbe: ${p.color}"

        // Identifikation
        binding.tvChip.text = "Chip: ${p.chipNumber.ifEmpty { "-" }}"
        binding.tvTax.text = "Steuernummer: ${p.taxNumber.ifEmpty { "-" }}"
        binding.tvTasso.text = "TASSO: ${p.tassoNumber.ifEmpty { "-" }}"
        binding.tvPetPassport.text = "Heimtierausweis: ${p.petPassportCountry} ${p.petPassportNumber}".trim().ifEmpty { "-" }

        // Gesundheit
        binding.tvVaccination.text = "Impfstatus: ${p.vaccinationStatus.ifEmpty { "-" }}"
        binding.tvAdditionalVaccination.text = "Weitere Impfungen: ${p.additionalVaccination.ifEmpty { "-" }}"
        binding.tvDeworming.text = "Entwurmung: ${p.lastDeworming.ifEmpty { "-" }}"
        binding.tvFleaTick.text = "Floh/Zecke: ${p.lastFleaTick.ifEmpty { "-" }}"
        binding.tvWeight.text = if (p.weight.isNotEmpty()) {
            "Gewicht: ${p.weight} kg (${p.weightDate})"
        } else {
            "Gewicht: -"
        }
        binding.tvShoulderHeight.text = "Schulterhöhe: ${p.shoulderHeight.ifEmpty { "-" }} cm"
        binding.tvBodyLength.text = "Körperlänge: ${p.bodyLength.ifEmpty { "-" }} cm"
        binding.tvMedical.text = "Hinweise: ${p.medicalNotes.ifEmpty { "-" }}"

        // Kontakte
        binding.tvVet.text = "Tierarzt: ${p.veterinarian.ifEmpty { "-" }}"
        binding.tvInsurance.text = "Haftpflicht: ${p.liabilityInsurance.ifEmpty { "-" }}"
        binding.tvHealthInsurance.text = "Krankenvers.: ${p.healthInsurance.ifEmpty { "-" }}"
        binding.tvOwner.text = "Besitzer: ${p.owner.ifEmpty { "-" }}"
        binding.tvEmergency.text = "Notfall: ${p.additionalEmergencyContact.ifEmpty { "-" }}"

        // Futter & Verhalten
        binding.tvFood.text = "Futter: ${p.food.ifEmpty { "-" }}"
        binding.tvBehavior.text = "Verhalten: ${p.behavior.ifEmpty { "-" }}"

        // Vitalwerte
        displayVitalSection(p)

        // Läufigkeit
        displayHeatCycleSection(p)
    }

    // ════════════════════════════════════════════════════════════════
    // VITALWERTE — ABSCHNITT
    // ════════════════════════════════════════════════════════════════

    private fun displayVitalSection(p: DogProfile) {
        val records = VitalManager.getForDog(this, p.id)
        val shoulderCm = p.shoulderHeight.trim().toIntOrNull()

        // Tabellen-Container leeren
        binding.llVitalTable.removeAllViews()

        if (records.isEmpty()) {
            binding.tvVitalEmpty.visibility = View.VISIBLE
            binding.llVitalTable.visibility = View.GONE
        } else {
            binding.tvVitalEmpty.visibility = View.GONE
            binding.llVitalTable.visibility = View.VISIBLE

            // Tabellen-Header
            addVitalTableRow(
                binding.llVitalTable,
                listOf("Datum", "Puls", "Atem", "Temp.", "Status"),
                isHeader = true
            )

            // Max. 10 Einträge anzeigen
            records.take(10).forEach { r ->
                val status = when (r.pulseStatus(shoulderCm)) {
                    PulseStatus.NORMAL  -> "✓"
                    PulseStatus.HIGH    -> "↑"
                    PulseStatus.LOW     -> "↓"
                    PulseStatus.UNKNOWN -> "–"
                }
                val statusColor = when (r.pulseStatus(shoulderCm)) {
                    PulseStatus.NORMAL  -> Color.parseColor("#2d5016")
                    PulseStatus.HIGH    -> Color.parseColor("#B00020")
                    PulseStatus.LOW     -> Color.parseColor("#E65100")
                    PulseStatus.UNKNOWN -> Color.GRAY
                }
                addVitalTableRow(
                    binding.llVitalTable,
                    listOf(
                        r.formattedDateShort(),
                        "${r.pulse}/min",
                        r.respiration?.let { "$it/min" } ?: "–",
                        r.temperature?.let { "${"%.1f".format(it)}°C" } ?: "–",
                        status
                    ),
                    isHeader = false,
                    lastColColor = statusColor
                )
            }

            if (records.size > 10) {
                val hint = buildVitalInfoText("… und ${records.size - 10} weitere Messungen")
                binding.llVitalTable.addView(hint)
            }
        }

        // Button: Alle Messungen öffnen
        binding.btnOpenVitalList.setOnClickListener {
            val intent = Intent(this, VitalListActivity::class.java)
            intent.putExtra("dogId", p.id)
            intent.putExtra("dogName", p.name)
            startActivity(intent)
        }

        // Button: Neue Messung
        binding.btnStartPulscheck.setOnClickListener {
            val intent = Intent(this, PulscheckActivity::class.java)
            intent.putExtra("profile", p)
            startActivity(intent)
        }
    }

    private fun addVitalTableRow(
        container: LinearLayout,
        cells: List<String>,
        isHeader: Boolean,
        lastColColor: Int? = null
    ) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val weights = listOf(2f, 1.5f, 1.5f, 1.5f, 0.8f)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpToPx(2) }
            if (isHeader) {
                setBackgroundColor(if (isDark) Color.parseColor("#1B3A0A") else Color.parseColor("#e8f5e9"))
            }
        }

        cells.forEachIndexed { index, text ->
            val tv = TextView(this).apply {
                this.text = text
                textSize = if (isHeader) 11f else 12f
                if (isHeader) setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(6), dpToPx(4), dpToPx(4), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[index])
                if (index == cells.lastIndex && lastColColor != null) {
                    setTextColor(lastColColor)
                    setTypeface(null, Typeface.BOLD)
                } else {
                    setTextColor(if (isDark) Color.WHITE else Color.parseColor("#212121"))
                }
            }
            row.addView(tv)
        }
        container.addView(row)
    }

    private fun buildVitalInfoText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.GRAY)
        setPadding(dpToPx(6), dpToPx(4), dpToPx(4), dpToPx(4))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    // ════════════════════════════════════════════════════════════════
    // LÄUFIGKEIT — ABSCHNITT
    // ════════════════════════════════════════════════════════════════

    private fun displayHeatCycleSection(p: DogProfile) {
        // Nur für intakte Hündinnen anzeigen
        if (p.gender != "Hündin" || p.neutered == "Ja") {
            binding.llHeatCycleSection.visibility = View.GONE
            return
        }
        binding.llHeatCycleSection.visibility = View.VISIBLE

        val cycles = p.heatCycles

        // ── Stats-Karte ──────────────────────────────────────────
        binding.tvHeatAvg.text = HeatCycleManager.analysisLabel(cycles)

        binding.tvHeatNextDate.text =
            "Nächste Läufigkeit voraussichtlich: ${HeatCycleManager.predictedNextDateFormatted(cycles)}"

        val countdown = HeatCycleManager.daysUntilNext(cycles)
        binding.tvHeatCountdown.text = if (countdown != null) {
            HeatCycleManager.countdownLabel(cycles)
        } else {
            ""
        }

        // ── Zyklen-Liste (dynamisch) ─────────────────────────────
        binding.llCycleList.removeAllViews()

        if (cycles.isEmpty()) {
            val hint = buildCycleInfoText("Noch keine Läufigkeit eingetragen.")
            binding.llCycleList.addView(hint)
        } else {
            cycles.sortedByDescending { it.startDate }.forEach { entry ->
                binding.llCycleList.addView(buildCycleRow(entry, p.id))
            }
        }

        // ── Button: neue Läufigkeit ──────────────────────────────
        binding.btnAddHeatCycle.setOnClickListener {
            HeatCycleActivity.startForNew(
                context      = this,
                dogId        = p.id,
                cycleNumber  = HeatCycleManager.nextCycleNumber(cycles),
                launcher     = heatCycleLauncher
            )
        }

        // Button-Farbe ans Theme anpassen
        val isDark = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val accentColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        binding.btnAddHeatCycle.setTextColor(accentColor)
    }

    // ════════════════════════════════════════════════════════════════
    // HILFSFUNKTIONEN — UI-BAUSTEINE
    // ════════════════════════════════════════════════════════════════

    /**
     * Erstellt eine tippbare Zeile für einen Zyklus-Eintrag.
     * Zeigt: Zyklusnummer, Zeitraum, Dauer, Intensität, Scheinträchtigkeit.
     * Tipp → HeatCycleActivity zum Bearbeiten öffnen.
     */
    private fun buildCycleRow(entry: HeatCycleEntry, dogId: String): View {
        val isDark = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                    if (isDark) 0x33FFFFFF else 0x33000000
                ),
                null, null
            )
            setOnClickListener {
                HeatCycleActivity.startForEdit(
                    context  = this@DogViewActivity,
                    dogId    = dogId,
                    entry    = entry,
                    launcher = heatCycleLauncher
                )
            }
        }

        // Zeile 1: Zyklusnummer + Intensität-Badge
        val header = buildRowText(
            text      = "Läufigkeit #${entry.cycleNumber}  ·  ${entry.intensity}",
            bold      = true,
            textSizeSp = 14f
        )
        container.addView(header)

        // Zeile 2: Zeitraum
        val zeitraum = if (entry.endDate.isNotEmpty()) {
            "${entry.startDate} – ${entry.endDate}  (${entry.durationLabel})"
        } else if (entry.startDate.isNotEmpty()) {
            "Seit ${entry.startDate}  ·  noch aktiv"
        } else {
            "Kein Datum eingetragen"
        }
        container.addView(buildRowText(zeitraum, textSizeSp = 13f))

        // Zeile 3: Verhalten (nur wenn vorhanden)
        if (entry.behaviorSigns.isNotEmpty()) {
            container.addView(buildRowText(
                "Verhalten: ${entry.behaviorSigns.joinToString(", ")}",
                textSizeSp = 12f,
                alpha = 0.75f
            ))
        }

        // Zeile 4: Scheinträchtigkeit (nur wenn vorhanden)
        if (entry.hasPseudoPregnancy) {
            val pseudoText = if (entry.pseudoPregnancySigns.isNotEmpty()) {
                "🐾 Scheinträchtigkeit: ${entry.pseudoPregnancySigns.joinToString(", ")}"
            } else {
                "🐾 Scheinträchtigkeit beobachtet"
            }
            container.addView(buildRowText(pseudoText, textSizeSp = 12f, alpha = 0.75f))
        }

        // Trennlinie zwischen Einträgen
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 8, 0, 0) }
            setBackgroundColor(if (isDark) 0x33FFFFFF else 0x1A000000)
        }
        container.addView(divider)

        return container
    }

    private fun buildRowText(
        text: String,
        bold: Boolean = false,
        textSizeSp: Float = 14f,
        alpha: Float = 1f
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = textSizeSp
        this.alpha = alpha
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 2, 0, 2) }
    }

    private fun buildCycleInfoText(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        alpha = 0.6f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 8) }
    }

    // ════════════════════════════════════════════════════════════════

    private fun reloadAndRefresh() {
        DogProfileManager.getProfile(this, profileId)?.let { fresh ->
            displayHeatCycleSection(fresh)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}