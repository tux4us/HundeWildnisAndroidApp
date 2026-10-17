package com.hundewildnis.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.hundewildnis.app.databinding.ActivityTimelineBinding
import com.hundewildnis.app.databinding.ItemTimelineEventBinding
import com.hundewildnis.app.databinding.ItemTimelineHeaderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Timeline eines Hundes: Training, Nasenarbeit, Spiel & Spaß, Intervall-Timer,
 * Vitalwerte, Läufigkeit und Beginn der gemeinsamen Zeit in einer Liste,
 * neueste Einträge zuerst, mit Monatstrennern und Filtern (Kategorie, Zeitraum).
 *
 * Es wird nichts gespeichert; die Liste wird bei jedem onResume aus den
 * vorhandenen Daten neu aufgebaut. Angezeigt werden zunächst PAGE_SIZE Einträge,
 * beim Scrollen werden weitere nachgeladen.
 */
class TimelineActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityTimelineBinding
    private lateinit var profile: DogProfile
    private lateinit var adapter: TimelineAdapter

    private var allEvents: List<TimelineEvent> = emptyList()
    private var filtered: List<TimelineEvent> = emptyList()
    private val selectedCategories = mutableSetOf<TimelineCategory>()
    private var rangeDays: Int? = null
    private var visibleLimit = PAGE_SIZE

    private val heatLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* onResume lädt die Daten ohnehin neu */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimelineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val loadedProfile = intent.getParcelableExtra<DogProfile>("profile")
        if (loadedProfile == null) {
            finish()
            return
        }
        profile = loadedProfile

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.timeline_title_format, profile.name)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.btnAddEntry.setOnClickListener {
            startActivity(Intent(this, TimelineEntryActivity::class.java).putExtra("profile", profile))
        }

        binding.btnExport.setOnClickListener { showExportDialog() }

        setupRecyclerView()
        setupCategoryChips()
        setupRangeChips()
    }

    override fun onResume() {
        super.onResume()
        if (::profile.isInitialized) loadEvents()
    }

    // ════════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════════

    private fun setupRecyclerView() {
        adapter = TimelineAdapter(this) { event -> openEvent(event) }
        val layoutManager = LinearLayoutManager(this)
        binding.rvTimeline.layoutManager = layoutManager
        binding.rvTimeline.adapter = adapter
        binding.rvTimeline.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 5 && visibleLimit < filtered.size) {
                    // Adapter-Änderungen sind innerhalb des Scroll-Callbacks nicht erlaubt
                    recyclerView.post {
                        visibleLimit += PAGE_SIZE
                        showRows()
                    }
                }
            }
        })
    }

    private fun setupCategoryChips() {
        for (category in TimelineCategory.values().filter { it.filterable }) {
            val chip = Chip(this).apply {
                text = "${category.icon} ${category.label(this@TimelineActivity)}"
                isCheckable = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedCategories.add(category) else selectedCategories.remove(category)
                    applyFilter()
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    private fun setupRangeChips() {
        val ranges = listOf(
            R.string.timeline_range_all to null,
            R.string.timeline_range_30d to 30,
            R.string.timeline_range_6m to 183,
            R.string.timeline_range_12m to 365
        )
        for ((labelRes, days) in ranges) {
            val chip = Chip(this).apply {
                text = getString(labelRes)
                isCheckable = true
                id = View.generateViewId()
                tag = days
            }
            binding.chipGroupRange.addView(chip)
            if (days == null) chip.isChecked = true
        }
        binding.chipGroupRange.setOnCheckedStateChangeListener { group, checkedIds ->
            val checked = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            rangeDays = checked?.tag as? Int
            applyFilter()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DATEN
    // ════════════════════════════════════════════════════════════════

    private fun loadEvents() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.Default) {
                TimelineRepository.load(this@TimelineActivity, profile.id)
            }
            allEvents = events
            updateSummary()
            applyFilter()
        }
    }

    private fun updateSummary() {
        val counts = TimelineCategory.values()
            .filter { it.filterable }
            .map { it to allEvents.count { e -> e.category == it } }
            .filter { it.second > 0 }

        val total = counts.sumOf { it.second }
        binding.tvSummaryTitle.text = getString(R.string.timeline_summary_title_format, total)
        binding.tvSummary.text = if (counts.isEmpty()) {
            getString(R.string.timeline_summary_none)
        } else {
            counts.joinToString("  ·  ") { (cat, n) -> "${cat.label(this)}: $n" }
        }
    }

    private fun applyFilter() {
        val cutoff = rangeDays?.let { System.currentTimeMillis() - it * DAY_MS }
        filtered = allEvents.filter { e ->
            val categoryOk = selectedCategories.isEmpty() ||
                e.category in selectedCategories ||
                e.category == TimelineCategory.START
            val rangeOk = cutoff == null || e.timestamp >= cutoff
            categoryOk && rangeOk
        }
        visibleLimit = PAGE_SIZE
        showRows()
        binding.rvTimeline.scrollToPosition(0)
    }

    private fun showRows() {
        val rows = buildRows(filtered.take(visibleLimit))
        adapter.submit(rows)
        val empty = filtered.isEmpty()
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvTimeline.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun buildRows(events: List<TimelineEvent>): List<TimelineRow> {
        val locale = resources.configuration.locales.get(0)
        val monthFormat = SimpleDateFormat("LLLL yyyy", locale)
        val cal = Calendar.getInstance()
        val rows = mutableListOf<TimelineRow>()
        var lastKey = -1
        for (e in events) {
            cal.timeInMillis = e.timestamp
            val key = cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            if (key != lastKey) {
                val label = monthFormat.format(Date(e.timestamp))
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
                rows.add(TimelineRow.Header(label))
                lastKey = key
            }
            rows.add(TimelineRow.Entry(e))
        }
        return rows
    }

    // ════════════════════════════════════════════════════════════════
    // EXPORT (CSV / PDF) — exportiert die aktuelle Filterauswahl
    // ════════════════════════════════════════════════════════════════

    private fun showExportDialog() {
        if (filtered.isEmpty()) {
            Toast.makeText(this, getString(R.string.timeline_export_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf(
            getString(R.string.timeline_export_option_csv),
            getString(R.string.timeline_export_option_pdf)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.timeline_export_dialog_title, filtered.size))
            .setItems(options) { _, which -> runExport(csv = which == 0) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun filterDescription(): String {
        val rangeText = getString(
            when (rangeDays) {
                30 -> R.string.timeline_range_30d
                183 -> R.string.timeline_range_6m
                365 -> R.string.timeline_range_12m
                else -> R.string.timeline_range_all
            }
        )
        val categoryText = if (selectedCategories.isEmpty()) {
            getString(R.string.timeline_export_all_categories)
        } else {
            TimelineCategory.values().filter { it in selectedCategories }.joinToString(", ") { it.label(this) }
        }
        return getString(R.string.timeline_export_filter_format, rangeText, categoryText)
    }

    private fun runExport(csv: Boolean) {
        val events = filtered.toList()
        val exportProfile = DogProfileManager.getProfile(this, profile.id) ?: profile
        val filterText = filterDescription()

        Toast.makeText(this, getString(R.string.timeline_export_working), Toast.LENGTH_SHORT).show()
        binding.btnExport.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (csv) TimelineCsvExporter.export(this@TimelineActivity, exportProfile, events)
                    else TimelinePdfExporter.export(this@TimelineActivity, exportProfile, events, filterText)
                }
            }
            binding.btnExport.isEnabled = true
            result
                .onSuccess { file -> shareFile(file, if (csv) "text/csv" else "application/pdf") }
                .onFailure { e ->
                    e.printStackTrace()
                    Toast.makeText(
                        this@TimelineActivity,
                        getString(R.string.timeline_export_error, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun shareFile(file: java.io.File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.timeline_export_share)))
    }

    // ════════════════════════════════════════════════════════════════
    // NAVIGATION ZUR DETAILANSICHT
    // ════════════════════════════════════════════════════════════════

    private fun openEvent(event: TimelineEvent) {
        when (event.category) {
            TimelineCategory.TRAINING -> startActivity(
                Intent(this, TrainingDetailActivity::class.java).apply {
                    putExtra("profile", profile)
                    putExtra("category_id", event.payload.toIntOrNull() ?: return)
                }
            )
            TimelineCategory.NASENARBEIT -> {
                val session = NasenarbeitManager.loadSessions(this).find { it.id == event.payload } ?: return
                startActivity(
                    Intent(this, NasenarbeitSessionDetailActivity::class.java).apply {
                        putExtra("session", session)
                        putExtra("profile", profile)
                    }
                )
            }
            TimelineCategory.SPIEL -> {
                val (aktivitaetId, ort) = event.payload.split("|").let {
                    it.getOrElse(0) { "" } to it.getOrElse(1) { "" }
                }
                startActivity(
                    Intent(this, SpielSpassDetailActivity::class.java).apply {
                        putExtra("profile", profile)
                        putExtra("aktivitaetId", aktivitaetId)
                        putExtra("ort", ort)
                    }
                )
            }
            TimelineCategory.INTERVALL -> startActivity(
                Intent(this, IntervalTimerHistoryActivity::class.java).apply {
                    putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_ID, profile.id)
                    putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_NAME, profile.name)
                }
            )
            TimelineCategory.VITAL -> startActivity(
                Intent(this, VitalDetailActivity::class.java).apply {
                    putExtra("recordId", event.payload)
                    putExtra("dogId", profile.id)
                    putExtra("dogName", profile.name)
                }
            )
            TimelineCategory.HEAT -> {
                val fresh = DogProfileManager.getProfile(this, profile.id) ?: return
                val entry = fresh.heatCycles.find { it.id == event.payload } ?: return
                HeatCycleActivity.startForEdit(this, profile.id, entry, heatLauncher)
            }
            TimelineCategory.EIGENE -> startActivity(
                Intent(this, TimelineEntryActivity::class.java).apply {
                    putExtra("profile", profile)
                    putExtra("entry_id", event.payload)
                }
            )
            TimelineCategory.START -> Unit
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

// ════════════════════════════════════════════════════════════════
// ADAPTER
// Zwei Zeilentypen: Monatskopf und Eintrag. Die vertikale Linie mit
// Punkt ist Teil des Item-Layouts (kein ItemDecoration nötig).
// ════════════════════════════════════════════════════════════════

class TimelineAdapter(
    private val context: android.content.Context,
    private val onClick: (TimelineEvent) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<TimelineRow> = emptyList()

    private val locale: Locale = context.resources.configuration.locales.get(0)
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale)

    fun submit(newRows: List<TimelineRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is TimelineRow.Header -> TYPE_HEADER
        is TimelineRow.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemTimelineHeaderBinding.inflate(inflater, parent, false))
        } else {
            EntryHolder(ItemTimelineEventBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TimelineRow.Header -> (holder as HeaderHolder).binding.tvMonth.text = row.label
            is TimelineRow.Entry -> bindEntry((holder as EntryHolder).binding, row.event)
        }
    }

    private fun bindEntry(b: ItemTimelineEventBinding, e: TimelineEvent) {
        val accent = ContextCompat.getColor(
            context, StartGroupColors.forGroup(context, e.category.colorGroup).accentColorRes
        )
        ViewCompat.setBackgroundTintList(b.dotView, ColorStateList.valueOf(accent))

        b.tvIcon.text = e.icon ?: e.category.icon
        b.tvTitle.text = e.title
        b.tvSubtitle.text = e.subtitle
        b.tvSubtitle.visibility = if (e.subtitle.isEmpty()) View.GONE else View.VISIBLE

        if (e.photoUri.isEmpty()) {
            b.ivPhoto.setImageDrawable(null)
            b.ivPhoto.visibility = View.GONE
        } else {
            b.ivPhoto.visibility = View.VISIBLE
            // Fehlt die Datei (z. B. Foto gelöscht), erscheint ein Platzhalter
            b.ivPhoto.load(Uri.parse(e.photoUri)) {
                error(R.drawable.ic_dog_placeholder)
            }
        }

        val date = Date(e.timestamp)
        b.tvDate.text = if (e.dateText != null) {
            e.dateText
        } else if (e.hasTime) {
            context.getString(
                R.string.timeline_date_time_format, dateFormat.format(date), timeFormat.format(date)
            )
        } else {
            dateFormat.format(date)
        }

        val clickable = e.category != TimelineCategory.START
        b.card.isClickable = clickable
        b.card.setOnClickListener(if (clickable) View.OnClickListener { onClick(e) } else null)
    }

    class HeaderHolder(val binding: ItemTimelineHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class EntryHolder(val binding: ItemTimelineEventBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ENTRY = 1
    }
}
