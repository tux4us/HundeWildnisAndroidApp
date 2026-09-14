package com.hundewildnis.app

// ════════════════════════════════════════════════════════════════
// ERSTE-HILFE ANWEISUNGEN DATENBANK
// Neu erstellen: FirstAidInstructions.kt
// ════════════════════════════════════════════════════════════════

object FirstAidInstructions {

    /**
     * Alle Erste-Hilfe Anleitungen
     */
    fun getAll(): Map<String, FirstAidInstruction> = mapOf(

        // ════════════════════════════════════════════════════════
        // BEWUSSTLOSIGKEIT
        // ════════════════════════════════════════════════════════
        "UNCONSCIOUSNESS" to FirstAidInstruction(
            type = "UNCONSCIOUSNESS",
            titleRes = R.string.firstaid_unconsciousness_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_unconsciousness_symptom_1,
                R.string.firstaid_unconsciousness_symptom_2,
                R.string.firstaid_unconsciousness_symptom_3,
                R.string.firstaid_unconsciousness_symptom_4
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_unconsciousness_action_1,
                R.string.firstaid_unconsciousness_action_2,
                R.string.firstaid_unconsciousness_action_3,
                R.string.firstaid_unconsciousness_action_4,
                R.string.firstaid_unconsciousness_action_5,
                R.string.firstaid_unconsciousness_action_6,
                R.string.firstaid_unconsciousness_action_7,
                R.string.firstaid_unconsciousness_action_8,
                R.string.firstaid_unconsciousness_action_9,
                R.string.firstaid_unconsciousness_action_10,
                R.string.firstaid_unconsciousness_action_11,
                R.string.firstaid_unconsciousness_action_12,
                R.string.firstaid_unconsciousness_action_13,
                R.string.firstaid_unconsciousness_action_14,
                R.string.firstaid_unconsciousness_action_15
            ),
            doListRes = listOf(
                R.string.firstaid_unconsciousness_do_1,
                R.string.firstaid_unconsciousness_do_2,
                R.string.firstaid_unconsciousness_do_3,
                R.string.firstaid_unconsciousness_do_4,
                R.string.firstaid_unconsciousness_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_unconsciousness_dont_1,
                R.string.firstaid_unconsciousness_dont_2,
                R.string.firstaid_unconsciousness_dont_3,
                R.string.firstaid_unconsciousness_dont_4
            ),
            transportInfoRes = R.string.firstaid_unconsciousness_transport,
            needsHLW = true
        ),

