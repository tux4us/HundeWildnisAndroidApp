package com.hundewildnis.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hundewildnis.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "hundewildnis_updates"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("ContentUpdateWorker", "🔄 Hintergrund-Update gestartet")

                val wordPressApi = WordPressApiParser()
                val youtubeParser = YouTubeParser()
                val contentCache = ContentCache(applicationContext)

                // Lade alte Daten (nur Blog & YouTube)
                val oldBlogPosts = contentCache.getBlogPosts() ?: emptyList()
                val oldVideos = contentCache.getYouTubeVideos() ?: emptyList()

                Log.d("ContentUpdateWorker", "Alter Stand: ${oldBlogPosts.size} Posts, ${oldVideos.size} Videos")

                // SCHRITT 1: Lade nur die neuesten 20 Posts zum Check
                val recentPosts = try {
                    wordPressApi.loadBlogPosts(perPage = 20)
                } catch (e: Exception) {
                    Log.e("ContentUpdateWorker", "Blog-Fehler: ${e.message}")
                    emptyList()
                }

                // Prüfe ob es neue Posts gibt
                val oldRecentIds = oldBlogPosts.take(20).map { it.url }.toSet()
                val newRecentIds = recentPosts.map { it.url }.toSet()
                val hasNewBlogPosts = newRecentIds.any { it !in oldRecentIds }

                // SCHRITT 2: Wenn neue Posts -> Lade ALLE
                val newBlogPosts = if (hasNewBlogPosts) {
                    Log.d("ContentUpdateWorker", "🆕 Neue Posts erkannt! Lade alle...")
                    try {
                        val allPosts = mutableListOf<ContentItem>()
                        var page = 1
                        var hasMore = true

                        while (hasMore && page <= 10) {
                            val posts = wordPressApi.loadBlogPosts(page = page, perPage = 100)
                            if (posts.isEmpty()) {
                                hasMore = false
                            } else {
                                allPosts.addAll(posts)
                                page++
                                if (posts.size < 100) hasMore = false
                            }
                        }
                        Log.d("ContentUpdateWorker", "Alle Posts geladen: ${allPosts.size}")
                        allPosts
                    } catch (e: Exception) {
                        Log.e("ContentUpdateWorker", "Fehler beim Laden aller Posts: ${e.message}")
                        oldBlogPosts
                    }
                } else {
                    Log.d("ContentUpdateWorker", "Keine neuen Blog-Posts")
                    oldBlogPosts
                }

                // Lade YouTube Videos
                val newVideos = try {
                    youtubeParser.loadLatestVideos()
                } catch (e: Exception) {
                    Log.e("ContentUpdateWorker", "YouTube-Fehler: ${e.message}")
                    oldVideos
                }

                // Speichere neue Daten
                contentCache.saveBlogPosts(newBlogPosts)
                contentCache.saveYouTubeVideos(newVideos)

                Log.d("ContentUpdateWorker", "Neuer Stand: ${newBlogPosts.size} Posts, ${newVideos.size} Videos")

                // Berechne neue Inhalte
                val newBlogCount = maxOf(0, newBlogPosts.size - oldBlogPosts.size)
                val newVideosCount = maxOf(0, newVideos.size - oldVideos.size)

                val totalNew = newBlogCount + newVideosCount

                Log.d("ContentUpdateWorker", "✓ Update abgeschlossen: $totalNew neue Inhalte")

                if (totalNew > 0) {
                    showNotification(newBlogCount, newVideosCount)
                } else {
                    Log.d("ContentUpdateWorker", "Keine neuen Inhalte - keine Benachrichtigung")
                }

                Result.success()
            } catch (e: Exception) {
                Log.e("ContentUpdateWorker", "❌ Fehler beim Update", e)
                Result.retry()
            }
        }
    }

    private fun showNotification(
        newBlogs: Int,
        newVideos: Int
    ) {
        // Erstelle Notification Channel (ab Android 8.0)
        createNotificationChannel()

        // Prüfe ob Benachrichtigungen erlaubt sind
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)

        if (!notificationsEnabled) {
            Log.d("ContentUpdateWorker", "Benachrichtigungen vom User deaktiviert")
            return
        }

        // Erstelle Nachrichtentext
        val parts = mutableListOf<String>()
        if (newBlogs > 0) parts.add("$newBlogs ${if (newBlogs == 1) "Beitrag" else "Beiträge"}")
        if (newVideos > 0) parts.add("$newVideos ${if (newVideos == 1) "Video" else "Videos"}")

        val message = when {
            parts.isEmpty() -> return
            parts.size == 1 -> "Neu: ${parts[0]}"
            else -> "Neu: ${parts.joinToString(", ")}"
        }

        // Intent zum Öffnen der App
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Erstelle Notification
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_hundewildnis)
            .setContentTitle("🐕 Neue Inhalte bei HundeWildnis!")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.parseColor("#2d5016"))
            .build()

        // Zeige Notification
        try {
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIFICATION_ID, notification)
            Log.d("ContentUpdateWorker", "✓ Benachrichtigung angezeigt: $message")
        } catch (e: SecurityException) {
            Log.e("ContentUpdateWorker", "Benachrichtigungs-Berechtigung fehlt", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Neue Inhalte"
            val descriptionText = "Benachrichtigungen über neue Blogbeiträge und Videos"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}