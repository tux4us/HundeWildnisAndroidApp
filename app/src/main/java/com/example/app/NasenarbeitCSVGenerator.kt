package com.hundewildnis.app

import android.content.Context
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
        sb.append("Datum;Disziplin;Empfohlen;Bewertung;Schritte;Ha;Minuten;Gefunden;Gesamt;Erfolg;Temperatur;Beaufort;Feuchtigkeit;Windrichtung;LevelV;LevelP;LevelF;Notizen\n")

        sessions.forEach { s ->
            val p = s.parameter
            sb.append(df.format(Date(s.datum))).append(";")
            sb.append(s.durchgefuehrteDisziplin.displayName).append(";")
            sb.append(s.empfohleneDisziplin.displayName).append(";")
            sb.append(s.erfolgsbewertung).append(";")
            sb.append(s.durchgefuehrteSchritte ?: "").append(";")
            sb.append(s.durchgefuehrteHa?.let { String.format("%.1f", it) } ?: "").append(";")
            sb.append(s.suchdauerMinuten ?: "").append(";")
            sb.append(s.gefundeneGegenstaende ?: "").append(";")
            sb.append(s.gegenstaendeGesamt ?: "").append(";")
            sb.append(s.personenfundErfolgreich?.let { if(it) "Ja" else "Nein" } ?: "").append(";")
            sb.append(p.temperaturCelsius).append(";")
            sb.append(p.beaufort).append(";")
            sb.append(p.luftfeuchtigkeit).append(";")
            sb.append(listOf("Rücken","Seite","Gegen")[p.windrichtung]).append(";")
            sb.append(s.levelVerlorensuche).append(";")
            sb.append(s.levelPersonensuche).append(";")
            sb.append(s.levelFreisuche).append(";")
            sb.append(s.notizen.replace(";", ",").replace("\n", " ")).append("\n")
        }

        return try {
            val safeName = profile.name.replace("\\s+".toRegex(), "_").filter { it.isLetterOrDigit() || it == '_' }
            val filename = "nasenarbeit_export_${safeName}.csv"
            
            val docDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docDir, "HundeWildnis").also { it.mkdirs() }
            val file = File(dir, filename)
            
            FileOutputStream(file).use { it.write(sb.toString().toByteArray()) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
