package com.hundewildnis.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * BaseBottomNavActivity - Gemeinsame Bottom Navigation für die ganze App
 * 
 * Wird von StartActivity und MainActivity genutzt
 * Erbt von SecureBaseActivity für Screenshot-Schutz
 */
abstract class BaseBottomNavActivity : SecureBaseActivity() {

    protected abstract fun getBottomNavigationView(): BottomNavigationView
    protected abstract fun getCurrentNavigationItemId(): Int

    // Optionale Callbacks für MainActivity
    protected open fun onBlogSelected() { navigateToMainActivity(MainActivity.VIEW_BLOG) }
    protected open fun onPagesSelected() { navigateToMainActivity(MainActivity.VIEW_PAGES) }
    protected open fun onCoursesSelected() { navigateToMainActivity(MainActivity.VIEW_COURSES) }
    protected open fun onYouTubeSelected() { navigateToMainActivity(MainActivity.VIEW_YOUTUBE) }

    // Optionales Callback für StartActivity
    protected open fun onHomeSelected() { /* StartActivity überschreibt diese Methode */ }

    protected fun setupBottomNavigation() {
        val bottomNav = getBottomNavigationView()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    onHomeSelected()
                    true
                }
                R.id.nav_blog -> {
                    onBlogSelected()
                    true
                }
                R.id.nav_pages -> {
                    onPagesSelected()
                    true
                }
                R.id.nav_courses -> {
                    onCoursesSelected()
                    true
                }
                R.id.nav_more -> {
                    showMoreDialog()
                    true
                }
                else -> false
            }
        }

        // Aktuellen Tab markieren
        bottomNav.selectedItemId = getCurrentNavigationItemId()
    }

    private fun navigateToMainActivity(viewType: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("INITIAL_VIEW", viewType)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun navigateToStartActivity() {
        val intent = Intent(this, StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun showMoreDialog() {
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_more_menu, null)
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Click Listener für alle Einträge
        bottomSheetView.findViewById<android.view.View>(R.id.btnSheetYouTube).setOnClickListener {
            bottomSheetDialog.dismiss()
            openYouTube()
        }

        bottomSheetView.findViewById<android.view.View>(R.id.btnSheetPodcast).setOnClickListener {
            bottomSheetDialog.dismiss()
            openPodcast()
        }

        bottomSheetView.findViewById<android.view.View>(R.id.btnSheetTwitter).setOnClickListener {
            bottomSheetDialog.dismiss()
            openTwitter()
        }

        bottomSheetView.findViewById<android.view.View>(R.id.btnSheetSignalManuela).setOnClickListener {
            bottomSheetDialog.dismiss()
            openSignalManuela()
        }

        bottomSheetView.findViewById<android.view.View>(R.id.btnSheetSignalJoerg).setOnClickListener {
            bottomSheetDialog.dismiss()
            openSignalJoerg()
        }

        bottomSheetDialog.show()

        // Zurück-Navigation: Aktuellen Tab wieder markieren
        bottomSheetDialog.setOnDismissListener {
            getBottomNavigationView().postDelayed({
                getBottomNavigationView().selectedItemId = getCurrentNavigationItemId()
            }, 100)
        }
    }

    private fun openYouTube() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val hasSeenYouTubeNotice = prefs.getBoolean("youtube_notice_shown", false)

        if (!hasSeenYouTubeNotice) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Hinweis")
                .setMessage(
                    "Du wirst zu unserem Kanal auf YouTube weitergeleitet. " +
                            "Dabei werden Daten an Google übertragen (z.B. deine IP-Adresse).\n\n" +
                            "Weitere Informationen findest du in der Datenschutzerklärung von YouTube.\n\n" +
                            "Pro-Tipp: auf dem Smartphone und Tablet empfiehlt sich immer die Nutzung eines vertrauenswürdigen VPN-Dienstes zum Schutz Deiner Daten und Deiner Privatsphäre\n\n" +
                            "Möchtest du fortfahren? Die App merkt sich deine Entscheidung."
                )
                .setPositiveButton("Ja, fortfahren") { _, _ ->
                    prefs.edit().putBoolean("youtube_notice_shown", true).apply()
                    openYouTubeInternal()
                }
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Datenschutz") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://policies.google.com/privacy"))
                    startActivity(intent)
                }
                .create()

            dialog.show()

            val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openYouTubeInternal()
        }
    }

    private fun openYouTubeInternal() {
        try {
            val channelUrl = "https://www.youtube.com/@teamnaturedogs"

            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl))
            appIntent.setPackage("com.google.android.youtube")

            if (appIntent.resolveActivity(packageManager) != null) {
                Log.d("BaseBottomNav", "Öffne in YouTube App")
                startActivity(appIntent)
            } else {
                Log.d("BaseBottomNav", "YouTube App nicht gefunden, öffne im Browser")
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Fehler beim Öffnen von YouTube", e)
            Toast.makeText(this, "Konnte YouTube nicht öffnen", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPodcast() {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/podcasts-audiobeitraege/")
            putExtra("title", "HundeWildnis Podcast")
        }
        startActivity(intent)
    }

    private fun openTwitter() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val hasSeenTwitterNotice = prefs.getBoolean("twitter_notice_shown", false)

        if (!hasSeenTwitterNotice) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Hinweis")
                .setMessage(
                    "Du wirst zu unserem Account auf X/Twitter weitergeleitet. " +
                            "Wenn Du 'Ja, fortfahren' wählst werden Daten an X Corp. übertragen (z.B. deine IP-Adresse).\n\n" +
                            "Weitere Informationen findest du in der Datenschutzerklärung von X/Twitter.\n\n" +
                            "Pro-Tipp: auf dem Smartphone und Tablet empfiehlt sich immer die Nutzung eines vertrauenswürdigen VPN-Dienstes zum Schutz Deiner Daten und Deiner Privatsphäre\n\n" +
                            "Möchtest du fortfahren? Die App merkt sich deine Entscheidung."
                )
                .setPositiveButton("Ja, fortfahren") { _, _ ->
                    prefs.edit().putBoolean("twitter_notice_shown", true).apply()
                    openTwitterInternal()
                }
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Datenschutz") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/privacy"))
                    startActivity(intent)
                }
                .create()

            dialog.show()

            val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openTwitterInternal()
        }
    }

    private fun openTwitterInternal() {
        try {
            val twitterUsername = "@teamnaturedogs"

            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("twitter://user?screen_name=$twitterUsername"))
            appIntent.setPackage("com.twitter.android")

            if (appIntent.resolveActivity(packageManager) != null) {
                Log.d("BaseBottomNav", "Öffne in Twitter App")
                startActivity(appIntent)
            } else {
                Log.d("BaseBottomNav", "Twitter App nicht gefunden, öffne im Browser")
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://twitter.com/$twitterUsername"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Fehler beim Öffnen von Twitter", e)
            Toast.makeText(this, "Konnte Twitter nicht öffnen", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSignalManuela() {
        openSignalTrainer(
            name = "Hundetrainerin und Wildnislehrerin Manuela",
            link = "https://signal.me/#eu/Ia1RmzOJN5Qo9YkKQfs3vh7f8X70AjwOqb_jpVe5QV1Bw1UVnbpCk5abghaeuz5U"
        )
    }

    private fun openSignalJoerg() {
        openSignalTrainer(
            name = "Hundetrainer und Wildnislehrer Jörg",
            link = "https://signal.me/#eu/UquXr52-0uUIh1o40pI5jH6HLy6gb3eIWeaSLn8eTIuCTKmOtFRF9Ji2VDvGXqFo"
        )
    }

    private fun openSignalTrainer(name: String, link: String) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val prefKey = "signal_trainer_notice_shown"
        val hasSeenNotice = prefs.getBoolean(prefKey, false)

        if (!hasSeenNotice) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("$name via Signal kontaktieren")
                .setMessage(
                    "Du wirst zu Signal weitergeleitet, um direkt mit $name zu chatten.\n\n" +
                            "Unterstützung bei Fragen zur Nutzung der App ist kostenfrei.\n\n" +
                            "Für fachliche, individuelle Beratung zu inhaltlichen Fragen und Anliegen zu allen Ausbildungs-, Trainings-, Umsetzungs- und Lernthemen erbitten wir ein kleines Honorar als Wertschätzung für unseren zeitlichen Aufwand und der Weitergabe unserer Erfahrungen.\n" +
                            "Wir teilen bei Eingang einer Anfrage das der Anfrage entsprechende Honorar im Voraus mit. Nach der Annahme des Honorarangebotes und der Begleichung über PayPal erfolgt der ausführliche, fachlich fundierte Beratungs-Chat incl. der Möglichkeit zu weiteren Rückfragen zum Thema.\n" +
                            "Es gibt seitens der/des Anfragenden keine Verpflichtung zur Annahme des Honorarangebotes. Nachdem das Honorarangebot jedoch angenommen wurde ist die Beratung verbindlich vereinbart.\n\n" +
                            "Wir sind fair, transparent und ehrlich.\n\n" +
                            "Datenschutz:\n" +
                            "• Deine Telefonnummer bleibt privat\n" +
                            "• Signal ist Ende-zu-Ende-verschlüsselt\n" +
                            "• Kein Tracking oder Datensammlung\n" +
                            "• Die HundeWildnis-App speichert keine Daten über deine Signal-Nutzung\n\n" +
                            "Pro-Tipp: Signal ist kostenlos und quelloffen. Falls du es noch nicht nutzt, " +
                            "kannst du es aus dem Play Store, über F-Droid-Store oder von signal.org herunterladen.\n\n" +
                            "Möchtest du fortfahren? Die App merkt sich deine Entscheidung."
                )
                .setPositiveButton("Ja, fortfahren") { _, _ ->
                    prefs.edit().putBoolean(prefKey, true).apply()
                    openSignalTrainerInternal(name, link)
                }
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Mehr zu Signal") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.org/"))
                    startActivity(intent)
                }
                .create()

            dialog.show()

            val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openSignalTrainerInternal(name, link)
        }
    }

    private fun openSignalTrainerInternal(name: String, link: String) {
        try {
            Log.d("BaseBottomNav", "Öffne Signal-Link für $name: $link")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
            Log.d("BaseBottomNav", "✓ Signal-Intent gestartet")

        } catch (e: ActivityNotFoundException) {
            Log.e("BaseBottomNav", "Signal nicht installiert")
            showSignalNotInstalledDialog()
        } catch (e: Exception) {
            Log.e("BaseBottomNav", "Fehler beim Öffnen von Signal", e)
            Toast.makeText(this, "Fehler beim Öffnen von Signal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSignalNotInstalledDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Signal nicht installiert")
            .setMessage(
                "Um die Trainer zu kontaktieren, benötigst du die Signal-App.\n\n" +
                        "Signal ist eine sichere, verschlüsselte Messenger-App und kostenlos.\n\n" +
                        "Download-Quellen:\n" +
                        "• Google Play Store\n" +
                        "• F-Droid (Open Source)\n" +
                        "• signal.org (direkt vom Hersteller)\n\n" +
                        "Möchtest du Signal jetzt herunterladen?"
            )
            .setPositiveButton("Ja, zur Website") { _, _ ->
                val websiteIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.org/download/"))
                startActivity(websiteIntent)
            }
            .setNeutralButton("Play Store") { _, _ ->
                val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.thoughtcrime.securesms"))
                startActivity(playStoreIntent)
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()

        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    }
}
