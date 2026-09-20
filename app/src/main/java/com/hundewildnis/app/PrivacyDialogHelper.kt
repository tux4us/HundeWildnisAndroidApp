package com.hundewildnis.app

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Helper-Klasse für Datenschutz-Dialoge
 * Zeigt Feldauswahl für QR/PDF/Teilen
 */
object PrivacyDialogHelper {

    /**
     * Zeigt Dialog zur Feldauswahl
     * 
     * @param context Android Context
     * @param profile Das DogProfile
     * @param title Dialog-Titel
     * @param onConfirm Callback mit ausgewählten Feld-Keys
     */
    fun showFieldSelectionDialog(
        context: Context,
        profile: DogProfile,
        title: String,
        onConfirm: (Set<String>) -> Unit
    ) {
        val isDark = (context.resources.configuration.uiMode and 
                     Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        val buttonColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        val allFields = profile.getAllFields(context)
        val checkBoxes = mutableMapOf<String, CheckBox>()
        
        // Layout erstellen
        val scrollView = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        
        // ✅ "ALLES AUSWÄHLEN" Button
        val selectAllCheckbox = CheckBox(context).apply {
            text = "✅ ALLES AUSWÄHLEN"
            textSize = 16f
            setTextColor(textColor)
            setPadding(0, 20, 0, 30)
            setOnCheckedChangeListener { _, isChecked ->
                checkBoxes.values.forEach { it.isChecked = isChecked }
            }
        }
        layout.addView(selectAllCheckbox)
        
        // Felder gruppiert nach Kategorien
        val categories = mapOf(
            "📋 BASIS-DATEN" to listOf("name", "breed", "gender", "neutered", "birthDate", "originCountry", "color"),
            "🔖 IDENTIFIKATION" to listOf("chipNumber", "taxNumber", "tassoNumber", "petPassportCountry", "petPassportNumber"),
            "💉 GESUNDHEIT" to listOf("vaccinationStatus", "additionalVaccination", "lastDeworming", "lastFleaTick", 
                                      "weight", "weightDate", "shoulderHeight", "bodyLength", "medicalNotes"),
            "👨‍⚕️ KONTAKTE" to listOf("veterinarian", "liabilityInsurance", "healthInsurance", "owner", "additionalEmergencyContact"),
            "🍖 FUTTER & VERHALTEN" to listOf("food", "behavior")
        )
        
        categories.forEach { (categoryName, fieldKeys) ->
            // Kategorie-Überschrift
            val categoryHeader = android.widget.TextView(context).apply {
                text = categoryName
                textSize = 14f
                setTextColor(textColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 20, 0, 10)
            }
            layout.addView(categoryHeader)
            
            // Felder der Kategorie
            fieldKeys.forEach { key ->
                allFields[key]?.let { (label, value) ->
                    val checkbox = CheckBox(context).apply {
                        text = label
                        textSize = 14f
                        setTextColor(textColor)
                        isChecked = value.isNotEmpty() // Standard: Nur ausgefüllte Felder
                        setPadding(20, 8, 0, 8)
                    }
                    checkBoxes[key] = checkbox
                    layout.addView(checkbox)
                }
            }
        }
        
        scrollView.addView(layout)
        
        // Dialog erstellen
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("Weiter") { _, _ ->
                val selectedFields = checkBoxes.filter { it.value.isChecked }.keys
                onConfirm(selectedFields)
            }
            .setNegativeButton("Abbrechen", null)
            .create()
        
        dialog.show()
        
        // Button-Farben anpassen
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    }
}
