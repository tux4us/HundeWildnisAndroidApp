package com.hundewildnis.app// ════════════════════════════════════════════════════════════════
// INTEGRATION – Gefahrenpflanzen-Scout
// ════════════════════════════════════════════════════════════════


// ── 1. AndroidManifest.xml ───────────────────────────────────────
// Vor </application> einfügen:
/*
        <activity
            android:name="com.hundewildnis.app.PlantScoutActivity"
            android:exported="false"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.HundeWildnis" />

        <activity
            android:name="com.hundewildnis.app.PlantDetailActivity"
            android:exported="false"
            android:screenOrientation="portrait"
            android:theme="@style/Theme.HundeWildnis" />
*/


// ── 2. activity_start.xml ───────────────────────────────────────
// Neuen Button im Bereich "GRUPPE 2: Trainingswerkzeuge"
// Nach btnNasenarbeit oder btnTickLoupe einfügen:
/*
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnPlantScout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="64dp"
            android:text="🌿☠️ Gefahrenpflanzen-Scout"
            android:textSize="16sp"
            android:textStyle="bold"
            app:cornerRadius="12dp"
            app:backgroundTint="#3E5723"
            android:textColor="#FFFFFF"
            android:layout_marginBottom="8dp" />
*/


// ── 3. StartActivity.kt ─────────────────────────────────────────
// In setupButtons() nach btnTickLoupe einfügen:
/*
        binding.btnPlantScout.setOnClickListener {
            startActivity(Intent(this, PlantScoutActivity::class.java))
        }
*/


// ── 4. FirstAidWizardActivity.kt – Vorauswahl (optional) ────────
// In onCreate() oder der Methode die Intent-Extras verarbeitet,
// folgenden Block ergänzen um die Pflanzennamen-Vorauswahl zu nutzen:
/*
        val poisonName = intent.getStringExtra("poison_name")
        if (!poisonName.isNullOrEmpty()) {
            // Symptomfeld oder Notiz-Feld mit Pflanzennamen vorausfüllen
            binding.etAdditionalNotes?.setText("Aufgenommene Pflanze: $poisonName")
        }
*/


// ════════════════════════════════════════════════════════════════
// DATEI-ÜBERSICHT – Alle neuen Dateien
// ════════════════════════════════════════════════════════════════
//
// Kotlin (src/main/java/com/hundewildnis/app/):
//   ToxicPlantData.kt         – Datenmodell + Datenbank (40 Pflanzen, offline)
//   PlantScoutActivity.kt     – Hauptscreen (Suche, Filter, Saison-Banner)
//   PlantDetailActivity.kt    – Detailansicht mit Notfall-Button
//   PlantListAdapter.kt       – RecyclerView-Adapter
//
// Layouts (src/main/res/layout/):
//   activity_plant_scout.xml  – Hauptscreen-Layout
//   activity_plant_detail.xml – Detailansicht-Layout
//   item_plant.xml            – RecyclerView-Item
//
// Menü (src/main/res/menu/):
//   menu_plant_scout.xml      – Saisonfilter-Toggle
//
// Keine neuen Berechtigungen. Keine neuen Bibliotheken.
// Vollständig offline – keine API-Abhängigkeit.
// ════════════════════════════════════════════════════════════════
