package com.hundewildnis.app

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

// ════════════════════════════════════════════════════════════════
// PLANT PHOTO MANAGER
// Datei: PlantPhotoManager.kt
//
// Speichert bis zu 3 Fotos pro Pflanze dauerhaft im internen
// App-Speicher (context.filesDir/plant_photos/).
// URIs werden nach dem Kopieren als interne file://-Pfade gespeichert,
// die unabhängig von externen Storage-Berechtigungen funktionieren.
// ════════════════════════════════════════════════════════════════

object PlantPhotoManager {

    private const val TAG           = "PlantPhotoManager"
    private const val PREFS_NAME    = "plant_photo_prefs"
    private const val KEY_MAP       = "plant_photo_map"
    private const val PHOTOS_SUBDIR = "plant_photos"
    const val MAX_PHOTOS            = 3

    // ── Verzeichnis sicherstellen ────────────────────────────────
    private fun photoDir(context: Context): File =
        File(context.filesDir, PHOTOS_SUBDIR).also { it.mkdirs() }

    // ── Alle Foto-URIs für eine Pflanze laden ────────────────────
    fun getPhotos(context: Context, plantId: String): List<String> {
        val map = loadMap(context)
        return map[plantId] ?: emptyList()
    }

    // ── Foto hinzufügen (URI → interner Speicher kopieren) ───────
    fun addPhoto(context: Context, plantId: String, sourceUri: Uri): Boolean {
        val current = getPhotos(context, plantId).toMutableList()
        if (current.size >= MAX_PHOTOS) return false

        return try {
            val timestamp  = System.currentTimeMillis()
            val destFile   = File(photoDir(context), "plant_${plantId}_${timestamp}.jpg")
            val copied     = copyUriToFile(context, sourceUri, destFile)
            if (copied) {
                current.add(destFile.absolutePath)
                saveForPlant(context, plantId, current)
            }
            copied
        } catch (e: Exception) {
            Log.e(TAG, "addPhoto Fehler: ${e.message}")
            false
        }
    }

    // ── Foto löschen ─────────────────────────────────────────────
    fun removePhoto(context: Context, plantId: String, path: String) {
        val current = getPhotos(context, plantId).toMutableList()
        current.remove(path)
        saveForPlant(context, plantId, current)
        try { File(path).delete() } catch (_: Exception) {}
    }

    // ── Alle Fotos einer Pflanze löschen ─────────────────────────
    fun removeAllPhotos(context: Context, plantId: String) {
        getPhotos(context, plantId).forEach {
            try { File(it).delete() } catch (_: Exception) {}
        }
        val map = loadMap(context).toMutableMap()
        map.remove(plantId)
        saveMap(context, map)
    }

    // ── Gesamte Map (für Backup) ─────────────────────────────────
    fun getAllPhotos(context: Context): Map<String, List<String>> =
        loadMap(context)

    // ── Restore aus Backup ────────────────────────────────────────
    // sourcePaths: absolute Pfade von bereits ins filesDir kopierten Dateien
    fun restorePhotos(
        context: Context,
        plantId: String,
        sourcePaths: List<String>,
        mergeMode: Boolean
    ) {
        val existing = if (mergeMode) getPhotos(context, plantId).toMutableList()
                       else mutableListOf()
        sourcePaths.forEach { srcPath ->
            if (existing.size >= MAX_PHOTOS) return@forEach
            val src = File(srcPath)
            if (!src.exists()) return@forEach
            // Bereits im plant_photos-Verzeichnis? Direkt übernehmen.
            if (src.parent == photoDir(context).absolutePath) {
                if (srcPath !in existing) existing.add(srcPath)
            } else {
                // Aus einem anderen Verzeichnis kopieren (z. B. restored_photos)
                val destFile = File(photoDir(context), src.name)
                try {
                    src.copyTo(destFile, overwrite = true)
                    if (destFile.absolutePath !in existing)
                        existing.add(destFile.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "restorePhotos Fehler: ${e.message}")
                }
            }
        }
        saveForPlant(context, plantId, existing)
    }

    // ── Interne Helfer ────────────────────────────────────────────

    private fun saveForPlant(context: Context, plantId: String, paths: List<String>) {
        val map = loadMap(context).toMutableMap()
        if (paths.isEmpty()) map.remove(plantId) else map[plantId] = paths
        saveMap(context, map)
    }

    private fun loadMap(context: Context): Map<String, List<String>> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MAP, "{}") ?: "{}"
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveMap(context: Context, map: Map<String, List<String>>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MAP, Gson().toJson(map)).apply()
    }

    private fun copyUriToFile(context: Context, uri: Uri, dest: File): Boolean =
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyUriToFile Fehler: ${e.message}")
            false
        }
}
