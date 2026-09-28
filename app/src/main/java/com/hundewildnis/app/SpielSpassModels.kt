package com.hundewildnis.app

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize
import java.util.*

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – ÖRTLICHKEITEN
// ════════════════════════════════════════════════════════════════

/**
 * Örtlichkeit, an der eine Aktivität durchgeführt werden kann.
 * Stabiler Enum-Name wird gespeichert, NIE der Anzeigetext.
 */
@Parcelize
enum class SpielOrt(val icon: String) : Parcelable {
    WALD("🌲"),
    GRUENLAND("🌾"),
    PARK("🌳"),
    GARTEN("🏡"),
    HAUS("🏠"),
    WOHNUNG("🏢");

    fun displayName(context: android.content.Context): String = when (this) {
        WALD -> context.getString(R.string.spiel_ort_wald)
        GRUENLAND -> context.getString(R.string.spiel_ort_gruenland)
        PARK -> context.getString(R.string.spiel_ort_park)
        GARTEN -> context.getString(R.string.spiel_ort_garten)
        HAUS -> context.getString(R.string.spiel_ort_haus)
        WOHNUNG -> context.getString(R.string.spiel_ort_wohnung)
    }

    companion object {
        fun fromDisplayName(context: android.content.Context, text: String): SpielOrt? =
            values().find { it.displayName(context) == text }
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – ZEITBUDGET
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class SpielZeitbudget(val maxMinuten: Int) : Parcelable {
    KURZ(10),       // bis 10 Minuten
    MITTEL(20),     // bis 20 Minuten
    LANG(40),       // bis 40 Minuten
    AUSGIEBIG(Int.MAX_VALUE); // mehr als 40 Minuten möglich

    fun displayName(context: android.content.Context): String = when (this) {
        KURZ -> context.getString(R.string.spiel_zeit_kurz)
        MITTEL -> context.getString(R.string.spiel_zeit_mittel)
        LANG -> context.getString(R.string.spiel_zeit_lang)
        AUSGIEBIG -> context.getString(R.string.spiel_zeit_ausgiebig)
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – MATERIAL
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class SpielMaterial : Parcelable {
    KEINS,
    ALLTAGSGEGENSTAND,
    SPIELZEUG,
    FUTTER_DISPENSER,
    LECKERLI;

    fun displayName(context: android.content.Context): String = when (this) {
        KEINS -> context.getString(R.string.spiel_material_keins)
        ALLTAGSGEGENSTAND -> context.getString(R.string.spiel_material_alltagsgegenstand)
        SPIELZEUG -> context.getString(R.string.spiel_material_spielzeug)
        FUTTER_DISPENSER -> context.getString(R.string.spiel_material_futter_dispenser)
        LECKERLI -> context.getString(R.string.spiel_material_leckerli)
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – FOKUS / ZIEL
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class SpielFokus(val icon: String) : Parcelable {
    NASENARBEIT("👃"),
    BEWEGUNG("🏃"),
    RUHE_ENTSPANNUNG("😌"),
    KOPFARBEIT("🧠"),
    SOZIALKONTAKT("🤝");

    fun displayName(context: android.content.Context): String = when (this) {
        NASENARBEIT -> context.getString(R.string.spiel_fokus_nasenarbeit)
        BEWEGUNG -> context.getString(R.string.spiel_fokus_bewegung)
        RUHE_ENTSPANNUNG -> context.getString(R.string.spiel_fokus_ruhe_entspannung)
        KOPFARBEIT -> context.getString(R.string.spiel_fokus_kopfarbeit)
        SOZIALKONTAKT -> context.getString(R.string.spiel_fokus_sozialkontakt)
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – ALTER / FITNESS-EIGNUNG
// ════════════════════════════════════════════════════════════════

@Parcelize
enum class SpielEignung : Parcelable {
    ALLE,                 // keine bekannte Einschränkung
    NICHT_WELPE,          // Belastung für offene Wachstumsfugen (Sprünge, Stöße, Zerren, Sprints)
    SENIOR_GEEIGNET,      // ausdrücklich schonend, auch für ältere/eingeschränkte Hunde
    NICHT_GELENKPROBLEM;  // bei Arthrose/Gelenkerkrankungen ungeeignet

    fun displayName(context: android.content.Context): String = when (this) {
        ALLE -> context.getString(R.string.spiel_eignung_alle)
        NICHT_WELPE -> context.getString(R.string.spiel_eignung_nicht_welpe)
        SENIOR_GEEIGNET -> context.getString(R.string.spiel_eignung_senior_geeignet)
        NICHT_GELENKPROBLEM -> context.getString(R.string.spiel_eignung_nicht_gelenkproblem)
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – AKTIVITÄT (statischer Katalogeintrag)
// ════════════════════════════════════════════════════════════════

/**
 * Eine einzelne Spiel-/Beschäftigungsidee aus dem statischen Katalog (PlayActivityCatalog).
 * @param id             stabiler, sprachunabhängiger Schlüssel (wird in Sessions/Favoriten gespeichert)
 * @param nameRes        String-Resource für den Anzeigenamen
 * @param beschreibungRes String-Resource für die Beschreibung
 * @param orte           Alle Örtlichkeiten, an denen die Aktivität sinnvoll durchführbar ist
 * @param minDauerMinuten untere Dauer-Schätzung in Minuten
 * @param maxDauerMinuten obere Dauer-Schätzung in Minuten
 * @param material       benötigtes Material
 * @param fokus          ein oder mehrere pädagogische Schwerpunkte
 * @param eignung        Alter-/Fitness-Eignungshinweise
 * @param hinweisRes     optionale String-Resource mit Eignungs-/Sicherheitshinweis (0 = keiner)
 */
data class SpielAktivitaet(
    val id: String,
    val nameRes: Int,
    val beschreibungRes: Int,
    val orte: List<SpielOrt>,
    val minDauerMinuten: Int,
    val maxDauerMinuten: Int,
    val material: SpielMaterial,
    val fokus: List<SpielFokus>,
    val eignung: List<SpielEignung> = listOf(SpielEignung.ALLE),
    val hinweisRes: Int = 0
) {
    fun name(context: android.content.Context): String = context.getString(nameRes)
    fun beschreibung(context: android.content.Context): String = context.getString(beschreibungRes)
    fun hinweis(context: android.content.Context): String? =
        if (hinweisRes != 0) context.getString(hinweisRes) else null

    fun dauerText(context: android.content.Context): String {
        return if (minDauerMinuten == maxDauerMinuten) {
            context.getString(R.string.spiel_dauer_exakt, minDauerMinuten)
        } else {
            context.getString(R.string.spiel_dauer_bereich, minDauerMinuten, maxDauerMinuten)
        }
    }

    fun zeitbudget(): SpielZeitbudget = when {
        maxDauerMinuten <= 10 -> SpielZeitbudget.KURZ
        maxDauerMinuten <= 20 -> SpielZeitbudget.MITTEL
        maxDauerMinuten <= 40 -> SpielZeitbudget.LANG
        else -> SpielZeitbudget.AUSGIEBIG
    }

    fun passtZuOrt(ort: SpielOrt): Boolean = orte.contains(ort)

    fun passtZuZeitbudget(budget: SpielZeitbudget): Boolean = minDauerMinuten <= budget.maxMinuten

    fun passtZuMaterial(verfuegbar: Set<SpielMaterial>): Boolean =
        material == SpielMaterial.KEINS || verfuegbar.contains(material)

    fun passtZuFokus(gewuenscht: Set<SpielFokus>): Boolean =
        gewuenscht.isEmpty() || fokus.any { gewuenscht.contains(it) }

    fun geeignetFuer(welpeOderJunghund: Boolean, senior: Boolean, gelenkproblem: Boolean): Boolean {
        if (welpeOderJunghund && eignung.contains(SpielEignung.NICHT_WELPE)) return false
        if (gelenkproblem && eignung.contains(SpielEignung.NICHT_GELENKPROBLEM)) return false
        return true
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – SESSION (protokollierte Durchführung)
// ════════════════════════════════════════════════════════════════

@Parcelize
data class SpielSession(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("dogId") val dogId: String = "",
    @SerializedName("aktivitaetId") val aktivitaetId: String = "",
    @SerializedName("datum") val datum: Long = System.currentTimeMillis(),
    @SerializedName("ort") val ort: SpielOrt = SpielOrt.GARTEN,
    @SerializedName("dauerMinuten") val dauerMinuten: Int? = null,
    @SerializedName("bewertung") val bewertung: Int = 0, // 1-4, wie TrainingSession.successRating
    @SerializedName("notizen") val notizen: String = ""
) : Parcelable {

    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMAN)
        return sdf.format(java.util.Date(datum))
    }

    fun getBewertungIcon(): String = when (bewertung) {
        1 -> "🔴"
        2 -> "🟡"
        3 -> "🟢"
        4 -> "🟢🟢"
        else -> "⚪"
    }
}

// ════════════════════════════════════════════════════════════════
// SPIEL & SPASS – STATISTIK PRO HUND
// ════════════════════════════════════════════════════════════════

@Parcelize
data class SpielStatistik(
    val dogId: String = "",
    val gesamtSessions: Int = 0,
    val sessionsProOrt: Map<String, Int> = emptyMap(),       // Key = SpielOrt.name
    val sessionsProFokus: Map<String, Int> = emptyMap(),     // Key = SpielFokus.name
    val topAktivitaeten: List<Pair<String, Int>> = emptyList(), // aktivitaetId -> Anzahl, absteigend sortiert
    val letzteSession: Long = 0L,
    val durchschnittsBewertung: Double = 0.0
) : Parcelable
