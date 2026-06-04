package com.hundewildnis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class HeatCycleReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val dogName      = inputData.getString(KEY_DOG_NAME)    ?: "Deine Hündin"
        val predictedDate = inputData.getString(KEY_PREDICTED)  ?: "bald"

        ensureNotificationChannel()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)   // App-Icon anpassen
            .setContentTitle("🐾 Läufigkeit in ~30 Tagen")
            .setContentText("$dogName: voraussichtlich am $predictedDate")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Bei $dogName steht voraussichtlich am $predictedDate " +
                    "die nächste Läufigkeit an.\n\n" +
                    "Denk an: Sicherung des Gartens, Leinenpflicht und ggf. Tierarzttermin."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Notification-ID basiert auf Hundename → ein Reminder pro Hund
        notificationManager.notify(dogName.hashCode(), notification)
        return Result.success()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Läufigkeit – Erinnerungen",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Erinnert 30 Tage vor der voraussichtlichen Läufigkeit."
                    }
                )
            }
        }
    }

    companion object {
        const val CHANNEL_ID    = "heat_cycle_reminders"
        const val KEY_DOG_ID    = "dog_id"
        const val KEY_DOG_NAME  = "dog_name"
        const val KEY_PREDICTED = "predicted_date"
    }
}
