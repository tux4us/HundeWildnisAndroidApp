package com.hundewildnis.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout

/**
 * Base Activity mit Screenshot-/Screen-Recording-Schutz
 * Alle Activities sollten von dieser Klasse erben
 */
abstract class SecureBaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Glas-Design ist nur dunkel: systemweite Hell-Einstellung ignorieren
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)

        // Edge-to-Edge aktivieren
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Die App ist ausschließlich dunkel: helle Symbole in Status- und Navigationsleiste
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // FLAG_SECURE: Blockiert Screenshots & Screen Recording
        window.setFlags(
          WindowManager.LayoutParams.FLAG_SECURE,
          WindowManager.LayoutParams.FLAG_SECURE
        )

        // ✅ NEU: Globaler HOME Button in der Toolbar (über MenuProvider)
        setupGlobalHomeButton()
    }

    private fun setupGlobalHomeButton() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // Home-Icon nur anzeigen, wenn wir NICHT in der StartActivity sind
                if (this@SecureBaseActivity !is StartActivity) {
                    val item = menu.add(Menu.NONE, R.id.action_home, Menu.NONE, "Home")
                    item.setIcon(R.drawable.ic_home)
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)

                    val iconColor = androidx.core.content.ContextCompat.getColor(this@SecureBaseActivity, R.color.rd_text_primary)
                    item.icon?.setTint(iconColor)
                }
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == R.id.action_home) {
                    val intent = Intent(this@SecureBaseActivity, StartActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    true
                } else false
            }
        })
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        
        // Globales Inset-Handling: Sucht nach AppBar oder Toolbar und setzt Padding
        val contentRoot = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Suche AppBar oder Toolbar in der aktuellen Activity
            val appBar = findViewById<View>(R.id.appbar) 
                ?: findViewById<View>(resources.getIdentifier("appbar", "id", packageName))
            val toolbar = findViewById<View>(R.id.toolbar) 
                ?: findViewById<View>(resources.getIdentifier("toolbar", "id", packageName))

            if (appBar != null) {
                // Falls ein AppBarLayout gefunden wird, setzen wir das Padding dort
                appBar.setPadding(0, systemBars.top, 0, 0)
            } else if (toolbar != null) {
                // Falls nur eine Toolbar da ist (ohne AppBarLayout Wrapper)
                toolbar.setPadding(0, systemBars.top, 0, 0)
            }

            // Suche BottomNavigationView
            val bottomNav = findViewById<View>(R.id.bottomNavigation)
                ?: findViewById<View>(resources.getIdentifier("bottomNavigation", "id", packageName))

            if (bottomNav != null) {
                // Falls eine BottomNav vorhanden ist:
                // 1. Root-Padding unten auf 0 (damit die Nav-Bar ganz unten sitzt)
                contentRoot.setPadding(contentRoot.paddingLeft, contentRoot.paddingTop, contentRoot.paddingRight, 0)
                // 2. Padding auf die BottomNav selbst (damit Icons über den System-Buttons liegen)
                bottomNav.setPadding(0, 0, 0, systemBars.bottom)
            } else {
                // Keine BottomNav: Root-Padding setzen, um untere Buttons zu schützen
                contentRoot.setPadding(
                    contentRoot.paddingLeft,
                    contentRoot.paddingTop,
                    contentRoot.paddingRight,
                    systemBars.bottom
                )
            }
            
            insets
        }
        
        // Insets-Anfrage explizit auslösen
        ViewCompat.requestApplyInsets(contentRoot)
    }
}

