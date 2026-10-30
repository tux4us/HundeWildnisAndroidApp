package com.hundewildnis.app

/**
 * Datenmodell der lokal gebündelten Kurse (assets/courses/<sprache>/...).
 * Alle Klassen werden per Gson gelesen und stehen in proguard-rules.pro.
 *
 * Block-Typen: "h" (Zwischenüberschrift), "p" (Absatz), "list" (Aufzählung, Feld items).
 */
data class CourseBlock(
    val type: String = "p",
    val text: String = "",
    val items: List<String> = emptyList()
)

/** Ein Kurs (Wurzel) oder eine Lektion (beliebig tief verschachtelt). */
data class CourseLesson(
    val id: String = "",
    val title: String = "",
    val blocks: List<CourseBlock> = emptyList(),
    val lessons: List<CourseLesson> = emptyList()
)

/** Eintrag in assets/courses/<sprache>/index.json (Reihenfolge = Anzeigereihenfolge). */
data class CourseIndexEntry(
    val id: String = "",
    val title: String = "",
    val summary: String = "",
    val file: String = ""
)
