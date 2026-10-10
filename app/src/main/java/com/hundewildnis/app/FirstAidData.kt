package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE DATA MODELS
// Neu erstellen: FirstAidData.kt
// ════════════════════════════════════════════════════════════════

/**
 * Hauptkategorien
 */
enum class EmergencyMainCategory(val icon: String) {
    ACCIDENT("⚠️"),
    ILLNESS("💉");

    fun displayName(context: android.content.Context): String = when (this) {
        ACCIDENT -> context.getString(R.string.emergency_main_category_accident)
        ILLNESS -> context.getString(R.string.emergency_main_category_illness)
    }
}

/**
 * Unfall-Unterkategorien
 */
enum class AccidentType(
    val icon: String,
    val urgency: UrgencyLevel
) {
    TRAFFIC_ACCIDENT("💥", UrgencyLevel.CRITICAL),
    BLEEDING("🩸", UrgencyLevel.CRITICAL),
    BONE_FRACTURE("🦴", UrgencyLevel.URGENT),
    EYE_INJURY("👁️", UrgencyLevel.URGENT),
    DROWNING("💧", UrgencyLevel.CRITICAL),
    HYPOTHERMIA("🥶", UrgencyLevel.URGENT),
    HEAT_STROKE("🌡️", UrgencyLevel.CRITICAL),
    FOREIGN_OBJECT("🍽️", UrgencyLevel.URGENT),
    POISONING_KNOWN("🧪", UrgencyLevel.CRITICAL);

    fun displayName(context: android.content.Context): String = when (this) {
        TRAFFIC_ACCIDENT -> context.getString(R.string.accident_type_traffic_accident)
        BLEEDING -> context.getString(R.string.accident_type_bleeding)
        BONE_FRACTURE -> context.getString(R.string.accident_type_bone_fracture)
        EYE_INJURY -> context.getString(R.string.accident_type_eye_injury)
        DROWNING -> context.getString(R.string.accident_type_drowning)
        HYPOTHERMIA -> context.getString(R.string.accident_type_hypothermia)
        HEAT_STROKE -> context.getString(R.string.accident_type_heat_stroke)
        FOREIGN_OBJECT -> context.getString(R.string.accident_type_foreign_object)
        POISONING_KNOWN -> context.getString(R.string.accident_type_poisoning_known)
    }
}

/**
 * Erkrankungs-Unterkategorien
 */
enum class IllnessType(
    val icon: String,
    val urgency: UrgencyLevel
) {
    UNCONSCIOUSNESS("💫", UrgencyLevel.CRITICAL),
    SEIZURE("⚡", UrgencyLevel.CRITICAL),
    GASTRIC_TORSION("🌀", UrgencyLevel.CRITICAL),
    BREATHING_DIFFICULTY("🫁", UrgencyLevel.CRITICAL),
    BLEEDING_NO_ACCIDENT("🩸", UrgencyLevel.CRITICAL),
    VOMITING("🤮", UrgencyLevel.URGENT),
    POISONING_UNKNOWN("☠️", UrgencyLevel.CRITICAL),
    ALLERGIC_REACTION("💊", UrgencyLevel.URGENT);

    fun displayName(context: android.content.Context): String = when (this) {
        UNCONSCIOUSNESS -> context.getString(R.string.illness_type_unconsciousness)
        SEIZURE -> context.getString(R.string.illness_type_seizure)
        GASTRIC_TORSION -> context.getString(R.string.illness_type_gastric_torsion)
        BREATHING_DIFFICULTY -> context.getString(R.string.illness_type_breathing_difficulty)
        BLEEDING_NO_ACCIDENT -> context.getString(R.string.illness_type_bleeding_no_accident)
        VOMITING -> context.getString(R.string.illness_type_vomiting)
        POISONING_UNKNOWN -> context.getString(R.string.illness_type_poisoning_unknown)
        ALLERGIC_REACTION -> context.getString(R.string.illness_type_allergic_reaction)
    }
}

/**
 * Dringlichkeitsstufen
 */
