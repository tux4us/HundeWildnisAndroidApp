package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hundewildnis.app.databinding.ActivityCalendarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ════════════════════════════════════════════════════════════════
// AKTIVITÄTSKALENDER – Hauptscreen
// Datei: ActivityCalendarActivity.kt
//
// Monatsansicht mit absolvierten Trainingstagen, Lückenanalyse,
// Vorausplanung und 7-Tage-Wettereignungsampel.
// ════════════════════════════════════════════════════════════════

class ActivityCalendarActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityCalendarBinding

    private var profiles: List<DogProfile> = emptyList()
    private var selectedDog: DogProfile? = null

    private var displayedMonth: Calendar = Calendar.getInstance()
    private var allTrainingSessions: List<TrainingSession> = emptyList()
    private var allProgress: List<TrainingProgress> = emptyList()
    private var allPlanned: List<PlannedSession> = emptyList()
    private var weekForecast: List<CalendarDayForecast> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadProfiles()
        setupDogSelector()
        setupMonthNavigation()
        setupWeekAheadCard()

        loadAllData()
    }

    override fun onResume() {
        super.onResume()
        loadAllData()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ════════════════════════════════════════════════════════════
    // SETUP
    // ════════════════════════════════════════════════════════════

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.calendar_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun loadProfiles() {
        profiles = DogProfileManager.getAllProfiles(this)
        selectedDog = profiles.firstOrNull()
        binding.tvNoProfiles.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        binding.contentGroup.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupDogSelector() {
        if (profiles.size <= 1) {
            binding.rowDogSelector.visibility = View.GONE
            return
        }
        binding.rowDogSelector.visibility = View.VISIBLE
        val names = profiles.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCalendarDog.adapter = adapter

        binding.spinnerCalendarDog.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedDog = profiles.getOrNull(pos)
                loadAllData()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupMonthNavigation() {
        binding.btnPrevMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, -1)
            renderMonthView()
        }
        binding.btnNextMonth.setOnClickListener {
            displayedMonth.add(Calendar.MONTH, 1)
            renderMonthView()
        }
        binding.btnPlanSession.setOnClickListener {
            showPlanSessionDialog(Calendar.getInstance())
        }
    }

    private fun setupWeekAheadCard() {
        fetchWeekForecast()
    }

    // ════════════════════════════════════════════════════════════
    // DATEN LADEN
    // ════════════════════════════════════════════════════════════

    private fun loadAllData() {
        loadProfiles()
        val dog = selectedDog ?: return

        allTrainingSessions = TrainingManager.getAllSessions(this)
        allProgress         = TrainingManager.getAllProgress(this)

        // Geplante Sessions mit tatsächlichen Trainings abgleichen
        ActivityCalendarManager.syncCompletionStatus(this, allTrainingSessions)
        allPlanned = ActivityCalendarManager.getPlannedForDog(this, dog.id)

        renderMonthView()
        renderNeglectedCategories()
    }

    // ════════════════════════════════════════════════════════════
    // MONATSANSICHT
    // ════════════════════════════════════════════════════════════

    private fun renderMonthView() {
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvMonthLabel.text = monthFmt.format(displayedMonth.time).replaceFirstChar { it.uppercase() }

        binding.calendarGrid.removeAllViews()

        val dog = selectedDog ?: return
        val cal = displayedMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)

        // Wochentags-Header (Mo–So)
        val weekdayLabels = listOf(
            getString(R.string.calendar_weekday_mo),
            getString(R.string.calendar_weekday_tu),
            getString(R.string.calendar_weekday_we),
            getString(R.string.calendar_weekday_th),
            getString(R.string.calendar_weekday_fr),
            getString(R.string.calendar_weekday_sa),
            getString(R.string.calendar_weekday_su)
        )
        weekdayLabels.forEach { label ->
            binding.calendarGrid.addView(buildWeekdayHeader(label))
        }

        // Leerzellen bis zum ersten Wochentag (Montag = Spalte 0)
        var firstDayCol = cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (firstDayCol < 0) firstDayCol += 7
        repeat(firstDayCol) {
            binding.calendarGrid.addView(buildEmptyCell())
        }

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dogSessions = allTrainingSessions.filter { it.dogId == dog.id }
        val dogPlanned  = allPlanned

        for (day in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dayStart = PlannedSession.startOfDay(cal.timeInMillis)
            val dayEnd   = dayStart + 24L * 60 * 60 * 1000

            val sessionsThisDay = dogSessions.filter { it.date in dayStart until dayEnd }
            val plannedThisDay  = dogPlanned.filter {
                PlannedSession.startOfDay(it.plannedDateMillis) == dayStart
            }
            val isOverdue = sessionsThisDay.isEmpty() &&
                    isOverdueDay(dayStart, dogSessions)

            binding.calendarGrid.addView(
                buildDayCell(day, dayStart, sessionsThisDay, plannedThisDay, isOverdue)
            )
        }
    }

    /** Ein Tag gilt als "überfällig", wenn er in der Vergangenheit liegt,
     *  selbst kein Training stattfand, und seit dem letzten Training davor
     *  bereits 3 oder mehr Tage vergangen waren. */
    private fun isOverdueDay(dayStart: Long, sessions: List<TrainingSession>): Boolean {
        val todayStart = PlannedSession.startOfDay(System.currentTimeMillis())
        if (dayStart >= todayStart) return false

        val lastBefore = sessions.filter { it.date < dayStart }.maxByOrNull { it.date } ?: return false
        val gapDays = (dayStart - lastBefore.date) / (24 * 60 * 60 * 1000)
        return gapDays >= 3
    }

    private fun buildWeekdayHeader(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = android.view.Gravity.CENTER
        setTextColor(Color.parseColor("#888888"))
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(2, 8, 2, 8)
        }
    }

    private fun buildEmptyCell(): View = View(this).apply {
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0; height = (40 * resources.displayMetrics.density).toInt()
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        }
    }

    private fun buildDayCell(
        day: Int,
        dayStart: Long,
        sessions: List<TrainingSession>,
        planned: List<PlannedSession>,
        isOverdue: Boolean
    ): View {
        val dp = resources.displayMetrics.density
        val isToday = PlannedSession.isSameDay(dayStart, System.currentTimeMillis())
        val isFuture = dayStart > PlannedSession.startOfDay(System.currentTimeMillis())

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0; height = (44 * dp).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(2, 2, 2, 2)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 6 * dp
                setColor(
                    when {
                        isToday -> Color.parseColor("#2d5016")
                        else -> Color.parseColor("#2A2A2A")
                    }
                )
                if (isToday) {
                    setStroke((1.5f * dp).toInt(), Color.parseColor("#8FC357"))
                }
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showDayDetailDialog(dayStart, sessions, planned)
            }
        }

        cell.addView(TextView(this).apply {
            text = day.toString()
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setTextColor(if (isFuture) Color.parseColor("#AAAAAA") else Color.WHITE)
        })

        // Indikator-Zeile: farbige Punkte für absolvierte Kategorien / Planungs-Symbol
        val indicatorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }
        sessions.take(3).forEach { session ->
            indicatorRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams((5 * dp).toInt(), (5 * dp).toInt()).also {
                    it.marginStart = (1 * dp).toInt(); it.marginEnd = (1 * dp).toInt()
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(categoryColor(session.category))
                }
            })
        }
        if (planned.isNotEmpty() && sessions.isEmpty()) {
            indicatorRow.addView(TextView(this).apply {
                text = "📅"
                textSize = 8f
            })
        }
        if (isOverdue) {
            indicatorRow.addView(TextView(this).apply {
                text = "⚠️"
                textSize = 8f
            })
        }
        cell.addView(indicatorRow)

        return cell
    }

    private fun categoryColor(category: TrainingCategory): Int {
        val palette = listOf(
            "#8FC357", "#5C9EAD", "#C9A227", "#B5566F", "#7A6FB0",
            "#4F8FCC", "#D88A3F", "#6FAE6F", "#C75C5C", "#5FA8A8"
        )
        return Color.parseColor(palette[category.id % palette.size])
    }

    // ════════════════════════════════════════════════════════════
    // TAG-DETAIL-DIALOG
    // ════════════════════════════════════════════════════════════

    private fun showDayDetailDialog(
        dayStart: Long,
        sessions: List<TrainingSession>,
        planned: List<PlannedSession>
    ) {
        val dateFmt = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault())
        val title = dateFmt.format(Date(dayStart)).replaceFirstChar { it.uppercase() }

        val msg = StringBuilder()
        if (sessions.isNotEmpty()) {
            msg.append(getString(R.string.activity_calendar_completed_header)).append("\n")
            sessions.forEach { s ->
                msg.append(getString(R.string.activity_calendar_session_line, s.category.displayName(this), s.getEffortText(), s.getSuccessText())).append("\n")
            }
            msg.append("\n")
        }
        if (planned.isNotEmpty()) {
            msg.append(getString(R.string.activity_calendar_planned_header)).append("\n")
            planned.forEach { p ->
                val status = if (p.completed) getString(R.string.activity_calendar_status_done) else getString(R.string.activity_calendar_status_open)
                msg.append(getString(R.string.activity_calendar_planned_line, p.category().displayName(this), p.plannedDurationMinutes, status)).append("\n")
            }
        }
        if (sessions.isEmpty() && planned.isEmpty()) {
            msg.append(getString(R.string.activity_calendar_no_entries))
        }

        val isFuture = dayStart >= PlannedSession.startOfDay(System.currentTimeMillis())

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg.toString())
            .setNegativeButton(getString(R.string.cancel), null)

        if (isFuture) {
            builder.setPositiveButton(getString(R.string.calendar_btn_plan_session)) { _, _ ->
                val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
                showPlanSessionDialog(cal)
            }
        }

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#8FC357"))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#8FC357"))
    }

    // ════════════════════════════════════════════════════════════
    // SESSION PLANEN
    // ════════════════════════════════════════════════════════════

    private fun showPlanSessionDialog(targetDay: Calendar) {
        val dog = selectedDog ?: run {
            Toast.makeText(this, getString(R.string.calendar_toast_no_dog), Toast.LENGTH_SHORT).show()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_plan_session, null)
        val spinnerCategory = view.findViewById<Spinner>(R.id.spinnerPlanCategory)
        val tvDate          = view.findViewById<TextView>(R.id.tvPlanDate)
        val etDuration       = view.findViewById<EditText>(R.id.etPlanDuration)
        val etNote           = view.findViewById<EditText>(R.id.etPlanNote)
        val switchReminder   = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchPlanReminder)
        val tvSuggestion     = view.findViewById<TextView>(R.id.tvPlanSuggestion)

        val dateFmt = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault())
        tvDate.text = dateFmt.format(targetDay.time).replaceFirstChar { it.uppercase() }

        val categories = TrainingCategory.values().toList()
        val catNames = categories.map { it.displayName(this) }
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, catNames)
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = catAdapter

        // Empfehlung vorab berechnen
        val suggestedCategory = ActivityCalendarEngine.suggestNextCategory(
            dog.id, allTrainingSessions, allProgress
        )
        val suggestedIdx = categories.indexOf(suggestedCategory).coerceAtLeast(0)
        spinnerCategory.setSelection(suggestedIdx)

        fun updateSuggestion(category: TrainingCategory) {
            val progress = allProgress.firstOrNull { it.dogId == dog.id && it.category == category }
                ?: TrainingProgress(dogId = dog.id, category = category)
            val recentSessions = allTrainingSessions
                .filter { it.dogId == dog.id && it.category == category }
                .sortedByDescending { it.date }
            val character = dog.toCharacter()
            val duration = ActivityCalendarEngine.suggestDuration(progress, recentSessions, character)
            etDuration.setText(duration.toString())
            tvSuggestion.text = getString(R.string.calendar_suggestion_format, duration, progress.progressPercentage)
        }
        updateSuggestion(suggestedCategory)

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                updateSuggestion(categories[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_dialog_plan_title))
            .setView(view)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val category = categories.getOrNull(spinnerCategory.selectedItemPosition)
                    ?: TrainingCategory.LEINENARBEIT
                val duration = etDuration.text.toString().toIntOrNull() ?: 15
                val note = etNote.text.toString()
                val reminderEnabled = switchReminder.isChecked

                val planned = PlannedSession(
                    dogId = dog.id,
                    dogName = dog.name,
                    categoryId = category.id,
                    plannedDateMillis = PlannedSession.startOfDay(targetDay.timeInMillis),
                    plannedDurationMinutes = duration,
                    note = note,
                    reminderEnabled = reminderEnabled
                )
                ActivityCalendarManager.save(this, planned)

                if (reminderEnabled) {
                    scheduleReminder(planned)
                }

                Toast.makeText(this, getString(R.string.calendar_toast_planned), Toast.LENGTH_SHORT).show()
                loadAllData()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
            .apply {
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#8FC357"))
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#8FC357"))
            }
    }

    private fun scheduleReminder(planned: PlannedSession) {
        // 30 Minuten vor dem geplanten Termin auslösen.
        // Da nur das Datum (nicht die Uhrzeit) geplant wird, wird die
        // Erinnerung auf 9:00 Uhr morgens am geplanten Tag minus 30 Min gelegt –
        // praktikabler Standardwert für tägliche Trainingsplanung.
        val cal = Calendar.getInstance().apply {
            timeInMillis = planned.plannedDateMillis
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
        }
        val delay = cal.timeInMillis - System.currentTimeMillis()
        if (delay <= 0) return // Termin liegt bereits in der Vergangenheit oder zu nah

        val data = workDataOf(
            TrainingReminderWorker.KEY_DOG_NAME    to planned.dogName,
            TrainingReminderWorker.KEY_CATEGORY_ID to planned.category().id,
            TrainingReminderWorker.KEY_DURATION    to planned.plannedDurationMinutes,
            TrainingReminderWorker.KEY_SESSION_ID  to planned.id
        )
        val request = OneTimeWorkRequestBuilder<TrainingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "training_reminder_${planned.id}",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    // ════════════════════════════════════════════════════════════
    // LÜCKENANALYSE
    // ════════════════════════════════════════════════════════════

    private fun renderNeglectedCategories() {
        val dog = selectedDog ?: return
        val neglected = ActivityCalendarEngine.findNeglectedCategories(
            dog.id, allTrainingSessions, allProgress, thresholdDays = 14
        )

        binding.neglectedContainer.removeAllViews()

        if (neglected.isEmpty()) {
            binding.tvNeglectedEmpty.visibility = View.VISIBLE
            binding.tvNeglectedEmpty.text = getString(R.string.calendar_gap_all_trained)
            return
        }
        binding.tvNeglectedEmpty.visibility = View.GONE

        neglected.take(6).forEach { item ->
            binding.neglectedContainer.addView(buildNeglectedRow(item))
        }
    }

    private fun buildNeglectedRow(item: NeglectedCategory): View {
        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            isClickable = true
            setOnClickListener {
                showPlanSessionDialog(Calendar.getInstance())
            }
        }
        row.addView(TextView(this).apply {
            text = "🔸"
            textSize = 13f
            setPadding(0, 0, (8 * dp).toInt(), 0)
        })
        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        infoCol.addView(TextView(this).apply {
            text = item.category.displayName(this@ActivityCalendarActivity)
            textSize = 12.5f
            setTextColor(Color.parseColor("#DDDDDD"))
        })
        val daysText = item.daysSinceLastSession?.let { getString(R.string.activity_calendar_trained_days_ago, it) } ?: getString(R.string.activity_calendar_never_trained)
        infoCol.addView(TextView(this).apply {
            text = daysText
            textSize = 10.5f
            setTextColor(Color.parseColor("#888888"))
        })
        row.addView(infoCol)
        row.addView(TextView(this).apply {
            text = "📅"
            textSize = 14f
        })
        return row
    }

    // ════════════════════════════════════════════════════════════
    // 7-TAGE WETTEREIGNUNG
    // ════════════════════════════════════════════════════════════

    private fun fetchWeekForecast() {
        val location = lastKnownLocationOrNull()
        if (location == null) {
            binding.weekForecastRow.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            try {
                val forecast = withContext(Dispatchers.IO) {
                    fetchOpenMeteoWeekForecast(location.first, location.second)
                }
                weekForecast = forecast
                renderWeekForecastRow()
            } catch (e: Exception) {
                Log.w("ActivityCalendar", "Wettervorhersage fehlgeschlagen: ${e.message}")
                binding.weekForecastRow.visibility = View.GONE
            }
        }
    }

    private fun lastKnownLocationOrNull(): Pair<Double, Double>? {
        return try {
            val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return null
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            loc?.let { it.latitude to it.longitude }
        } catch (e: Exception) { null }
    }

    private fun fetchOpenMeteoWeekForecast(lat: Double, lon: Double): List<CalendarDayForecast> {
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,precipitation_probability_max,wind_speed_10m_max" +
                "&forecast_days=7&timezone=auto"

        val json = JSONObject(URL(url).readText())
        val daily = json.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val tMax  = daily.getJSONArray("temperature_2m_max")
        val precP = daily.getJSONArray("precipitation_probability_max")
        val windM = daily.getJSONArray("wind_speed_10m_max")

        val dayNames = listOf(
            getString(R.string.calendar_weekday_su),
            getString(R.string.calendar_weekday_mo),
            getString(R.string.calendar_weekday_tu),
            getString(R.string.calendar_weekday_we),
            getString(R.string.calendar_weekday_th),
            getString(R.string.calendar_weekday_fr),
            getString(R.string.calendar_weekday_sa)
        )
        val dateFmt  = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("dd.MM.", Locale.getDefault())

        val items = mutableListOf<CalendarDayForecast>()
        for (i in 0 until times.length()) {
            val date = dateFmt.parse(times.getString(i)) ?: continue
            val cal = Calendar.getInstance().also { it.time = date }
            items.add(CalendarDayForecast(
                dateMillis        = date.time,
                dayLabel          = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1],
                dateLabel         = labelFmt.format(date),
                weatherCode       = codes.getInt(i),
                tempMax           = tMax.getDouble(i),
                precipProbability = precP.getInt(i),
                windSpeedMax      = windM.getDouble(i)
            ))
        }
        return items
    }

    private fun renderWeekForecastRow() {
        binding.weekForecastRow.removeAllViews()
        binding.weekForecastRow.visibility = View.VISIBLE

        weekForecast.forEach { day ->
            binding.weekForecastRow.addView(buildWeekForecastChip(day))
        }
    }

    private fun buildWeekForecastChip(day: CalendarDayForecast): View {
        val dp = resources.displayMetrics.density
        val suitability = day.suitability()

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                (64 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = (6 * dp).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * dp
                setColor(Color.parseColor("#2A2A2A"))
                setStroke((1 * dp).toInt(), Color.parseColor(suitability.color))
            }
            isClickable = true
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = day.dateMillis }
                Toast.makeText(
                    this@ActivityCalendarActivity,
                    getString(R.string.activity_calendar_week_forecast_toast, suitability.icon, suitability.label(this@ActivityCalendarActivity), day.tempMax.toInt(), day.precipProbability),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        card.addView(TextView(this).apply {
            text = day.dayLabel; textSize = 11f
            setTextColor(Color.parseColor("#CCCCCC"))
        })
        card.addView(TextView(this).apply {
            text = suitability.icon; textSize = 16f
        })
        card.addView(TextView(this).apply {
            text = "${day.tempMax.toInt()}°"
            textSize = 11f
            setTextColor(Color.WHITE)
        })
        return card
    }
}
