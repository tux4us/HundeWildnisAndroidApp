package com.hundewildnis.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import android.widget.TextView

/**
 * Kursübersicht: zeigt die lokal gebündelten Kurse (assets/courses/<sprache>/index.json).
 * Ein Tipp auf eine Kachel öffnet den Kurs in der CourseActivity.
 */
class MainActivity : BaseBottomNavActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        const val VIEW_COURSES = 2
        const val VIEW_START = 3
    }

    override fun getBottomNavigationView() = binding.bottomNavigation

    override fun getCurrentNavigationItemId() = R.id.nav_courses

    override fun onCoursesSelected() { /* bereits auf der Kursübersicht */ }

    override fun onHomeSelected() {
        val intent = Intent(this, StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.main_toolbar_title)
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_hamburger)?.apply {
            // Themenkonformes Tinting: das Icon war fest auf Weiß kodiert und dadurch
            // im hellen Modus auf hellem Toolbar-Hintergrund unsichtbar (weiß auf weiß).
            setTint(Color.WHITE)
        }
        super.setupBottomNavigation()
        requestNotificationPermission()
        loadCourses()
    }

    private fun loadCourses() {
        lifecycleScope.launch {
            val index = withContext(Dispatchers.IO) {
                try { CourseRepository.loadIndex(this@MainActivity) } catch (e: Exception) { null }
            }
            when {
                index == null -> showMessage(getString(R.string.courses_load_error))
                index.isEmpty() -> showMessage(getString(R.string.courses_empty))
                else -> bindCourseGrid(index)
            }
        }
    }

    private fun showMessage(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
        binding.coursesMenu.root.visibility = View.GONE
    }

    private fun bindCourseGrid(index: List<CourseIndexEntry>) {
        binding.errorText.visibility = View.GONE
        binding.coursesMenu.root.visibility = View.VISIBLE
        val tiles = index.map { StartTile("course_${it.id}", R.drawable.ic_courses, it.title) }
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridCoursesGroup2),
            spanCount = calculateSpanCount(100),
            groupIndex = 0, // Gelb/Ocker
            tiles = tiles
        )
    }

    private fun bindGrid(
        recyclerView: RecyclerView?,
        spanCount: Int,
        groupIndex: Int,
        tiles: List<StartTile>
    ) {
        recyclerView?.let { rv ->
            val colors = StartGroupColors.forGroup(this, groupIndex)
            rv.layoutManager = GridLayoutManager(this, spanCount)
            rv.setHasFixedSize(true)
            rv.adapter = StartTileAdapter(
                tiles = tiles,
                chipColorRes = colors.chipColorRes,
                accentColorRes = colors.accentColorRes,
                onTileClick = ::handleGridTileClick
            )
        }
    }

    private fun handleGridTileClick(tileId: String) {
        if (tileId.startsWith("course_")) {
            startActivity(Intent(this, CourseActivity::class.java).apply {
                putExtra(CourseActivity.EXTRA_COURSE_ID, tileId.removePrefix("course_"))
            })
        }
    }

    private fun calculateSpanCount(referenceItemWidthDp: Int): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        val fontScale = resources.configuration.fontScale
        val effectiveItemWidthDp = referenceItemWidthDp * fontScale
        val rawSpan = (screenWidthDp / effectiveItemWidthDp).toInt()
        return if (screenWidthDp < 600) rawSpan.coerceIn(2, 2) else rawSpan.coerceIn(3, 5)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val textColor = Color.WHITE
        for (i in 0 until menu.size()) {
            val span = SpannableString(menu.getItem(i).title.toString())
            span.setSpan(ForegroundColorSpan(textColor), 0, span.length, 0)
            menu.getItem(i).title = span
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> { showSettingsDialog(); true }
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_privacy -> { openPrivacyPolicy(); true }
            R.id.action_imprint -> { openImprint(); true }
            R.id.action_about -> { showAboutDialog(); true }
            R.id.action_home -> { startActivity(Intent(this, StartActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }); true }
            R.id.action_backup_create -> { lifecycleScope.launch { val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@MainActivity) }; Toast.makeText(this@MainActivity, getString(R.string.main_toast_backup_created_format, file.name), Toast.LENGTH_LONG).show() }; true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.main_button_understood), null).create()
        dialog.show()
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.ok), null).create()
        dialog.show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)

        val titleColor = Color.WHITE
        val linkColor = Color.parseColor("#8fad6f")

        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)
        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor); tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = getString(R.string.main_about_version_format, versionName)

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:hundewildnisschuleapp@online.de"); putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_about_email_subject)) }, getString(R.string.main_about_email_chooser_title))) }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton(getString(R.string.main_button_close), null).create()
        dialog.show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val appSettings = AppSettings(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val s1 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        val orsEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOrsApiKey)
        val orsLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutOrsApiKey)
        val btnClearOrs = view.findViewById<View>(R.id.btnClearOrsKey)

        orsLayout.setEndIconOnClickListener {
            showOrsInfoDialog()
        }

        btnClearOrs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_ors_key_delete_title))
                .setMessage(getString(R.string.main_ors_key_delete_message))
                .setPositiveButton(getString(R.string.main_ors_key_delete_confirm)) { _, _ ->
                    appSettings.clearOrsApiKey()
                    orsEdit.setText("")
                    Toast.makeText(this, getString(R.string.main_toast_key_deleted), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)

        // Aktuellen Key (falls vorhanden) im Feld anzeigen (verschleiert)
        if (appSettings.hasCustomOrsKey()) {
            orsEdit.setText("********")
        }

        val color = Color.WHITE
        val csl = android.content.res.ColorStateList.valueOf(color)
        listOf(s1, s2, s3).forEach { it.thumbTintList = csl; it.trackTintList = csl.withAlpha(77) }

        s1.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_enabled", isChecked).apply(); UpdateHelper.scheduleContentUpdates(this); s2.isEnabled = isChecked; s3.isEnabled = isChecked }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply(); UpdateHelper.scheduleContentUpdates(this) }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }
        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_settings_dialog_title))
            .setView(view)
            .setPositiveButton(getString(R.string.main_button_done)) { _, _ ->
                val newKey = orsEdit.text?.toString() ?: ""
                if (newKey.isNotBlank() && newKey != "********") {
                    appSettings.saveOrsApiKey(newKey)
                }
            }
            .create()
        dialog.show()
    }

    private fun showOrsInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_ors_info_title))
            .setMessage(getString(R.string.main_ors_info_message))
            .setPositiveButton(getString(R.string.ok), null)
            .create()
            .apply {
                show()
            }
    }

    private fun openPrivacyPolicy() { startActivity(Intent(this, PrivacyActivity::class.java)) }
    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                val dialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_notification_permission_title))
                    .setMessage(getString(R.string.main_notification_permission_message))
                    .setPositiveButton(getString(R.string.main_notification_permission_confirm)) { _, _ ->
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                    }
                    .setNegativeButton(getString(R.string.main_notification_permission_decline)) { _, _ ->
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        prefs.edit().putBoolean("notifications_enabled", false).apply()
                    }
                    .create()
                dialog.show()
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
