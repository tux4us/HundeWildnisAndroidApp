package com.hundewildnis.app

import android.content.Context
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DogProfile(
    val id: String = java.util.UUID.randomUUID().toString(),

    // ═══════════════════════════════════════════════════════════
    // BASIS-DATEN
    // ═══════════════════════════════════════════════════════════
    var name: String = "",
    var breed: String = "",
    var gender: String = "", // Stabiler interner Code: "male" / "female" (siehe GenderCode)
    var neutered: String = "", // Stabiler interner Code: "yes" / "no" / "unknown" (siehe NeuteredCode)
    var birthDate: String = "",
    // Beginn der gemeinsamen Zeit (Kauf/Übernahme/Pflege), Format wie birthDate:
    // dd.MM.yyyy, MM.yyyy oder yyyy. Leer = Geburtsdatum gilt (siehe TogetherMath.compute).
    var togetherSince: String = "",
    // Stabiler Code aus TogetherSinceType ("birth", "purchase", "adoption", "foster"); leer = birth
    var togetherSinceType: String = "",
    var originCountry: String = "",
    var color: String = "",

    // ═══════════════════════════════════════════════════════════
    // IDENTIFIKATION
    // ═══════════════════════════════════════════════════════════
    var chipNumber: String = "",
    var taxNumber: String = "",
    var tassoNumber: String = "",
    var petPassportCountry: String = "",
    var petPassportNumber: String = "",

    // ═══════════════════════════════════════════════════════════
    // GESUNDHEIT
    // ═══════════════════════════════════════════════════════════
    var vaccinationStatus: String = "",
    var additionalVaccination: String = "",
    var lastDeworming: String = "",
    var lastFleaTick: String = "",
    var weight: String = "",
    var weightDate: String = "",
    var shoulderHeight: String = "",
    var bodyLength: String = "",
    var medicalNotes: String = "",

    // ═══════════════════════════════════════════════════════════
    // KONTAKTE
    // ═══════════════════════════════════════════════════════════
    var veterinarian: String = "",
    var liabilityInsurance: String = "",
    var healthInsurance: String = "",
    var owner: String = "",
    var additionalEmergencyContact: String = "",

    // ═══════════════════════════════════════════════════════════
    // SONSTIGES
    // ═══════════════════════════════════════════════════════════
    var food: String = "",
    var behavior: String = "",

    // ═══════════════════════════════════════════════════════════
    // ✅ WESENSMERKMALE (für Trainingstagebuch) - NEU!
    // ═══════════════════════════════════════════════════════════
    var characterTemperament: Int = 3,      // 1=Sehr ruhig → 5=Sehr lebhaft
    var characterConfidence: Int = 3,       // 1=Ängstlich → 5=Selbstsicher
    var characterSocial: Int = 3,           // 1=Zurückhaltend → 5=Kontaktfreudig
    var characterExcitability: Int = 3,     // 1=Gelassen → 5=Leicht erregbar
    var characterLearning: Int = 3,         // 1=Langsam → 5=Schnell
    var characterFrustration: Int = 3,      // 1=Niedrig → 5=Hoch

    var characterNotes: String = "",        // Freitext für Besonderheiten

    // Foto
    var photoUri: String = "",

    // ═══════════════════════════════════════════════════════════
    // LÄUFIGKEIT
    // Nur befüllt wenn gender == "Hündin" && neutered != "Ja"
    // Gson serialisiert/deserialisiert die Liste automatisch mit.
    // ═══════════════════════════════════════════════════════════
    var heatCycles: List<HeatCycleEntry> = emptyList(),

    // ═══════════════════════════════════════════════════════════
    // VITALWERTE
    // Alle Vitalwert-Messungen dieses Hundes.
    // Werden von VitalManager synchron gehalten.
    // ═══════════════════════════════════════════════════════════
    var vitalRecords: List<VitalRecord> = emptyList(),

    // ═══════════════════════════════════════════════════════════
    // EIGENE TIMELINE-EINTRÄGE
    // Vom Nutzer nachträglich erfasste Stationen (siehe TimelineUserEntry).
    // ═══════════════════════════════════════════════════════════
    var timelineEntries: List<TimelineUserEntry> = emptyList(),
    /** Nachträglich gesetzte Standorte für Timeline-Ereignisse (siehe TimelineLocation.kt). */
    var timelineLocations: List<TimelineLocation> = emptyList(),

    // lastModified bleibt das letzte Feld (bereits vorhanden):
    var lastModified: Long = 0L

) : Parcelable {

    fun isComplete(): Boolean {
        return name.isNotEmpty() && breed.isNotEmpty()
    }

    /**
     * ✅ NEU: Konvertiert zu DogCharacter für Trainingstagebuch
     */
    fun toCharacter(): DogCharacter {
        return DogCharacter(
            temperament = characterTemperament,
            selfConfidence = characterConfidence,
            socialBehavior = characterSocial,
            excitability = characterExcitability,
            learningSpeed = characterLearning,
            frustrationTolerance = characterFrustration
        )
    }

    /**
     * ✅ NEU: Quick-Profil anwenden
     */
    fun applyQuickProfile(profile: QuickCharacterProfile) {
        characterTemperament = profile.temperament
        characterConfidence = profile.confidence
        characterSocial = profile.social
        characterExcitability = profile.excitability
        characterLearning = profile.learning
        characterFrustration = profile.frustration
    }

    /**
     * Gibt ALLE Felder als Map zurück (für Datenschutz-Auswahl)
     */
    fun getAllFields(context: Context): Map<String, Pair<String, String>> {
        return mapOf(
            // BASIS-DATEN
            "name" to Pair(context.getString(R.string.dog_field_name), name),
            "breed" to Pair(context.getString(R.string.dog_field_breed), breed),
            "gender" to Pair(context.getString(R.string.dog_field_gender), GenderCode.displayName(context, gender)),
            "neutered" to Pair(context.getString(R.string.dog_field_neutered), NeuteredCode.displayName(context, neutered)),
            "birthDate" to Pair(context.getString(R.string.dog_field_birth_date), birthDate),
            "originCountry" to Pair(context.getString(R.string.dog_field_origin_country), originCountry),
            "color" to Pair(context.getString(R.string.dog_field_color), color),

            // IDENTIFIKATION
            "chipNumber" to Pair(context.getString(R.string.dog_field_chip_number), chipNumber),
            "taxNumber" to Pair(context.getString(R.string.dog_field_tax_number), taxNumber),
            "tassoNumber" to Pair(context.getString(R.string.dog_field_tasso_number), tassoNumber),
            "petPassportCountry" to Pair(context.getString(R.string.dog_field_pet_passport_country), petPassportCountry),
            "petPassportNumber" to Pair(context.getString(R.string.dog_field_pet_passport_number), petPassportNumber),

            // GESUNDHEIT
            "vaccinationStatus" to Pair(context.getString(R.string.dog_field_vaccination_status), vaccinationStatus),
            "additionalVaccination" to Pair(context.getString(R.string.dog_field_additional_vaccination), additionalVaccination),
            "lastDeworming" to Pair(context.getString(R.string.dog_field_last_deworming), lastDeworming),
            "lastFleaTick" to Pair(context.getString(R.string.dog_field_last_flea_tick), lastFleaTick),
            "weight" to Pair(context.getString(R.string.dog_field_weight), weight),
            "weightDate" to Pair(context.getString(R.string.dog_field_weight_date), weightDate),
            "shoulderHeight" to Pair(context.getString(R.string.dog_field_shoulder_height), shoulderHeight),
            "bodyLength" to Pair(context.getString(R.string.dog_field_body_length), bodyLength),
            "medicalNotes" to Pair(context.getString(R.string.dog_field_medical_notes), medicalNotes),

            // KONTAKTE
            "veterinarian" to Pair(context.getString(R.string.dog_field_veterinarian), veterinarian),
            "liabilityInsurance" to Pair(context.getString(R.string.dog_field_liability_insurance), liabilityInsurance),
            "healthInsurance" to Pair(context.getString(R.string.dog_field_health_insurance), healthInsurance),
            "owner" to Pair(context.getString(R.string.dog_field_owner), owner),
            "additionalEmergencyContact" to Pair(context.getString(R.string.dog_field_additional_emergency_contact), additionalEmergencyContact),

            // SONSTIGES
            "food" to Pair(context.getString(R.string.dog_field_food), food),
            "behavior" to Pair(context.getString(R.string.dog_field_behavior), behavior),
            "photoUri" to Pair(context.getString(R.string.dog_field_photo), photoUri)
        )
    }

    /**
     * Erstellt QR/PDF/Share-String mit ausgewählten Feldern
     */
    fun toFilteredString(context: Context, selectedFields: Set<String>): String {
        val allFields = getAllFields(context)
        val sections = mutableListOf<String>()

        sections.add(context.getString(R.string.dog_export_title))
        sections.add("")

        // BASIS-DATEN
        val basisFields = listOf("name", "breed", "gender", "neutered", "birthDate", "originCountry", "color")
        val basisSelected = basisFields.filter { selectedFields.contains(it) }
        if (basisSelected.isNotEmpty()) {
            sections.add(context.getString(R.string.dog_export_section_basis))
            basisSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) sections.add("$label: $value")
                }
            }
            sections.add("")
        }

        // IDENTIFIKATION
        val idFields = listOf("chipNumber", "taxNumber", "tassoNumber", "petPassportCountry", "petPassportNumber")
        val idSelected = idFields.filter { selectedFields.contains(it) }
        if (idSelected.isNotEmpty()) {
            sections.add(context.getString(R.string.dog_export_section_identification))
            idSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) sections.add("$label: $value")
                }
            }
            sections.add("")
        }

        // GESUNDHEIT
        val healthFields = listOf("vaccinationStatus", "additionalVaccination", "lastDeworming", "lastFleaTick",
            "weight", "weightDate", "shoulderHeight", "bodyLength", "medicalNotes")
        val healthSelected = healthFields.filter { selectedFields.contains(it) }
        if (healthSelected.isNotEmpty()) {
            sections.add(context.getString(R.string.dog_export_section_health))
            healthSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) sections.add("$label: $value")
                }
            }
            sections.add("")
        }

        // KONTAKTE
        val contactFields = listOf("veterinarian", "liabilityInsurance", "healthInsurance", "owner", "additionalEmergencyContact")
        val contactSelected = contactFields.filter { selectedFields.contains(it) }
        if (contactSelected.isNotEmpty()) {
            sections.add(context.getString(R.string.dog_export_section_contacts))
            contactSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) sections.add("$label: $value")
                }
            }
            sections.add("")
        }

        // SONSTIGES
        val miscFields = listOf("food", "behavior")
        val miscSelected = miscFields.filter { selectedFields.contains(it) }
        if (miscSelected.isNotEmpty()) {
            sections.add(context.getString(R.string.dog_export_section_food_behavior))
            miscSelected.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    if (value.isNotEmpty()) sections.add("$label: $value")
                }
            }
        }

        return sections.joinToString("\n")
    }

    /**
     * Legacy-Funktion für Abwärtskompatibilität
     */
    fun toQRString(context: Context): String {
        return toFilteredString(context, getAllFields(context).keys)
    }
}

