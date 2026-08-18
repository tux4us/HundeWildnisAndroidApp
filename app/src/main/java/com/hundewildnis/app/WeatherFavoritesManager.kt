package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WeatherFavoritesManager {
    private const val PREFS_NAME = "weather_favorites_prefs"
    private const val KEY_LIST = "favorites_list"
    private val gson = Gson()

    fun getAll(context: Context): List<WeatherFavorite> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<WeatherFavorite>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(context: Context, list: List<WeatherFavorite>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LIST, gson.toJson(list)).apply()
    }

    fun add(context: Context, favorite: WeatherFavorite) {
        val list = getAll(context).toMutableList()
        if (list.none { it.lat == favorite.lat && it.lon == favorite.lon }) {
            list.add(favorite)
            saveAll(context, list)
        }
    }

    fun remove(context: Context, lat: Double, lon: Double) {
        val list = getAll(context).filterNot { it.lat == lat && it.lon == lon }
        saveAll(context, list)
    }

    fun isFavorite(context: Context, lat: Double, lon: Double): Boolean {
        return getAll(context).any { it.lat == lat && it.lon == lon }
    }
}
