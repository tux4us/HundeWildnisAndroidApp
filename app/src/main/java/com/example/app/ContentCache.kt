package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContentCache(private val context: Context) {
    private val prefs = context.getSharedPreferences("content_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BLOG_POSTS = "blog_posts"
        private const val KEY_PAGES = "pages"
        private const val KEY_COURSES = "courses"
        private const val KEY_YOUTUBE = "youtube_videos"
        private const val KEY_LAST_UPDATE = "last_update_timestamp"

        // Cache-Gültigkeit: 6 Stunden (in Millisekunden)
        private const val CACHE_VALIDITY_MS = 6 * 60 * 60 * 1000L // 6 Stunden
    }

    // Prüfe ob Cache veraltet ist
    fun isCacheExpired(): Boolean {
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
        val currentTime = System.currentTimeMillis()
        val cacheAge = currentTime - lastUpdate

        android.util.Log.d("ContentCache", "Cache-Alter: ${cacheAge / 1000 / 60} Minuten")

        return cacheAge > CACHE_VALIDITY_MS
    }

    // Setze Timestamp beim Speichern
    private fun updateTimestamp() {
        prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
    }

    fun saveBlogPosts(posts: List<ContentItem>) {
        val json = gson.toJson(posts)
        prefs.edit().putString(KEY_BLOG_POSTS, json).apply()
        updateTimestamp()
    }

    fun savePages(pages: List<ContentItem>) {
        val json = gson.toJson(pages)
        prefs.edit().putString(KEY_PAGES, json).apply()
        updateTimestamp()
    }

    fun saveCourses(courses: List<ContentItem>) {
        val json = gson.toJson(courses)
        prefs.edit().putString(KEY_COURSES, json).apply()
        updateTimestamp()
    }

    fun getBlogPosts(): List<ContentItem>? {
        val json = prefs.getString(KEY_BLOG_POSTS, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getPages(): List<ContentItem>? {
        val json = prefs.getString(KEY_PAGES, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getCourses(): List<ContentItem>? {
        val json = prefs.getString(KEY_COURSES, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun clearCache() {
        prefs.edit().clear().apply()
        android.util.Log.d("ContentCache", "Cache gelöscht")
    }
}