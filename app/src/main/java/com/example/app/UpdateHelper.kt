package com.hundewildnis.app

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UpdateHelper {

    fun scheduleContentUpdates(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // Falls deaktiviert -> Alle Jobs abbrechen
        if (!prefs.getBoolean("background_updates_enabled", true)) {
            WorkManager.getInstance(context.applicationContext).cancelAllWork()
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.getBoolean("background_updates_wifi_only", false)) 
                    NetworkType.UNMETERED 
                else 
                    NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()

        // Periodisches Update (alle 12 Stunden)
        // Wir setzen das erste Update auf 8:00 Uhr morgens
        val morningRequest = PeriodicWorkRequestBuilder<ContentUpdateWorker>(12, TimeUnit.HOURS, 1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(calculateDelayUntil(8), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "content_updates_morning",
            ExistingPeriodicWorkPolicy.KEEP, // Behalte bestehenden Job, falls vorhanden
            morningRequest
        )
    }

    /**
     * Startet sofort ein einmaliges Update im Hintergrund.
     * Nützlich bei Erstinstallation oder wenn der Cache leer ist.
     */
    fun triggerImmediateUpdate(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("background_updates_enabled", true)) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = OneTimeWorkRequestBuilder<ContentUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "content_update_immediate",
            ExistingWorkPolicy.REPLACE, // Sofort ausführen
            updateRequest
        )
    }

    private fun calculateDelayUntil(hour: Int): Long {
        val now = Calendar.getInstance()
        val due = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return due.timeInMillis - now.timeInMillis
    }
}