        // ════════════════════════════════════════════════════════
        // HITZSCHLAG
        // ════════════════════════════════════════════════════════
        "HEAT_STROKE" to FirstAidInstruction(
            type = "HEAT_STROKE",
            titleRes = R.string.firstaid_heatstroke_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_heatstroke_symptom_1,
                R.string.firstaid_heatstroke_symptom_2,
                R.string.firstaid_heatstroke_symptom_3,
                R.string.firstaid_heatstroke_symptom_4,
                R.string.firstaid_heatstroke_symptom_5,
                R.string.firstaid_heatstroke_symptom_6,
                R.string.firstaid_heatstroke_symptom_7
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_heatstroke_action_1,
                R.string.firstaid_heatstroke_action_2,
                R.string.firstaid_heatstroke_action_3,
                R.string.firstaid_heatstroke_action_4,
                R.string.firstaid_heatstroke_action_5,
                R.string.firstaid_heatstroke_action_6,
                R.string.firstaid_heatstroke_action_7,
                R.string.firstaid_heatstroke_action_8,
                R.string.firstaid_heatstroke_action_9,
                R.string.firstaid_heatstroke_action_10,
                R.string.firstaid_heatstroke_action_11,
                R.string.firstaid_heatstroke_action_12
            ),
            doListRes = listOf(
                R.string.firstaid_heatstroke_do_1,
                R.string.firstaid_heatstroke_do_2,
                R.string.firstaid_heatstroke_do_3,
                R.string.firstaid_heatstroke_do_4,
                R.string.firstaid_heatstroke_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_heatstroke_dont_1,
                R.string.firstaid_heatstroke_dont_2,
                R.string.firstaid_heatstroke_dont_3,
                R.string.firstaid_heatstroke_dont_4
            ),
            transportInfoRes = R.string.firstaid_heatstroke_transport,
            specialNotesRes = R.string.firstaid_heatstroke_special_notes,
            needsTimer = true,
            timerMinutes = 15
        ),

        // ════════════════════════════════════════════════════════
        // VERGIFTUNG
        // ════════════════════════════════════════════════════════
        "POISONING_KNOWN" to FirstAidInstruction(
            type = "POISONING_KNOWN",
            titleRes = R.string.firstaid_poisoning_known_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_poisoning_known_symptom_1,
                R.string.firstaid_poisoning_known_symptom_2,
                R.string.firstaid_poisoning_known_symptom_3,
                R.string.firstaid_poisoning_known_symptom_4,
                R.string.firstaid_poisoning_known_symptom_5,
                R.string.firstaid_poisoning_known_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_poisoning_known_action_1,
                R.string.firstaid_poisoning_known_action_2,
                R.string.firstaid_poisoning_known_action_3,
                R.string.firstaid_poisoning_known_action_4,
                R.string.firstaid_poisoning_known_action_5,
                R.string.firstaid_poisoning_known_action_6,
                R.string.firstaid_poisoning_known_action_7,
                R.string.firstaid_poisoning_known_action_8,
                R.string.firstaid_poisoning_known_action_9,
                R.string.firstaid_poisoning_known_action_10,
                R.string.firstaid_poisoning_known_action_11,
                R.string.firstaid_poisoning_known_action_12,
                R.string.firstaid_poisoning_known_action_13
            ),
            doListRes = listOf(
                R.string.firstaid_poisoning_known_do_1,
                R.string.firstaid_poisoning_known_do_2,
                R.string.firstaid_poisoning_known_do_3,
                R.string.firstaid_poisoning_known_do_4,
                R.string.firstaid_poisoning_known_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_poisoning_known_dont_1,
                R.string.firstaid_poisoning_known_dont_2,
                R.string.firstaid_poisoning_known_dont_3,
                R.string.firstaid_poisoning_known_dont_4
            ),
            transportInfoRes = R.string.firstaid_poisoning_known_transport,
            specialNotesRes = R.string.firstaid_poisoning_known_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // MAGENDREHUNG
        // ════════════════════════════════════════════════════════
        "GASTRIC_TORSION" to FirstAidInstruction(
            type = "GASTRIC_TORSION",
            titleRes = R.string.firstaid_gastric_torsion_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_gastric_torsion_symptom_1,
                R.string.firstaid_gastric_torsion_symptom_2,
                R.string.firstaid_gastric_torsion_symptom_3,
                R.string.firstaid_gastric_torsion_symptom_4,
                R.string.firstaid_gastric_torsion_symptom_5,
                R.string.firstaid_gastric_torsion_symptom_6,
                R.string.firstaid_gastric_torsion_symptom_7
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_gastric_torsion_action_1,
                R.string.firstaid_gastric_torsion_action_2,
                R.string.firstaid_gastric_torsion_action_3,
                R.string.firstaid_gastric_torsion_action_4,
                R.string.firstaid_gastric_torsion_action_5,
                R.string.firstaid_gastric_torsion_action_6,
                R.string.firstaid_gastric_torsion_action_7,
                R.string.firstaid_gastric_torsion_action_8,
                R.string.firstaid_gastric_torsion_action_9,
                R.string.firstaid_gastric_torsion_action_10,
                R.string.firstaid_gastric_torsion_action_11,
                R.string.firstaid_gastric_torsion_action_12,
                R.string.firstaid_gastric_torsion_action_13,
                R.string.firstaid_gastric_torsion_action_14
            ),
            doListRes = listOf(
                R.string.firstaid_gastric_torsion_do_1,
                R.string.firstaid_gastric_torsion_do_2,
                R.string.firstaid_gastric_torsion_do_3,
                R.string.firstaid_gastric_torsion_do_4
            ),
            dontListRes = listOf(
                R.string.firstaid_gastric_torsion_dont_1,
                R.string.firstaid_gastric_torsion_dont_2,
                R.string.firstaid_gastric_torsion_dont_3,
                R.string.firstaid_gastric_torsion_dont_4
            ),
            transportInfoRes = R.string.firstaid_gastric_torsion_transport,
            specialNotesRes = R.string.firstaid_gastric_torsion_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // KRAMPFANFALL
        // ════════════════════════════════════════════════════════
        "SEIZURE" to FirstAidInstruction(
            type = "SEIZURE",
            titleRes = R.string.firstaid_seizure_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_seizure_symptom_1,
                R.string.firstaid_seizure_symptom_2,
                R.string.firstaid_seizure_symptom_3,
                R.string.firstaid_seizure_symptom_4,
                R.string.firstaid_seizure_symptom_5,
                R.string.firstaid_seizure_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_seizure_action_1,
                R.string.firstaid_seizure_action_2,
                R.string.firstaid_seizure_action_3,
                R.string.firstaid_seizure_action_4,
                R.string.firstaid_seizure_action_5,
                R.string.firstaid_seizure_action_6,
                R.string.firstaid_seizure_action_7,
                R.string.firstaid_seizure_action_8,
                R.string.firstaid_seizure_action_9,
                R.string.firstaid_seizure_action_10,
                R.string.firstaid_seizure_action_11,
                R.string.firstaid_seizure_action_12,
                R.string.firstaid_seizure_action_13,
                R.string.firstaid_seizure_action_14,
                R.string.firstaid_seizure_action_15,
                R.string.firstaid_seizure_action_16,
                R.string.firstaid_seizure_action_17
            ),
            doListRes = listOf(
                R.string.firstaid_seizure_do_1,
                R.string.firstaid_seizure_do_2,
                R.string.firstaid_seizure_do_3,
                R.string.firstaid_seizure_do_4,
                R.string.firstaid_seizure_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_seizure_dont_1,
                R.string.firstaid_seizure_dont_2,
                R.string.firstaid_seizure_dont_3,
                R.string.firstaid_seizure_dont_4
            ),
            transportInfoRes = R.string.firstaid_seizure_transport,
            specialNotesRes = R.string.firstaid_seizure_special_notes,
            needsTimer = true,
            timerMinutes = 5
        ),

        // ════════════════════════════════════════════════════════
        // STARKE BLUTUNG
        // ════════════════════════════════════════════════════════
        "BLEEDING" to FirstAidInstruction(
            type = "BLEEDING",
            titleRes = R.string.firstaid_bleeding_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_bleeding_symptom_1,
                R.string.firstaid_bleeding_symptom_2,
                R.string.firstaid_bleeding_symptom_3,
                R.string.firstaid_bleeding_symptom_4
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_bleeding_action_1,
                R.string.firstaid_bleeding_action_2,
                R.string.firstaid_bleeding_action_3,
                R.string.firstaid_bleeding_action_4,
                R.string.firstaid_bleeding_action_5,
                R.string.firstaid_bleeding_action_6,
                R.string.firstaid_bleeding_action_7,
                R.string.firstaid_bleeding_action_8,
                R.string.firstaid_bleeding_action_9,
                R.string.firstaid_bleeding_action_10,
                R.string.firstaid_bleeding_action_11,
                R.string.firstaid_bleeding_action_12,
                R.string.firstaid_bleeding_action_13,
                R.string.firstaid_bleeding_action_14,
                R.string.firstaid_bleeding_action_15
            ),
            doListRes = listOf(
                R.string.firstaid_bleeding_do_1,
                R.string.firstaid_bleeding_do_2,
                R.string.firstaid_bleeding_do_3,
                R.string.firstaid_bleeding_do_4,
                R.string.firstaid_bleeding_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_bleeding_dont_1,
                R.string.firstaid_bleeding_dont_2,
                R.string.firstaid_bleeding_dont_3,
                R.string.firstaid_bleeding_dont_4
            ),
            transportInfoRes = R.string.firstaid_bleeding_transport,
            specialNotesRes = R.string.firstaid_bleeding_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // KNOCHENBRUCH
        // ════════════════════════════════════════════════════════
        "BONE_FRACTURE" to FirstAidInstruction(
            type = "BONE_FRACTURE",
            titleRes = R.string.firstaid_bone_fracture_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_bone_fracture_symptom_1,
                R.string.firstaid_bone_fracture_symptom_2,
                R.string.firstaid_bone_fracture_symptom_3,
                R.string.firstaid_bone_fracture_symptom_4,
                R.string.firstaid_bone_fracture_symptom_5
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_bone_fracture_action_1,
                R.string.firstaid_bone_fracture_action_2,
                R.string.firstaid_bone_fracture_action_3,
                R.string.firstaid_bone_fracture_action_4,
                R.string.firstaid_bone_fracture_action_5,
                R.string.firstaid_bone_fracture_action_6,
                R.string.firstaid_bone_fracture_action_7,
                R.string.firstaid_bone_fracture_action_8,
                R.string.firstaid_bone_fracture_action_9,
                R.string.firstaid_bone_fracture_action_10,
                R.string.firstaid_bone_fracture_action_11,
                R.string.firstaid_bone_fracture_action_12,
                R.string.firstaid_bone_fracture_action_13
            ),
            doListRes = listOf(
                R.string.firstaid_bone_fracture_do_1,
                R.string.firstaid_bone_fracture_do_2,
                R.string.firstaid_bone_fracture_do_3,
                R.string.firstaid_bone_fracture_do_4,
                R.string.firstaid_bone_fracture_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_bone_fracture_dont_1,
                R.string.firstaid_bone_fracture_dont_2,
                R.string.firstaid_bone_fracture_dont_3,
                R.string.firstaid_bone_fracture_dont_4
            ),
            transportInfoRes = R.string.firstaid_bone_fracture_transport,
            specialNotesRes = R.string.firstaid_bone_fracture_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // AUGENVERLETZUNG
        // ════════════════════════════════════════════════════════
        "EYE_INJURY" to FirstAidInstruction(
            type = "EYE_INJURY",
            titleRes = R.string.firstaid_eye_injury_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_eye_injury_symptom_1,
                R.string.firstaid_eye_injury_symptom_2,
                R.string.firstaid_eye_injury_symptom_3,
                R.string.firstaid_eye_injury_symptom_4,
                R.string.firstaid_eye_injury_symptom_5,
                R.string.firstaid_eye_injury_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_eye_injury_action_1,
                R.string.firstaid_eye_injury_action_2,
                R.string.firstaid_eye_injury_action_3,
                R.string.firstaid_eye_injury_action_4,
                R.string.firstaid_eye_injury_action_5,
                R.string.firstaid_eye_injury_action_6,
                R.string.firstaid_eye_injury_action_7,
                R.string.firstaid_eye_injury_action_8,
                R.string.firstaid_eye_injury_action_9,
                R.string.firstaid_eye_injury_action_10,
                R.string.firstaid_eye_injury_action_11,
                R.string.firstaid_eye_injury_action_12,
                R.string.firstaid_eye_injury_action_13,
                R.string.firstaid_eye_injury_action_14
            ),
            doListRes = listOf(
                R.string.firstaid_eye_injury_do_1,
                R.string.firstaid_eye_injury_do_2,
                R.string.firstaid_eye_injury_do_3,
                R.string.firstaid_eye_injury_do_4,
                R.string.firstaid_eye_injury_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_eye_injury_dont_1,
                R.string.firstaid_eye_injury_dont_2,
                R.string.firstaid_eye_injury_dont_3,
                R.string.firstaid_eye_injury_dont_4
            ),
            transportInfoRes = R.string.firstaid_eye_injury_transport,
            specialNotesRes = R.string.firstaid_eye_injury_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // ERTRINKEN
        // ════════════════════════════════════════════════════════
        "DROWNING" to FirstAidInstruction(
            type = "DROWNING",
            titleRes = R.string.firstaid_drowning_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_drowning_symptom_1,
                R.string.firstaid_drowning_symptom_2,
                R.string.firstaid_drowning_symptom_3,
                R.string.firstaid_drowning_symptom_4,
                R.string.firstaid_drowning_symptom_5,
                R.string.firstaid_drowning_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_drowning_action_1,
                R.string.firstaid_drowning_action_2,
                R.string.firstaid_drowning_action_3,
                R.string.firstaid_drowning_action_4,
                R.string.firstaid_drowning_action_5,
                R.string.firstaid_drowning_action_6,
                R.string.firstaid_drowning_action_7,
                R.string.firstaid_drowning_action_8,
                R.string.firstaid_drowning_action_9,
                R.string.firstaid_drowning_action_10,
                R.string.firstaid_drowning_action_11,
                R.string.firstaid_drowning_action_12,
                R.string.firstaid_drowning_action_13,
                R.string.firstaid_drowning_action_14,
                R.string.firstaid_drowning_action_15
            ),
            doListRes = listOf(
                R.string.firstaid_drowning_do_1,
                R.string.firstaid_drowning_do_2,
                R.string.firstaid_drowning_do_3,
                R.string.firstaid_drowning_do_4,
                R.string.firstaid_drowning_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_drowning_dont_1,
                R.string.firstaid_drowning_dont_2,
                R.string.firstaid_drowning_dont_3,
                R.string.firstaid_drowning_dont_4
            ),
            transportInfoRes = R.string.firstaid_drowning_transport,
            specialNotesRes = R.string.firstaid_drowning_special_notes,
            needsHLW = true
        ),

        // ════════════════════════════════════════════════════════
        // UNTERKÜHLUNG
        // ════════════════════════════════════════════════════════
        "HYPOTHERMIA" to FirstAidInstruction(
            type = "HYPOTHERMIA",
            titleRes = R.string.firstaid_hypothermia_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_hypothermia_symptom_1,
                R.string.firstaid_hypothermia_symptom_2,
                R.string.firstaid_hypothermia_symptom_3,
                R.string.firstaid_hypothermia_symptom_4,
                R.string.firstaid_hypothermia_symptom_5,
                R.string.firstaid_hypothermia_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_hypothermia_action_1,
                R.string.firstaid_hypothermia_action_2,
                R.string.firstaid_hypothermia_action_3,
                R.string.firstaid_hypothermia_action_4,
                R.string.firstaid_hypothermia_action_5,
                R.string.firstaid_hypothermia_action_6,
                R.string.firstaid_hypothermia_action_7,
                R.string.firstaid_hypothermia_action_8,
                R.string.firstaid_hypothermia_action_9,
                R.string.firstaid_hypothermia_action_10,
                R.string.firstaid_hypothermia_action_11,
                R.string.firstaid_hypothermia_action_12,
                R.string.firstaid_hypothermia_action_13,
                R.string.firstaid_hypothermia_action_14,
                R.string.firstaid_hypothermia_action_15
            ),
            doListRes = listOf(
                R.string.firstaid_hypothermia_do_1,
                R.string.firstaid_hypothermia_do_2,
                R.string.firstaid_hypothermia_do_3,
                R.string.firstaid_hypothermia_do_4,
                R.string.firstaid_hypothermia_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_hypothermia_dont_1,
                R.string.firstaid_hypothermia_dont_2,
                R.string.firstaid_hypothermia_dont_3,
                R.string.firstaid_hypothermia_dont_4
            ),
            transportInfoRes = R.string.firstaid_hypothermia_transport,
            specialNotesRes = R.string.firstaid_hypothermia_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // FREMDKÖRPER IM SCHLUND
        // ════════════════════════════════════════════════════════
        "FOREIGN_OBJECT" to FirstAidInstruction(
            type = "FOREIGN_OBJECT",
            titleRes = R.string.firstaid_foreign_object_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_foreign_object_symptom_1,
                R.string.firstaid_foreign_object_symptom_2,
                R.string.firstaid_foreign_object_symptom_3,
                R.string.firstaid_foreign_object_symptom_4,
                R.string.firstaid_foreign_object_symptom_5
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_foreign_object_action_1,
                R.string.firstaid_foreign_object_action_2,
                R.string.firstaid_foreign_object_action_3,
                R.string.firstaid_foreign_object_action_4,
                R.string.firstaid_foreign_object_action_5,
                R.string.firstaid_foreign_object_action_6,
                R.string.firstaid_foreign_object_action_7,
                R.string.firstaid_foreign_object_action_8,
                R.string.firstaid_foreign_object_action_9,
                R.string.firstaid_foreign_object_action_10,
                R.string.firstaid_foreign_object_action_11,
                R.string.firstaid_foreign_object_action_12,
                R.string.firstaid_foreign_object_action_13,
                R.string.firstaid_foreign_object_action_14,
                R.string.firstaid_foreign_object_action_15,
                R.string.firstaid_foreign_object_action_16
            ),
            doListRes = listOf(
                R.string.firstaid_foreign_object_do_1,
                R.string.firstaid_foreign_object_do_2,
                R.string.firstaid_foreign_object_do_3,
                R.string.firstaid_foreign_object_do_4
            ),
            dontListRes = listOf(
                R.string.firstaid_foreign_object_dont_1,
                R.string.firstaid_foreign_object_dont_2,
                R.string.firstaid_foreign_object_dont_3,
                R.string.firstaid_foreign_object_dont_4
            ),
            transportInfoRes = R.string.firstaid_foreign_object_transport,
            specialNotesRes = R.string.firstaid_foreign_object_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // ATEMNOT
        // ════════════════════════════════════════════════════════
        "BREATHING_DIFFICULTY" to FirstAidInstruction(
            type = "BREATHING_DIFFICULTY",
            titleRes = R.string.firstaid_breathing_difficulty_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_breathing_difficulty_symptom_1,
                R.string.firstaid_breathing_difficulty_symptom_2,
                R.string.firstaid_breathing_difficulty_symptom_3,
                R.string.firstaid_breathing_difficulty_symptom_4,
                R.string.firstaid_breathing_difficulty_symptom_5,
                R.string.firstaid_breathing_difficulty_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_breathing_difficulty_action_1,
                R.string.firstaid_breathing_difficulty_action_2,
                R.string.firstaid_breathing_difficulty_action_3,
                R.string.firstaid_breathing_difficulty_action_4,
                R.string.firstaid_breathing_difficulty_action_5,
                R.string.firstaid_breathing_difficulty_action_6,
                R.string.firstaid_breathing_difficulty_action_7,
                R.string.firstaid_breathing_difficulty_action_8,
                R.string.firstaid_breathing_difficulty_action_9,
                R.string.firstaid_breathing_difficulty_action_10,
                R.string.firstaid_breathing_difficulty_action_11,
                R.string.firstaid_breathing_difficulty_action_12,
                R.string.firstaid_breathing_difficulty_action_13,
                R.string.firstaid_breathing_difficulty_action_14,
                R.string.firstaid_breathing_difficulty_action_15,
                R.string.firstaid_breathing_difficulty_action_16
            ),
            doListRes = listOf(
                R.string.firstaid_breathing_difficulty_do_1,
                R.string.firstaid_breathing_difficulty_do_2,
                R.string.firstaid_breathing_difficulty_do_3,
                R.string.firstaid_breathing_difficulty_do_4,
                R.string.firstaid_breathing_difficulty_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_breathing_difficulty_dont_1,
                R.string.firstaid_breathing_difficulty_dont_2,
                R.string.firstaid_breathing_difficulty_dont_3,
                R.string.firstaid_breathing_difficulty_dont_4
            ),
            transportInfoRes = R.string.firstaid_breathing_difficulty_transport,
            specialNotesRes = R.string.firstaid_breathing_difficulty_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // ERBRECHEN / DURCHFALL
        // ════════════════════════════════════════════════════════
        "VOMITING" to FirstAidInstruction(
            type = "VOMITING",
            titleRes = R.string.firstaid_vomiting_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_vomiting_symptom_1,
                R.string.firstaid_vomiting_symptom_2,
                R.string.firstaid_vomiting_symptom_3,
                R.string.firstaid_vomiting_symptom_4,
                R.string.firstaid_vomiting_symptom_5,
                R.string.firstaid_vomiting_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_vomiting_action_1,
                R.string.firstaid_vomiting_action_2,
                R.string.firstaid_vomiting_action_3,
                R.string.firstaid_vomiting_action_4,
                R.string.firstaid_vomiting_action_5,
                R.string.firstaid_vomiting_action_6,
                R.string.firstaid_vomiting_action_7,
                R.string.firstaid_vomiting_action_8,
                R.string.firstaid_vomiting_action_9,
                R.string.firstaid_vomiting_action_10,
                R.string.firstaid_vomiting_action_11,
                R.string.firstaid_vomiting_action_12,
                R.string.firstaid_vomiting_action_13,
                R.string.firstaid_vomiting_action_14,
                R.string.firstaid_vomiting_action_15,
                R.string.firstaid_vomiting_action_16,
                R.string.firstaid_vomiting_action_17
            ),
            doListRes = listOf(
                R.string.firstaid_vomiting_do_1,
                R.string.firstaid_vomiting_do_2,
                R.string.firstaid_vomiting_do_3,
                R.string.firstaid_vomiting_do_4,
                R.string.firstaid_vomiting_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_vomiting_dont_1,
                R.string.firstaid_vomiting_dont_2,
                R.string.firstaid_vomiting_dont_3,
                R.string.firstaid_vomiting_dont_4
            ),
            transportInfoRes = R.string.firstaid_vomiting_transport,
            specialNotesRes = R.string.firstaid_vomiting_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // ALLERGISCHE REAKTION
        // ════════════════════════════════════════════════════════
        "ALLERGIC_REACTION" to FirstAidInstruction(
            type = "ALLERGIC_REACTION",
            titleRes = R.string.firstaid_allergic_reaction_title,
            urgency = UrgencyLevel.URGENT,
            symptomsRes = listOf(
                R.string.firstaid_allergic_reaction_symptom_1,
                R.string.firstaid_allergic_reaction_symptom_2,
                R.string.firstaid_allergic_reaction_symptom_3,
                R.string.firstaid_allergic_reaction_symptom_4,
                R.string.firstaid_allergic_reaction_symptom_5,
                R.string.firstaid_allergic_reaction_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_allergic_reaction_action_1,
                R.string.firstaid_allergic_reaction_action_2,
                R.string.firstaid_allergic_reaction_action_3,
                R.string.firstaid_allergic_reaction_action_4,
                R.string.firstaid_allergic_reaction_action_5,
                R.string.firstaid_allergic_reaction_action_6,
                R.string.firstaid_allergic_reaction_action_7,
                R.string.firstaid_allergic_reaction_action_8,
                R.string.firstaid_allergic_reaction_action_9,
                R.string.firstaid_allergic_reaction_action_10,
                R.string.firstaid_allergic_reaction_action_11,
                R.string.firstaid_allergic_reaction_action_12,
                R.string.firstaid_allergic_reaction_action_13,
                R.string.firstaid_allergic_reaction_action_14,
                R.string.firstaid_allergic_reaction_action_15
            ),
            doListRes = listOf(
                R.string.firstaid_allergic_reaction_do_1,
                R.string.firstaid_allergic_reaction_do_2,
                R.string.firstaid_allergic_reaction_do_3,
                R.string.firstaid_allergic_reaction_do_4,
                R.string.firstaid_allergic_reaction_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_allergic_reaction_dont_1,
                R.string.firstaid_allergic_reaction_dont_2,
                R.string.firstaid_allergic_reaction_dont_3,
                R.string.firstaid_allergic_reaction_dont_4
            ),
            transportInfoRes = R.string.firstaid_allergic_reaction_transport,
            specialNotesRes = R.string.firstaid_allergic_reaction_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // VERKEHRSUNFALL
        // ════════════════════════════════════════════════════════
        "TRAFFIC_ACCIDENT" to FirstAidInstruction(
            type = "TRAFFIC_ACCIDENT",
            titleRes = R.string.firstaid_traffic_accident_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_traffic_accident_symptom_1,
                R.string.firstaid_traffic_accident_symptom_2,
                R.string.firstaid_traffic_accident_symptom_3,
                R.string.firstaid_traffic_accident_symptom_4,
                R.string.firstaid_traffic_accident_symptom_5,
                R.string.firstaid_traffic_accident_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_traffic_accident_action_1,
                R.string.firstaid_traffic_accident_action_2,
                R.string.firstaid_traffic_accident_action_3,
                R.string.firstaid_traffic_accident_action_4,
                R.string.firstaid_traffic_accident_action_5,
                R.string.firstaid_traffic_accident_action_6,
                R.string.firstaid_traffic_accident_action_7,
                R.string.firstaid_traffic_accident_action_8,
                R.string.firstaid_traffic_accident_action_9,
                R.string.firstaid_traffic_accident_action_10,
                R.string.firstaid_traffic_accident_action_11,
                R.string.firstaid_traffic_accident_action_12,
                R.string.firstaid_traffic_accident_action_13,
                R.string.firstaid_traffic_accident_action_14,
                R.string.firstaid_traffic_accident_action_15,
                R.string.firstaid_traffic_accident_action_16,
                R.string.firstaid_traffic_accident_action_17,
                R.string.firstaid_traffic_accident_action_18,
                R.string.firstaid_traffic_accident_action_19
            ),
            doListRes = listOf(
                R.string.firstaid_traffic_accident_do_1,
                R.string.firstaid_traffic_accident_do_2,
                R.string.firstaid_traffic_accident_do_3,
                R.string.firstaid_traffic_accident_do_4,
                R.string.firstaid_traffic_accident_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_traffic_accident_dont_1,
                R.string.firstaid_traffic_accident_dont_2,
                R.string.firstaid_traffic_accident_dont_3,
                R.string.firstaid_traffic_accident_dont_4
            ),
            transportInfoRes = R.string.firstaid_traffic_accident_transport,
            specialNotesRes = R.string.firstaid_traffic_accident_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // VERÄTZUNG (SÄUREN/LAUGEN)
        // ════════════════════════════════════════════════════════
        "CHEMICAL_BURN" to FirstAidInstruction(
            type = "CHEMICAL_BURN",
            titleRes = R.string.firstaid_chemical_burn_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_chemical_burn_symptom_1,
                R.string.firstaid_chemical_burn_symptom_2,
                R.string.firstaid_chemical_burn_symptom_3,
                R.string.firstaid_chemical_burn_symptom_4,
                R.string.firstaid_chemical_burn_symptom_5,
                R.string.firstaid_chemical_burn_symptom_6
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_chemical_burn_action_1,
                R.string.firstaid_chemical_burn_action_2,
                R.string.firstaid_chemical_burn_action_3,
                R.string.firstaid_chemical_burn_action_4,
                R.string.firstaid_chemical_burn_action_5,
                R.string.firstaid_chemical_burn_action_6,
                R.string.firstaid_chemical_burn_action_7,
                R.string.firstaid_chemical_burn_action_8,
                R.string.firstaid_chemical_burn_action_9,
                R.string.firstaid_chemical_burn_action_10,
                R.string.firstaid_chemical_burn_action_11,
                R.string.firstaid_chemical_burn_action_12,
                R.string.firstaid_chemical_burn_action_13,
                R.string.firstaid_chemical_burn_action_14,
                R.string.firstaid_chemical_burn_action_15,
                R.string.firstaid_chemical_burn_action_16,
                R.string.firstaid_chemical_burn_action_17
            ),
            doListRes = listOf(
                R.string.firstaid_chemical_burn_do_1,
                R.string.firstaid_chemical_burn_do_2,
                R.string.firstaid_chemical_burn_do_3,
                R.string.firstaid_chemical_burn_do_4,
                R.string.firstaid_chemical_burn_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_chemical_burn_dont_1,
                R.string.firstaid_chemical_burn_dont_2,
                R.string.firstaid_chemical_burn_dont_3,
                R.string.firstaid_chemical_burn_dont_4
            ),
            transportInfoRes = R.string.firstaid_chemical_burn_transport,
            specialNotesRes = R.string.firstaid_chemical_burn_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // BLUTUNGEN OHNE UNFALL
        // ════════════════════════════════════════════════════════
        "BLEEDING_NO_ACCIDENT" to FirstAidInstruction(
            type = "BLEEDING_NO_ACCIDENT",
            titleRes = R.string.firstaid_bleeding_no_accident_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_bleeding_no_accident_symptom_1,
                R.string.firstaid_bleeding_no_accident_symptom_2,
                R.string.firstaid_bleeding_no_accident_symptom_3,
                R.string.firstaid_bleeding_no_accident_symptom_4,
                R.string.firstaid_bleeding_no_accident_symptom_5
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_bleeding_no_accident_action_1,
                R.string.firstaid_bleeding_no_accident_action_2,
                R.string.firstaid_bleeding_no_accident_action_3,
                R.string.firstaid_bleeding_no_accident_action_4,
                R.string.firstaid_bleeding_no_accident_action_5,
                R.string.firstaid_bleeding_no_accident_action_6,
                R.string.firstaid_bleeding_no_accident_action_7,
                R.string.firstaid_bleeding_no_accident_action_8,
                R.string.firstaid_bleeding_no_accident_action_9,
                R.string.firstaid_bleeding_no_accident_action_10,
                R.string.firstaid_bleeding_no_accident_action_11,
                R.string.firstaid_bleeding_no_accident_action_12,
                R.string.firstaid_bleeding_no_accident_action_13,
                R.string.firstaid_bleeding_no_accident_action_14,
                R.string.firstaid_bleeding_no_accident_action_15,
                R.string.firstaid_bleeding_no_accident_action_16,
                R.string.firstaid_bleeding_no_accident_action_17
            ),
            doListRes = listOf(
                R.string.firstaid_bleeding_no_accident_do_1,
                R.string.firstaid_bleeding_no_accident_do_2,
                R.string.firstaid_bleeding_no_accident_do_3,
                R.string.firstaid_bleeding_no_accident_do_4,
                R.string.firstaid_bleeding_no_accident_do_5
            ),
            dontListRes = listOf(
                R.string.firstaid_bleeding_no_accident_dont_1,
                R.string.firstaid_bleeding_no_accident_dont_2,
                R.string.firstaid_bleeding_no_accident_dont_3,
                R.string.firstaid_bleeding_no_accident_dont_4
            ),
            transportInfoRes = R.string.firstaid_bleeding_no_accident_transport,
            specialNotesRes = R.string.firstaid_bleeding_no_accident_special_notes
        ),

        // ════════════════════════════════════════════════════════
        // VERGIFTUNG (UNBEKANNT)
        // ════════════════════════════════════════════════════════
        "POISONING_UNKNOWN" to FirstAidInstruction(
            type = "POISONING_UNKNOWN",
            titleRes = R.string.firstaid_poisoning_unknown_title,
            urgency = UrgencyLevel.CRITICAL,
            symptomsRes = listOf(
                R.string.firstaid_poisoning_unknown_symptom_1,
                R.string.firstaid_poisoning_unknown_symptom_2,
                R.string.firstaid_poisoning_unknown_symptom_3,
                R.string.firstaid_poisoning_unknown_symptom_4,
                R.string.firstaid_poisoning_unknown_symptom_5,
                R.string.firstaid_poisoning_unknown_symptom_6,
                R.string.firstaid_poisoning_unknown_symptom_7,
                R.string.firstaid_poisoning_unknown_symptom_8
            ),
            immediateActionsRes = listOf(
                R.string.firstaid_poisoning_unknown_action_1,
                R.string.firstaid_poisoning_unknown_action_2,
                R.string.firstaid_poisoning_unknown_action_3,
                R.string.firstaid_poisoning_unknown_action_4,
                R.string.firstaid_poisoning_unknown_action_5,
                R.string.firstaid_poisoning_unknown_action_6,
                R.string.firstaid_poisoning_unknown_action_7,
                R.string.firstaid_poisoning_unknown_action_8,
                R.string.firstaid_poisoning_unknown_action_9,
                R.string.firstaid_poisoning_unknown_action_10,
                R.string.firstaid_poisoning_unknown_action_11,
                R.string.firstaid_poisoning_unknown_action_12,
                R.string.firstaid_poisoning_unknown_action_13,
                R.string.firstaid_poisoning_unknown_action_14,
                R.string.firstaid_poisoning_unknown_action_15,
                R.string.firstaid_poisoning_unknown_action_16,
                R.string.firstaid_poisoning_unknown_action_17,
                R.string.firstaid_poisoning_unknown_action_18,
                R.string.firstaid_poisoning_unknown_action_19,
                R.string.firstaid_poisoning_unknown_action_20,
                R.string.firstaid_poisoning_unknown_action_21
            ),
            doListRes = listOf(
                R.string.firstaid_poisoning_unknown_do_1,
                R.string.firstaid_poisoning_unknown_do_2,
                R.string.firstaid_poisoning_unknown_do_3,
                R.string.firstaid_poisoning_unknown_do_4,
                R.string.firstaid_poisoning_unknown_do_5,
                R.string.firstaid_poisoning_unknown_do_6,
                R.string.firstaid_poisoning_unknown_do_7
            ),
            dontListRes = listOf(
                R.string.firstaid_poisoning_unknown_dont_1,
                R.string.firstaid_poisoning_unknown_dont_2,
                R.string.firstaid_poisoning_unknown_dont_3,
                R.string.firstaid_poisoning_unknown_dont_4,
                R.string.firstaid_poisoning_unknown_dont_5,
                R.string.firstaid_poisoning_unknown_dont_6
            ),
            transportInfoRes = R.string.firstaid_poisoning_unknown_transport,
            specialNotesRes = R.string.firstaid_poisoning_unknown_special_notes
        )
    )

    /**
     * Symptom-Check Fragen pro Typ
     */
    fun getSymptomChecks(context: android.content.Context, type: String): List<SymptomCheckQuestion> = when (type) {
        "UNCONSCIOUSNESS" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_unconsciousness_q1, criticalIfNo = true),
            SymptomCheckQuestion(R.string.firstaid_check_unconsciousness_q2, criticalIfNo = true),
            SymptomCheckQuestion(R.string.firstaid_check_unconsciousness_q3, criticalIfNo = true),
            SymptomCheckQuestion(R.string.firstaid_check_unconsciousness_q4, criticalIfNo = true)
        )

        "HEAT_STROKE" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_heat_stroke_q1, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_heat_stroke_q2, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_heat_stroke_q3, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_heat_stroke_q4, criticalIfYes = true)
        )

        "POISONING_KNOWN" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_known_q1, criticalIfNo = false),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_known_q2, criticalIfYes = false),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_known_q3, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_known_q4, criticalIfYes = true)
        )

        "GASTRIC_TORSION" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_gastric_torsion_q1, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_gastric_torsion_q2, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_gastric_torsion_q3, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_gastric_torsion_q4, criticalIfYes = true)
        )

        "SEIZURE" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_seizure_q1, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_seizure_q2, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_seizure_q3, criticalIfYes = false)
        )

        "BLEEDING" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_bleeding_q1, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_bleeding_q2, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_bleeding_q3, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_bleeding_q4, criticalIfYes = true)
        )

        "POISONING_UNKNOWN" -> listOf(
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_unknown_q1, criticalIfYes = false),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_unknown_q2, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_unknown_q3, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_unknown_q4, criticalIfYes = true),
            SymptomCheckQuestion(R.string.firstaid_check_poisoning_unknown_q5, criticalIfYes = true)
        )

        else -> emptyList()
    }
}
