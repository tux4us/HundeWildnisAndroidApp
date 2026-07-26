package com.hundewildnis.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import com.hundewildnis.app.databinding.ActivityWebdavSyncBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Einstellungs- und Steuerungs-Bildschirm für die WebDAV-Synchronisation.
 *
 * Aufgerufen von BackupActivity ("🔄 WebDAV-Sync" Button), da es sich
 * thematisch um eine Erweiterung des Backup/Restore-Features handelt.
 */
class WebDavSyncActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityWebdavSyncBinding
    private lateinit var prefs: WebDavPreferences
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private val INTERVAL_OPTIONS = listOf(
            "Alle 2 Stunden" to 2,
            "Alle 4 Stunden" to 4,
            "Alle 6 Stunden" to 6,
            "Alle 12 Stunden" to 12,
            "Einmal täglich" to 24
        )
    }

    private fun buttonColor(): Int {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#2d5016")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebdavSyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "🔄 WebDAV-Synchronisation"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_arrow_back_white)
        }

        prefs = WebDavPreferences(this)

        setupIntervalSpinner()
        loadCurrentSettings()
        setupListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun setupIntervalSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            INTERVAL_OPTIONS.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSyncInterval.adapter = adapter
    }

    private fun loadCurrentSettings() {
        binding.switchWebdavEnabled.isChecked = prefs.syncEnabled
        binding.layoutWebdavCredentials.isVisible = prefs.syncEnabled

        val config = prefs.loadConfig()
        binding.etServerUrl.setText(config?.serverUrl ?: "")
        binding.etUsername.setText(config?.username ?: "")
        // Passwort-Feld bewusst leer lassen - vorhandenes Passwort bleibt beim Speichern erhalten
        binding.tvPasswordSavedHint.isVisible = !config?.password.isNullOrBlank()

        binding.switchAutoSync.isChecked = prefs.autoSyncEnabled
        binding.layoutAutoSyncOptions.isVisible = prefs.autoSyncEnabled

        val savedInterval = prefs.syncIntervalHours
        val selectedIndex = INTERVAL_OPTIONS.indexOfFirst { it.second == savedInterval }.takeIf { it >= 0 } ?: 2
        binding.spinnerSyncInterval.setSelection(selectedIndex)

        binding.switchWifiOnly.isChecked = prefs.wifiOnlySync

        updateLastSyncLabel()
        binding.tvDeviceId.text = "Geräte-ID: ${WebDavSyncManager.getDeviceId(this)}"
    }

    private fun setupListeners() {
        binding.switchWebdavEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.syncEnabled = isChecked
            binding.layoutWebdavCredentials.isVisible = isChecked
            if (!isChecked) {
                WebDavSyncWorker.cancelPeriodicSync(this)
            }
        }

        binding.btnTestConnection.setOnClickListener {
            saveCredentialsFromFields()
            testConnection()
        }

        binding.btnClearWebdavCredentials.setOnClickListener {
            confirmAndClearCredentials()
        }

        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoSyncEnabled = isChecked
            binding.layoutAutoSyncOptions.isVisible = isChecked
            if (isChecked) {
                val hours = INTERVAL_OPTIONS[binding.spinnerSyncInterval.selectedItemPosition].second
                WebDavSyncWorker.schedulePeriodicSync(this, hours)
            } else {
                WebDavSyncWorker.cancelPeriodicSync(this)
            }
        }

        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.wifiOnlySync = isChecked
            if (prefs.autoSyncEnabled) {
                // Neu planen, damit der geänderte Netzwerk-Constraint sofort greift
                WebDavSyncWorker.schedulePeriodicSync(this, prefs.syncIntervalHours)
            }
        }

        binding.spinnerSyncInterval.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, pos: Int, id: Long) {
                val hours = INTERVAL_OPTIONS[pos].second
                prefs.syncIntervalHours = hours
                if (prefs.autoSyncEnabled) {
                    WebDavSyncWorker.schedulePeriodicSync(this@WebDavSyncActivity, hours)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        binding.btnSyncNow.setOnClickListener {
            saveCredentialsFromFields()
            confirmAndSync()
        }
    }

    private fun saveCredentialsFromFields() {
        val url = binding.etServerUrl.text?.toString()?.trim().orEmpty()
        val user = binding.etUsername.text?.toString()?.trim().orEmpty()
        val enteredPass = binding.etPassword.text?.toString().orEmpty()

        val existingPass = prefs.loadConfig()?.password ?: ""
        val finalPass = enteredPass.ifBlank { existingPass }

        prefs.saveCredentials(url, user, finalPass)
    }

    private fun testConnection() {
        val config = prefs.loadConfig()
        if (config == null || config.serverUrl.isBlank() || config.username.isBlank()) {
            showConnectionStatus("Bitte Server-URL und Benutzername eingeben.", isError = true)
            return
        }

        setLoading(true)
        binding.tvConnectionStatus.isVisible = false

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                WebDavClient(config.serverUrl, config.username, config.password).testConnection()
            }
            setLoading(false)
            result.fold(
                onSuccess = { code ->
                    when (code) {
                        in 200..299, 207 -> showConnectionStatus("✅ Verbindung erfolgreich", isError = false)
                        401 -> showConnectionStatus("❌ Authentifizierung fehlgeschlagen (401)", isError = true)
                        404 -> showConnectionStatus("❌ URL nicht gefunden (404)", isError = true)
                        else -> showConnectionStatus("⚠️ Server antwortet mit HTTP $code", isError = true)
                    }
                },
                onFailure = { e ->
                    showConnectionStatus("❌ Verbindungsfehler: ${e.message}", isError = true)
                }
            )
        }
    }

    /**
     * Da BackupManager.restoreBackup(mergeMode=true) additiv und ID-basiert
     * dedupliziert (keine Datenverluste möglich), reicht ein einfacher
     * Bestätigungsdialog - ein gesonderter Konflikt-Dialog ist nicht nötig.
     */
    private fun confirmAndSync() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🔄 Jetzt synchronisieren?")
            .setMessage(
                "Deine Daten werden mit dem WebDAV-Server abgeglichen.\n\n" +
                        "Daten von anderen Geräten werden automatisch zusammengeführt " +
                        "(wie beim Backup-Import \"Zusammenführen\") - es gehen keine " +
                        "vorhandenen Daten verloren.\n\n" +
                        "Fortfahren?"
            )
            .setPositiveButton("Ja, synchronisieren") { _, _ -> triggerSync() }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor())
    }

    private fun confirmAndClearCredentials() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🗑️ Zugangsdaten löschen?")
            .setMessage("Möchtest du die gespeicherten WebDAV-Zugangsdaten (URL, Benutzername, Passwort) wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                prefs.clearCredentials()
                loadCurrentSettings()
                Toast.makeText(this, "Zugangsdaten gelöscht", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.RED)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor())
    }

    private fun triggerSync() {
        setLoading(true)
        binding.tvLastSync.text = "Sync läuft…"

        scope.launch {
            val syncManager = WebDavSyncManager(this@WebDavSyncActivity)
            val result = withContext(Dispatchers.IO) { syncManager.sync() }
            setLoading(false)
            handleSyncResult(result)
        }
    }

    private fun handleSyncResult(result: WebDavSyncManager.SyncResult) {
        when (result) {
            is WebDavSyncManager.SyncResult.Success -> {
                prefs.lastSyncTimestamp = System.currentTimeMillis()
                prefs.lastSyncResult = if (result.mergedDeviceCount > 0)
                    "${result.mergedDeviceCount} Gerät${if (result.mergedDeviceCount != 1) "e" else ""} zusammengeführt"
                else "Keine anderen Geräte gefunden"
                updateLastSyncLabel()

                val message = if (result.mergedDeviceCount > 0)
                    "✅ Sync abgeschlossen: ${result.mergedDeviceCount} Gerät${if (result.mergedDeviceCount != 1) "e" else ""} zusammengeführt."
                else
                    "✅ Sync abgeschlossen. Keine anderen Geräte gefunden."
                showConnectionStatus(message, isError = false)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

            is WebDavSyncManager.SyncResult.NetworkError -> {
                prefs.lastSyncResult = "Netzwerkfehler"
                updateLastSyncLabel()
                val message = "❌ Netzwerkfehler: ${result.message}"
                showConnectionStatus(message, isError = true)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

            is WebDavSyncManager.SyncResult.NoCredentials -> {
                val message = "Bitte zuerst Zugangsdaten eingeben."
                showConnectionStatus(message, isError = true)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }

            is WebDavSyncManager.SyncResult.Error -> {
                prefs.lastSyncResult = "Fehler"
                updateLastSyncLabel()
                val message = "❌ Fehler: ${result.message}"
                showConnectionStatus(message, isError = true)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showConnectionStatus(message: String, isError: Boolean) {
        binding.tvConnectionStatus.isVisible = true
        binding.tvConnectionStatus.text = message
        binding.tvConnectionStatus.setTextColor(
            android.graphics.Color.parseColor(if (isError) "#F44336" else "#2E7D32")
        )
    }

    private fun updateLastSyncLabel() {
        val ts = prefs.lastSyncTimestamp
        if (ts == 0L) {
            binding.tvLastSync.text = "Letzter Sync: noch nie"
        } else {
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(ts))
            binding.tvLastSync.text = "Letzter Sync: $dateStr (${prefs.lastSyncResult})"
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.btnSyncNow.isEnabled = !loading
        binding.btnTestConnection.isEnabled = !loading
    }
}