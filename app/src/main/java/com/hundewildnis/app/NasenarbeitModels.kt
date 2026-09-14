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
    val icon: String
) : Parcelable {
    VERLORENSUCHE("🐾"),
    PERSONENSUCHE("🧑"),
    FREISUCHE("🔍");

    /** Sprachabhängiger Anzeigename – wird zur Laufzeit aus strings.xml gezogen.
     *  NIEMALS für Vergleiche oder Speicherung nutzen, dafür den Enum-Namen (VERLORENSUCHE etc.) verwenden. */
    fun displayName(context: android.content.Context): String = when (this) {
        VERLORENSUCHE -> context.getString(R.string.nasenarbeit_disziplin_verlorensuche)
        PERSONENSUCHE -> context.getString(R.string.nasenarbeit_disziplin_personensuche)
        FREISUCHE -> context.getString(R.string.nasenarbeit_disziplin_freisuche)
    }

    /** Sprachabhängige Beschreibung – wird zur Laufzeit aus strings.xml gezogen. */
    fun description(context: android.content.Context): String = when (this) {
        VERLORENSUCHE -> context.getString(R.string.nasenarbeit_disziplin_verlorensuche_desc)
        PERSONENSUCHE -> context.getString(R.string.nasenarbeit_disziplin_personensuche_desc)
        FREISUCHE -> context.getString(R.string.nasenarbeit_disziplin_freisuche_desc)
    }
}

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – TRAININGSLEVEL
// ════════════════════════════════════════════════════════════════

enum class NasenarbeitLevel(
    val minSessions: Int,
    val maxSessions: Int,
    val maxSchritte_V: Int,   // Verlorensuche
    val maxSchritte_P: Int,   // Personensuche
    val maxHa_F: Double,       // Freisuche
    val steigerung: Double
) {
    ANFAENGER(0, 49, 100, 100, 0.2, 0.20),
    FORTGESCHRITTEN(50, 99, 250, 250, 0.5, 0.15),
    ERFAHREN(100, 499, 500, 1000, 2.0, 0.10),
    EXPERTE(500, Int.MAX_VALUE, 1000, 2000, 5.0, 0.05);

    /** Sprachabhängiger Anzeigename – wird zur Laufzeit aus strings.xml gezogen.
     *  NIEMALS für Vergleiche oder Speicherung nutzen, dafür den Enum-Namen (ANFAENGER etc.) verwenden. */
    fun displayName(context: android.content.Context): String = when (this) {
        ANFAENGER -> context.getString(R.string.nasenarbeit_level_anfaenger)
        FORTGESCHRITTEN -> context.getString(R.string.nasenarbeit_level_fortgeschritten)
        ERFAHREN -> context.getString(R.string.nasenarbeit_level_erfahren)
        EXPERTE -> context.getString(R.string.nasenarbeit_level_experte)
    }

    /** Sprachabhängige Schwierigkeitsangabe – wird zur Laufzeit aus strings.xml gezogen. */
    fun schwierigkeit(context: android.content.Context): String = when (this) {
        ANFAENGER -> context.getString(R.string.nasenarbeit_level_schwierigkeit_leicht)
        FORTGESCHRITTEN -> context.getString(R.string.nasenarbeit_level_schwierigkeit_mittel)
        ERFAHREN -> context.getString(R.string.nasenarbeit_level_schwierigkeit_mittel_schwer)
        EXPERTE -> context.getString(R.string.nasenarbeit_level_schwierigkeit_schwer)
    }

    companion object {
        fun fromSessions(sessions: Int): NasenarbeitLevel =
            values().find { sessions in it.minSessions..it.maxSessions } ?: ANFAENGER

        /** Migration: löst alte, bereits als Text gespeicherte Level-Namen (Deutsch,
         *  aus Backups vor der Sprachumstellung) robust auf den stabilen Enum-Wert auf. */
        fun fromLegacyStoredName(name: String): NasenarbeitLevel = when (name) {
            "Anfänger" -> ANFAENGER
            "Fortgeschritten" -> FORTGESCHRITTEN
            "Erfahren" -> ERFAHREN
            "Experte" -> EXPERTE
            else -> values().find { it.name == name } ?: ANFAENGER
        }
    }
}

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – GELÄNDEABSCHNITT (Schicht 2+3 kombiniert)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class GelaendeAbschnitt(
    @SerializedName("umgebung") val umgebung: String = "",
    @SerializedName("bodenTyp") val bodenTyp: String = "",
    @SerializedName("bodenZustand") val bodenZustand: String = "",
    @SerializedName("laengeSchritte") val laengeSchritte: Int? = null,   // null = keine Angabe
    @SerializedName("flaechenanteilProzent") val flaechenanteilProzent: Double? = null,
    @SerializedName("flaechenM2") val flaechenM2: Double? = null
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
    @SerializedName("letzteEinheit") val letzteEinheit: String = "",
    @SerializedName("tagesverfassung") val tagesverfassung: String = "",
    @SerializedName("letzteLeistungSchritte") val letzteLeistungSchritte: Int = 0,
    @SerializedName("letzteLeistungHa") val letzteLeistungHa: Double = 0.0
) : Parcelable

// ════════════════════════════════════════════════════════════════
// NASENARBEIT – SESSION (Ergebnis einer Trainingseinheit)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class NasenarbeitSession(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("dogId") val dogId: String = "",
    @SerializedName("datum") val datum: Long = System.currentTimeMillis(),

    // Empfohlene + durchgeführte Disziplin
    @SerializedName("empfohleneDisziplin") val empfohleneDisziplin: NasenarbeitDisziplin = NasenarbeitDisziplin.VERLORENSUCHE,
    @SerializedName("durchgefuehrteDisziplin") val durchgefuehrteDisziplin: NasenarbeitDisziplin = NasenarbeitDisziplin.VERLORENSUCHE,

    // Scoring-Ergebnis
    @SerializedName("scoreVerlorensuche") val scoreVerlorensuche: Double = 0.0,
    @SerializedName("scorePersonensuche") val scorePersonensuche: Double = 0.0,
    @SerializedName("scoreFreisuche") val scoreFreisuche: Double = 0.0,

    // Parameter der Session
    @SerializedName("parameter") val parameter: NasenarbeitParameter = NasenarbeitParameter(),

    // Trainingsstand zur Zeit der Session
    // WICHTIG: Speichert den stabilen Enum-Namen (z. B. "ANFAENGER"), NICHT den
    // sprachabhängigen Anzeigetext. Für Anzeige: NasenarbeitLevel.valueOf(...).displayName(context)
    @SerializedName("levelVerlorensuche") val levelVerlorensuche: String = NasenarbeitLevel.ANFAENGER.name,
    @SerializedName("levelPersonensuche") val levelPersonensuche: String = NasenarbeitLevel.ANFAENGER.name,
    @SerializedName("levelFreisuche") val levelFreisuche: String = NasenarbeitLevel.ANFAENGER.name,

    // Ergebnisse
    @SerializedName("durchgefuehrteSchritte") val durchgefuehrteSchritte: Int? = null,
    @SerializedName("durchgefuehrteHa") val durchgefuehrteHa: Double? = null,
    @SerializedName("durchgefuehrteM2") val durchgefuehrteM2: Double? = null,
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
    @SerializedName("dogId") val dogId: String = "",
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