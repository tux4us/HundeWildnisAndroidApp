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
        const val KEY_CATEGORY_ID  = "category_id"
        const val KEY_DURATION     = "duration_minutes"
        const val KEY_SESSION_ID   = "session_id"
        const val CHANNEL_ID       = "training_reminders"
    }

    override fun doWork(): Result {
        val dogName  = inputData.getString(KEY_DOG_NAME)   ?: context.getString(R.string.training_reminder_default_dog_name)
        val categoryId = inputData.getInt(KEY_CATEGORY_ID, -1)
        // Sprachneutral gespeichert (Enum-ID), Anzeigetext wird erst hier in der
        // zum Auslösezeitpunkt aktuellen App-Sprache aufgelöst.
        val category = TrainingCategory.fromId(categoryId)?.displayName(context)
            ?: context.getString(R.string.training_reminder_default_category)
        val duration = inputData.getInt(KEY_DURATION, 15)
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: ""

        ensureNotificationChannel()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.training_reminder_notification_title))
            .setContentText(context.getString(R.string.training_reminder_notification_text, dogName, category, duration))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.training_reminder_notification_big_text, dogName, category, duration)
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
                        context.getString(R.string.training_reminder_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = context.getString(R.string.training_reminder_channel_description)
                    }
                )
            }
        }
    }
}