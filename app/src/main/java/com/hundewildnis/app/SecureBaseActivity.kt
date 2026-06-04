package com.hundewildnis.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
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
        super.onCreate(savedInstanceState)

        // Edge-to-Edge aktivieren
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Status- und Navigation-Bar Icons je nach Theme (Hell/Dunkel) anpassen
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        // FLAG_SECURE: Blockiert Screenshots & Screen Recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
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