// ════════════════════════════════════════════════════════════════
// STABILE CODES für Geschlecht / Kastrationsstatus
// Werden intern (Vergleiche, Spinner-Zuordnung, Datenmodell) verwendet,
// damit die App-Logik unabhängig von der UI-Sprache funktioniert.
// Die Anzeige läuft ausschließlich über displayName(context).
// ════════════════════════════════════════════════════════════════
object GenderCode {
    const val MALE = "male"
    const val FEMALE = "female"

    fun displayName(context: Context, code: String): String = when (code) {
        MALE -> context.getString(R.string.dog_gender_male)
        FEMALE -> context.getString(R.string.dog_gender_female)
        else -> ""
    }

    /** Migriert alte, direkt als deutscher Anzeigetext gespeicherte Werte auf den stabilen Code. */
    fun migrate(value: String): String = when (value) {
        "Rüde", MALE -> MALE
        "Hündin", FEMALE -> FEMALE
        else -> value
    }
}

object NeuteredCode {
    const val YES = "yes"
    const val NO = "no"
    const val UNKNOWN = "unknown"

    fun displayName(context: Context, code: String): String = when (code) {
        YES -> context.getString(R.string.dog_neutered_yes)
        NO -> context.getString(R.string.dog_neutered_no)
        UNKNOWN -> context.getString(R.string.dog_neutered_unknown)
        else -> ""
    }

