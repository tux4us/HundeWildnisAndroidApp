package com.hundewildnis.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.os.Build
import android.view.Gravity
import android.widget.TextView

class MainActivity : BaseBottomNavActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter

    private val wordPressApi = WordPressApiParser()
    private lateinit var contentCache: ContentCache

    private var allBlogPosts = listOf<ContentItem>()

    private var currentView = VIEW_BLOG

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
        const val VIEW_PODCASTS = 4
    }

    override fun getBottomNavigationView() = binding.bottomNavigation

    override fun getCurrentNavigationItemId() = when (currentView) {
        VIEW_BLOG -> R.id.nav_blog
        VIEW_PAGES -> R.id.nav_pages
        VIEW_COURSES -> R.id.nav_courses
        else -> R.id.nav_blog
    }

    override fun onBlogSelected() { showBlogView() }
    override fun onPagesSelected() { showPagesView() }
    override fun onCoursesSelected() { showCoursesView() }

    override fun onHomeSelected() {
        val intent = Intent(this, StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemePreference()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_hamburger)
        contentCache = ContentCache(this)
        setupRecyclerViews()
        setupButtons()
        setupPagesMenu()
        setupCoursesMenu()

        val initialView = intent.getIntExtra("INITIAL_VIEW", VIEW_BLOG)
        when (initialView) {
            VIEW_BLOG -> showBlogView()
            VIEW_PAGES -> showPagesView()
            VIEW_COURSES -> showCoursesView()
            else -> showBlogView()
        }

        binding.bottomNavigation.selectedItemId = when (initialView) {
            VIEW_PAGES -> R.id.nav_pages
            VIEW_COURSES -> R.id.nav_courses
            else -> R.id.nav_blog
        }

        loadContent()
        UpdateHelper.scheduleContentUpdates(this)
        requestNotificationPermission()
    }

    private fun Int.dpToPx(): Int { return (this * resources.displayMetrics.density).toInt() }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item -> openContent(item) }
        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter
    }

    private fun setupButtons() {
        super.setupBottomNavigation()
        
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadContentFromNetwork(silent = false)
        }
        binding.swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#1B3A0A"))

        binding.btnLoadMore.setOnClickListener { loadMoreBlogPosts() }
        binding.blogRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (currentView != VIEW_BLOG) { binding.btnLoadMore.visibility = View.GONE; return }
                if (blogAdapter.itemCount >= allBlogPosts.size) { binding.btnLoadMore.visibility = View.GONE; return }
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findLastVisibleItemPosition() >= layoutManager.itemCount - 5) binding.btnLoadMore.visibility = View.VISIBLE
            }
        })
    }

    private fun showBlogView() {
        currentView = VIEW_BLOG
        binding.blogRecyclerView.visibility = View.VISIBLE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.btnLoadMore.visibility = if (blogAdapter.itemCount < allBlogPosts.size) View.VISIBLE else View.GONE
    }

    private fun loadMoreBlogPosts() {
        val currentSize = blogAdapter.itemCount
        val newList = allBlogPosts.take(currentSize + 20)
        blogAdapter.submitList(newList)
        if (newList.size >= allBlogPosts.size) binding.btnLoadMore.visibility = View.GONE
    }

    private fun showPagesView() {
        currentView = VIEW_PAGES
        binding.blogRecyclerView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.VISIBLE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showCoursesView() {
        currentView = VIEW_COURSES
        binding.blogRecyclerView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun setupPagesMenu() {
        binding.root.findViewById<View>(R.id.btnHundeWildnisSchule)?.setOnClickListener { openUrl("https://wildnislehrer.com/", "HundeWildnis Schule") }
        binding.root.findViewById<View>(R.id.btnAntiJagdTraining)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/anti-jagd-training/", "Anti-Jagd-Training") }
        binding.root.findViewById<View>(R.id.btnAusbildungAssistenzhunde)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/ausbildung-von-paedagogischen-assistenzbegleithunden/", "Ausbildung Assistenzhunde") }
        binding.root.findViewById<View>(R.id.btnBeratung)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/beratung/", "Beratung") }
        binding.root.findViewById<View>(R.id.btnFahrradtraining)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/fahrradtraining/", "Fahrradtraining") }
        binding.root.findViewById<View>(R.id.btnFaehrteUndSuche)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/faehrte-und-suche/", "Fährte und Suche") }
        binding.root.findViewById<View>(R.id.btnGedankenUndGrundlagen)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/gedanken-und-grundlagen/", "Gedanken und Grundlagen") }
        binding.root.findViewById<View>(R.id.btnGrundausbildung)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/grundausbildung/", "Grundausbildung") }
        binding.root.findViewById<View>(R.id.btnHundeleinen)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/hundeleinen/", "Hundeleinen") }
        binding.root.findViewById<View>(R.id.btnLernfoerderungSchulen)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/lernfoerderunganschulen/", "Lernförderung an Schulen") }
        binding.root.findViewById<View>(R.id.btnProblemberatung)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/problemtherapie/", "Problemberatung") }
        binding.root.findViewById<View>(R.id.btnSeminare)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/seminare/", "Seminare") }
        binding.root.findViewById<View>(R.id.btnTeamsport)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/teamsport/", "Teamsport") }
        binding.root.findViewById<View>(R.id.btnTierschutzHunde)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/hunde-mit-lebensgeschichte/", "Tierschutz Hunde") }
        binding.root.findViewById<View>(R.id.btnTiertherapeutischerSozialbesuchshundeservice)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/tiertherapeutischersozialbesuchshundeservice/", "Tiertherapeutischer Service") }
        binding.root.findViewById<View>(R.id.btnWandernZumGutenZweck)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/trainingswanderungen/", "Wandern zum guten Zweck") }
        binding.root.findViewById<View>(R.id.btnWelpenschule)?.setOnClickListener { openUrl("https://wildnislehrer.com/hundeschule/welpenschule/", "Welpenschule") }
        binding.root.findViewById<View>(R.id.btnWildnisInUns)?.setOnClickListener { openUrl("https://wildnislehrer.com/wildnisschule/die-wildnis-in-uns/", "Die Wildnis in uns") }
        binding.root.findViewById<View>(R.id.btnSpurenUndZeichen)?.setOnClickListener { openUrl("https://wildnislehrer.com/wildnisschule/spuren-und-zeichen/", "Spuren und Zeichen") }
        binding.root.findViewById<View>(R.id.btnWildnishandwerkSchulen)?.setOnClickListener { openUrl("https://wildnislehrer.com/wildnisschule/wildnishandwerksschule/", "Wildnishandwerk für Schulen") }
        binding.root.findViewById<View>(R.id.btnWildnispaedagogikSchulen)?.setOnClickListener { openUrl("https://wildnislehrer.com/wildnisschule/schulwildnis/", "Wildnispädagogik für Schulen") }
        binding.root.findViewById<View>(R.id.btnDownloads)?.setOnClickListener { openUrl("https://wildnislehrer.com/downloads/", "Downloads") }
        binding.root.findViewById<View>(R.id.btnHonorare)?.setOnClickListener { openUrl("https://wildnislehrer.com/honorare/", "Honorare") }
        binding.root.findViewById<View>(R.id.btnImpressum)?.setOnClickListener { openUrl("https://wildnislehrer.com/impressum/", "Impressum") }
        binding.root.findViewById<View>(R.id.btnAGBs)?.setOnClickListener { openUrl("https://wildnislehrer.com/impressum/agb/", "AGB") }
        binding.root.findViewById<View>(R.id.btnDatenschutz)?.setOnClickListener { openUrl("https://wildnislehrer.com/datenschutzerklaerung/", "Datenschutzerklärung") }
        binding.root.findViewById<View>(R.id.btnInformationen)?.setOnClickListener { openUrl("https://wildnislehrer.com/informationen/", "Informationen") }
        binding.root.findViewById<View>(R.id.btnKontakt)?.setOnClickListener { openUrl("https://wildnislehrer.com/kontakt/", "Kontakt") }
        binding.root.findViewById<View>(R.id.btnUeberUns)?.setOnClickListener { openUrl("https://wildnislehrer.com/ueber-uns/", "Über Uns") }
    }

    private fun setupCoursesMenu() {
        binding.root.findViewById<View>(R.id.btnCoursesMain)?.setOnClickListener { openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/", "Onlinekurse Hundeschule") }
        for (i in 1..41) {
            val id = resources.getIdentifier("btnCourse${String.format("%02d", i)}", "id", packageName)
            if (id != 0) {
                binding.root.findViewById<View>(id)?.setOnClickListener {
                    val url = when(i) {
                        12 -> "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/onlinekurs-12-junghundepraegung/"
                        else -> "https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-${String.format("%02d", i)}-${getCourseSlug(i)}/"
                    }
                    openUrl(url, "$i ${getCourseName(i)}")
                }
            }
        }
    }

    private fun getCourseSlug(i: Int) = when(i) { 1 -> "grundlagen"; 2 -> "motivation"; 3 -> "korrektur"; 4 -> "harmonie"; 5 -> "mentaltraining"; 6 -> "koerpersprache"; 7 -> "sprachkommunikation"; 8 -> "sinneswahrnehmung"; 9 -> "teamarbeit"; 10 -> "routinen"; 11 -> "unfallschutz"; 13 -> "leinenarbeit"; 14 -> "unterbrechungen"; 15 -> "fuss"; 16 -> "sitz"; 17 -> "platz"; 18 -> "steh"; 19 -> "sozialisierungstechniken"; 20 -> "langleinentraining"; 21 -> "distanzunterbrechungen"; 22 -> "freilauf"; 23 -> "freifolge"; 24 -> "teamwalk"; 25 -> "scouttechniken"; 26 -> "jagdvermeidung"; 27 -> "nasenarbeit"; 28 -> "leinenfuehrigkeit"; 29 -> "begegnungssituationen"; 30 -> "stresstoleranztraining"; 31 -> "strassenroutinen"; 32 -> "hundekonflikte"; 33 -> "menschenkonflikte"; 34 -> "umweltkonflikte"; 35 -> "besitzerkonflikte"; 36 -> "tierschutzhunde"; 37 -> "hunde-mit-vorbesitzern"; 38 -> "kranke-hunde"; 39 -> "traumahunde"; 40 -> "erste-hilfe-set"; 41 -> "erste-hilfe-am-hund"; else -> "" }
    private fun getCourseName(i: Int) = when(i) { 1 -> "Grundlagen"; 2 -> "Motivation"; 3 -> "Korrektur"; 4 -> "Harmonie"; 5 -> "Mentaltraining"; 6 -> "Körpersprache"; 7 -> "Sprachkommunikation"; 8 -> "Sinneswahrnehmung"; 9 -> "Teamarbeit"; 10 -> "Routinen"; 11 -> "Unfallschutz"; 12 -> "Junghundeprägung"; 13 -> "Leinenarbeit"; 14 -> "Unterbrechungen"; 15 -> "Fuss"; 16 -> "Sitz"; 17 -> "Platz"; 18 -> "Steh"; 19 -> "Sozialisierungstechniken"; 20 -> "Langleinentraining"; 21 -> "Distanzunterbrechungen"; 22 -> "Freilauf"; 23 -> "Freifolge"; 24 -> "Teamwalk"; 25 -> "Scouttechniken"; 26 -> "Jagdvermeidung"; 27 -> "Nasenarbeit"; 28 -> "Leinenfühigkeit"; 29 -> "Begegnungssituationen"; 30 -> "Stresstoleranztraining"; 31 -> "Strassenroutinen"; 32 -> "Hundekonflikte"; 33 -> "Menschenkonflikte"; 34 -> "Umweltkonflikte"; 35 -> "Besitzerkonflikte"; 36 -> "Tierschutzhunde"; 37 -> "Hunde mit Vorbesitzern"; 38 -> "Kranke Hunde"; 39 -> "Traumahunde"; 40 -> "Erste Hilfe Set"; 41 -> "Erste Hilfe am Hund"; else -> "" }

    private fun openContent(item: ContentItem) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", item.url)
            putExtra("title", item.title)
            putExtra(ContentActivity.EXTRA_SOURCE_VIEW, currentView)  // ← NEU!
        }
        startActivity(intent)
    }

    private fun openUrl(url: String, title: String) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
            putExtra(ContentActivity.EXTRA_SOURCE_VIEW, currentView)  // ← NEU!
        }
        startActivity(intent)
    }

    private fun loadContent() {
        val cachedPosts = contentCache.getBlogPosts()
        if (cachedPosts != null) {
            allBlogPosts = cachedPosts
            blogAdapter.submitList(cachedPosts.take(20))
            binding.progressBar.visibility = View.GONE; binding.errorText.visibility = View.GONE
            when (currentView) { VIEW_BLOG -> showBlogView(); VIEW_PAGES -> showPagesView(); VIEW_COURSES -> showCoursesView() }
            if (contentCache.isCacheExpired()) loadContentFromNetwork(silent = true)
            return
        }
        binding.progressBar.visibility = View.VISIBLE; binding.errorText.text = "Lade Inhalte...\n\n⏳ Circa 5 Sekunden.\n\nDanach startet die App sofort! 🚀"
        binding.errorText.visibility = View.VISIBLE
        loadContentFromNetwork(silent = false)
    }

    private fun loadContentFromNetwork(silent: Boolean) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val posts = try { wordPressApi.loadBlogPosts(perPage = 100) } catch (e: Exception) { emptyList() }
                    posts
                }
                allBlogPosts = result
                contentCache.saveBlogPosts(result)
                blogAdapter.submitList(result.take(20))
                
                // Scroll to top if this is a refresh
                if (!silent) {
                    binding.blogRecyclerView.post {
                        binding.blogRecyclerView.scrollToPosition(0)
                    }
                }

                if (!silent) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressBar.visibility = View.GONE; binding.errorText.visibility = View.GONE
                    when (currentView) { VIEW_BLOG -> showBlogView(); VIEW_PAGES -> showPagesView(); VIEW_COURSES -> showCoursesView() }
                }
            } catch (e: Exception) {
                if (!silent) { 
                    binding.swipeRefreshLayout.isRefreshing = false
                    binding.progressBar.visibility = View.GONE; binding.errorText.text = "Fehler: ${e.message}"; binding.errorText.visibility = View.VISIBLE 
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isDark) Color.WHITE else Color.BLACK
        for (i in 0 until menu.size()) {
            val span = SpannableString(menu.getItem(i).title.toString())
            span.setSpan(ForegroundColorSpan(textColor), 0, span.length, 0)
            menu.getItem(i).title = span
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                binding.swipeRefreshLayout.isRefreshing = true
                loadContentFromNetwork(silent = false)
                true
            }
            R.id.action_theme -> { toggleTheme(); true }
            R.id.action_settings -> { showSettingsDialog(); true }
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_privacy -> { openPrivacyPolicy(); true }
            R.id.action_imprint -> { openImprint(); true }
            R.id.action_about -> { showAboutDialog(); true }
            R.id.action_home -> { startActivity(Intent(this, StartActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }); true }
            R.id.action_backup_create -> { lifecycleScope.launch { val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@MainActivity) }; Toast.makeText(this@MainActivity, "Backup erstellt: ${file.name}", Toast.LENGTH_LONG).show() }; true }
            else -> super.onOptionsItemSelected(item)
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
        tvWeb.setTextColor(linkColor); tvEmail.setTextColor(linkColor)
        
        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = "Version $versionName"
        
        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:hundewildnisschuleapp@online.de"); putExtra(Intent.EXTRA_SUBJECT, "HundeWildnis App Feedback") }, "E-Mail senden")) }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton("Schließen", null).create()
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(if (isDark) Color.WHITE else Color.parseColor("#2d5016"))
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val s1 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
        val csl = android.content.res.ColorStateList.valueOf(color)
        listOf(s1, s2, s3).forEach { it.thumbTintList = csl; it.trackTintList = csl.withAlpha(77) }
        s1.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_enabled", isChecked).apply(); if (isChecked) UpdateHelper.scheduleContentUpdates(this) else WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates_morning"); s2.isEnabled = isChecked; s3.isEnabled = isChecked }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply(); UpdateHelper.scheduleContentUpdates(this) }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }
        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked
        val dialog = AlertDialog.Builder(this).setTitle("⚙️ Einstellungen").setView(view).setPositiveButton("Fertig", null).create()
        dialog.show(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
    }

    private fun openPrivacyPolicy() { openUrl("https://wildnislehrer.com/datenschutzerklaerung/", "Datenschutzerklärung") }
    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }
    private fun loadThemePreference() { if (getSharedPreferences("theme_prefs", MODE_PRIVATE).getBoolean("is_dark_mode", false)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
    private fun toggleTheme() {
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().putBoolean("is_dark_mode", !isDark).apply()
        AppCompatDelegate.setDefaultNightMode(if (isDark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES)
        recreate()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val color = if (isDark) Color.WHITE else Color.parseColor("#2d5016")
                val dialog = AlertDialog.Builder(this)
                    .setTitle("🔔 Benachrichtigungen aktivieren?")
                    .setMessage("Möchtest Du über neue Artikel im Blog informiert werden? \n\nZudem können Dich der Tempo-Trainer und der Sprach-Trainer auch bei ausgeschaltetem Display zuverlässig im Training unterstützen. Benachrichtigungen können jederzeit in den Systemeinstellungen für die App deaktiviert oder lautlos gestellt werden")
                    .setPositiveButton("Ja, aktivieren") { _, _ -> 
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001) 
                    }
                    .setNegativeButton("Nicht jetzt") { _, _ -> 
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        prefs.edit().putBoolean("notifications_enabled", false).apply() 
                    }
                    .create()
                dialog.show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color)
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
