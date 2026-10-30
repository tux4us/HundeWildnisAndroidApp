package com.hundewildnis.app

import android.content.Context
import androidx.work.WorkManager

/**
 * Plant die Hintergrundjobs der App. Aktuell nur die optionale WebDAV-Synchronisation.
 * Es werden keine Inhalte von Webseiten nachgeladen.
 */
object UpdateHelper {

    fun scheduleContentUpdates(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val webDavPrefs = WebDavPreferences(context)

        // Falls deaktiviert -> Alle Jobs abbrechen
        if (!prefs.getBoolean("background_updates_enabled", true)) {
            WorkManager.getInstance(context.applicationContext).cancelAllWork()
            return
        }

        // WebDAV Synchronisation (falls aktiviert)
        if (webDavPrefs.syncEnabled && webDavPrefs.autoSyncEnabled && webDavPrefs.hasCredentials()) {
            WebDavSyncWorker.schedulePeriodicSync(context, webDavPrefs.syncIntervalHours)
        } else {
            WebDavSyncWorker.cancelPeriodicSync(context)
        }
    }
}
