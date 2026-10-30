package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileNotFoundException

/**
 * Lädt die lokal gebündelten Kurse aus assets/courses/<sprache>/.
 * Sprache = aktuelle App-Sprache; fehlt der Ordner, wird Deutsch verwendet.
 */
object CourseRepository {
    private const val FALLBACK_LANG = "de"
    private val gson = Gson()

    private fun currentLang(context: Context): String =
        context.resources.configuration.locales[0].language.ifBlank { FALLBACK_LANG }

    private fun readAsset(context: Context, lang: String, name: String): String =
        context.assets.open("courses/$lang/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun <T> withFallback(context: Context, block: (String) -> T): T {
        val lang = currentLang(context)
        return try {
            block(lang)
        } catch (e: FileNotFoundException) {
            if (lang == FALLBACK_LANG) throw e
            block(FALLBACK_LANG)
        }
    }

    fun loadIndex(context: Context): List<CourseIndexEntry> = withFallback(context) { lang ->
        val type = object : TypeToken<List<CourseIndexEntry>>() {}.type
        gson.fromJson<List<CourseIndexEntry>>(readAsset(context, lang, "index.json"), type) ?: emptyList()
    }

    fun loadCourse(context: Context, entry: CourseIndexEntry): CourseLesson = withFallback(context) { lang ->
        gson.fromJson(readAsset(context, lang, entry.file), CourseLesson::class.java)
            ?: CourseLesson(id = entry.id, title = entry.title)
    }

    /** Kurs anhand der ID laden (Index wird dafür gelesen). */
    fun loadCourseById(context: Context, courseId: String): CourseLesson? =
        loadIndex(context).firstOrNull { it.id == courseId }?.let { loadCourse(context, it) }
}
