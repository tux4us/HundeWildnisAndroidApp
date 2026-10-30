package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BaseBottomNavActivity - Gemeinsame Bottom Navigation (Home, Kurse)
 */
abstract class BaseBottomNavActivity : SecureBaseActivity() {

    protected abstract fun getBottomNavigationView(): BottomNavigationView
    protected abstract fun getCurrentNavigationItemId(): Int

    // Callbacks für Navigation
    protected open fun onCoursesSelected() { navigateToMainActivity(MainActivity.VIEW_COURSES) }
    protected open fun onHomeSelected() { /* Von StartActivity überschrieben */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Insets werden in setupBottomNavigation oder spezifischen Activities gehandelt
    }

    override fun onResume() {
        super.onResume()
        // WICHTIG: Auswahl synchronisieren, wenn man per Back-Button zurückkommt
        getBottomNavigationView().post {
            getBottomNavigationView().selectedItemId = getCurrentNavigationItemId()
        }
    }

    protected fun setupBottomNavigation() {
        val bottomNav = getBottomNavigationView()
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { onHomeSelected(); true }
                R.id.nav_courses -> { onCoursesSelected(); true }
                // Notfall öffnet den Assistenten, die aktuelle Auswahl der Leiste bleibt unverändert
                R.id.nav_emergency -> { openEmergencyWizard(); false }
                else -> false
            }
        }
        bottomNav.selectedItemId = getCurrentNavigationItemId()

        // Hinweis: Insets werden zentral in SecureBaseActivity gehandelt,
        // um Doppelpufferung und Überlagerung durch die Android-Buttons zu vermeiden.
    }

    private fun navigateToMainActivity(viewType: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("INITIAL_VIEW", viewType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    protected fun openEmergencyWizard() {
        // Prüfe ob Hunde vorhanden
        val profiles = DogProfileManager.getAllProfiles(this)

        if (profiles.isEmpty()) {
            // Kein Hund vorhanden - zeige Hinweis

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.start_no_dog_title))
                .setMessage(getString(R.string.start_no_dog_message))
                .setPositiveButton(getString(R.string.start_to_master_data)) { _, _ ->
                    startActivity(Intent(this, DogProfileActivity::class.java))
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .create()
                .apply {
                    show()
                }
            return
        }

        // Mehrere Hunde? Auswahl anzeigen
        if (profiles.size > 1) {
            val dogNames = profiles.map { it.name }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.start_which_dog_title))
                .setItems(dogNames) { _, which ->
                    val selectedProfile = profiles[which]
                    openFirstAidWizard(selectedProfile)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .create().apply {
                    show()
                }
        } else {
            // Nur ein Hund - direkt öffnen
            openFirstAidWizard(profiles[0])
        }
    }

    protected fun openFirstAidWizard(profile: DogProfile) {
        val intent = Intent(this, FirstAidWizardActivity::class.java)
        intent.putExtra("profile", profile)
        startActivity(intent)
    }
}
