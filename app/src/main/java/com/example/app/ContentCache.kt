package com.hundewildnis.app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContentCache(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("content_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_BLOG_POSTS = "blog_posts"
        private const val KEY_PAGES = "pages"
        private const val KEY_COURSES = "courses"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val CACHE_VALIDITY = 3600000L // 1 Stunde in Millisekunden
    }

    fun saveBlogPosts(posts: List<ContentItem>) {
        val json = gson.toJson(posts)
        prefs.edit()
            .putString(KEY_BLOG_POSTS, json)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun savePages(pages: List<ContentItem>) {
        val json = gson.toJson(pages)
        prefs.edit()
            .putString(KEY_PAGES, json)
            .apply()
    }

    fun saveCourses(courses: List<ContentItem>) {
        val json = gson.toJson(courses)
        prefs.edit()
            .putString(KEY_COURSES, json)
            .apply()
    }

    fun getBlogPosts(): List<ContentItem>? {
        if (!isCacheValid()) return null

        val json = prefs.getString(KEY_BLOG_POSTS, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun getPages(): List<ContentItem>? {
        if (!isCacheValid()) return null

        val json = prefs.getString(KEY_PAGES, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun getCourses(): List<ContentItem>? {
        if (!isCacheValid()) return null

        val json = prefs.getString(KEY_COURSES, null) ?: return null
        val type = object : TypeToken<List<ContentItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    private fun isCacheValid(): Boolean {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0)
        return System.currentTimeMillis() - timestamp < CACHE_VALIDITY
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}