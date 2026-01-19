package com.hundewildnis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter
    private lateinit var pageAdapter: ContentAdapter
    private lateinit var courseAdapter: ContentAdapter
    private lateinit var youtubeAdapter: YouTubeAdapter

    private val wordPressApi = WordPressApiParser()
    private val wordPressParser = WordPressParser() // Behalte auch den alten Parser für Kurse
    private val youtubeParser = YouTubeParser()
    private lateinit var contentCache: ContentCache

    private var allBlogPosts = listOf<ContentItem>()
    private var allPages = listOf<ContentItem>()
    private var allCourses = listOf<ContentItem>()
    private var allYouTubeVideos = listOf<YouTubeVideo>()

    private var currentView = VIEW_BLOG

    private var isFabOpen = false

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
        const val VIEW_YOUTUBE = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadThemePreference()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon?.setTint(android.graphics.Color.WHITE)

        contentCache = ContentCache(this)

        setupRecyclerViews()
        setupButtons()
        setupFab()
        loadContent()

        binding.swipeRefresh.setOnRefreshListener {
            refreshContent()
        }
    }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item -> openContent(item) }
        pageAdapter = ContentAdapter { item -> openContent(item) }
        courseAdapter = ContentAdapter { item -> openContent(item) }
        youtubeAdapter = YouTubeAdapter { video -> openYouTubeVideo(video) } // NEU

        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter

        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pagesRecyclerView.adapter = pageAdapter

        binding.coursesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.coursesRecyclerView.adapter = courseAdapter

        binding.youtubeRecyclerView.layoutManager = LinearLayoutManager(this) // NEU
        binding.youtubeRecyclerView.adapter = youtubeAdapter // NEU
    }

    private fun setupButtons() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_blog -> {
                    showBlogView()
                    true
                }
                R.id.nav_pages -> {
                    showPagesView()
                    true
                }
                R.id.nav_courses -> {
                    showCoursesView()
                    true
                }
                R.id.nav_youtube -> {
                    showYouTubeView()
                    true
                }
                else -> false
            }
        }

        binding.btnLoadMore.setOnClickListener {
            loadMoreBlogPosts()
        }

        binding.blogRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Nur im Blog-View aktiv
                if (currentView != VIEW_BLOG) {
                    binding.btnLoadMore.visibility = View.GONE
                    return
                }

                // Keine weiteren Posts? Button verstecken
                if (blogAdapter.itemCount >= allBlogPosts.size) {
                    binding.btnLoadMore.visibility = View.GONE
                    return
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItems = layoutManager.itemCount

                // Zeige Button nur wenn fast am Ende (letzte 5 Items sichtbar)
                if (lastVisibleItem >= totalItems - 5) {
                    binding.btnLoadMore.visibility = View.VISIBLE
                }
                // WICHTIG: Wenn NICHT am Ende, Button NICHT verstecken (bleibt wie er ist)
                // Entferne das "else { visibility = GONE }"
            }
        })

        showBlogView()
        binding.bottomNavigation.selectedItemId = R.id.nav_blog
    }

    private fun showBlogView() {
        currentView = VIEW_BLOG
        binding.blogRecyclerView.visibility = View.VISIBLE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE

        // Zeige Button wenn mehr Posts vorhanden
        binding.btnLoadMore.visibility = if (blogAdapter.itemCount < allBlogPosts.size) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun loadMoreBlogPosts() {
        val currentSize = blogAdapter.itemCount
        val nextBatch = allBlogPosts.drop(currentSize).take(20)

        if (nextBatch.isEmpty()) {
            Toast.makeText(this, "Alle Beiträge geladen", Toast.LENGTH_SHORT).show()
            binding.btnLoadMore.visibility = View.GONE
            return
        }

        val newList = allBlogPosts.take(currentSize + 20)
        blogAdapter.submitList(newList)

        Toast.makeText(this, "${nextBatch.size} weitere Beiträge geladen", Toast.LENGTH_SHORT).show()

        // Prüfe ob noch mehr Posts vorhanden
        if (newList.size >= allBlogPosts.size) {
            binding.btnLoadMore.visibility = View.GONE
            Toast.makeText(this, "Alle Beiträge geladen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPagesView() {
        currentView = VIEW_PAGES
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.VISIBLE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showCoursesView() {
        currentView = VIEW_COURSES
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.VISIBLE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showYouTubeView() { // NEU
        currentView = VIEW_YOUTUBE
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun loadContent() {
        val cachedPosts = contentCache.getBlogPosts()
        val cachedPages = contentCache.getPages()
        val cachedCourses = contentCache.getCourses()
        val cachedYouTube = contentCache.getYouTubeVideos() // NEU

        if (cachedPosts != null && cachedPages != null && cachedCourses != null && cachedYouTube != null) {
            // ALLES aus Cache verfügbar
            allBlogPosts = cachedPosts
            allPages = cachedPages
            allCourses = cachedCourses
            allYouTubeVideos = cachedYouTube

            blogAdapter.submitList(cachedPosts.take(20))
            pageAdapter.submitList(cachedPages)
            courseAdapter.submitList(cachedCourses)
            youtubeAdapter.submitList(cachedYouTube) // NEU

            binding.progressBar.visibility = View.GONE
            binding.errorText.visibility = View.GONE

            when (currentView) {
                VIEW_BLOG -> showBlogView()
                VIEW_PAGES -> showPagesView()
                VIEW_COURSES -> showCoursesView()
                VIEW_YOUTUBE -> showYouTubeView()
            }

            Toast.makeText(this, "Inhalte aus Cache geladen", Toast.LENGTH_SHORT).show()

            // Lade im Hintergrund neue Daten
            loadContentFromNetwork(silent = true)
            return
        }

        // Kein vollständiger Cache - zeige Ladeanimation
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.text = "Inhalte werden aktualisiert..."
        binding.errorText.setTextColor(android.graphics.Color.parseColor("#2d5016"))
        binding.errorText.visibility = View.VISIBLE

        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE

        loadContentFromNetwork(silent = false)
    }

    private fun loadContentFromNetwork(silent: Boolean) {
        lifecycleScope.launch {
            try {
                val totalStartTime = System.currentTimeMillis()
                android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                android.util.Log.d("MainActivity", "⏱️ START Laden: ${System.currentTimeMillis()}")

                val result = withContext(Dispatchers.IO) {
                    // Blog laden (direkt, nicht async)
                    val blogStart = System.currentTimeMillis()
                    android.util.Log.d("MainActivity", "📝 Blog: Starte Laden...")
                    val posts = try {
                        wordPressApi.loadBlogPosts(perPage = 100)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Blog-Fehler: ${e.message}")
                        emptyList()
                    }
                    val blogDuration = System.currentTimeMillis() - blogStart
                    android.util.Log.d("MainActivity", "✓ Blog: ${posts.size} Posts in ${blogDuration}ms")

                    // Seiten laden (async)
                    val pagesStart = System.currentTimeMillis()
                    val pagesDeferred = async {
                        android.util.Log.d("MainActivity", "📄 Seiten: Starte Laden...")
                        val result = wordPressParser.parsePages()
                        val duration = System.currentTimeMillis() - pagesStart
                        android.util.Log.d("MainActivity", "✓ Seiten: ${result.size} Seiten in ${duration}ms")
                        result
                    }

                    // Kurse laden (async)
                    val coursesStart = System.currentTimeMillis()
                    val coursesDeferred = async {
                        android.util.Log.d("MainActivity", "🎓 Kurse: Starte Laden...")
                        val result = wordPressParser.parseCourses()
                        val duration = System.currentTimeMillis() - coursesStart
                        android.util.Log.d("MainActivity", "✓ Kurse: ${result.size} Kurse in ${duration}ms")
                        result
                    }

                    // YouTube laden (async)
                    val youtubeStart = System.currentTimeMillis()
                    val youtubeDeferred = async {
                        android.util.Log.d("MainActivity", "🎥 YouTube: Starte Laden...")
                        val result = youtubeParser.loadLatestVideos()
                        val duration = System.currentTimeMillis() - youtubeStart
                        android.util.Log.d("MainActivity", "✓ YouTube: ${result.size} Videos in ${duration}ms")
                        result
                    }

                    // Warte auf async Operationen
                    val pages = pagesDeferred.await()
                    val courses = coursesDeferred.await()
                    val videos = youtubeDeferred.await()

                    Quad(posts, pages, courses, videos)
                }

                val posts = result.first
                val pages = result.second
                val courses = result.third
                val videos = result.fourth

                // Cache speichern
                val cacheStart = System.currentTimeMillis()
                contentCache.saveBlogPosts(posts)
                contentCache.savePages(pages)
                contentCache.saveCourses(courses)
                contentCache.saveYouTubeVideos(videos)
                val cacheDuration = System.currentTimeMillis() - cacheStart
                android.util.Log.d("MainActivity", "💾 Cache gespeichert in ${cacheDuration}ms")

                allBlogPosts = posts
                allPages = pages
                allCourses = courses
                allYouTubeVideos = videos

                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)
                courseAdapter.submitList(courses)
                youtubeAdapter.submitList(videos)

                val totalDuration = System.currentTimeMillis() - totalStartTime
                android.util.Log.d("MainActivity", "⏱️ GESAMT: ${totalDuration}ms (${totalDuration/1000.0}s)")
                android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                if (!silent) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    when (currentView) {
                        VIEW_BLOG -> showBlogView()
                        VIEW_PAGES -> showPagesView()
                        VIEW_COURSES -> showCoursesView()
                        VIEW_YOUTUBE -> showYouTubeView()
                    }

                    Toast.makeText(this@MainActivity,
                        "${posts.size} Beiträge, ${pages.size} Seiten, ${courses.size} Kurse, ${videos.size} Videos geladen",
                        Toast.LENGTH_SHORT).show()
                } else {
                    binding.swipeRefresh.isRefreshing = false
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Fehler beim Laden", e)
                e.printStackTrace()
                if (!silent) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.errorText.text = getString(R.string.error_loading, e.message)
                    binding.errorText.setTextColor(android.graphics.Color.RED)
                    binding.errorText.visibility = View.VISIBLE

                    Toast.makeText(this@MainActivity,
                        getString(R.string.error_loading, e.message),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Hilfsklasse für 4 Werte
    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private fun openYouTubeVideo(video: YouTubeVideo) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hasSeenYouTubeNotice = prefs.getBoolean("youtube_notice_shown", false)

        if (!hasSeenYouTubeNotice) {
            val dialog = AlertDialog.Builder(this)
                .setTitle("Hinweis")
                .setMessage(
                    "Du wirst zu unserem Kanal auf YouTube weitergeleitet." +
                            "Dabei werden Daten an Google übertragen (z.B. deine IP-Adresse)." +
                            "Weitere Informationen findest du in der Datenschutzerklärung von YouTube.\n\n" +
                            "Pro-Tipp: auf dem Smartphone und Tablet empfiehlt sich immer die Nutzung eines vertrauenswürdigen VPN-Dienstes zum Schutz Deiner Daten und Deiner Privatsphäre\n\n" +
                            "Möchtest du fortfahren? Die App merkt sich Deine Entscheidung."
                )
                .setPositiveButton("Ja, fortfahren") { _, _ ->
                    prefs.edit().putBoolean("youtube_notice_shown", true).apply()
                    openYouTubeVideoInternal(video)
                }
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Datenschutz") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://policies.google.com/privacy"))
                    startActivity(intent)
                }
                .create()

            dialog.show()

            // Bestimme Farbe basierend auf Theme
            val isDarkTheme = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                android.graphics.Color.WHITE  // Dark Mode: Weiße Schrift
            } else {
                android.graphics.Color.parseColor("#2d5016")  // Light Mode: Grüne Schrift
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openYouTubeVideoInternal(video)
        }
    }

    private fun openYouTubeVideoInternal(video: YouTubeVideo) {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${video.videoId}"))
            appIntent.setPackage("com.google.android.youtube")

            if (appIntent.resolveActivity(packageManager) != null) {
                android.util.Log.d("MainActivity", "Öffne in YouTube App")
                startActivity(appIntent)
            } else {
                android.util.Log.d("MainActivity", "YouTube App nicht gefunden, öffne im Browser")
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=${video.videoId}"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Fehler beim Öffnen des Videos", e)
            Toast.makeText(this,
                "Konnte Video nicht öffnen",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshContent() {
        contentCache.clearCache()
        loadContent()
    }

    private fun openContent(item: ContentItem) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", item.url)
            putExtra("title", item.title)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val textColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spanString = android.text.SpannableString(item.title.toString())
            spanString.setSpan(
                android.text.style.ForegroundColorSpan(textColor),
                0,
                spanString.length,
                0
            )
            item.title = spanString
        }

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterContent(newText ?: "")
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                contentCache.clearCache()
                refreshContent()
                true
            }
            R.id.action_theme -> {
                android.util.Log.d("MainActivity", "Theme button clicked!")
                toggleTheme()
                true
            }
            R.id.action_privacy -> {
                openPrivacyPolicy()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", getString(R.string.privacy_url))
            putExtra("title", getString(R.string.privacy_policy))
        }
        startActivity(intent)
    }

    private fun filterContent(query: String) {
        when (currentView) {
            VIEW_BLOG -> {
                val filtered = if (query.isEmpty()) {
                    allBlogPosts.take(blogAdapter.itemCount)
                } else {
                    allBlogPosts.filter { item ->
                        item.title.contains(query, ignoreCase = true) ||
                                item.excerpt.contains(query, ignoreCase = true)
                    }
                }
                blogAdapter.submitList(filtered)
            }
            VIEW_PAGES -> {
                val filtered = if (query.isEmpty()) {
                    allPages
                } else {
                    allPages.filter { item ->
                        item.title.contains(query, ignoreCase = true)
                    }
                }
                pageAdapter.submitList(filtered)
            }
            VIEW_COURSES -> {
                val filtered = if (query.isEmpty()) {
                    allCourses
                } else {
                    allCourses.filter { item ->
                        item.title.contains(query, ignoreCase = true)
                    }
                }
                courseAdapter.submitList(filtered)
            }
        }
    }

    private fun setupFab() {
        android.util.Log.d("MainActivity", "setupFab() wird aufgerufen")

        // Haupt-FAB Click - WICHTIG: stopPropagation
        binding.fabMain.setOnClickListener { view ->
            android.util.Log.d("MainActivity", "Haupt-FAB geklickt!")
            view.isClickable = false  // Verhindere Doppel-Klicks
            toggleFab()
            view.postDelayed({ view.isClickable = true }, 300)
        }

        // NEUER Wetter FAB
        binding.fabWeather.setOnClickListener { view ->
            android.util.Log.d("MainActivity", "Wetter FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openWeather()
                view.isClickable = true
            }, 300)
        }

        // Standort FAB (vorher Timer)
        binding.fabLocation.setOnClickListener { view ->
            android.util.Log.d("MainActivity", "Standort FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openLocation()
                view.isClickable = true
            }, 300)
        }

        // Kompass FAB
        binding.fabCompass.setOnClickListener { view ->
            android.util.Log.d("MainActivity", "Kompass FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openCompass()
                view.isClickable = true
            }, 300)
        }

        // Höhenmesser FAB (vorher GPS)
        binding.fabAltimeter.setOnClickListener { view ->
            android.util.Log.d("MainActivity", "Höhenmesser FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openAltimeter()
                view.isClickable = true
            }, 300)
        }

        android.util.Log.d("MainActivity", "setupFab() abgeschlossen")
    }

    private fun toggleFab() {
        if (isFabOpen) {
            closeFab()
        } else {
            openFab()
        }
    }

    private fun openFab() {
        android.util.Log.d("MainActivity", "openFab() aufgerufen")
        isFabOpen = true

        // KEIN Overlay mehr - nur Hintergrund dimmen
        binding.swipeRefresh.alpha = 0.3f
        binding.swipeRefresh.isEnabled = false

        // Reihenfolge von oben nach unten (passend zur XML)
        showFabWithAnimation(binding.fabLocation, binding.fabLocationLabel, 0)
        showFabWithAnimation(binding.fabCompass, binding.fabCompassLabel, 50)
        showFabWithAnimation(binding.fabWeather, binding.fabWeatherLabel, 100)
        showFabWithAnimation(binding.fabAltimeter, binding.fabAltimeterLabel, 150)

        binding.fabMain.animate().rotation(45f).setDuration(200).start()

        // Root-Klick schließt FAB (aber nur außerhalb der FABs)
        binding.root.setOnClickListener {
            closeFab()
        }

        android.util.Log.d("MainActivity", "openFab() abgeschlossen")
    }

    private fun closeFab() {
        isFabOpen = false

        // Stelle Hintergrund wieder her
        binding.swipeRefresh.alpha = 1f
        binding.swipeRefresh.isEnabled = true

        // Entferne Root-Klick-Listener
        binding.root.setOnClickListener(null)

        // Verstecke FABs
        hideFabWithAnimation(binding.fabLocation, binding.fabLocationLabel, 0)
        hideFabWithAnimation(binding.fabCompass, binding.fabCompassLabel, 50)
        hideFabWithAnimation(binding.fabWeather, binding.fabWeatherLabel, 100)
        hideFabWithAnimation(binding.fabAltimeter, binding.fabAltimeterLabel, 150)

        binding.fabMain.animate().rotation(0f).setDuration(200).start()
    }

    private fun showFabWithAnimation(fab: View, label: View, delay: Long) {
        fab.visibility = View.VISIBLE
        label.visibility = View.VISIBLE

        fab.alpha = 0f
        fab.translationY = 20f
        label.alpha = 0f
        label.translationX = 20f

        fab.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(200)
            .start()

        label.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(delay)
            .setDuration(200)
            .start()
    }

    private fun hideFabWithAnimation(fab: View, label: View, delay: Long) {
        fab.animate()
            .alpha(0f)
            .translationY(20f)
            .setStartDelay(delay)
            .setDuration(200)
            .withEndAction { fab.visibility = View.GONE }
            .start()

        label.animate()
            .alpha(0f)
            .translationX(20f)
            .setStartDelay(delay)
            .setDuration(200)
            .withEndAction { label.visibility = View.GONE }
            .start()
    }

    private fun openWeather() {
        val intent = Intent(this, WeatherActivity::class.java)
        startActivity(intent)
    }

    private fun openCompass() {
        val intent = Intent(this, CompassActivity::class.java)
        startActivity(intent)
    }

    private fun openLocation() {
        val intent = Intent(this, LocationActivity::class.java)
        startActivity(intent)
    }

    private fun openAltimeter() {
        val intent = Intent(this, AltimeterActivity::class.java)
        startActivity(intent)
    }

    private fun loadThemePreference() {
        val sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("is_dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun saveThemePreference(isDarkMode: Boolean) {
        val sharedPreferences = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("is_dark_mode", isDarkMode)
            apply()
        }
    }

    private fun toggleTheme() {
        val nightMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePreference(false)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePreference(true)
        }
        recreate()
    }
}