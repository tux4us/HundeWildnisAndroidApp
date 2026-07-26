package com.hundewildnis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-Worker für die WebDAV-Synchronisation.
 * Stil analog zu ContentUpdateWorker.kt / UpdateHelper.kt.
 */
class WebDavSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WebDavSyncWorker"
        private const val NOTIFICATION_CHANNEL_ID = "hundewildnis_webdav_sync"
        private const val NOTIFICATION_ID = 1002

        private const val WORK_NAME_PERIODIC = "webdav_sync_periodic"
        private const val WORK_NAME_MANUAL = "webdav_sync_manual"

        /** Plant periodischen Hintergrund-Sync. Ersetzt eine bestehende Planung bei Intervalländerung. */
        fun schedulePeriodicSync(context: Context, intervalHours: Int) {
            val wifiOnly = WebDavPreferences(context).wifiOnlySync
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WebDavSyncWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME_PERIODIC)
        }

        /** Stößt einen sofortigen, einmaligen Sync an (z.B. Button "Jetzt synchronisieren"). */
        fun triggerManualSync(context: Context) {
            val wifiOnly = WebDavPreferences(context).wifiOnlySync
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WebDavSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME_MANUAL,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = WebDavPreferences(applicationContext)
        if (!prefs.syncEnabled || !prefs.hasCredentials()) {
            return Result.success()
        }

        return withContext(Dispatchers.IO) {
            try {
                val syncManager = WebDavSyncManager(applicationContext)
                when (val result = syncManager.sync()) {
                    is WebDavSyncManager.SyncResult.Success -> {
                        prefs.lastSyncTimestamp = System.currentTimeMillis()
                        prefs.lastSyncResult = if (result.mergedDeviceCount > 0)
                            "Erfolgreich (${result.mergedDeviceCount} Gerät${if (result.mergedDeviceCount != 1) "e" else ""} zusammengeführt)"
                        else "Erfolgreich (keine anderen Geräte)"

                        if (result.mergedDeviceCount > 0) {
                            showNotification(
                                "✅ HundeWildnis Sync",
                                "Daten von ${result.mergedDeviceCount} Gerät${if (result.mergedDeviceCount != 1) "en" else ""} zusammengeführt."
                            )
                        }
                        Result.success()
                    }

                    is WebDavSyncManager.SyncResult.NetworkError -> {
                        prefs.lastSyncResult = "Netzwerkfehler: ${result.message}"
                        Result.retry()
                    }

                    is WebDavSyncManager.SyncResult.NoCredentials -> {
                        Result.success()
                    }

                    is WebDavSyncManager.SyncResult.Error -> {
                        prefs.lastSyncResult = "Fehler: ${result.message}"
                        Result.failure()
                    }
                }
            } catch (e: Exception) {
                prefs.lastSyncResult = "Fehler: ${e.message}"
                Result.retry()
            }
        }
    }

    private fun showNotification(title: String, message: String) {
        createNotificationChannel()

        val notificationsEnabled = applicationContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("notifications_enabled", true)
        if (!notificationsEnabled) return

        val intent = Intent(applicationContext, BackupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_hundewildnis)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#2d5016"))
            .build()

        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Benachrichtigungs-Berechtigung fehlt - stiller Fallback
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "WebDAV Synchronisation",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen zur WebDAV-Synchronisation der HundeWildnis-Daten"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
