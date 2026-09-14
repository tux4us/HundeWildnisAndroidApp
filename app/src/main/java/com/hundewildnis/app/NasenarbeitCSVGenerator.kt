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

object NasenarbeitCSVGenerator {

    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)

    fun exportSessions(context: Context, profile: DogProfile, sessions: List<NasenarbeitSession>): File? {
        if (sessions.isEmpty()) return null

        val sb = StringBuilder()
        // Header
        sb.append(context.getString(R.string.nasenarbeit_csv_header)).append("\n")

        sessions.forEach { s ->
            val p = s.parameter
            sb.append(df.format(Date(s.datum))).append(";")
            sb.append(s.durchgefuehrteDisziplin.displayName(context)).append(";")
            sb.append(s.empfohleneDisziplin.displayName(context)).append(";")
            sb.append(s.erfolgsbewertung).append(";")
            sb.append(s.durchgefuehrteSchritte ?: "").append(";")
            sb.append(s.durchgefuehrteHa?.let { String.format("%.1f", it) } ?: "").append(";")
            sb.append(s.suchdauerMinuten ?: "").append(";")
            sb.append(s.gefundeneGegenstaende ?: "").append(";")
            sb.append(s.gegenstaendeGesamt ?: "").append(";")
            sb.append(s.personenfundErfolgreich?.let { if(it) context.getString(R.string.yes) else context.getString(R.string.no) } ?: "").append(";")
            sb.append(p.temperaturCelsius).append(";")
            sb.append(p.beaufort).append(";")
            sb.append(p.luftfeuchtigkeit).append(";")
            sb.append(context.resources.getStringArray(R.array.nasenarbeit_wind_directions)[p.windrichtung]).append(";")
            // Level-Werte bewusst als stabiler Enum-Name (z. B. "ANFAENGER"), nicht übersetzt:
            // maschinenlesbar und unabhängig von der App-Sprache zum Exportzeitpunkt.
            sb.append(s.levelVerlorensuche).append(";")
            sb.append(s.levelPersonensuche).append(";")
            sb.append(s.levelFreisuche).append(";")
            sb.append(s.notizen.replace(";", ",").replace("\n", " ")).append("\n")
        }

        val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.GERMAN).format(Date())
        val fileName = "Nasenarbeit_Export_${safeName}_$timestamp.csv"

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