enum class UrgencyLevel(val color: String) {
    CRITICAL("#FF6F60"),
    URGENT("#FFB74D"),
    MODERATE("#6FBF73");

    fun displayName(context: android.content.Context): String = when (this) {
        CRITICAL -> context.getString(R.string.urgency_level_critical)
        URGENT -> context.getString(R.string.urgency_level_urgent)
        MODERATE -> context.getString(R.string.urgency_level_moderate)
    }
}

/**
 * Erste-Hilfe Anleitung
 */
data class FirstAidInstruction(
    val type: String,  // z.B. "UNCONSCIOUSNESS" — stabiler, sprachneutraler Schlüssel
    @androidx.annotation.StringRes val titleRes: Int,
    val urgency: UrgencyLevel,
    @androidx.annotation.StringRes val symptomsRes: List<Int>,
    @androidx.annotation.StringRes val immediateActionsRes: List<Int>,
    @androidx.annotation.StringRes val doListRes: List<Int>,
    @androidx.annotation.StringRes val dontListRes: List<Int>,
    @androidx.annotation.StringRes val transportInfoRes: Int,
    @androidx.annotation.StringRes val specialNotesRes: Int? = null,
    val needsHLW: Boolean = false,
    val needsTimer: Boolean = false,
    val timerMinutes: Int = 0
) {
    fun title(context: android.content.Context): String = context.getString(titleRes)
    fun symptoms(context: android.content.Context): List<String> = symptomsRes.map { context.getString(it) }
    fun immediateActions(context: android.content.Context): List<String> = immediateActionsRes.map { context.getString(it) }
    fun doList(context: android.content.Context): List<String> = doListRes.map { context.getString(it) }
    fun dontList(context: android.content.Context): List<String> = dontListRes.map { context.getString(it) }
    fun transportInfo(context: android.content.Context): String = context.getString(transportInfoRes)
    fun specialNotes(context: android.content.Context): String? = specialNotesRes?.let { context.getString(it) }
}

/**
 * HLW (Herz-Lungen-Wiederbelebung) Daten
 */
data class HLWData(
    val compressionRate: Int = 120,  // pro Minute
    val ventilationRate: Int = 2,     // nach 30 Kompressionen
    @androidx.annotation.StringRes val compressionDepthRes: Int = R.string.hlw_compression_depth_default,
    val checkPulseEveryMinutes: Int = 2
) {
    fun compressionDepth(context: android.content.Context): String = context.getString(compressionDepthRes)
}

/**
 * Symptom-Check Frage
 */
data class SymptomCheckQuestion(
    @androidx.annotation.StringRes val questionRes: Int,
    val criticalIfNo: Boolean = false,  // Wenn "Nein" → KRITISCH
    val criticalIfYes: Boolean = false   // Wenn "Ja" → KRITISCH
) {
    fun question(context: android.content.Context): String = context.getString(questionRes)
}

/**
 * Vitalwerte Normalbereich
 */
data class VitalSigns(
    val pulseMin: Int = 60,
    val pulseMax: Int = 120,
    val pulseMiniDogsMax: Int = 140,
    val tempMin: Double = 38.0,
    val tempMax: Double = 39.0,
    val breathingMin: Int = 15,
    val breathingMax: Int = 30,
    val capillaryRefillMaxSeconds: Int = 2
)

// ════════════════════════════════════════════════════════════════
// ERWEITERTE EmergencyReport Data Class
// ════════════════════════════════════════════════════════════════

data class EmergencyReportExtended(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long,
    val dogId: String,
    val dogName: String,

    // Bestehende Felder
    val symptom: String,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    val photoUris: List<String>,
    val pdfPath: String,
    val contentUri: String? = null,

    // ✅ NEUE Felder für Erste-Hilfe
    val wasFirstAidUsed: Boolean = false,
    val emergencyType: String? = null,  // z.B. "UNCONSCIOUSNESS"
    val mainCategory: String? = null,   // "ACCIDENT" oder "ILLNESS"
    val firstAidInstructions: String? = null,  // Komplette Anleitung
    val symptomCheckResults: Map<String, Boolean>? = null  // Frage → Antwort
)
