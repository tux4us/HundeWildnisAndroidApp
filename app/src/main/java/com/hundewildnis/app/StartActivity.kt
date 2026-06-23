package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityStartBinding
import kotlinx.coroutines.*
import com.hundewildnis.app.BuildConfig
import java.io.File
import android.view.View
import coil.load
import coil.size.Scale
import coil.request.CachePolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * StartActivity - Hauptstartseite der App
 */
class StartActivity : BaseBottomNavActivity() {

    private lateinit var binding: ActivityStartBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val tempFile = File(cacheDir, "temp_backup.hundebackup")
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    confirmRestore(tempFile)
                } catch (e: Exception) {
                    Toast.makeText(this, "Fehler beim Lesen: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✅ Benachrichtigungen aktiviert", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getBottomNavigationView() = binding.bottomNavigation
    override fun getCurrentNavigationItemId() = R.id.nav_home

    override fun onHomeSelected() {
        // Falls wir schon in StartActivity sind, nichts tun
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_hamburger)

        setupButtons()
        super.setupBottomNavigation()

        // ✅ Hintergrund-Updates initialisieren (Wichtig bei Neuinstallation!)
        UpdateHelper.scheduleContentUpdates(this)

        // ✅ NEU: Blog-Preview laden
        loadLatestBlogPost()

        // ✅ Erst-Info Dialog für Benachrichtigungen (Google Play Konform)
        checkNotificationDisclosure()

        // ✅ Update-Dialog prüfen
        checkForUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spanString = SpannableString(item.title.toString())
            spanString.setSpan(ForegroundColorSpan(textColor), 0, spanString.length, 0)
            item.title = spanString
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadLatestBlogPost()
                Toast.makeText(this, "Blog-Vorschau wird aktualisiert...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_theme -> { toggleTheme(); true }
            R.id.action_settings -> { showSettingsDialog(); true }
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_backup_create -> { createBackup(); true }
            R.id.action_backup_restore -> { selectBackupFile(); true }
            R.id.action_privacy -> { openPrivacyPolicy(); true }
            R.id.action_imprint -> { openImprint(); true }
            R.id.action_about -> { showAboutDialog(); true }
            R.id.action_home -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkNotificationDisclosure() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasAccepted = prefs.getBoolean("notification_disclosure_accepted", false)

        if (!hasAccepted) {
            showNotificationDisclosure()
        }
    }

    private fun showNotificationDisclosure() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("🔔 Wichtige Informationen zu:\nBenachrichtigungen aktivieren")
            .setMessage("HundeWildnis nutzt Benachrichtigungen für folgende Zwecke:\n\n" +
                    "• BLOG-AKTUALISIERUNGEN:\nDamit Du immer informiert bist, wenn neue Blog-Artikel veröffentlicht werden.\n\n" +
                    "• NAVIGATION & TEMPO-TRAINER  SPRACH-TRAINER:\nDamit Navigation, Tempo-Trainer und Sprach-Trainer auch dann zuverlässig funktionieren, wenn das Display ausgeschaltet ist oder die App in den Hintergrund verschoben wird (z.B. wenn das Handy beim Training in der Tasche steckt oder die Hände für Aktivitäten frei sein müssen). In diesem Fall wird eine dauerhafte Benachrichtigung angezeigt, um den Standortdienst aktiv zu halten.\nBei deaktivierten Benachrichtigungen funktionieren NAVIGATION, TEMPO-TRAINER und SPRACH-TRAINER nur, wenn die App im Vordergrund ist und der Bildschirm aktiv ist.\n\n" +
                    "DATENSCHUTZ:\nEs werden keine personenbezogenen Daten für die Benachrichtigungen an externe Server übermittelt. Du kannst diese Funktion jederzeit in den Einstellungen:\nDein Gerät - Einstellungen - Apps - HundeWildnis - Benachrichtigungen\naktivieren, anpassen oder deaktivieren.")
            .setPositiveButton("AKTIVIEREN") { _, _ ->
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .putBoolean("notification_disclosure_accepted", true).apply()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("ABLEHNEN", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }


    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("Verstanden", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton("OK", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val titleColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val linkColor = if (isDark) Color.parseColor("#8fad6f") else Color.parseColor("#2d5016")

        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)

        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor)
        tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = "Version $versionName"

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:hundewildnisschuleapp@online.de")
                putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis App Feedback")
            }
            startActivity(Intent.createChooser(intent, "E-Mail senden"))
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Schließen", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun toggleTheme() {
        val sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("is_dark_mode", false)
        sharedPreferences.edit().putBoolean("is_dark_mode", !isDarkMode).apply()
        AppCompatDelegate.setDefaultNightMode(if (isDarkMode) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
        recreate()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val appSettings = AppSettings(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val accentColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val colorStateList = android.content.res.ColorStateList.valueOf(accentColor)

        val s1 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        val orsEdit = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOrsApiKey)

        listOf(s1, s2, s3).forEach {
            it.thumbTintList = colorStateList
            it.trackTintList = colorStateList.withAlpha(77)
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)
        
        if (appSettings.hasCustomOrsKey()) {
            orsEdit.setText("********")
        }

        s1.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_enabled", isChecked).apply()
            s2.isEnabled = isChecked; s3.isEnabled = isChecked
        }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply() }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }

        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙️ Einstellungen")
            .setView(dialogView)
            .setPositiveButton("Fertig") { _, _ ->
                val newKey = orsEdit.text?.toString() ?: ""
                if (newKey.isNotBlank() && newKey != "********") {
                    appSettings.saveOrsApiKey(newKey)
                }
            }
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accentColor)
    }

    private fun openPrivacyPolicy() {
        startActivity(Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/datenschutzerklaerung/")
            putExtra("title", "Datenschutzerklärung")
        })
    }

    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }

    private fun setupButtons() {
        binding.btnNavBlog.setOnClickListener { openMainActivity(MainActivity.VIEW_BLOG) }
        binding.btnNavPages.setOnClickListener { openMainActivity(MainActivity.VIEW_PAGES) }
        binding.btnNavCourses.setOnClickListener { openMainActivity(MainActivity.VIEW_COURSES) }

        binding.btnDailyExercise.setOnClickListener { openWebView("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/uebung-der-woche/", "Übung der Woche") }
        binding.btnDailyMeditation.setOnClickListener { openWebView("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/meditation/", "Meditation der Woche") }
        binding.btnWalkingSpeed.setOnClickListener { startActivity(Intent(this, WalkingSpeedActivity::class.java)) }
        binding.btnCommunication.setOnClickListener { startActivity(Intent(this, CommunicationActivity::class.java)) }
        binding.btnTrainingDiary.setOnClickListener { startActivity(Intent(this, TrainingDiaryActivity::class.java)) }
        binding.btnNasenarbeit.setOnClickListener { startActivity(Intent(this, NasenarbeitActivity::class.java)) }
        binding.btnDailyNotes.setOnClickListener { startActivity(Intent(this, DailyNotesActivity::class.java)) }
        binding.btnWeather.setOnClickListener { startActivity(Intent(this, WeatherActivity::class.java)) }
        binding.btnLocation.setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        binding.btnCompass.setOnClickListener { startActivity(Intent(this, CompassActivity::class.java)) }
        binding.btnAltimeter.setOnClickListener { startActivity(Intent(this, AltimeterActivity::class.java)) }
        binding.btnDogProfiles.setOnClickListener { startActivity(Intent(this, DogProfileActivity::class.java)) }
        binding.btnLocationShare.setOnClickListener { startActivity(Intent(this, LocationShareActivity::class.java)) }
        binding.btnEmergency.setOnClickListener { openEmergencyWizard() }
        binding.btnBackup.setOnClickListener { startActivity(Intent(this, BackupActivity::class.java)) }
        binding.btnSupport.setOnClickListener { startActivity(Intent(this, SupportActivity::class.java)) }
    }

    private fun openEmergencyWizard() {
        // Prüfe ob Hunde vorhanden
        val profiles = DogProfileManager.getAllProfiles(this)

        if (profiles.isEmpty()) {
            // Kein Hund vorhanden - zeige Hinweis
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

            AlertDialog.Builder(this)
                .setTitle("🐕 Kein Hund angelegt")
                .setMessage("Bitte lege zuerst einen Hund in den Stammdaten an.\n\n" +
                        "Die Erste-Hilfe Funktion benötigt die Stammdaten für die Notfallmeldung.")
                .setPositiveButton("Zu Stammdaten") { _, _ ->
                    startActivity(Intent(this, DogProfileActivity::class.java))
                }
                .setNegativeButton("Abbrechen", null)
                .create()
                .apply {
                    show()
                    getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
                }
            return
        }

        // Mehrere Hunde? Auswahl anzeigen
        if (profiles.size > 1) {
            val dogNames = profiles.map { it.name }.toTypedArray()
            val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

            AlertDialog.Builder(this)
                .setTitle("🐕 Welcher Hund?")
                .setItems(dogNames) { _, which ->
                    val selectedProfile = profiles[which]
                    openFirstAidWizard(selectedProfile)
                }
                .setNegativeButton("Abbrechen", null)
                .create().apply {
                    show()
                    getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
                }
        } else {
            // Nur ein Hund - direkt öffnen
            openFirstAidWizard(profiles[0])
        }
    }

    private fun openFirstAidWizard(profile: DogProfile) {
        val intent = Intent(this, FirstAidWizardActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }

    private fun createBackup() {
        val progress = AlertDialog.Builder(this).setTitle("Backup erstellen").setMessage("Bitte warten...").setCancelable(false).show()
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@StartActivity) }
                progress.dismiss()
                Toast.makeText(this@StartActivity, "✅ Backup gespeichert in Downloads/HundeWildnis_Backups", Toast.LENGTH_LONG).show()
                offerShare(file)
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@StartActivity, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        pickBackupFile.launch(intent)
    }

    private fun confirmRestore(file: File) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val dialog = AlertDialog.Builder(this).setTitle("⚠️ Backup wiederherstellen?").setMessage("Alle Daten werden überschrieben!").setPositiveButton("Ja") { _, _ -> restoreBackup(file) }.setNegativeButton("Abbrechen") { _, _ -> file.delete() }.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
    }

    private fun restoreBackup(file: File) {
        val progress = AlertDialog.Builder(this).setTitle("Wiederherstellung").setMessage("Bitte warten...").setCancelable(false).show()
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) { BackupManager.restoreBackup(this@StartActivity, file) }
                file.delete(); progress.dismiss()
                if (success) restartApp()
                else Toast.makeText(this@StartActivity, "❌ Fehler beim Import", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                progress.dismiss(); Toast.makeText(this@StartActivity, "❌ Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun offerShare(file: File) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        AlertDialog.Builder(this)
            .setTitle("Backup teilen?")
            .setPositiveButton("Teilen") { _, _ -> shareBackupFile(file) }
            .setNegativeButton("Später", null)
            .create().apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            }
    }

    private fun shareBackupFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/octet-stream"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Backup teilen"))
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent); finishAffinity()
    }

    private fun openMainActivity(viewType: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("INITIAL_VIEW", viewType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openWebView(url: String, title: String) {
        startActivity(Intent(this, ContentActivity::class.java).apply { putExtra("url", url); putExtra("title", title) })
    }

    // ════════════════════════════════════════════════════════════════
    // ✅ UPDATE-DIALOG FUNKTIONEN
    // ════════════════════════════════════════════════════════════════

    private fun checkForUpdate() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val currentVersion = BuildConfig.VERSION_CODE
        val lastSeenVersion = prefs.getInt("last_seen_version", 0)

        if (currentVersion > lastSeenVersion) {
            showUpdateDialog()
            prefs.edit().putInt("last_seen_version", currentVersion).apply()
        }
    }

    private fun showUpdateDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_support, null)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val titleColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val textColor = if (isDark) Color.parseColor("#E0E0E0") else Color.parseColor("#424242")
        val buttonBgColor = Color.parseColor("#2d5016")
        val buttonOutlineColor = if (isDark) Color.WHITE else Color.parseColor("#2d5016")

        dialogView.findViewById<TextView>(R.id.tvUpdateTitle).setTextColor(titleColor)
        dialogView.findViewById<TextView>(R.id.tvUpdateVersion).apply {
            text = "Version ${BuildConfig.VERSION_NAME}"
            setTextColor(textColor)
        }
        dialogView.findViewById<TextView>(R.id.tvUpdateMessage).setTextColor(textColor)

        // Dialog erstellen
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Unterstützen-Button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSupport).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(buttonBgColor)
            setOnClickListener {
                startActivity(Intent(this@StartActivity, SupportActivity::class.java))
                dialog.dismiss()
            }
        }

        // Später-Button
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLater).apply {
            setTextColor(buttonOutlineColor)
            strokeColor = android.content.res.ColorStateList.valueOf(buttonOutlineColor)
            strokeWidth = 2
            setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /**
     * Lädt den neuesten Blog-Beitrag für die Preview-Card
     */
    private fun loadLatestBlogPost() {
        scope.launch {
            try {
                // Zeige initialen Status
                binding.cardLatestBlogPost.visibility = View.VISIBLE
                binding.blogPreviewLoading.visibility = View.VISIBLE

                val contentCache = ContentCache(this@StartActivity)
                var cachedPost: ContentItem? = null

                // 1. ZUERST: Versuche aus Cache zu laden für sofortige Anzeige
                val cachedPosts = contentCache.getBlogPosts()
                if (cachedPosts != null && cachedPosts.isNotEmpty()) {
                    cachedPost = cachedPosts.first()
                    displayBlogPreview(cachedPost)
                    binding.blogPreviewLoading.visibility = View.GONE
                    android.util.Log.d("StartActivity", "Blog-Preview aus Cache angezeigt")
                } else {
                    binding.blogPreviewTitle.text = "Lädt..."
                }

                // 2. DANN: Immer im Hintergrund prüfen, ob es einen neueren Post gibt
                val newestPost = withContext(Dispatchers.IO) {
                    try {
                        val api = WordPressApiParser()
                        // Lade nur den allerneuesten Post
                        val posts = api.loadBlogPosts(page = 1, perPage = 1)
                        if (posts.isNotEmpty()) posts.first() else null
                    } catch (e: Exception) {
                        android.util.Log.e("StartActivity", "Netzwerk-Check fehlgeschlagen: ${e.message}")
                        null
                    }
                }

                // 3. VERGLEICH & UPDATE
                if (newestPost != null) {
                    if (cachedPost == null || newestPost.url != cachedPost.url) {
                        android.util.Log.d("StartActivity", "Neuer Post gefunden oder Cache war leer. Aktualisiere Anzeige.")
                        displayBlogPreview(newestPost)

                        // Wenn es wirklich ein neuer Post ist -> Vollständiges Hintergrund-Update triggern
                        if (cachedPost != null && newestPost.url != cachedPost.url) {
                            UpdateHelper.triggerImmediateUpdate(this@StartActivity)
                        }
                    }
                } else if (cachedPost == null) {
                    // Gar nichts gefunden (weder Cache noch Netz)
                    hideBlogPreview()
                }

                binding.blogPreviewLoading.visibility = View.GONE

            } catch (e: Exception) {
                android.util.Log.e("StartActivity", "Fehler bei Blog-Preview", e)
                if (binding.blogPreviewTitle.text == "Lädt...") {
                    hideBlogPreview()
                }
            }
        }
    }

    /**
     * Zeigt den Blog-Post in der Preview-Card
     */
    private fun displayBlogPreview(post: ContentItem) {
        binding.blogPreviewLoading.visibility = View.GONE

        // Titel
        binding.blogPreviewTitle.text = post.title

        // Datum
        if (post.date.isNotEmpty()) {
            binding.blogPreviewDate.text = post.date
            binding.blogPreviewDate.visibility = View.VISIBLE
        } else {
            binding.blogPreviewDate.visibility = View.GONE
        }

        // Excerpt
        if (post.excerpt.isNotEmpty()) {
            binding.blogPreviewExcerpt.text = post.excerpt
            binding.blogPreviewExcerpt.visibility = View.VISIBLE
        } else {
            binding.blogPreviewExcerpt.visibility = View.GONE
        }

        // Bild (falls vorhanden)
        if (post.imageUrl.isNotEmpty()) {
            binding.blogPreviewImage.visibility = View.VISIBLE
            binding.blogPreviewImage.load(post.imageUrl) {
                size(800) // Mittelgroßes Bild
                scale(Scale.FIT)
                diskCachePolicy(CachePolicy.ENABLED)
                memoryCachePolicy(CachePolicy.ENABLED)
                placeholder(R.drawable.ic_placeholder_image)
                error(R.drawable.ic_error_image)
                crossfade(200)
            }
        } else {
            binding.blogPreviewImage.visibility = View.GONE
        }

        // Click-Handler: Lade ZUERST alle Blog-Posts, DANN öffne MainActivity
        binding.cardLatestBlogPost.setOnClickListener {
            // Zeige kurz Loading-Feedback
            binding.blogPreviewTitle.alpha = 0.5f

            scope.launch {
                try {
                    // Lade Blog-Posts im Hintergrund (genau wie MainActivity)
                    withContext(Dispatchers.IO) {
                        val api = WordPressApiParser()
                        val contentCache = ContentCache(this@StartActivity)

                        // Prüfe ob Cache veraltet ist
                        if (contentCache.isCacheExpired() || contentCache.getBlogPosts().isNullOrEmpty()) {
                            android.util.Log.d("StartActivity", "Lade Blog-Posts vor Wechsel...")

                            // ✅ VEREINFACHT: Lade nur 100 Posts (wie MainActivity)
                            val posts = api.loadBlogPosts(perPage = 100)

                            // Speichere im Cache
                            if (posts.isNotEmpty()) {
                                contentCache.saveBlogPosts(posts)
                                android.util.Log.d("StartActivity", "✓ ${posts.size} Blog-Posts geladen und gecacht")
                            }
                        } else {
                            android.util.Log.d("StartActivity", "Cache noch gültig, überspringe Laden")
                        }
                    }

                    // JETZT öffne MainActivity - Cache ist gefüllt!
                    binding.blogPreviewTitle.alpha = 1.0f
                    val intent = Intent(this@StartActivity, MainActivity::class.java).apply {
                        putExtra("INITIAL_VIEW", MainActivity.VIEW_BLOG)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)

                } catch (e: Exception) {
                    android.util.Log.e("StartActivity", "Fehler beim Vorladen", e)
                    binding.blogPreviewTitle.alpha = 1.0f

                    // Öffne trotzdem MainActivity (lädt dann selbst)
                    val intent = Intent(this@StartActivity, MainActivity::class.java).apply {
                        putExtra("INITIAL_VIEW", MainActivity.VIEW_BLOG)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * Versteckt die Blog-Preview bei Fehler
     */
    private fun hideBlogPreview() {
        binding.cardLatestBlogPost.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        // WICHTIG: Auswahl in der BottomNav zurücksetzen, wenn wir zu Home zurückkehren
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
