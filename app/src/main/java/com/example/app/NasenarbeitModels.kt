package com.hundewildnis.app

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.*

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – DISZIPLINEN
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class NasenarbeitDisziplin(
    val displayName: String,
    val icon: String,
    val description: String
) : Parcelable {
    VERLORENSUCHE(
        "Verlorensuche",
        "🐾",
        "Der Hund folgt dem Fährtenmischgeruch und findet verlorene Gegenstände. Keine zweite Person notwendig."
    ),
    PERSONENSUCHE(
        "Personensuche",
        "🧑",
        "Der Hund folgt dem individuellen Geruch einer Zielperson. Zweite Person notwendig."
    ),
    FREISUCHE(
        "Freisuche",
        "🔍",
        "Der Hund durchsucht ein Gelände selbständig. Bei Personensuche zweite Person notwendig."
    )
}

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – TRAININGSLEVEL
// ════════════════════════════════════════════════════════════════

enum class NasenarbeitLevel(
    val displayName: String,
    val minSessions: Int,
    val maxSessions: Int,
    val maxSchritte_V: Int,   // Verlorensuche
    val maxSchritte_P: Int,   // Personensuche
    val maxHa_F: Double,       // Freisuche
    val steigerung: Double,
    val schwierigkeit: String
) {
    ANFAENGER("Anfänger", 0, 49, 100, 100, 0.2, 0.20, "Leicht"),
    FORTGESCHRITTEN("Fortgeschritten", 50, 99, 250, 250, 0.5, 0.15, "Mittel"),
    ERFAHREN("Erfahren", 100, 499, 500, 1000, 2.0, 0.10, "Mittel–Schwer"),
    EXPERTE("Experte", 500, Int.MAX_VALUE, 1000, 2000, 5.0, 0.05, "Schwer");

    companion object {
        fun fromSessions(sessions: Int): NasenarbeitLevel =
            values().find { sessions in it.minSessions..it.maxSessions } ?: ANFAENGER
    }
}

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – GELÄNDEABSCHNITT (Schicht 2+3 kombiniert)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class GelaendeAbschnitt(
    @SerializedName("umgebung") val umgebung: String,
    @SerializedName("bodenTyp") val bodenTyp: String = "",
    @SerializedName("bodenZustand") val bodenZustand: String = "",
    @SerializedName("laengeSchritte") val laengeSchritte: Int? = null,   // null = keine Angabe
    @SerializedName("flaechenanteilProzent") val flaechenanteilProzent: Double? = null
) : Parcelable

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – TRAININGSPARAMETER (Schicht 1-4 Input)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class NasenarbeitParameter(
    // Schicht 1: Wetter
    @SerializedName("temperaturCelsius") val temperaturCelsius: Double = 15.0,
    @SerializedName("beaufort") val beaufort: Int = 3,
    @SerializedName("wmoCode") val wmoCode: Int = 2,
    @SerializedName("luftfeuchtigkeit") val luftfeuchtigkeit: Int = 70,
    @SerializedName("windrichtung") val windrichtung: Int = 0, // 0=hinten, 1=seite, 2=vorn
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lon") val lon: Double = 0.0,

    // Schicht 2+3: Gelände + Boden (sequenziell)
    @SerializedName("topoSequenz") val topoSequenz: List<String> = emptyList(),
    @SerializedName("gelaendeAbschnitte") val gelaendeAbschnitte: List<GelaendeAbschnitt> = emptyList(),

    // Trainingsgebiet
    @SerializedName("gebietSchritte") val gebietSchritte: Int = 500,
    @SerializedName("gebietHa") val gebietHa: Double = 0.5,

    // Schicht 4: Trainingsstand
    @SerializedName("letzteEinheit") val letzteEinheit: String = "Gestern",
    @SerializedName("tagesverfassung") val tagesverfassung: String = "Normal motiviert",
    @SerializedName("letzteLeistungSchritte") val letzteLeistungSchritte: Int = 0,
    @SerializedName("letzteLeistungHa") val letzteLeistungHa: Double = 0.0
) : Parcelable

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – SESSION (Ergebnis einer Trainingseinheit)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class NasenarbeitSession(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("dogId") val dogId: String,
    @SerializedName("datum") val datum: Long = System.currentTimeMillis(),

    // Empfohlene + durchgeführte Disziplin
    @SerializedName("empfohleneDisziplin") val empfohleneDisziplin: NasenarbeitDisziplin,
    @SerializedName("durchgefuehrteDisziplin") val durchgefuehrteDisziplin: NasenarbeitDisziplin,

    // Scoring-Ergebnis
    @SerializedName("scoreVerlorensuche") val scoreVerlorensuche: Double = 0.0,
    @SerializedName("scorePersonensuche") val scorePersonensuche: Double = 0.0,
    @SerializedName("scoreFreisuche") val scoreFreisuche: Double = 0.0,

    // Parameter der Session
    @SerializedName("parameter") val parameter: NasenarbeitParameter,

    // Trainingsstand zur Zeit der Session
    @SerializedName("levelVerlorensuche") val levelVerlorensuche: String = "Anfänger",
    @SerializedName("levelPersonensuche") val levelPersonensuche: String = "Anfänger",
    @SerializedName("levelFreisuche") val levelFreisuche: String = "Anfänger",

    // Ergebnisse
    @SerializedName("durchgefuehrteSchritte") val durchgefuehrteSchritte: Int? = null,
    @SerializedName("durchgefuehrteHa") val durchgefuehrteHa: Double? = null,
    @SerializedName("suchdauerMinuten") val suchdauerMinuten: Int? = null,
    @SerializedName("gefundeneGegenstaende") val gefundeneGegenstaende: Int? = null,
    @SerializedName("gegenstaendeGesamt") val gegenstaendeGesamt: Int? = null,
    @SerializedName("personenfundErfolgreich") val personenfundErfolgreich: Boolean? = null,
    @SerializedName("erfolgsbewertung") val erfolgsbewertung: Int = 0, // 1-4
    @SerializedName("notizenTitel") val notizenTitel: String = "",
    @SerializedName("notizen") val notizen: String = "",
    @SerializedName("fotoUris") val fotoUris: List<String> = emptyList(),
    @SerializedName("gpsLat") val gpsLat: Double = 0.0,
    @SerializedName("gpsLon") val gpsLon: Double = 0.0
) : Parcelable

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – FORTSCHRITT PRO HUND
// ════════════════════════════════════════════════════════════════

@Parcelize
data class NasenarbeitFortschritt(
    @SerializedName("dogId") val dogId: String,
    @SerializedName("sessionsVerlorensuche") val sessionsVerlorensuche: Int = 0,
    @SerializedName("sessionsPersonensuche") val sessionsPersonensuche: Int = 0,
    @SerializedName("sessionsFreisuche") val sessionsFreisuche: Int = 0,
    @SerializedName("maxSchritte") val maxSchritte: Int = 0,
    @SerializedName("maxHa") val maxHa: Double = 0.0,
    @SerializedName("letzteSession") val letzteSession: Long = 0L
) : Parcelable {
    val levelVerlorensuche get() = NasenarbeitLevel.fromSessions(sessionsVerlorensuche)
    val levelPersonensuche get() = NasenarbeitLevel.fromSessions(sessionsPersonensuche)
    val levelFreisuche get() = NasenarbeitLevel.fromSessions(sessionsFreisuche)
    val gesamtSessions get() = sessionsVerlorensuche + sessionsPersonensuche + sessionsFreisuche
}