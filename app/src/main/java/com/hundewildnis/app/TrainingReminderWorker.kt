package com.hundewildnis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

// ════════════════════════════════════════════════════════════════
// TRAINING REMINDER WORKER
// Datei: TrainingReminderWorker.kt
//
// Löst 30 Minuten vor einer geplanten Trainingseinheit eine
// Benachrichtigung aus. Identisches Muster wie HeatCycleReminderWorker.
// ════════════════════════════════════════════════════════════════

class TrainingReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        const val KEY_DOG_NAME     = "dog_name"
        const val KEY_CATEGORY     = "category_name"
        const val KEY_DURATION     = "duration_minutes"
        const val KEY_SESSION_ID   = "session_id"
        const val CHANNEL_ID       = "training_reminders"
    }

    override fun doWork(): Result {
        val dogName  = inputData.getString(KEY_DOG_NAME)   ?: "Dein Hund"
        val category = inputData.getString(KEY_CATEGORY)   ?: "Training"
        val duration = inputData.getInt(KEY_DURATION, 15)
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: ""

        ensureNotificationChannel()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🦮 Trainingseinheit in 30 Minuten")
            .setContentText("$dogName: $category (ca. $duration Min)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Geplante Trainingseinheit für $dogName:\n" +
                    "$category – ca. $duration Minuten.\n\n" +
                    "Im Aktivitätskalender ansehen oder direkt starten."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(sessionId.hashCode(), notification)
        return Result.success()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Trainingsplanung – Erinnerungen",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Erinnert 30 Minuten vor geplanten Trainingseinheiten."
                    }
                )
            }
        }
    }
}
