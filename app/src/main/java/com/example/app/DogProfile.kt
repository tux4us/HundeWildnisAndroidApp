package com.hundewildnis.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DogProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    
    // Basis-Daten
    var name: String = "",
    var breed: String = "",
    var gender: String = "", // "Rüde" / "Hündin"
    var neutered: String = "", // "Ja" / "Nein" / "Unbekannt"
    var birthDate: String = "",
    var color: String = "",
    
    // Identifikation
    var chipNumber: String = "",
    var taxNumber: String = "",
    var tassoNumber: String = "",
    
    // Gesundheit
    var vaccinationStatus: String = "",
    var lastDeworming: String = "",
    var lastFleaTick: String = "",
    var weight: String = "",
    var weightDate: String = "",
    var medicalNotes: String = "",
    
    // Kontakte
    var veterinarian: String = "",
    var insurance: String = "",
    var owner: String = "",
    var emergencyContact: String = "",
    
    // Sonstiges
    var food: String = "",
    var behavior: String = "",
    
    // Foto
    var photoUri: String = ""
) : Parcelable {
    
    fun isComplete(): Boolean {
        return name.isNotEmpty() && breed.isNotEmpty()
    }
    
    fun toQRString(): String {
        return """
            |🐕 HUNDE-STAMMDATEN
            |
            |NAME: $name
            |RASSE: $breed
            |GESCHLECHT: $gender
            |KASTRIERT: $neutered
            |GEBURTSDATUM: $birthDate
            |FARBE: $color
            |
            |📋 IDENTIFIKATION
            |CHIP: $chipNumber
            |STEUERNR: $taxNumber
            |TASSO: $tassoNumber
            |
            |💉 GESUNDHEIT
            |IMPFSTATUS: $vaccinationStatus
            |ENTWURMUNG: $lastDeworming
            |FLOH/ZECKE: $lastFleaTick
            |GEWICHT: $weight (am $weightDate)
            |HINWEISE: $medicalNotes
            |
            |👨‍⚕️ KONTAKTE
            |TIERARZT: $veterinarian
            |VERSICHERUNG: $insurance
            |BESITZER: $owner
            |NOTFALL: $emergencyContact
            |
            |🍖 FUTTER & VERHALTEN
            |FUTTER: $food
            |VERHALTEN: $behavior
        """.trimMargin()
    }
}
