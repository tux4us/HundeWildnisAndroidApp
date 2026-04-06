package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE DATA MODELS
// Neu erstellen: FirstAidData.kt
// ════════════════════════════════════════════════════════════════

/**
 * Hauptkategorien
 */
enum class EmergencyMainCategory(val displayName: String, val icon: String) {
    ACCIDENT("Unfall", "⚠\uFE0F"),
    ILLNESS("Erkrankung", "\uD83D\uDC89")
}

/**
 * Unfall-Unterkategorien
 */
enum class AccidentType(
    val displayName: String,
    val icon: String,
    val urgency: UrgencyLevel
) {
    TRAFFIC_ACCIDENT("Verkehrsunfall", "💥", UrgencyLevel.CRITICAL),
    BLEEDING("Blutungen / Wunden", "🩸", UrgencyLevel.CRITICAL),
    BONE_FRACTURE("Knochenbruch / Lahmheit", "🦴", UrgencyLevel.URGENT),
    EYE_INJURY("Augenverletzung", "👁️", UrgencyLevel.URGENT),
    DROWNING("Ertrinken / Wasserunfall", "💧", UrgencyLevel.CRITICAL),
    HYPOTHERMIA("Unterkühlung / Eis", "🥶", UrgencyLevel.URGENT),
    HEAT_STROKE("Hitzschlag", "🌡️", UrgencyLevel.CRITICAL),
    FOREIGN_OBJECT("Fremdkörper verschluckt", "🍽️", UrgencyLevel.URGENT),
    POISONING_KNOWN("Vergiftung (Substanz bekannt)", "🧪", UrgencyLevel.CRITICAL)
}

/**
 * Erkrankungs-Unterkategorien
 */
enum class IllnessType(
    val displayName: String,
    val icon: String,
    val urgency: UrgencyLevel
) {
    UNCONSCIOUSNESS("Bewusstlosigkeit / Kollaps", "💫", UrgencyLevel.CRITICAL),
    SEIZURE("Krampfanfall", "⚡", UrgencyLevel.CRITICAL),
    GASTRIC_TORSION("Magendrehung", "🌀", UrgencyLevel.CRITICAL),
    BREATHING_DIFFICULTY("Atemnot", "🫁", UrgencyLevel.CRITICAL),
    BLEEDING_NO_ACCIDENT("Blutungen (ohne Unfall)", "🩸", UrgencyLevel.CRITICAL),
    VOMITING("Erbrechen / Durchfall", "🤮", UrgencyLevel.URGENT),
    POISONING_UNKNOWN("Vergiftung (unbekannt)", "☠️", UrgencyLevel.CRITICAL),
    ALLERGIC_REACTION("Allergische Reaktion", "💊", UrgencyLevel.URGENT)
}

/**
 * Dringlichkeitsstufen
 */
enum class UrgencyLevel(val displayName: String, val color: String) {
    CRITICAL("🔴 LEBENSBEDROHLICH", "#d32f2f"),
    URGENT("🟡 DRINGEND", "#f57c00"),
    MODERATE("🟢 BEOBACHTEN", "#388e3c")
}

/**
 * Erste-Hilfe Anleitung
 */
data class FirstAidInstruction(
    val type: String,  // z.B. "UNCONSCIOUSNESS"
    val title: String,
    val urgency: UrgencyLevel,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val doList: List<String>,
    val dontList: List<String>,
    val transportInfo: String,
    val specialNotes: String? = null,
    val needsHLW: Boolean = false,
    val needsTimer: Boolean = false,
    val timerMinutes: Int = 0
)

/**
 * HLW (Herz-Lungen-Wiederbelebung) Daten
 */
data class HLWData(
    val compressionRate: Int = 120,  // pro Minute
    val ventilationRate: Int = 2,     // nach 30 Kompressionen
    val compressionDepth: String = "1/3 bis 1/2 der Brusttiefe",
    val checkPulseEveryMinutes: Int = 2
)

/**
 * Symptom-Check Frage
 */
data class SymptomCheckQuestion(
    val question: String,
    val criticalIfNo: Boolean = false,  // Wenn "Nein" → KRITISCH
    val criticalIfYes: Boolean = false   // Wenn "Ja" → KRITISCH
)

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
