package com.hundewildnis.app

import android.os.Parcelable
import androidx.annotation.StringRes
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Gesundheitsbeobachtung eines Hundes (Gesundheitskalender).
 *
 * Wird als Liste in DogProfile.healthObservations gespeichert (Gson, wie heatCycles).
 * Alle Auswahlwerte werden als stabile Codes gespeichert, die Anzeige läuft über
 * die Enums unten (sprachunabhängig, siehe GenderCode in DogProfile.kt).
 */
@Parcelize
data class HealthObservationEntry(

    val id: String = UUID.randomUUID().toString(),

    // Verlauf
    var startDate: String = "",          // "dd.MM.yyyy"
    var endDate: String = "",            // "dd.MM.yyyy" — leer wenn noch nicht abgeklungen
    var status: String = HealthStatus.ACTIVE.code,   // Code aus HealthStatus

    // Inhalt
    var category: String = HealthCategory.OTHER.code, // Code aus HealthCategory
    var symptoms: List<String> = emptyList(),         // Codes aus HealthSymptom
    var severity: String = HealthSeverity.MODERATE.code, // Code aus HealthSeverity
    var notes: String = "",

    // Tierarzt
    var vetVisited: Boolean = false,
    var vetNotes: String = "",           // Diagnose / Behandlung / Medikation

    // Fotos (interner Speicher, siehe HealthPhotos)
    var photoUris: List<String> = emptyList(),

    val createdAt: Long = System.currentTimeMillis()

) : Parcelable {

    val isActive: Boolean
        get() = HealthStatus.fromCode(status) != HealthStatus.RESOLVED

    /** Dauer in Tagen (inkl. Starttag). null wenn nicht berechenbar. */
    val durationDays: Int?
        get() {
            val start = HealthDates.parse(startDate) ?: return null
            val end = HealthDates.parse(endDate) ?: return null
            if (end.before(start)) return null
            return (((end.time - start.time) / (1000L * 60 * 60 * 24)).toInt()) + 1
        }
}

// ════════════════════════════════════════════════════════════════
// STABILE CODES
// ════════════════════════════════════════════════════════════════

enum class HealthStatus(val code: String, @StringRes val labelRes: Int) {
    ACTIVE("active", R.string.health_status_active),
    IMPROVING("improving", R.string.health_status_improving),
    RESOLVED("resolved", R.string.health_status_resolved);

    companion object {
        fun fromCode(code: String): HealthStatus = values().firstOrNull { it.code == code } ?: ACTIVE
    }
}

enum class HealthSeverity(val code: String, @StringRes val labelRes: Int) {
    MILD("mild", R.string.health_severity_mild),
    MODERATE("moderate", R.string.health_severity_moderate),
    SEVERE("severe", R.string.health_severity_severe);

    companion object {
        fun fromCode(code: String): HealthSeverity = values().firstOrNull { it.code == code } ?: MODERATE
    }
}

enum class HealthCategory(val code: String, val emoji: String, @StringRes val labelRes: Int) {
    DIGESTION("digestion", "🍽️", R.string.health_cat_digestion),
    SKIN("skin", "🐾", R.string.health_cat_skin),
    MOVEMENT("movement", "🦴", R.string.health_cat_movement),
    BREATHING("breathing", "🫁", R.string.health_cat_breathing),
    BEHAVIOR("behavior", "🧠", R.string.health_cat_behavior),
    HEAD("head", "👁️", R.string.health_cat_head),
    URINARY("urinary", "💧", R.string.health_cat_urinary),
    OTHER("other", "📝", R.string.health_cat_other);

    fun symptoms(): List<HealthSymptom> = HealthSymptom.values().filter { it.category == this }

    companion object {
        fun fromCode(code: String): HealthCategory = values().firstOrNull { it.code == code } ?: OTHER
    }
}

enum class HealthSymptom(val code: String, val category: HealthCategory, @StringRes val labelRes: Int) {
    // Verdauung
    VOMITING("vomiting", HealthCategory.DIGESTION, R.string.health_sym_vomiting),
    DIARRHEA("diarrhea", HealthCategory.DIGESTION, R.string.health_sym_diarrhea),
    CONSTIPATION("constipation", HealthCategory.DIGESTION, R.string.health_sym_constipation),
    BLOOD_STOOL("blood_stool", HealthCategory.DIGESTION, R.string.health_sym_blood_stool),
    APPETITE_LOSS("appetite_loss", HealthCategory.DIGESTION, R.string.health_sym_appetite_loss),
    INCREASED_THIRST("increased_thirst", HealthCategory.DIGESTION, R.string.health_sym_increased_thirst),
    FLATULENCE("flatulence", HealthCategory.DIGESTION, R.string.health_sym_flatulence),

