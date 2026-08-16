package com.hundewildnis.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BaseBottomNavActivity - Gemeinsame Bottom Navigation für die ganze App
 */
abstract class BaseBottomNavActivity : SecureBaseActivity() {

    protected abstract fun getBottomNavigationView(): BottomNavigationView
    protected abstract fun getCurrentNavigationItemId(): Int

    // Callbacks für Navigation
    protected open fun onBlogSelected() { navigateToMainActivity(MainActivity.VIEW_BLOG) }
    protected open fun onPagesSelected() { navigateToMainActivity(MainActivity.VIEW_PAGES) }
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
                R.id.nav_blog -> { onBlogSelected(); true }
                R.id.nav_pages -> { onPagesSelected(); true }
                R.id.nav_courses -> { onCoursesSelected(); true }
                R.id.nav_more -> { showMoreDialog(); true }
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

    private fun showMoreDialog() {
        val view = layoutInflater.inflate(R.layout.bottom_sheet_more_menu, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(view)

        view.findViewById<android.view.View>(R.id.btnSheetYouTube).setOnClickListener { dialog.dismiss(); openYouTube() }
        view.findViewById<android.view.View>(R.id.btnSheetPodcast).setOnClickListener { dialog.dismiss(); openPodcast() }
        view.findViewById<android.view.View>(R.id.btnSheetSpotify).setOnClickListener { dialog.dismiss(); openSpotify() }
        view.findViewById<android.view.View>(R.id.btnSheetSignalManuela).setOnClickListener { dialog.dismiss(); openSignalManuela() }
        view.findViewById<android.view.View>(R.id.btnSheetSignalJoerg).setOnClickListener { dialog.dismiss(); openSignalJoerg() }

        dialog.show()
        dialog.setOnDismissListener {
            getBottomNavigationView().postDelayed({
                getBottomNavigationView().selectedItemId = getCurrentNavigationItemId()
            }, 100)
        }
    }

    private fun openYouTube() {
        val channelUrl = "https://www.youtube.com/@teamnaturedogs"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl))
            intent.setPackage("com.google.android.youtube")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)))
        }
    }

    private fun openPodcast() {
        startActivity(Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/podcasts-audiobeitraege/")
            putExtra("title", "HundeWildnis Podcast")
        })
    }

    private fun openSpotify() {
        val url = "https://open.spotify.com/show/0W9JJZhtc0TPzYMyRNxaiD?si=6b3c2d41681b45c3"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage("com.twitter.android")
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun openSignalManuela() { openSignal("Manuela", "https://signal.me/#eu/Ia1RmzOJN5Qo9YkKQfs3vh7f8X70AjwOqb_jpVe5QV1Bw1UVnbpCk5abghaeuz5U") }
    private fun openSignalJoerg() { openSignal("Jörg", "https://signal.me/#eu/UquXr52-0uUIh1o40pI5jH6HLy6gb3eIWeaSLn8eTIuCTKmOtFRF9Ji2VDvGXqFo") }

    private fun openSignal(name: String, link: String) {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        
        AlertDialog.Builder(this)
            .setTitle("Signal Kontakt: $name")
            .setMessage("Du wirst zu Signal weitergeleitet...")
            .setPositiveButton("Fortfahren") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                catch (e: Exception) { Toast.makeText(this, "Signal App nicht gefunden", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
    }
}
