// FavoritesManager.kt
package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object FavoritesManager {

    private const val PREFS_NAME = "favorites_prefs"
    private const val KEY_LIST   = "favorites_list"
    private val gson             = Gson()

    fun getAll(context: Context): List<FavoritePOI> {
        val json = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<FavoritePOI>>() {}.type
            (gson.fromJson<List<FavoritePOI>>(json, type) ?: emptyList())
                .sortedBy { it.sortOrder }
        } catch (e: Exception) { emptyList() }
    }

    fun add(context: Context, fav: FavoritePOI) {
        val list    = getAll(context).toMutableList()
        val maxSort = list.maxOfOrNull { it.sortOrder } ?: -1
        list.add(fav.copy(sortOrder = maxSort + 1))
        persist(context, list)
    }

    fun delete(context: Context, id: String) {
        persist(context, getAll(context).filter { it.id != id })
    }

    fun rename(context: Context, id: String, newName: String) {
        persist(context, getAll(context).map { fav ->
            if (fav.id == id) fav.copy(customName = newName.trim().ifBlank { null }) else fav
        })
    }

    fun reorder(context: Context, reordered: List<FavoritePOI>) {
        persist(context, reordered.mapIndexed { idx, fav -> fav.copy(sortOrder = idx) })
    }

    fun isFavorite(context: Context, lat: Double, lon: Double): Boolean =
        getAll(context).any { approxEqual(it.lat, lat) && approxEqual(it.lon, lon) }

    /** Gibt true zurück wenn hinzugefügt, false wenn entfernt */
    fun toggle(context: Context, fav: FavoritePOI): Boolean {
        val existing = getAll(context).firstOrNull {
            approxEqual(it.lat, fav.lat) && approxEqual(it.lon, fav.lon)
        }
        return if (existing != null) { delete(context, existing.id); false }
        else { add(context, fav); true }
    }

    /** Direkter Bulk-Restore für Backup (überschreibt alles) */
    fun restoreAll(context: Context, favs: List<FavoritePOI>) {
        persist(context, favs.mapIndexed { idx, fav -> fav.copy(sortOrder = idx) })
    }

    private fun approxEqual(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.00005

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persist(context: Context, list: List<FavoritePOI>) =
        prefs(context).edit().putString(KEY_LIST, gson.toJson(list)).apply()
}