package com.hundewildnis.app

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
    var gender: String = "", // "Rüde" / "Hündin"
    var neutered: String = "", // "Ja" / "Nein" / "Unbekannt"
    var birthDate: String = "",
    var originCountry: String = "",  // ✅ NEU
    var color: String = "",

    // ═══════════════════════════════════════════════════════════
    // IDENTIFIKATION
    // ═══════════════════════════════════════════════════════════
    var chipNumber: String = "",
    var taxNumber: String = "",
    var tassoNumber: String = "",
    var petPassportCountry: String = "",  // ✅ NEU
    var petPassportNumber: String = "",   // ✅ NEU

    // ═══════════════════════════════════════════════════════════
    // GESUNDHEIT
    // ═══════════════════════════════════════════════════════════
    var vaccinationStatus: String = "",
    var additionalVaccination: String = "",  // ✅ NEU
    var lastDeworming: String = "",
    var lastFleaTick: String = "",
    var weight: String = "",
    var weightDate: String = "",
    var shoulderHeight: String = "",  // ✅ NEU (cm)
    var bodyLength: String = "",      // ✅ NEU (cm)
    var medicalNotes: String = "",

    // ═══════════════════════════════════════════════════════════
    // KONTAKTE
    // ═══════════════════════════════════════════════════════════
    var veterinarian: String = "",
    var liabilityInsurance: String = "",      // ✅ UMBENANNT von "insurance"
    var healthInsurance: String = "",          // ✅ NEU
    var owner: String = "",
    var additionalEmergencyContact: String = "",  // ✅ UMBENANNT von "emergencyContact"

    // ═══════════════════════════════════════════════════════════
    // SONSTIGES
    // ═══════════════════════════════════════════════════════════
    var food: String = "",
    var behavior: String = "",

    // Foto
    var photoUri: String = ""
) : Parcelable {

    fun isComplete(): Boolean {
        return name.isNotEmpty() && breed.isNotEmpty()
    }

    /**
     * Gibt ALLE Felder als Map zurück (für Datenschutz-Auswahl)
     */
    fun getAllFields(): Map<String, Pair<String, String>> {
        return mapOf(
            // BASIS-DATEN
            "name" to Pair("Name", name),
            "breed" to Pair("Rasse", breed),
            "gender" to Pair("Geschlecht", gender),
            "neutered" to Pair("Kastrationsstatus", neutered),
            "birthDate" to Pair("Geburtsdatum", birthDate),
            "originCountry" to Pair("Herkunftsort/-land", originCountry),
            "color" to Pair("Farbe/Fellzeichnung", color),

            // IDENTIFIKATION
            "chipNumber" to Pair("Chip-Nummer", chipNumber),
            "taxNumber" to Pair("Steuernummer", taxNumber),
            "tassoNumber" to Pair("TASSO Nummer", tassoNumber),
            "petPassportCountry" to Pair("Heimtierausweis Land", petPassportCountry),
            "petPassportNumber" to Pair("Heimtierausweis Nummer", petPassportNumber),

            // GESUNDHEIT
            "vaccinationStatus" to Pair("Impfstatus", vaccinationStatus),
            "additionalVaccination" to Pair("Weitere Impfung", additionalVaccination),
            "lastDeworming" to Pair("Letzte Entwurmung", lastDeworming),
            "lastFleaTick" to Pair("Letzte Floh-/Zeckenprophylaxe", lastFleaTick),
            "weight" to Pair("Gewicht", weight),
            "weightDate" to Pair("Gewicht Datum", weightDate),
            "shoulderHeight" to Pair("Schulterhöhe (cm)", shoulderHeight),
            "bodyLength" to Pair("Körperlänge (cm)", bodyLength),
            "medicalNotes" to Pair("Medizinische Hinweise", medicalNotes),

            // KONTAKTE
            "veterinarian" to Pair("Tierarzt", veterinarian),
            "liabilityInsurance" to Pair("Hundehaftpflichtversicherung", liabilityInsurance),
            "healthInsurance" to Pair("Hundekranken-/OP-Versicherung", healthInsurance),
            "owner" to Pair("Besitzer", owner),
            "additionalEmergencyContact" to Pair("Zusätzlicher Notfallkontakt", additionalEmergencyContact),

            // SONSTIGES
            "food" to Pair("Futter", food),
            "behavior" to Pair("Verhalten", behavior),
            "photoUri" to Pair("Foto", photoUri)
        )
    }

    /**
     * Erstellt QR/PDF/Share-String mit ausgewählten Feldern
     */
    fun toFilteredString(selectedFields: Set<String>): String {
        val allFields = getAllFields()
        val sections = mutableListOf<String>()

        sections.add("🐕 HUNDE-STAMMDATEN")
        sections.add("")

        // BASIS-DATEN
        val basisFields = listOf("name", "breed", "gender", "neutered", "birthDate", "originCountry", "color")
        val basisSelected = basisFields.filter { selectedFields.contains(it) }
        if (basisSelected.isNotEmpty()) {
            sections.add("📋 BASIS-DATEN")
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
            sections.add("🔖 IDENTIFIKATION")
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
            sections.add("💉 GESUNDHEIT")
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
            sections.add("👨‍⚕️ KONTAKTE")
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
            sections.add("🍖 FUTTER & VERHALTEN")
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
    fun toQRString(): String {
        return toFilteredString(getAllFields().keys)
    }
}
