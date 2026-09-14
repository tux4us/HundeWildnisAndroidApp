package com.hundewildnis.app

import java.util.Calendar

// ════════════════════════════════════════════════════════════════
// GEFAHRENPFLANZEN-SCOUT – Datenbasis
// Datei: ToxicPlantData.kt
// Vollständig lokal / offline – keine API-Abhängigkeit
// ════════════════════════════════════════════════════════════════

// ── Giftigkeitsstufen ────────────────────────────────────────────
enum class PlantToxicityLevel(
    val icon: String,
    val color: String
) {
    SLIGHTLY_TOXIC("🟡", "#F57C00"),
    MODERATELY_TOXIC("🟠", "#E64A19"),
    HIGHLY_TOXIC("🔴", "#C62828"),
    DEADLY("☠️", "#7B0000");

    fun displayName(context: android.content.Context): String = when (this) {
        SLIGHTLY_TOXIC -> context.getString(R.string.plant_toxicity_slightly_toxic)
        MODERATELY_TOXIC -> context.getString(R.string.plant_toxicity_moderately_toxic)
        HIGHLY_TOXIC -> context.getString(R.string.plant_toxicity_highly_toxic)
        DEADLY -> context.getString(R.string.plant_toxicity_deadly)
    }

    fun shortLabel(context: android.content.Context): String = when (this) {
        SLIGHTLY_TOXIC -> context.getString(R.string.plant_toxicity_slightly_toxic_short)
        MODERATELY_TOXIC -> context.getString(R.string.plant_toxicity_moderately_toxic_short)
        HIGHLY_TOXIC -> context.getString(R.string.plant_toxicity_highly_toxic_short)
        DEADLY -> context.getString(R.string.plant_toxicity_deadly_short)
    }
}

// ── Betroffene Körpersysteme ─────────────────────────────────────
enum class PlantBodySystem {
    DIGESTIVE, CARDIOVASCULAR, NERVOUS, LIVER, KIDNEYS, SKIN, RESPIRATORY, BLOOD;

    fun displayName(context: android.content.Context): String = when (this) {
        DIGESTIVE -> context.getString(R.string.plant_body_system_digestive)
        CARDIOVASCULAR -> context.getString(R.string.plant_body_system_cardiovascular)
        NERVOUS -> context.getString(R.string.plant_body_system_nervous)
        LIVER -> context.getString(R.string.plant_body_system_liver)
        KIDNEYS -> context.getString(R.string.plant_body_system_kidneys)
        SKIN -> context.getString(R.string.plant_body_system_skin)
        RESPIRATORY -> context.getString(R.string.plant_body_system_respiratory)
        BLOOD -> context.getString(R.string.plant_body_system_blood)
    }
}

// ── Pflanzendaten-Modell ─────────────────────────────────────────
// WICHTIG: Alle Textfelder sind String-Resource-IDs (R.string.*), keine
// rohen Strings. Anzeige immer über context.getString(id) auflösen.
// So ist die id (z.B. "eibe") weiterhin der stabile, sprachneutrale
// Schlüssel für Suche/Vergleich/Speicherung; nur der ANGEZEIGTE Text
// wird sprachabhängig aus strings.xml gezogen.
data class ToxicPlant(
    val id: String,
    @androidx.annotation.StringRes val nameRes: Int,
    val latinName: String,                              // wissenschaftlicher Name, sprachneutral
    @androidx.annotation.StringRes val alternativeNamesRes: List<Int> = emptyList(),
    val toxicityLevel: PlantToxicityLevel,
    @androidx.annotation.StringRes val toxicPartsRes: List<Int>,
    val affectedSystems: List<PlantBodySystem>,
    @androidx.annotation.StringRes val symptomsRes: List<Int>,
    @androidx.annotation.StringRes val identificationFeaturesRes: List<Int>,
    val bloomMonths: List<Int>,                          // Monate 1–12, sprachneutral
    @androidx.annotation.StringRes val habitatRes: List<Int>,
    @androidx.annotation.StringRes val firstAidStepsRes: List<Int>,
    val mustCallVet: Boolean,
    @androidx.annotation.StringRes val confusionRiskRes: Int? = null,
    @androidx.annotation.StringRes val noteRes: Int? = null
) {
    /** Ist diese Pflanze im aktuellen Monat besonders relevant? */
    fun isCurrentlySeasonal(): Boolean {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return month in bloomMonths
    }

    fun name(context: android.content.Context): String = context.getString(nameRes)
    fun alternativeNames(context: android.content.Context): List<String> = alternativeNamesRes.map { context.getString(it) }
    fun toxicParts(context: android.content.Context): List<String> = toxicPartsRes.map { context.getString(it) }
    fun symptoms(context: android.content.Context): List<String> = symptomsRes.map { context.getString(it) }
    fun identificationFeatures(context: android.content.Context): List<String> = identificationFeaturesRes.map { context.getString(it) }
    fun habitat(context: android.content.Context): List<String> = habitatRes.map { context.getString(it) }
    fun firstAidSteps(context: android.content.Context): List<String> = firstAidStepsRes.map { context.getString(it) }
    fun confusionRisk(context: android.content.Context): String? = confusionRiskRes?.let { context.getString(it) }
    fun note(context: android.content.Context): String? = noteRes?.let { context.getString(it) }

    /** Alle Namen für die Suche kombiniert (aktuelle App-Sprache + lateinischer Name) */
    fun allSearchNames(context: android.content.Context): String =
        "${name(context)} $latinName ${alternativeNames(context).joinToString(" ")}".lowercase()
}

// ════════════════════════════════════════════════════════════════
// PFLANZENDATENBANK – 55 Einträge
// ════════════════════════════════════════════════════════════════
object ToxicPlantDatabase {