    // Haut & Fell
    ITCHING("itching", HealthCategory.SKIN, R.string.health_sym_itching),
    HAIR_LOSS("hair_loss", HealthCategory.SKIN, R.string.health_sym_hair_loss),
    REDNESS("redness", HealthCategory.SKIN, R.string.health_sym_redness),
    WOUND("wound", HealthCategory.SKIN, R.string.health_sym_wound),
    LUMP("lump", HealthCategory.SKIN, R.string.health_sym_lump),
    PARASITES("parasites", HealthCategory.SKIN, R.string.health_sym_parasites),
    DANDRUFF("dandruff", HealthCategory.SKIN, R.string.health_sym_dandruff),

    // Bewegung & Gelenke
    LIMPING("limping", HealthCategory.MOVEMENT, R.string.health_sym_limping),
    STIFFNESS("stiffness", HealthCategory.MOVEMENT, R.string.health_sym_stiffness),
    MOVE_RELUCTANT("move_reluctant", HealthCategory.MOVEMENT, R.string.health_sym_move_reluctant),
    STANDING_DIFFICULTY("standing_difficulty", HealthCategory.MOVEMENT, R.string.health_sym_standing_difficulty),
    PAW_LICKING("paw_licking", HealthCategory.MOVEMENT, R.string.health_sym_paw_licking),

    // Atmung & Kreislauf
    COUGHING("coughing", HealthCategory.BREATHING, R.string.health_sym_coughing),
    SNEEZING("sneezing", HealthCategory.BREATHING, R.string.health_sym_sneezing),
    PANTING("panting", HealthCategory.BREATHING, R.string.health_sym_panting),
    LABORED_BREATHING("labored_breathing", HealthCategory.BREATHING, R.string.health_sym_labored_breathing),
    COLLAPSE("collapse", HealthCategory.BREATHING, R.string.health_sym_collapse),

    // Verhalten & Allgemeinbefinden
    LETHARGY("lethargy", HealthCategory.BEHAVIOR, R.string.health_sym_lethargy),
    RESTLESSNESS("restlessness", HealthCategory.BEHAVIOR, R.string.health_sym_restlessness),
    TREMBLING("trembling", HealthCategory.BEHAVIOR, R.string.health_sym_trembling),
    WITHDRAWAL("withdrawal", HealthCategory.BEHAVIOR, R.string.health_sym_withdrawal),
    PAIN_SENSITIVE("pain_sensitive", HealthCategory.BEHAVIOR, R.string.health_sym_pain_sensitive),
    SEIZURE("seizure", HealthCategory.BEHAVIOR, R.string.health_sym_seizure),

    // Augen, Ohren, Maul
    EYE_DISCHARGE("eye_discharge", HealthCategory.HEAD, R.string.health_sym_eye_discharge),
    EYE_REDNESS("eye_redness", HealthCategory.HEAD, R.string.health_sym_eye_redness),
    EAR_SCRATCHING("ear_scratching", HealthCategory.HEAD, R.string.health_sym_ear_scratching),
    EAR_DISCHARGE("ear_discharge", HealthCategory.HEAD, R.string.health_sym_ear_discharge),
    BAD_BREATH("bad_breath", HealthCategory.HEAD, R.string.health_sym_bad_breath),
    GUM_CHANGE("gum_change", HealthCategory.HEAD, R.string.health_sym_gum_change),
    DROOLING("drooling", HealthCategory.HEAD, R.string.health_sym_drooling),

    // Harn- & Geschlechtsorgane
    FREQUENT_URINATION("frequent_urination", HealthCategory.URINARY, R.string.health_sym_frequent_urination),
    URINATION_STRAINING("urination_straining", HealthCategory.URINARY, R.string.health_sym_urination_straining),
    BLOOD_URINE("blood_urine", HealthCategory.URINARY, R.string.health_sym_blood_urine),
    INCONTINENCE("incontinence", HealthCategory.URINARY, R.string.health_sym_incontinence),
    GENITAL_DISCHARGE("genital_discharge", HealthCategory.URINARY, R.string.health_sym_genital_discharge);

    companion object {
        fun fromCode(code: String): HealthSymptom? = values().firstOrNull { it.code == code }
    }
}

object HealthDates {
    private val sdf get() = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).apply { isLenient = false }

    fun parse(text: String): java.util.Date? =
        if (text.isBlank()) null else runCatching { sdf.parse(text) }.getOrNull()

    fun today(): String = sdf.format(java.util.Date())

    fun format(timeMillis: Long): String = sdf.format(java.util.Date(timeMillis))
}
