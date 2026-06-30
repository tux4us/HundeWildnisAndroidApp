package com.hundewildnis.app// ════════════════════════════════════════════════════════════════
// INTEGRATIONS-ANLEITUNG – Intervall-Timer
// Alle erforderlichen Änderungen an bestehenden Dateien
// ════════════════════════════════════════════════════════════════


// ── 1. AndroidManifest.xml ───────────────────────────────────────
// Folgende drei <activity>-Einträge nach den bestehenden Activities einfügen:

/*
<activity
    android:name=".IntervalTimerSetupActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize"
    android:theme="@style/Theme.HundeWildnis" />

<activity
    android:name=".IntervalTimerActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.HundeWildnis" />

<activity
    android:name=".IntervalTimerHistoryActivity"
    android:exported="false"
    android:theme="@style/Theme.HundeWildnis" />
*/


// ── 2. SelectiveBackupTypes.kt ───────────────────────────────────
// In BackupDataType enum folgenden Eintrag ergänzen (nach VITAL_RECORDS):

/*
INTERVAL_TIMER(
    "Intervall-Timer",
    "Alle Timer-Sessions"
),
*/


// ── 3. BackupData.kt ─────────────────────────────────────────────
// Im BackupData data class folgendes Feld ergänzen (nach nasenarbeitSessions):

/*
@SerializedName("intervalTimerSessions") val intervalTimerSessions: List<IntervalTimerSession>? = null,
*/


// ── 4. BackupManager.kt – createBackup() ─────────────────────────
// Im createBackupData()-Aufruf ergänzen:

/*
intervalTimerSessions = IntervalTimerManager.getAllSessions(context),
*/


// ── 5. BackupManager.kt – restoreBackup() ────────────────────────
// Im restore-Block ergänzen (Muster wie vorhanden):

/*
data.intervalTimerSessions?.forEach { session ->
    IntervalTimerManager.saveSession(context, session)
}
*/


// ── 6. Einstiegspunkt: DogProfileActivity oder StartActivity ─────
// Intervall-Timer über DogProfileActivity starten (mit Profil):

/*
// In DogProfileActivity: z.B. neuer Button "⏱️ Intervall-Timer"
val intent = Intent(this, IntervalTimerSetupActivity::class.java).apply {
    putExtra(IntervalTimerSetupActivity.EXTRA_DOG_PROFILE, currentProfile)
}
startActivity(intent)
*/

// Intervall-Timer ohne Profil (von Startseite oder Menü):
/*
startActivity(Intent(this, IntervalTimerSetupActivity::class.java))
*/


// ── 7. Verlauf öffnen ────────────────────────────────────────────
// Aus DogProfileActivity mit Filter auf Hund:
/*
val intent = Intent(this, IntervalTimerHistoryActivity::class.java).apply {
    putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_ID, profile.id)
    putExtra(IntervalTimerHistoryActivity.EXTRA_DOG_NAME, profile.name)
}
startActivity(intent)
*/

// Globaler Verlauf (alle Hunde):
/*
startActivity(Intent(this, IntervalTimerHistoryActivity::class.java))
*/


// ── 8. AddSessionActivity – Prefill-Extra ────────────────────────
// In AddSessionActivity.kt onCreate(), nach dem Lesen von category_id:

/*
val prefillMinutes = intent.getIntExtra("prefill_minutes", -1)
if (prefillMinutes > 0) {
    // SeekBar / EditText für Dauer vorausfüllen
    binding.seekBarDuration.progress = prefillMinutes.coerceAtMost(TrainingConfig.MAX_TRAINING_DURATION)
    // Falls MeasurementType-RadioButton vorhanden:
    binding.radioTypeTime.isChecked = true
}
*/


// ════════════════════════════════════════════════════════════════
// DATEI-ÜBERSICHT – Alle neuen Dateien
// ════════════════════════════════════════════════════════════════
//
// Kotlin (src/main/java/com/hundewildnis/app/):
//   IntervalTimerModels.kt          – Datenmodell (Config, Session)
//   IntervalTimerManager.kt         – Persistenz (SharedPreferences + Gson)
//   IntervalTimerSetupActivity.kt   – Konfigurationsscreen
//   IntervalTimerActivity.kt        – Laufender Timer
//   IntervalTimerHistoryActivity.kt – Verlaufsansicht + CSV-Export
//
// Layouts (src/main/res/layout/):
//   activity_interval_timer_setup.xml   – Setup-Screen
//   activity_interval_timer.xml         – Timer-Screen
//   activity_interval_timer_history.xml – Verlaufs-Screen
//   item_interval_session.xml           – RecyclerView-Item
//
// Keine neuen Ressourcen, Permissions oder Bibliotheken erforderlich.
// Alle genutzten Klassen (ToneGenerator, CountDownTimer, Vibrator,
// Gson, SecureBaseActivity, MaterialButton, etc.) sind bereits vorhanden.
// ════════════════════════════════════════════════════════════════