    val plants: List<ToxicPlant> = listOf(

        // ── TÖDLICH (DEADLY) ─────────────────────────────────────

        ToxicPlant(
            id = "eibe",
            nameRes = R.string.plant_eibe_name,
            latinName = "Taxus baccata",
            alternativeNamesRes = listOf(R.string.plant_eibe_alt_1, R.string.plant_eibe_alt_2),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_eibe_part_1, R.string.plant_eibe_part_2, R.string.plant_eibe_part_3, R.string.plant_eibe_part_4),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_eibe_symptom_1, R.string.plant_eibe_symptom_2, R.string.plant_eibe_symptom_3, R.string.plant_eibe_symptom_4, R.string.plant_eibe_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_eibe_feature_1, R.string.plant_eibe_feature_2, R.string.plant_eibe_feature_3),
            bloomMonths = listOf(1, 2, 3, 4, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_eibe_habitat_1, R.string.plant_eibe_habitat_2, R.string.plant_eibe_habitat_3, R.string.plant_eibe_habitat_4),
            firstAidStepsRes = listOf(R.string.plant_eibe_firstaid_1, R.string.plant_eibe_firstaid_2, R.string.plant_eibe_firstaid_3),
            mustCallVet = true,
            noteRes = R.string.plant_eibe_note
        ),

        ToxicPlant(
            id = "oleander",
            nameRes = R.string.plant_oleander_name,
            latinName = "Nerium oleander",
            alternativeNamesRes = listOf(R.string.plant_oleander_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_oleander_part_1, R.string.plant_oleander_part_2),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_oleander_symptom_1, R.string.plant_oleander_symptom_2, R.string.plant_oleander_symptom_3, R.string.plant_oleander_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_oleander_feature_1, R.string.plant_oleander_feature_2, R.string.plant_oleander_feature_3),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_oleander_habitat_1, R.string.plant_oleander_habitat_2, R.string.plant_oleander_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_oleander_firstaid_1, R.string.plant_oleander_firstaid_2, R.string.plant_oleander_firstaid_3),
            mustCallVet = true,
            noteRes = R.string.plant_oleander_note
        ),

        ToxicPlant(
            id = "eisenhut",
            nameRes = R.string.plant_eisenhut_name,
            latinName = "Aconitum napellus",
            alternativeNamesRes = listOf(R.string.plant_eisenhut_alt_1, R.string.plant_eisenhut_alt_2),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_eisenhut_part_1, R.string.plant_eisenhut_part_2),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_eisenhut_symptom_1, R.string.plant_eisenhut_symptom_2, R.string.plant_eisenhut_symptom_3, R.string.plant_eisenhut_symptom_4, R.string.plant_eisenhut_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_eisenhut_feature_1, R.string.plant_eisenhut_feature_2, R.string.plant_eisenhut_feature_3),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_eisenhut_habitat_1, R.string.plant_eisenhut_habitat_2, R.string.plant_eisenhut_habitat_3, R.string.plant_eisenhut_habitat_4),
            firstAidStepsRes = listOf(R.string.plant_eisenhut_firstaid_1, R.string.plant_eisenhut_firstaid_2, R.string.plant_eisenhut_firstaid_3),
            mustCallVet = true,
            noteRes = R.string.plant_eisenhut_note
        ),

        ToxicPlant(
            id = "goldregen",
            nameRes = R.string.plant_goldregen_name,
            latinName = "Laburnum anagyroides",
            alternativeNamesRes = listOf(R.string.plant_goldregen_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_goldregen_part_1, R.string.plant_goldregen_part_2, R.string.plant_goldregen_part_3),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_goldregen_symptom_1, R.string.plant_goldregen_symptom_2, R.string.plant_goldregen_symptom_3, R.string.plant_goldregen_symptom_4, R.string.plant_goldregen_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_goldregen_feature_1, R.string.plant_goldregen_feature_2, R.string.plant_goldregen_feature_3),
            bloomMonths = listOf(4, 5, 6),
            habitatRes = listOf(R.string.plant_goldregen_habitat_1, R.string.plant_goldregen_habitat_2, R.string.plant_goldregen_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_goldregen_firstaid_1, R.string.plant_goldregen_firstaid_2, R.string.plant_goldregen_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "herbstzeitlose",
            nameRes = R.string.plant_herbstzeitlose_name,
            latinName = "Colchicum autumnale",
            alternativeNamesRes = listOf(R.string.plant_herbstzeitlose_alt_1, R.string.plant_herbstzeitlose_alt_2),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_herbstzeitlose_part_1, R.string.plant_herbstzeitlose_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_herbstzeitlose_symptom_1, R.string.plant_herbstzeitlose_symptom_2, R.string.plant_herbstzeitlose_symptom_3, R.string.plant_herbstzeitlose_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_herbstzeitlose_feature_1, R.string.plant_herbstzeitlose_feature_2, R.string.plant_herbstzeitlose_feature_3),
            bloomMonths = listOf(8, 9, 10),
            habitatRes = listOf(R.string.plant_herbstzeitlose_habitat_1, R.string.plant_herbstzeitlose_habitat_2, R.string.plant_herbstzeitlose_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_herbstzeitlose_firstaid_1, R.string.plant_herbstzeitlose_firstaid_2, R.string.plant_herbstzeitlose_firstaid_3),
            mustCallVet = true,
            noteRes = R.string.plant_herbstzeitlose_note
        ),

        ToxicPlant(
            id = "schierling",
            nameRes = R.string.plant_schierling_name,
            latinName = "Conium maculatum",
            alternativeNamesRes = listOf(R.string.plant_schierling_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_schierling_part_1, R.string.plant_schierling_part_2),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_schierling_symptom_1, R.string.plant_schierling_symptom_2, R.string.plant_schierling_symptom_3, R.string.plant_schierling_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_schierling_feature_1, R.string.plant_schierling_feature_2, R.string.plant_schierling_feature_3),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_schierling_habitat_1, R.string.plant_schierling_habitat_2, R.string.plant_schierling_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_schierling_firstaid_1, R.string.plant_schierling_firstaid_2, R.string.plant_schierling_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "tollkirsche",
            nameRes = R.string.plant_tollkirsche_name,
            latinName = "Atropa belladonna",
            alternativeNamesRes = listOf(R.string.plant_tollkirsche_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_tollkirsche_part_1, R.string.plant_tollkirsche_part_2, R.string.plant_tollkirsche_part_3),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_tollkirsche_symptom_1, R.string.plant_tollkirsche_symptom_2, R.string.plant_tollkirsche_symptom_3, R.string.plant_tollkirsche_symptom_4, R.string.plant_tollkirsche_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_tollkirsche_feature_1, R.string.plant_tollkirsche_feature_2),
            bloomMonths = listOf(6, 7, 8),
            habitatRes = listOf(R.string.plant_tollkirsche_habitat_1, R.string.plant_tollkirsche_habitat_2, R.string.plant_tollkirsche_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_tollkirsche_firstaid_1, R.string.plant_tollkirsche_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "wunderbaum",
            nameRes = R.string.plant_wunderbaum_name,
            latinName = "Ricinus communis",
            alternativeNamesRes = listOf(R.string.plant_wunderbaum_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_wunderbaum_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_wunderbaum_symptom_1, R.string.plant_wunderbaum_symptom_2, R.string.plant_wunderbaum_symptom_3, R.string.plant_wunderbaum_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_wunderbaum_feature_1, R.string.plant_wunderbaum_feature_2),
            bloomMonths = listOf(8, 9, 10),
            habitatRes = listOf(R.string.plant_wunderbaum_habitat_1, R.string.plant_wunderbaum_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_wunderbaum_firstaid_1, R.string.plant_wunderbaum_firstaid_2, R.string.plant_wunderbaum_firstaid_3),
            mustCallVet = true,
            noteRes = R.string.plant_wunderbaum_note
        ),

        ToxicPlant(
            id = "stechapfel",
            nameRes = R.string.plant_stechapfel_name,
            latinName = "Datura stramonium",
            alternativeNamesRes = listOf(R.string.plant_stechapfel_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_stechapfel_part_1, R.string.plant_stechapfel_part_2),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_stechapfel_symptom_1, R.string.plant_stechapfel_symptom_2, R.string.plant_stechapfel_symptom_3, R.string.plant_stechapfel_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_stechapfel_feature_1, R.string.plant_stechapfel_feature_2),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitatRes = listOf(R.string.plant_stechapfel_habitat_1, R.string.plant_stechapfel_habitat_2, R.string.plant_stechapfel_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_stechapfel_firstaid_1, R.string.plant_stechapfel_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "adonisroeschen",
            nameRes = R.string.plant_adonisroeschen_name,
            latinName = "Adonis vernalis",
            alternativeNamesRes = listOf(R.string.plant_adonisroeschen_alt_1),
            toxicityLevel = PlantToxicityLevel.DEADLY,
            toxicPartsRes = listOf(R.string.plant_adonisroeschen_part_1),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_adonisroeschen_symptom_1, R.string.plant_adonisroeschen_symptom_2, R.string.plant_adonisroeschen_symptom_3, R.string.plant_adonisroeschen_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_adonisroeschen_feature_1, R.string.plant_adonisroeschen_feature_2),
            bloomMonths = listOf(4, 5),
            habitatRes = listOf(R.string.plant_adonisroeschen_habitat_1, R.string.plant_adonisroeschen_habitat_2, R.string.plant_adonisroeschen_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_adonisroeschen_firstaid_1, R.string.plant_adonisroeschen_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "maigloeckchen",
            nameRes = R.string.plant_maigloeckchen_name,
            latinName = "Convallaria majalis",
            alternativeNamesRes = listOf(R.string.plant_maigloeckchen_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_maigloeckchen_part_1, R.string.plant_maigloeckchen_part_2),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_maigloeckchen_symptom_1, R.string.plant_maigloeckchen_symptom_2, R.string.plant_maigloeckchen_symptom_3, R.string.plant_maigloeckchen_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_maigloeckchen_feature_1, R.string.plant_maigloeckchen_feature_2, R.string.plant_maigloeckchen_feature_3),
            bloomMonths = listOf(4, 5, 6),
            habitatRes = listOf(R.string.plant_maigloeckchen_habitat_1, R.string.plant_maigloeckchen_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_maigloeckchen_firstaid_1, R.string.plant_maigloeckchen_firstaid_2, R.string.plant_maigloeckchen_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "fingerhut",
            nameRes = R.string.plant_fingerhut_name,
            latinName = "Digitalis purpurea",
            alternativeNamesRes = listOf(R.string.plant_fingerhut_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_fingerhut_part_1, R.string.plant_fingerhut_part_2),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_fingerhut_symptom_1, R.string.plant_fingerhut_symptom_2, R.string.plant_fingerhut_symptom_3, R.string.plant_fingerhut_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_fingerhut_feature_1, R.string.plant_fingerhut_feature_2, R.string.plant_fingerhut_feature_3),
            bloomMonths = listOf(6, 7, 8),
            habitatRes = listOf(R.string.plant_fingerhut_habitat_1, R.string.plant_fingerhut_habitat_2, R.string.plant_fingerhut_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_fingerhut_firstaid_1, R.string.plant_fingerhut_firstaid_2, R.string.plant_fingerhut_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "kirschlorbeer",
            nameRes = R.string.plant_kirschlorbeer_name,
            latinName = "Prunus laurocerasus",
            alternativeNamesRes = listOf(R.string.plant_kirschlorbeer_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_kirschlorbeer_part_1, R.string.plant_kirschlorbeer_part_2, R.string.plant_kirschlorbeer_part_3),
            affectedSystems = listOf(PlantBodySystem.RESPIRATORY, PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_kirschlorbeer_symptom_1, R.string.plant_kirschlorbeer_symptom_2, R.string.plant_kirschlorbeer_symptom_3, R.string.plant_kirschlorbeer_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_kirschlorbeer_feature_1, R.string.plant_kirschlorbeer_feature_2, R.string.plant_kirschlorbeer_feature_3),
            bloomMonths = listOf(4, 5, 6),
            habitatRes = listOf(R.string.plant_kirschlorbeer_habitat_1, R.string.plant_kirschlorbeer_habitat_2, R.string.plant_kirschlorbeer_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_kirschlorbeer_firstaid_1, R.string.plant_kirschlorbeer_firstaid_2, R.string.plant_kirschlorbeer_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "rhododendron",
            nameRes = R.string.plant_rhododendron_name,
            latinName = "Rhododendron spp.",
            alternativeNamesRes = listOf(R.string.plant_rhododendron_alt_1, R.string.plant_rhododendron_alt_2),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_rhododendron_part_1, R.string.plant_rhododendron_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_rhododendron_symptom_1, R.string.plant_rhododendron_symptom_2, R.string.plant_rhododendron_symptom_3, R.string.plant_rhododendron_symptom_4, R.string.plant_rhododendron_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_rhododendron_feature_1, R.string.plant_rhododendron_feature_2),
            bloomMonths = listOf(4, 5, 6),
            habitatRes = listOf(R.string.plant_rhododendron_habitat_1, R.string.plant_rhododendron_habitat_2, R.string.plant_rhododendron_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_rhododendron_firstaid_1, R.string.plant_rhododendron_firstaid_2, R.string.plant_rhododendron_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "buchsbaum",
            nameRes = R.string.plant_buchsbaum_name,
            latinName = "Buxus sempervirens",
            alternativeNamesRes = listOf(R.string.plant_buchsbaum_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_buchsbaum_part_1, R.string.plant_buchsbaum_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_buchsbaum_symptom_1, R.string.plant_buchsbaum_symptom_2, R.string.plant_buchsbaum_symptom_3, R.string.plant_buchsbaum_symptom_4, R.string.plant_buchsbaum_symptom_5),
            identificationFeaturesRes = listOf(R.string.plant_buchsbaum_feature_1, R.string.plant_buchsbaum_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_buchsbaum_habitat_1, R.string.plant_buchsbaum_habitat_2, R.string.plant_buchsbaum_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_buchsbaum_firstaid_1, R.string.plant_buchsbaum_firstaid_2, R.string.plant_buchsbaum_firstaid_3),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "seidelbast",
            nameRes = R.string.plant_seidelbast_name,
            latinName = "Daphne mezereum",
            alternativeNamesRes = listOf(R.string.plant_seidelbast_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_seidelbast_part_1, R.string.plant_seidelbast_part_2, R.string.plant_seidelbast_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_seidelbast_symptom_1, R.string.plant_seidelbast_symptom_2, R.string.plant_seidelbast_symptom_3, R.string.plant_seidelbast_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_seidelbast_feature_1, R.string.plant_seidelbast_feature_2),
            bloomMonths = listOf(2, 3, 4),
            habitatRes = listOf(R.string.plant_seidelbast_habitat_1, R.string.plant_seidelbast_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_seidelbast_firstaid_1, R.string.plant_seidelbast_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "engelstrompete",
            nameRes = R.string.plant_engelstrompete_name,
            latinName = "Brugmansia spp.",
            alternativeNamesRes = listOf(R.string.plant_engelstrompete_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_engelstrompete_part_1),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_engelstrompete_symptom_1, R.string.plant_engelstrompete_symptom_2, R.string.plant_engelstrompete_symptom_3, R.string.plant_engelstrompete_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_engelstrompete_feature_1, R.string.plant_engelstrompete_feature_2),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitatRes = listOf(R.string.plant_engelstrompete_habitat_1, R.string.plant_engelstrompete_habitat_2, R.string.plant_engelstrompete_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_engelstrompete_firstaid_1, R.string.plant_engelstrompete_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "bilsenkraut",
            nameRes = R.string.plant_bilsenkraut_name,
            latinName = "Hyoscyamus niger",
            alternativeNamesRes = listOf(R.string.plant_bilsenkraut_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_bilsenkraut_part_1, R.string.plant_bilsenkraut_part_2, R.string.plant_bilsenkraut_part_3),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_bilsenkraut_symptom_1, R.string.plant_bilsenkraut_symptom_2, R.string.plant_bilsenkraut_symptom_3, R.string.plant_bilsenkraut_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_bilsenkraut_feature_1, R.string.plant_bilsenkraut_feature_2),
            bloomMonths = listOf(6, 7, 8, 9, 10),
            habitatRes = listOf(R.string.plant_bilsenkraut_habitat_1, R.string.plant_bilsenkraut_habitat_2, R.string.plant_bilsenkraut_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_bilsenkraut_firstaid_1, R.string.plant_bilsenkraut_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "christrose",
            nameRes = R.string.plant_christrose_name,
            latinName = "Helleborus niger",
            alternativeNamesRes = listOf(R.string.plant_christrose_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_christrose_part_1, R.string.plant_christrose_part_2),
            affectedSystems = listOf(PlantBodySystem.CARDIOVASCULAR, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_christrose_symptom_1, R.string.plant_christrose_symptom_2, R.string.plant_christrose_symptom_3, R.string.plant_christrose_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_christrose_feature_1, R.string.plant_christrose_feature_2),
            bloomMonths = listOf(12, 1, 2, 3, 4),
            habitatRes = listOf(R.string.plant_christrose_habitat_1, R.string.plant_christrose_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_christrose_firstaid_1, R.string.plant_christrose_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "dieffenbachia",
            nameRes = R.string.plant_dieffenbachia_name,
            latinName = "Dieffenbachia seguine",
            alternativeNamesRes = listOf(R.string.plant_dieffenbachia_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_dieffenbachia_part_1, R.string.plant_dieffenbachia_part_2),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_dieffenbachia_symptom_1, R.string.plant_dieffenbachia_symptom_2, R.string.plant_dieffenbachia_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_dieffenbachia_feature_1),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_dieffenbachia_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_dieffenbachia_firstaid_1, R.string.plant_dieffenbachia_firstaid_2),
            mustCallVet = true,
            noteRes = R.string.plant_dieffenbachia_note
        ),

        ToxicPlant(
            id = "rittersporn",
            nameRes = R.string.plant_rittersporn_name,
            latinName = "Delphinium spp.",
            alternativeNamesRes = listOf(R.string.plant_rittersporn_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_rittersporn_part_1, R.string.plant_rittersporn_part_2),
            affectedSystems = listOf(PlantBodySystem.NERVOUS, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_rittersporn_symptom_1, R.string.plant_rittersporn_symptom_2, R.string.plant_rittersporn_symptom_3, R.string.plant_rittersporn_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_rittersporn_feature_1, R.string.plant_rittersporn_feature_2),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_rittersporn_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_rittersporn_firstaid_1, R.string.plant_rittersporn_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "robinie",
            nameRes = R.string.plant_robinie_name,
            latinName = "Robinia pseudoacacia",
            alternativeNamesRes = listOf(R.string.plant_robinie_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_robinie_part_1, R.string.plant_robinie_part_2, R.string.plant_robinie_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_robinie_symptom_1, R.string.plant_robinie_symptom_2, R.string.plant_robinie_symptom_3, R.string.plant_robinie_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_robinie_feature_1, R.string.plant_robinie_feature_2, R.string.plant_robinie_feature_3),
            bloomMonths = listOf(5, 6),
            habitatRes = listOf(R.string.plant_robinie_habitat_1, R.string.plant_robinie_habitat_2, R.string.plant_robinie_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_robinie_firstaid_1, R.string.plant_robinie_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "jakobskreuzkraut",
            nameRes = R.string.plant_jakobskreuzkraut_name,
            latinName = "Jacobaea vulgaris",
            alternativeNamesRes = listOf(R.string.plant_jakobskreuzkraut_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_jakobskreuzkraut_part_1, R.string.plant_jakobskreuzkraut_part_2),
            affectedSystems = listOf(PlantBodySystem.LIVER),
            symptomsRes = listOf(R.string.plant_jakobskreuzkraut_symptom_1, R.string.plant_jakobskreuzkraut_symptom_2, R.string.plant_jakobskreuzkraut_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_jakobskreuzkraut_feature_1, R.string.plant_jakobskreuzkraut_feature_2),
            bloomMonths = listOf(7, 8, 9),
            habitatRes = listOf(R.string.plant_jakobskreuzkraut_habitat_1, R.string.plant_jakobskreuzkraut_habitat_2, R.string.plant_jakobskreuzkraut_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_jakobskreuzkraut_firstaid_1),
            mustCallVet = false,
            noteRes = R.string.plant_jakobskreuzkraut_note
        ),

        ToxicPlant(
            id = "schwarzer-holunder-roh",
            nameRes = R.string.plant_schwarzer_holunder_roh_name,
            latinName = "Sambucus nigra",
            alternativeNamesRes = listOf(R.string.plant_schwarzer_holunder_roh_alt_1),
            toxicityLevel = PlantToxicityLevel.HIGHLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_schwarzer_holunder_roh_part_1, R.string.plant_schwarzer_holunder_roh_part_2, R.string.plant_schwarzer_holunder_roh_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_schwarzer_holunder_roh_symptom_1, R.string.plant_schwarzer_holunder_roh_symptom_2, R.string.plant_schwarzer_holunder_roh_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_schwarzer_holunder_roh_feature_1, R.string.plant_schwarzer_holunder_roh_feature_2),
            bloomMonths = listOf(8, 9, 10),
            habitatRes = listOf(R.string.plant_schwarzer_holunder_roh_habitat_1, R.string.plant_schwarzer_holunder_roh_habitat_2, R.string.plant_schwarzer_holunder_roh_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_schwarzer_holunder_roh_firstaid_1, R.string.plant_schwarzer_holunder_roh_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "baerlauch",
            nameRes = R.string.plant_baerlauch_name,
            latinName = "Allium ursinum",
            alternativeNamesRes = listOf(R.string.plant_baerlauch_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_baerlauch_part_1, R.string.plant_baerlauch_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.BLOOD),
            symptomsRes = listOf(R.string.plant_baerlauch_symptom_1, R.string.plant_baerlauch_symptom_2, R.string.plant_baerlauch_symptom_3, R.string.plant_baerlauch_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_baerlauch_feature_1, R.string.plant_baerlauch_feature_2),
            bloomMonths = listOf(3, 4, 5, 6),
            habitatRes = listOf(R.string.plant_baerlauch_habitat_1, R.string.plant_baerlauch_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_baerlauch_firstaid_1),
            mustCallVet = false,
            confusionRiskRes = R.string.plant_baerlauch_confusion
        ),

        ToxicPlant(
            id = "narzisse",
            nameRes = R.string.plant_narzisse_name,
            latinName = "Narcissus spp.",
            alternativeNamesRes = listOf(R.string.plant_narzisse_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_narzisse_part_1, R.string.plant_narzisse_part_2, R.string.plant_narzisse_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_narzisse_symptom_1, R.string.plant_narzisse_symptom_2, R.string.plant_narzisse_symptom_3, R.string.plant_narzisse_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_narzisse_feature_1),
            bloomMonths = listOf(2, 3, 4, 5),
            habitatRes = listOf(R.string.plant_narzisse_habitat_1, R.string.plant_narzisse_habitat_2, R.string.plant_narzisse_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_narzisse_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "buschwindroeschen",
            nameRes = R.string.plant_buschwindroeschen_name,
            latinName = "Anemone nemorosa",
            alternativeNamesRes = listOf(R.string.plant_buschwindroeschen_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_buschwindroeschen_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptomsRes = listOf(R.string.plant_buschwindroeschen_symptom_1, R.string.plant_buschwindroeschen_symptom_2, R.string.plant_buschwindroeschen_symptom_3, R.string.plant_buschwindroeschen_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_buschwindroeschen_feature_1),
            bloomMonths = listOf(3, 4, 5),
            habitatRes = listOf(R.string.plant_buschwindroeschen_habitat_1, R.string.plant_buschwindroeschen_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_buschwindroeschen_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "stechpalme",
            nameRes = R.string.plant_stechpalme_name,
            latinName = "Ilex aquifolium",
            alternativeNamesRes = listOf(R.string.plant_stechpalme_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_stechpalme_part_1, R.string.plant_stechpalme_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_stechpalme_symptom_1, R.string.plant_stechpalme_symptom_2, R.string.plant_stechpalme_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_stechpalme_feature_1, R.string.plant_stechpalme_feature_2),
            bloomMonths = listOf(11, 12, 1),
            habitatRes = listOf(R.string.plant_stechpalme_habitat_1, R.string.plant_stechpalme_habitat_2, R.string.plant_stechpalme_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_stechpalme_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "tulpe",
            nameRes = R.string.plant_tulpe_name,
            latinName = "Tulipa spp.",
            alternativeNamesRes = listOf(R.string.plant_tulpe_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_tulpe_part_1, R.string.plant_tulpe_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptomsRes = listOf(R.string.plant_tulpe_symptom_1, R.string.plant_tulpe_symptom_2, R.string.plant_tulpe_symptom_3, R.string.plant_tulpe_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_tulpe_feature_1, R.string.plant_tulpe_feature_2),
            bloomMonths = listOf(3, 4, 5),
            habitatRes = listOf(R.string.plant_tulpe_habitat_1, R.string.plant_tulpe_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_tulpe_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "clematis",
            nameRes = R.string.plant_clematis_name,
            latinName = "Clematis spp.",
            alternativeNamesRes = listOf(R.string.plant_clematis_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_clematis_part_1, R.string.plant_clematis_part_2),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_clematis_symptom_1, R.string.plant_clematis_symptom_2, R.string.plant_clematis_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_clematis_feature_1, R.string.plant_clematis_feature_2),
            bloomMonths = listOf(5, 6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_clematis_habitat_1, R.string.plant_clematis_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_clematis_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "wisteria",
            nameRes = R.string.plant_wisteria_name,
            latinName = "Wisteria sinensis",
            alternativeNamesRes = listOf(R.string.plant_wisteria_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_wisteria_part_1, R.string.plant_wisteria_part_2, R.string.plant_wisteria_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_wisteria_symptom_1, R.string.plant_wisteria_symptom_2, R.string.plant_wisteria_symptom_3, R.string.plant_wisteria_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_wisteria_feature_1, R.string.plant_wisteria_feature_2),
            bloomMonths = listOf(4, 5, 6),
            habitatRes = listOf(R.string.plant_wisteria_habitat_1, R.string.plant_wisteria_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_wisteria_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "efeu",
            nameRes = R.string.plant_efeu_name,
            latinName = "Hedera helix",
            alternativeNamesRes = listOf(R.string.plant_efeu_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_efeu_part_1, R.string.plant_efeu_part_2, R.string.plant_efeu_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_efeu_symptom_1, R.string.plant_efeu_symptom_2, R.string.plant_efeu_symptom_3, R.string.plant_efeu_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_efeu_feature_1, R.string.plant_efeu_feature_2, R.string.plant_efeu_feature_3),
            bloomMonths = listOf(9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_efeu_habitat_1, R.string.plant_efeu_habitat_2, R.string.plant_efeu_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_efeu_firstaid_1, R.string.plant_efeu_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "aronstab",
            nameRes = R.string.plant_aronstab_name,
            latinName = "Arum maculatum",
            alternativeNamesRes = listOf(R.string.plant_aronstab_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_aronstab_part_1, R.string.plant_aronstab_part_2, R.string.plant_aronstab_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.RESPIRATORY),
            symptomsRes = listOf(R.string.plant_aronstab_symptom_1, R.string.plant_aronstab_symptom_2, R.string.plant_aronstab_symptom_3, R.string.plant_aronstab_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_aronstab_feature_1, R.string.plant_aronstab_feature_2, R.string.plant_aronstab_feature_3),
            bloomMonths = listOf(4, 5, 8, 9, 10),
            habitatRes = listOf(R.string.plant_aronstab_habitat_1, R.string.plant_aronstab_habitat_2, R.string.plant_aronstab_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_aronstab_firstaid_1, R.string.plant_aronstab_firstaid_2),
            mustCallVet = true
        ),

        ToxicPlant(
            id = "adlerfarn",
            nameRes = R.string.plant_adlerfarn_name,
            latinName = "Pteridium aquilinum",
            alternativeNamesRes = listOf(R.string.plant_adlerfarn_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_adlerfarn_part_1, R.string.plant_adlerfarn_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.BLOOD),
            symptomsRes = listOf(R.string.plant_adlerfarn_symptom_1, R.string.plant_adlerfarn_symptom_2, R.string.plant_adlerfarn_symptom_3, R.string.plant_adlerfarn_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_adlerfarn_feature_1, R.string.plant_adlerfarn_feature_2),
            bloomMonths = listOf(5, 6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_adlerfarn_habitat_1, R.string.plant_adlerfarn_habitat_2, R.string.plant_adlerfarn_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_adlerfarn_firstaid_1, R.string.plant_adlerfarn_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "rainfarn",
            nameRes = R.string.plant_rainfarn_name,
            latinName = "Tanacetum vulgare",
            alternativeNamesRes = listOf(R.string.plant_rainfarn_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_rainfarn_part_1, R.string.plant_rainfarn_part_2, R.string.plant_rainfarn_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_rainfarn_symptom_1, R.string.plant_rainfarn_symptom_2, R.string.plant_rainfarn_symptom_3, R.string.plant_rainfarn_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_rainfarn_feature_1, R.string.plant_rainfarn_feature_2),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_rainfarn_habitat_1, R.string.plant_rainfarn_habitat_2, R.string.plant_rainfarn_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_rainfarn_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "pfaffenhuetchen",
            nameRes = R.string.plant_pfaffenhuetchen_name,
            latinName = "Euonymus europaeus",
            alternativeNamesRes = listOf(R.string.plant_pfaffenhuetchen_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_pfaffenhuetchen_part_1, R.string.plant_pfaffenhuetchen_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_pfaffenhuetchen_symptom_1, R.string.plant_pfaffenhuetchen_symptom_2, R.string.plant_pfaffenhuetchen_symptom_3, R.string.plant_pfaffenhuetchen_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_pfaffenhuetchen_feature_1, R.string.plant_pfaffenhuetchen_feature_2),
            bloomMonths = listOf(5, 6, 9, 10),
            habitatRes = listOf(R.string.plant_pfaffenhuetchen_habitat_1, R.string.plant_pfaffenhuetchen_habitat_2, R.string.plant_pfaffenhuetchen_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_pfaffenhuetchen_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "nachtschatten",
            nameRes = R.string.plant_nachtschatten_name,
            latinName = "Solanum dulcamara",
            alternativeNamesRes = listOf(R.string.plant_nachtschatten_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_nachtschatten_part_1, R.string.plant_nachtschatten_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_nachtschatten_symptom_1, R.string.plant_nachtschatten_symptom_2, R.string.plant_nachtschatten_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_nachtschatten_feature_1, R.string.plant_nachtschatten_feature_2, R.string.plant_nachtschatten_feature_3),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_nachtschatten_habitat_1, R.string.plant_nachtschatten_habitat_2, R.string.plant_nachtschatten_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_nachtschatten_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "hortensie",
            nameRes = R.string.plant_hortensie_name,
            latinName = "Hydrangea macrophylla",
            alternativeNamesRes = listOf(R.string.plant_hortensie_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_hortensie_part_1, R.string.plant_hortensie_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_hortensie_symptom_1, R.string.plant_hortensie_symptom_2, R.string.plant_hortensie_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_hortensie_feature_1, R.string.plant_hortensie_feature_2),
            bloomMonths = listOf(6, 7, 8, 9),
            habitatRes = listOf(R.string.plant_hortensie_habitat_1, R.string.plant_hortensie_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_hortensie_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "hyazinthe",
            nameRes = R.string.plant_hyazinthe_name,
            latinName = "Hyacinthus orientalis",
            alternativeNamesRes = listOf(R.string.plant_hyazinthe_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_hyazinthe_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_hyazinthe_symptom_1, R.string.plant_hyazinthe_symptom_2),
            identificationFeaturesRes = listOf(R.string.plant_hyazinthe_feature_1, R.string.plant_hyazinthe_feature_2),
            bloomMonths = listOf(3, 4, 5),
            habitatRes = listOf(R.string.plant_hyazinthe_habitat_1, R.string.plant_hyazinthe_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_hyazinthe_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "alpenveilchen",
            nameRes = R.string.plant_alpenveilchen_name,
            latinName = "Cyclamen persicum",
            alternativeNamesRes = listOf(R.string.plant_alpenveilchen_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_alpenveilchen_part_1, R.string.plant_alpenveilchen_part_2, R.string.plant_alpenveilchen_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_alpenveilchen_symptom_1, R.string.plant_alpenveilchen_symptom_2, R.string.plant_alpenveilchen_symptom_3, R.string.plant_alpenveilchen_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_alpenveilchen_feature_1, R.string.plant_alpenveilchen_feature_2),
            bloomMonths = listOf(9, 10, 11, 12, 1, 2, 3),
            habitatRes = listOf(R.string.plant_alpenveilchen_habitat_1, R.string.plant_alpenveilchen_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_alpenveilchen_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "amaryllis",
            nameRes = R.string.plant_amaryllis_name,
            latinName = "Hippeastrum spp.",
            alternativeNamesRes = listOf(R.string.plant_amaryllis_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_amaryllis_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.CARDIOVASCULAR),
            symptomsRes = listOf(R.string.plant_amaryllis_symptom_1, R.string.plant_amaryllis_symptom_2, R.string.plant_amaryllis_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_amaryllis_feature_1, R.string.plant_amaryllis_feature_2),
            bloomMonths = listOf(11, 12, 1, 2),
            habitatRes = listOf(R.string.plant_amaryllis_habitat_1, R.string.plant_amaryllis_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_amaryllis_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "clivia",
            nameRes = R.string.plant_clivia_name,
            latinName = "Clivia miniata",
            alternativeNamesRes = listOf(R.string.plant_clivia_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_clivia_part_1, R.string.plant_clivia_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_clivia_symptom_1, R.string.plant_clivia_symptom_2, R.string.plant_clivia_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_clivia_feature_1, R.string.plant_clivia_feature_2),
            bloomMonths = listOf(2, 3, 4, 5),
            habitatRes = listOf(R.string.plant_clivia_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_clivia_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "philodendron",
            nameRes = R.string.plant_philodendron_name,
            latinName = "Philodendron spp.",
            alternativeNamesRes = listOf(R.string.plant_philodendron_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_philodendron_part_1, R.string.plant_philodendron_part_2, R.string.plant_philodendron_part_3),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_philodendron_symptom_1, R.string.plant_philodendron_symptom_2, R.string.plant_philodendron_symptom_3, R.string.plant_philodendron_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_philodendron_feature_1, R.string.plant_philodendron_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_philodendron_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_philodendron_firstaid_1, R.string.plant_philodendron_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "chrysantheme",
            nameRes = R.string.plant_chrysantheme_name,
            latinName = "Chrysanthemum spp.",
            alternativeNamesRes = listOf(R.string.plant_chrysantheme_alt_1),
            toxicityLevel = PlantToxicityLevel.MODERATELY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_chrysantheme_part_1, R.string.plant_chrysantheme_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_chrysantheme_symptom_1, R.string.plant_chrysantheme_symptom_2, R.string.plant_chrysantheme_symptom_3, R.string.plant_chrysantheme_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_chrysantheme_feature_1, R.string.plant_chrysantheme_feature_2),
            bloomMonths = listOf(8, 9, 10, 11),
            habitatRes = listOf(R.string.plant_chrysantheme_habitat_1, R.string.plant_chrysantheme_habitat_2, R.string.plant_chrysantheme_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_chrysantheme_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "thuja",
            nameRes = R.string.plant_thuja_name,
            latinName = "Thuja occidentalis",
            alternativeNamesRes = listOf(R.string.plant_thuja_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_thuja_part_1, R.string.plant_thuja_part_2),
            affectedSystems = listOf(PlantBodySystem.KIDNEYS, PlantBodySystem.DIGESTIVE, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_thuja_symptom_1, R.string.plant_thuja_symptom_2, R.string.plant_thuja_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_thuja_feature_1, R.string.plant_thuja_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_thuja_habitat_1, R.string.plant_thuja_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_thuja_firstaid_1, R.string.plant_thuja_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "primel",
            nameRes = R.string.plant_primel_name,
            latinName = "Primula obconica",
            alternativeNamesRes = listOf(R.string.plant_primel_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_primel_part_1, R.string.plant_primel_part_2),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_primel_symptom_1, R.string.plant_primel_symptom_2, R.string.plant_primel_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_primel_feature_1, R.string.plant_primel_feature_2),
            bloomMonths = listOf(2, 3, 4, 5),
            habitatRes = listOf(R.string.plant_primel_habitat_1, R.string.plant_primel_habitat_2, R.string.plant_primel_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_primel_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "liguster",
            nameRes = R.string.plant_liguster_name,
            latinName = "Ligustrum vulgare",
            alternativeNamesRes = listOf(R.string.plant_liguster_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_liguster_part_1, R.string.plant_liguster_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_liguster_symptom_1, R.string.plant_liguster_symptom_2),
            identificationFeaturesRes = listOf(R.string.plant_liguster_feature_1, R.string.plant_liguster_feature_2),
            bloomMonths = listOf(6, 7, 9, 10, 11),
            habitatRes = listOf(R.string.plant_liguster_habitat_1, R.string.plant_liguster_habitat_2, R.string.plant_liguster_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_liguster_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "ficus",
            nameRes = R.string.plant_ficus_name,
            latinName = "Ficus elastica / benjamina",
            alternativeNamesRes = listOf(R.string.plant_ficus_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_ficus_part_1),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_ficus_symptom_1, R.string.plant_ficus_symptom_2, R.string.plant_ficus_symptom_3, R.string.plant_ficus_symptom_4),
            identificationFeaturesRes = listOf(R.string.plant_ficus_feature_1, R.string.plant_ficus_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_ficus_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_ficus_firstaid_1, R.string.plant_ficus_firstaid_2),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "weihnachtsstern",
            nameRes = R.string.plant_weihnachtsstern_name,
            latinName = "Euphorbia pulcherrima",
            alternativeNamesRes = listOf(R.string.plant_weihnachtsstern_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_weihnachtsstern_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN, PlantBodySystem.NERVOUS),
            symptomsRes = listOf(R.string.plant_weihnachtsstern_symptom_1, R.string.plant_weihnachtsstern_symptom_2, R.string.plant_weihnachtsstern_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_weihnachtsstern_feature_1),
            bloomMonths = listOf(11, 12, 1),
            habitatRes = listOf(R.string.plant_weihnachtsstern_habitat_1),
            firstAidStepsRes = listOf(R.string.plant_weihnachtsstern_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "aloevera",
            nameRes = R.string.plant_aloevera_name,
            latinName = "Aloe vera",
            alternativeNamesRes = listOf(R.string.plant_aloevera_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_aloevera_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_aloevera_symptom_1, R.string.plant_aloevera_symptom_2, R.string.plant_aloevera_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_aloevera_feature_1),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_aloevera_habitat_1, R.string.plant_aloevera_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_aloevera_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "geranie",
            nameRes = R.string.plant_geranie_name,
            latinName = "Pelargonium spp.",
            alternativeNamesRes = listOf(R.string.plant_geranie_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_geranie_part_1, R.string.plant_geranie_part_2, R.string.plant_geranie_part_3),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptomsRes = listOf(R.string.plant_geranie_symptom_1, R.string.plant_geranie_symptom_2),
            identificationFeaturesRes = listOf(R.string.plant_geranie_feature_1, R.string.plant_geranie_feature_2),
            bloomMonths = listOf(5, 6, 7, 8, 9, 10),
            habitatRes = listOf(R.string.plant_geranie_habitat_1, R.string.plant_geranie_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_geranie_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "agave",
            nameRes = R.string.plant_agave_name,
            latinName = "Agave spp.",
            alternativeNamesRes = listOf(R.string.plant_agave_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_agave_part_1),
            affectedSystems = listOf(PlantBodySystem.SKIN, PlantBodySystem.DIGESTIVE),
            symptomsRes = listOf(R.string.plant_agave_symptom_1, R.string.plant_agave_symptom_2),
            identificationFeaturesRes = listOf(R.string.plant_agave_feature_1, R.string.plant_agave_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_agave_habitat_1, R.string.plant_agave_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_agave_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "begonie",
            nameRes = R.string.plant_begonie_name,
            latinName = "Begonia spp.",
            alternativeNamesRes = listOf(R.string.plant_begonie_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_begonie_part_1),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.SKIN),
            symptomsRes = listOf(R.string.plant_begonie_symptom_1, R.string.plant_begonie_symptom_2, R.string.plant_begonie_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_begonie_feature_1, R.string.plant_begonie_feature_2),
            bloomMonths = listOf(5, 6, 7, 8, 9, 10),
            habitatRes = listOf(R.string.plant_begonie_habitat_1, R.string.plant_begonie_habitat_2),
            firstAidStepsRes = listOf(R.string.plant_begonie_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "wacholder",
            nameRes = R.string.plant_wacholder_name,
            latinName = "Juniperus communis",
            alternativeNamesRes = listOf(R.string.plant_wacholder_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_wacholder_part_1, R.string.plant_wacholder_part_2),
            affectedSystems = listOf(PlantBodySystem.DIGESTIVE, PlantBodySystem.KIDNEYS),
            symptomsRes = listOf(R.string.plant_wacholder_symptom_1, R.string.plant_wacholder_symptom_2),
            identificationFeaturesRes = listOf(R.string.plant_wacholder_feature_1, R.string.plant_wacholder_feature_2),
            bloomMonths = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            habitatRes = listOf(R.string.plant_wacholder_habitat_1, R.string.plant_wacholder_habitat_2, R.string.plant_wacholder_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_wacholder_firstaid_1),
            mustCallVet = false
        ),

        ToxicPlant(
            id = "riesenbaerenklau",
            nameRes = R.string.plant_riesenbaerenklau_name,
            latinName = "Heracleum mantegazzianum",
            alternativeNamesRes = listOf(R.string.plant_riesenbaerenklau_alt_1),
            toxicityLevel = PlantToxicityLevel.SLIGHTLY_TOXIC,
            toxicPartsRes = listOf(R.string.plant_riesenbaerenklau_part_1),
            affectedSystems = listOf(PlantBodySystem.SKIN),
            symptomsRes = listOf(R.string.plant_riesenbaerenklau_symptom_1, R.string.plant_riesenbaerenklau_symptom_2, R.string.plant_riesenbaerenklau_symptom_3),
            identificationFeaturesRes = listOf(R.string.plant_riesenbaerenklau_feature_1, R.string.plant_riesenbaerenklau_feature_2),
            bloomMonths = listOf(6, 7, 8),
            habitatRes = listOf(R.string.plant_riesenbaerenklau_habitat_1, R.string.plant_riesenbaerenklau_habitat_2, R.string.plant_riesenbaerenklau_habitat_3),
            firstAidStepsRes = listOf(R.string.plant_riesenbaerenklau_firstaid_1, R.string.plant_riesenbaerenklau_firstaid_2),
            mustCallVet = true,
            noteRes = R.string.plant_riesenbaerenklau_note
        )
    )

    // ── Suchfunktionen ───────────────────────────────────────────

    fun search(context: android.content.Context, query: String): List<ToxicPlant> {
        if (query.length < 2) return plants
        val q = query.lowercase().trim()
        return plants.filter { it.allSearchNames(context).contains(q) }
    }

    fun getBySeason(): List<ToxicPlant> {
        val month = Calendar.getInstance().get(Calendar.MONTH) + 1
        return plants.filter { month in it.bloomMonths }
            .sortedByDescending { it.toxicityLevel.ordinal }
    }

    fun getByToxicity(context: android.content.Context, level: PlantToxicityLevel): List<ToxicPlant> =
        plants.filter { it.toxicityLevel == level }
            .sortedBy { it.name(context) }

    fun getById(id: String): ToxicPlant? =
        plants.firstOrNull { it.id == id }

    fun currentMonth(context: android.content.Context): String {
        val months = arrayOf(
            R.string.month_january, R.string.month_february, R.string.month_march,
            R.string.month_april, R.string.month_may, R.string.month_june,
            R.string.month_july, R.string.month_august, R.string.month_september,
            R.string.month_october, R.string.month_november, R.string.month_december
        )
        return context.getString(months[Calendar.getInstance().get(Calendar.MONTH)])
    }

    fun currentMonthInt(): Int =
        Calendar.getInstance().get(Calendar.MONTH) + 1
}
