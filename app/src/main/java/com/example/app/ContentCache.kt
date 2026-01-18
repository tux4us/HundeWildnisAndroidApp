package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContentCache(private val context: Context) {
    private val prefs = context.getSharedPreferences("content_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BLOG_POSTS = "blog_posts"
        private const val KEY_PAGES = "pages"
        private const val KEY_COURSES = "courses"
        private const val KEY_YOUTUBE = "youtube_videos" // NEU
        private const val KEY_BLOG_TIMESTAMP = "blog_timestamp"
        private const val KEY_PAGES_TIMESTAMP = "pages_timestamp"
        private const val KEY_COURSES_TIMESTAMP = "courses_timestamp"
        private const val KEY_YOUTUBE_TIMESTAMP = "youtube_timestamp" // NEU
        private const val CACHE_DURATION = 12 * 60 * 60 * 1000L // 12 Stunden
    }

    fun saveBlogPosts(posts: List<ContentItem>) {
        prefs.edit()
            .putString(KEY_BLOG_POSTS, gson.toJson(posts))
            .putLong(KEY_BLOG_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getBlogPosts(): List<ContentItem>? {
        if (!isCacheValid(KEY_BLOG_TIMESTAMP)) return null
        val json = prefs.getString(KEY_BLOG_POSTS, null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<ContentItem>>() {}.type)
    }

    fun savePages(pages: List<ContentItem>) {
        prefs.edit()
            .putString(KEY_PAGES, gson.toJson(pages))
            .putLong(KEY_PAGES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getPages(): List<ContentItem>? {
        if (!isCacheValid(KEY_PAGES_TIMESTAMP)) return null
        val json = prefs.getString(KEY_PAGES, null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<ContentItem>>() {}.type)
    }

    fun saveCourses(courses: List<ContentItem>) {
        prefs.edit()
            .putString(KEY_COURSES, gson.toJson(courses))
            .putLong(KEY_COURSES_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getCourses(): List<ContentItem>? {
        if (!isCacheValid(KEY_COURSES_TIMESTAMP)) return null
        val json = prefs.getString(KEY_COURSES, null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<ContentItem>>() {}.type)
    }

    // NEU: YouTube Cache
    fun saveYouTubeVideos(videos: List<YouTubeVideo>) {
        prefs.edit()
            .putString(KEY_YOUTUBE, gson.toJson(videos))
            .putLong(KEY_YOUTUBE_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun getYouTubeVideos(): List<YouTubeVideo>? {
        if (!isCacheValid(KEY_YOUTUBE_TIMESTAMP)) return null
        val json = prefs.getString(KEY_YOUTUBE, null) ?: return null
        return gson.fromJson(json, object : TypeToken<List<YouTubeVideo>>() {}.type)
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }

    private fun isCacheValid(timestampKey: String): Boolean {
        val timestamp = prefs.getLong(timestampKey, 0)
        val age = System.currentTimeMillis() - timestamp
        return age < CACHE_DURATION
    }
}