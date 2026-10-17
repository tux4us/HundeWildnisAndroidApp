package com.hundewildnis.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ════════════════════════════════════════════════════════════════
// TIMELINE — REPOSITORY
//
// Liest alle Quellen eines Hundes, wandelt sie in TimelineEvent um
// und sortiert absteigend nach Zeitstempel (neueste zuerst).
// Die Quelldaten werden nicht verändert. Jede Quelle ist gekapselt:
// ein Fehler in einer Quelle lässt die übrigen unberührt.
// Aufruf aus einem Hintergrund-Thread (Dispatchers.Default).
// ════════════════════════════════════════════════════════════════

object TimelineRepository {

    fun load(context: Context, dogId: String): List<TimelineEvent> {
        val profile = DogProfileManager.getProfile(context, dogId)

        val events = mutableListOf<TimelineEvent>()
        events += safe { trainingEvents(context, dogId) }
        events += safe { nasenarbeitEvents(context, dogId) }
        events += safe { spielEvents(context, dogId) }
        events += safe { intervalEvents(context, dogId) }
        events += safe { vitalEvents(context, dogId) }
        if (profile != null) {
            events += safe { heatEvents(context, profile) }
            events += safe { userEvents(context, profile) }
            events += safe { startEvents(context, profile) }
        }

        return events.sortedWith(
            compareByDescending<TimelineEvent> { it.timestamp }.thenBy { it.id }
        )
    }

    private inline fun safe(block: () -> List<TimelineEvent>): List<TimelineEvent> =
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

    // ── Quellen ────────────────────────────────────────────────

    private fun trainingEvents(context: Context, dogId: String): List<TimelineEvent> =
        TrainingManager.getAllSessions(context)
            .filter { it.dogId == dogId }
            .map { s ->
                val effort = when (s.measurementType) {
                    MeasurementType.TIME ->
                        context.getString(R.string.timeline_minutes_format, s.durationMinutes ?: 0)
                    MeasurementType.REPETITIONS ->
                        context.getString(R.string.timeline_repetitions_format, s.repetitions ?: 0)
                }
                TimelineEvent(
                    id = "training_${s.id}",
                    timestamp = s.date,
                    hasTime = true,
                    category = TimelineCategory.TRAINING,
                    title = s.category.displayName(context),
                    subtitle = subtitle(effort, ratingIcon(s.successRating)),
                    payload = s.category.id.toString(),
                    plain = subtitle(effort, ratingText(context, s.successRating))
                )
            }

    private fun nasenarbeitEvents(context: Context, dogId: String): List<TimelineEvent> =
        NasenarbeitManager.loadSessionsForDog(context, dogId)
            .map { s ->
                val duration = s.suchdauerMinuten?.let {
                    context.getString(R.string.timeline_minutes_format, it)
                }
                TimelineEvent(
                    id = "nasenarbeit_${s.id}",
                    timestamp = s.datum,
                    hasTime = true,
                    category = TimelineCategory.NASENARBEIT,
                    title = s.durchgefuehrteDisziplin.displayName(context),
                    subtitle = subtitle(duration, ratingIcon(s.erfolgsbewertung)),
                    payload = s.id,
                    plain = subtitle(duration, ratingText(context, s.erfolgsbewertung))
                )
            }

    private fun spielEvents(context: Context, dogId: String): List<TimelineEvent> =
        SpielSpassManager.getSessionsForDog(context, dogId)
            .map { s ->
                val title = SpielAktivitaetCatalog.findById(s.aktivitaetId)?.name(context)
                    ?: context.getString(R.string.timeline_play_fallback)
                val duration = s.dauerMinuten?.let {
                    context.getString(R.string.timeline_minutes_format, it)
                }
                TimelineEvent(
                    id = "spiel_${s.id}",
                    timestamp = s.datum,
                    hasTime = true,
                    category = TimelineCategory.SPIEL,
                    title = title,
                    subtitle = subtitle(
                        "${s.ort.icon} ${s.ort.displayName(context)}",
                        duration,
                        ratingIcon(s.bewertung)
                    ),
                    payload = "${s.aktivitaetId}|${s.ort.name}",
                    plain = subtitle(s.ort.displayName(context), duration, ratingText(context, s.bewertung))
                )
            }

    private fun intervalEvents(context: Context, dogId: String): List<TimelineEvent> =
        IntervalTimerManager.getSessionsForDog(context, dogId)
            .map { s ->
                TimelineEvent(
                    id = "interval_${s.id}",
                    timestamp = s.timestamp,
                    hasTime = true,
                    category = TimelineCategory.INTERVALL,
                    title = context.getString(R.string.timeline_interval_title),
                    subtitle = s.getSummaryLine(context),
                    payload = s.id
                )
            }

    private fun vitalEvents(context: Context, dogId: String): List<TimelineEvent> =
        VitalManager.getForDog(context, dogId)
            .map { r ->
                TimelineEvent(
                    id = "vital_${r.id}",
                    timestamp = r.timestamp,
                    hasTime = true,
                    category = TimelineCategory.VITAL,
                    title = context.getString(R.string.timeline_vital_title),
                    subtitle = r.summaryLine(context),
                    payload = r.id
                )
            }

