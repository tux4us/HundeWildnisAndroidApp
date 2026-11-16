package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Ein Eintrag im Gesundheitskalender: entweder eine Beobachtung
 * oder eine (nur lesend eingeblendete) Notfallmeldung.
 */
sealed class HealthCalendarItem {
    abstract val sortTime: Long

    data class Observation(val entry: HealthObservationEntry) : HealthCalendarItem() {
        override val sortTime: Long
            get() = HealthDates.parse(entry.startDate)?.time ?: entry.createdAt
    }

    data class Emergency(val report: EmergencyReport) : HealthCalendarItem() {
        override val sortTime: Long
            get() = report.timestamp
    }
}

object HealthObservationManager {

    // ════════════════════════════════════════════════════════════════
    // SPEICHERN / LÖSCHEN (über DogProfile, wie bei Läufigkeit)
    // ════════════════════════════════════════════════════════════════

    fun save(context: Context, dogId: String, entry: HealthObservationEntry): Boolean {
        val profile = DogProfileManager.getProfile(context, dogId) ?: return false
        val list = profile.healthObservations.toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        return DogProfileManager.saveProfile(
            context,
            profile.copy(
                healthObservations = sortedNewestFirst(list),
                lastModified = System.currentTimeMillis()
            )
        )
    }

    fun delete(context: Context, dogId: String, entryId: String): Boolean {
        val profile = DogProfileManager.getProfile(context, dogId) ?: return false
        profile.healthObservations.find { it.id == entryId }?.let { HealthPhotos.deleteAll(context, it.photoUris) }
        return DogProfileManager.saveProfile(
            context,
            profile.copy(
                healthObservations = profile.healthObservations.filter { it.id != entryId },
                lastModified = System.currentTimeMillis()
            )
        )
    }

    fun sortedNewestFirst(list: List<HealthObservationEntry>): List<HealthObservationEntry> =
        list.sortedByDescending { HealthDates.parse(it.startDate)?.time ?: it.createdAt }

    // ════════════════════════════════════════════════════════════════
    // NOTFALLMELDUNGEN (nur lesen, Speicherort bleibt "emergency_history")
    // ════════════════════════════════════════════════════════════════

    /**
     * Liest alle Notfallmeldungen des Hundes. Die Liste wird bewusst hier gelesen
     * und nach dogId gefiltert; geschrieben wird sie weiterhin nur von EmergencyActivity.
     */
    fun emergencyReportsFor(context: Context, dogId: String): List<EmergencyReport> {
        val json = context.getSharedPreferences("emergency_history", Context.MODE_PRIVATE)
            .getString("reports", null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<EmergencyReport>>() {}.type
            Gson().fromJson<List<EmergencyReport>>(json, type)
                .orEmpty()
                .filter { it.dogId == dogId }
        }.getOrDefault(emptyList())
    }

    /** Beobachtungen und Notfallmeldungen zeitlich gemischt, neueste zuerst. */
    fun calendarItems(
        context: Context,
        profile: DogProfile,
        includeEmergencies: Boolean = true
    ): List<HealthCalendarItem> {
        val items = mutableListOf<HealthCalendarItem>()
        profile.healthObservations.forEach { items.add(HealthCalendarItem.Observation(it)) }
        if (includeEmergencies) {
            emergencyReportsFor(context, profile.id).forEach { items.add(HealthCalendarItem.Emergency(it)) }
        }
        return items.sortedByDescending { it.sortTime }
    }

    // ════════════════════════════════════════════════════════════════
    // ZUSAMMENFASSUNG
    // ════════════════════════════════════════════════════════════════

    fun activeCount(entries: List<HealthObservationEntry>): Int = entries.count { it.isActive }

    fun summaryLabel(context: Context, profile: DogProfile): String {
        val entries = profile.healthObservations
        val emergencies = emergencyReportsFor(context, profile.id).size
        if (entries.isEmpty() && emergencies == 0) {
            return context.getString(R.string.health_summary_empty)
        }
        val last = sortedNewestFirst(entries).firstOrNull()?.startDate.orEmpty()
        return context.getString(
            R.string.health_summary_format,
            activeCount(entries),
            entries.size,
            emergencies,
            last.ifEmpty { "—" }
        )
    }
}