    /** Migriert alte, direkt als deutscher Anzeigetext gespeicherte Werte auf den stabilen Code. */
    fun migrate(value: String): String = when (value) {
        "Ja", YES -> YES
        "Nein", NO -> NO
        "Unbekannt", UNKNOWN -> UNKNOWN
        else -> value
    }
}

// ════════════════════════════════════════════════════════════════
// ✅ QUICK-CHARACTER-PROFILE
// ════════════════════════════════════════════════════════════════

enum class QuickCharacterProfile(
    val temperament: Int,
    val confidence: Int,
    val social: Int,
    val excitability: Int,
    val learning: Int,
    val frustration: Int
) {
    BALANCED(
        temperament = 3, confidence = 3, social = 3,
        excitability = 3, learning = 3, frustration = 3
    ),
    ENERGETIC(
        temperament = 5, confidence = 4, social = 4,
        excitability = 5, learning = 4, frustration = 3
    ),
    CALM(
        temperament = 1, confidence = 3, social = 3,
        excitability = 1, learning = 2, frustration = 4
    ),
    ANXIOUS(
        temperament = 2, confidence = 1, social = 2,
        excitability = 4, learning = 3, frustration = 2
    ),
    CONFIDENT(
        temperament = 4, confidence = 5, social = 4,
        excitability = 3, learning = 4, frustration = 4
    ),
    PUPPY(
        temperament = 4, confidence = 3, social = 4,
        excitability = 5, learning = 4, frustration = 2
    ),
    SENIOR(
        temperament = 2, confidence = 3, social = 3,
        excitability = 2, learning = 2, frustration = 4
    ),
    WORKING_DOG(
        temperament = 4, confidence = 4, social = 3,
        excitability = 3, learning = 5, frustration = 4
    ),
    SOCIAL_BUTTERFLY(
        temperament = 4, confidence = 4, social = 5,
        excitability = 4, learning = 3, frustration = 3
    ),
    INDEPENDENT(
        temperament = 3, confidence = 4, social = 2,
        excitability = 2, learning = 3, frustration = 4
    ),
    RESCUE_DOG(
        temperament = 2, confidence = 2, social = 2,
        excitability = 3, learning = 3, frustration = 2
    );

    fun displayName(context: Context): String = when (this) {
        BALANCED -> context.getString(R.string.character_profile_balanced)
        ENERGETIC -> context.getString(R.string.character_profile_energetic)
        CALM -> context.getString(R.string.character_profile_calm)
        ANXIOUS -> context.getString(R.string.character_profile_anxious)
        CONFIDENT -> context.getString(R.string.character_profile_confident)
        PUPPY -> context.getString(R.string.character_profile_puppy)
        SENIOR -> context.getString(R.string.character_profile_senior)
        WORKING_DOG -> context.getString(R.string.character_profile_working_dog)
        SOCIAL_BUTTERFLY -> context.getString(R.string.character_profile_social_butterfly)
        INDEPENDENT -> context.getString(R.string.character_profile_independent)
        RESCUE_DOG -> context.getString(R.string.character_profile_rescue_dog)
    }

    fun description(context: Context): String = when (this) {
        BALANCED -> context.getString(R.string.character_profile_balanced_desc)
        ENERGETIC -> context.getString(R.string.character_profile_energetic_desc)
        CALM -> context.getString(R.string.character_profile_calm_desc)
        ANXIOUS -> context.getString(R.string.character_profile_anxious_desc)
        CONFIDENT -> context.getString(R.string.character_profile_confident_desc)
        PUPPY -> context.getString(R.string.character_profile_puppy_desc)
        SENIOR -> context.getString(R.string.character_profile_senior_desc)
        WORKING_DOG -> context.getString(R.string.character_profile_working_dog_desc)
        SOCIAL_BUTTERFLY -> context.getString(R.string.character_profile_social_butterfly_desc)
        INDEPENDENT -> context.getString(R.string.character_profile_independent_desc)
        RESCUE_DOG -> context.getString(R.string.character_profile_rescue_dog_desc)
    }

    fun toCharacter(): DogCharacter {
        return DogCharacter(
            temperament = temperament,
            selfConfidence = confidence,
            socialBehavior = social,
            excitability = excitability,
            learningSpeed = learning,
            frustrationTolerance = frustration
        )
    }
}