    /** Läufigkeit: ein Eintrag für den Beginn, ein weiterer für das Ende (falls gesetzt). */
    private fun heatEvents(context: Context, profile: DogProfile): List<TimelineEvent> {
        val result = mutableListOf<TimelineEvent>()
        for (entry in profile.heatCycles) {
            val subtitle = if (entry.cycleNumber > 0) {
                context.getString(R.string.timeline_heat_subtitle_format, entry.cycleNumber, entry.intensity)
            } else {
                entry.intensity
            }
            parseDate(entry.startDate)?.let { ts ->
                result += TimelineEvent(
                    id = "heat_start_${entry.id}",
                    timestamp = ts,
                    hasTime = false,
                    category = TimelineCategory.HEAT,
                    title = context.getString(R.string.timeline_heat_start),
                    subtitle = subtitle,
                    payload = entry.id
                )
            }
            parseDate(entry.endDate)?.let { ts ->
                result += TimelineEvent(
                    id = "heat_end_${entry.id}",
                    timestamp = ts,
                    hasTime = false,
                    category = TimelineCategory.HEAT,
                    title = context.getString(R.string.timeline_heat_end),
                    subtitle = subtitle,
                    payload = entry.id
                )
            }
        }
        return result
    }

    /**
     * Eigene Einträge. Bei ungenauem Datum (Monat/Jahr oder Jahr) wird der erste Tag des
     * Zeitraums zum Sortieren verwendet; angezeigt wird das Datum so, wie es eingegeben wurde.
     */
    private fun userEvents(context: Context, profile: DogProfile): List<TimelineEvent> =
        profile.timelineEntries.mapNotNull { e ->
            val date = TogetherMath.parse(e.date) ?: return@mapNotNull null
            val cal = Calendar.getInstance().apply {
                clear()
                set(date.year, date.month - 1, date.day)
            }
            val kind = TimelineEntryKind.normalize(e.kind)
            val place = e.place.takeIf { it.isNotBlank() }?.let { "📍 $it" }
            TimelineEvent(
                id = "eigene_${e.id}",
                timestamp = cal.timeInMillis,
                hasTime = false,
                category = TimelineCategory.EIGENE,
                title = e.title,
                subtitle = listOfNotNull(place, e.note.takeIf { it.isNotBlank() }).joinToString("\n"),
                payload = e.id,
                icon = TimelineEntryKind.icon(kind),
                dateText = TogetherFormatter.startText(date),
                photoUri = e.photoUri
            )
        }

    /** Beginn der gemeinsamen Zeit (Geburt, Kauf, Übernahme, Pflege/Adoption) als ältester Eintrag. */
    private fun startEvents(context: Context, profile: DogProfile): List<TimelineEvent> {
        val info = TogetherMath.compute(profile) ?: return emptyList()
        val typeLabel = context.getString(
            when (info.effectiveType) {
                TogetherSinceType.PURCHASE -> R.string.timeline_start_purchase
                TogetherSinceType.ADOPTION -> R.string.timeline_start_adoption
                TogetherSinceType.FOSTER -> R.string.timeline_start_foster
                else -> R.string.timeline_start_birth
            }
        )
        val cal = Calendar.getInstance().apply {
            clear()
            set(info.start.year, info.start.month - 1, info.start.day)
        }
        return listOf(
            TimelineEvent(
                id = "start_${profile.id}",
                timestamp = cal.timeInMillis,
                hasTime = false,
                category = TimelineCategory.START,
                title = context.getString(R.string.timeline_start_format, typeLabel),
                subtitle = TogetherFormatter.startText(info.start),
                payload = ""
            )
        )
    }

    // ── Hilfen ─────────────────────────────────────────────────

    /** Ohne Bewertung (0) kein Symbol. */
    private fun ratingIcon(rating: Int): String? = when (rating) {
        1 -> "🔴"
        2 -> "🟡"
        3 -> "🟢"
        4 -> "🟢🟢"
        else -> null
    }

    /** Bewertung als Text für den Export, z. B. "Bewertung: Erfolgreich". */
    private fun ratingText(context: Context, rating: Int): String? {
        val labelRes = when (rating) {
            1 -> R.string.timeline_rating_1
            2 -> R.string.timeline_rating_2
            3 -> R.string.timeline_rating_3
            4 -> R.string.timeline_rating_4
            else -> return null
        }
        return context.getString(R.string.timeline_rating_format, context.getString(labelRes))
    }

    private fun subtitle(vararg parts: String?): String =
        parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    /** Toleranter Parser für "dd.MM.yyyy"; leer oder ungültig ergibt null. */
    internal fun parseDate(text: String): Long? {
        val s = text.trim()
        if (s.isEmpty()) return null
        return try {
            SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { isLenient = false }.parse(s)?.time
        } catch (e: Exception) {
            null
        }
    }
}
