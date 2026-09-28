package com.hundewildnis.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object SpielSpassCSVGenerator {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

    fun exportSessions(context: Context, profile: DogProfile, sessions: List<SpielSession>): File? {
        if (sessions.isEmpty()) return null

        val sb = StringBuilder()
        sb.append(context.getString(R.string.spiel_spass_csv_header)).append("\n")

        sessions.sortedByDescending { it.datum }.forEach { s ->
            val aktivitaet = SpielAktivitaetCatalog.findById(s.aktivitaetId)
            sb.append(df.format(Date(s.datum))).append(";")
            sb.append(aktivitaet?.name(context) ?: s.aktivitaetId).append(";")
            sb.append(s.ort.displayName(context)).append(";")
            sb.append(aktivitaet?.fokus?.joinToString(", ") { it.displayName(context) } ?: "").append(";")
            sb.append(s.dauerMinuten ?: "").append(";")
            sb.append(s.bewertung).append(";")
            sb.append(s.notizen.replace(";", ",").replace("\n", " ")).append("\n")
        }

        val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date())
        val fileName = "SpielSpass_Export_${safeName}_$timestamp.csv"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportCsvViaMediaStore(context, fileName, sb.toString())
        } else {
            exportCsvOldWay(fileName, sb.toString())
        }
    }

    private fun exportCsvViaMediaStore(context: Context, fileName: String, content: String): File? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/HundeWildnis")
        }

        val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        } catch (e: Exception) {
            return null
        }

        var filePath: String? = null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (columnIndex >= 0) filePath = cursor.getString(columnIndex)
            }
        }
        return if (filePath != null) File(filePath) else null
    }

    private fun exportCsvOldWay(fileName: String, content: String): File? {
        val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "HundeWildnis")
        if (!docsDir.exists()) docsDir.mkdirs()
        val file = File(docsDir, fileName)
        return try {
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
