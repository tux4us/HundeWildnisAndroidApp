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
        const val VIEW_TWITTER = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadThemePreference()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 1. Bottom Navigation bekommt Padding für System-Navigation
            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)

            // 2. Warte bis BottomNavigation ihre Höhe kennt
            binding.bottomNavigation.post {
                val bottomNavHeight = binding.bottomNavigation.height
                val totalBottomPadding = bottomNavHeight + systemBars.bottom

                // 2a. Setze Padding für ALLE RecyclerViews
                binding.blogRecyclerView.setPadding(
                    binding.blogRecyclerView.paddingLeft,
                    binding.blogRecyclerView.paddingTop,
                    binding.blogRecyclerView.paddingRight,
                    totalBottomPadding
                )

                binding.pagesRecyclerView.setPadding(
                    binding.pagesRecyclerView.paddingLeft,
                    binding.pagesRecyclerView.paddingTop,
                    binding.pagesRecyclerView.paddingRight,
                    totalBottomPadding
                )

                binding.coursesRecyclerView.setPadding(
                    binding.coursesRecyclerView.paddingLeft,
                    binding.coursesRecyclerView.paddingTop,
                    binding.coursesRecyclerView.paddingRight,
                    totalBottomPadding
                )

                binding.youtubeRecyclerView.setPadding(
                    binding.youtubeRecyclerView.paddingLeft,
                    binding.youtubeRecyclerView.paddingTop,
                    binding.youtubeRecyclerView.paddingRight,
                    totalBottomPadding
                )

                // 2b. Button Position - IM SELBEN BLOCK!
                val buttonParams = binding.btnLoadMore.layoutParams as
                        android.widget.FrameLayout.LayoutParams
                buttonParams.bottomMargin = bottomNavHeight + systemBars.bottom + 16.dpToPx()
                binding.btnLoadMore.layoutParams = buttonParams

                android.util.Log.d("MainActivity",
                    "✓ Padding: BottomNav=${bottomNavHeight}px + SystemBars=${systemBars.bottom}px = ${totalBottomPadding}px")
                android.util.Log.d("MainActivity",
                    "✓ Button Margin: ${buttonParams.bottomMargin}px")
            }

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
    }

    // Hilfsfunktion
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
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
                R.id.nav_twitter -> {  // NEU
                    openTwitter()
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
        val cachedYouTube = contentCache.getYouTubeVideos()

        val hasFullCache = cachedPosts != null && cachedPages != null &&
                cachedCourses != null && cachedYouTube != null
        val isCacheExpired = contentCache.isCacheExpired()

        if (hasFullCache) {
            // Cache vorhanden - zeige sofort an
            android.util.Log.d("MainActivity", "📦 Lade Inhalte aus Cache (Alter: ${if(isCacheExpired) "veraltet" else "aktuell"})")

            allBlogPosts = cachedPosts!!
            allPages = cachedPages!!
            allCourses = cachedCourses!!
            allYouTubeVideos = cachedYouTube!!

            blogAdapter.submitList(cachedPosts.take(20))
            pageAdapter.submitList(cachedPages)
            courseAdapter.submitList(cachedCourses)
            youtubeAdapter.submitList(cachedYouTube)

            binding.progressBar.visibility = View.GONE
            binding.errorText.visibility = View.GONE

            when (currentView) {
                VIEW_BLOG -> showBlogView()
                VIEW_PAGES -> showPagesView()
                VIEW_COURSES -> showCoursesView()
                VIEW_YOUTUBE -> showYouTubeView()
            }

            if (isCacheExpired) {
                // Cache veraltet - aktualisiere im Hintergrund
                Toast.makeText(this, "Inhalte werden aktualisiert...", Toast.LENGTH_SHORT).show()
                loadContentFromNetwork(silent = true)
            } else {
                // Cache aktuell - kein Update nötig
                Toast.makeText(this, "Inhalte aus Cache geladen", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Kein Cache vorhanden - zeige Ladeanimation
        android.util.Log.d("MainActivity", "🌐 Kein Cache vorhanden - lade vom Server")
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.text = "Inhalte werden geladen..."
        binding.errorText.setTextColor(android.graphics.Color.parseColor("#2d5016"))
        binding.errorText.visibility = View.VISIBLE

        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE

        loadContentFromNetwork(silent = false)

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

                // UI IMMER aktualisieren (auch im silent mode!)
                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)
                courseAdapter.submitList(courses)
                youtubeAdapter.submitList(videos)

                val totalDuration = System.currentTimeMillis() - totalStartTime
                android.util.Log.d("MainActivity", "⏱️ GESAMT: ${totalDuration}ms (${totalDuration/1000.0}s)")
                android.util.Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                if (!silent) {
                    // Erstmaliges Laden - verstecke Ladeanimation
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.visibility = View.GONE

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
                    // Silent Update im Hintergrund - nur kurze Bestätigung
                    Toast.makeText(this@MainActivity,
                        "✓ Inhalte aktualisiert",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Fehler beim Laden", e)
                e.printStackTrace()
                if (!silent) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.text = getString(R.string.error_loading, e.message)
                    binding.errorText.setTextColor(android.graphics.Color.RED)
                    binding.errorText.visibility = View.VISIBLE

                    Toast.makeText(this@MainActivity,
                        getString(R.string.error_loading, e.message),
                        Toast.LENGTH_LONG).show()
                } else {
                    // Silent mode - nur Log, keine UI-Änderung
                    android.util.Log.e("MainActivity", "Hintergrund-Update fehlgeschlagen")
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

    private fun openTwitter() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
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
                .setNegativeButton("Abbrechen") { _, _ ->
                    // Zurück zum vorherigen Tab
                    binding.bottomNavigation.selectedItemId = when (currentView) {
                        VIEW_BLOG -> R.id.nav_blog
                        VIEW_PAGES -> R.id.nav_pages
                        VIEW_COURSES -> R.id.nav_courses
                        VIEW_YOUTUBE -> R.id.nav_youtube
                        else -> R.id.nav_blog
                    }
                }
                .setNeutralButton("Datenschutz") { _, _ ->
                    val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://twitter.com/privacy"))
                    startActivity(intent)
                }
                .create()

            dialog.show()

            // Bestimme Farbe basierend auf Theme
            val isDarkTheme = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.parseColor("#2d5016")
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openTwitterInternal()
        }
    }

    private fun openTwitterInternal() {
        try {
            // Ersetze mit eurem Twitter/X Username
            val twitterUsername = "@teamnaturedogs"  // ← HIER ANPASSEN!

            // Versuche Twitter/X App zu öffnen
            val appIntent = Intent(Intent.ACTION_VIEW,
                Uri.parse("twitter://user?screen_name=$twitterUsername"))
            appIntent.setPackage("com.twitter.android")

            if (appIntent.resolveActivity(packageManager) != null) {
                android.util.Log.d("MainActivity", "Öffne in Twitter App")
                startActivity(appIntent)
            } else {
                // Fallback: Öffne im Browser
                android.util.Log.d("MainActivity", "Twitter App nicht gefunden, öffne im Browser")
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://twitter.com/$twitterUsername"))
                startActivity(webIntent)
            }

            // Zurück zum vorherigen Tab nach dem Öffnen
            binding.bottomNavigation.postDelayed({
                binding.bottomNavigation.selectedItemId = when (currentView) {
                    VIEW_BLOG -> R.id.nav_blog
                    VIEW_PAGES -> R.id.nav_pages
                    VIEW_COURSES -> R.id.nav_courses
                    VIEW_YOUTUBE -> R.id.nav_youtube
                    else -> R.id.nav_blog
                }
            }, 300)

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Fehler beim Öffnen von Twitter", e)
            Toast.makeText(this,
                "Konnte Twitter nicht öffnen",
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
            R.id.action_imprint -> {
                openImprint()
                true
            }
            // NEU: SIGNAL Chat mit Wildnislehrerin
            R.id.action_signal_wildnislehrerin -> {
                openSignalTrainer(
                    name = "Hundetrainerin und Wildnislehrerin Manuela",
                    link = "https://signal.me/#eu/Ia1RmzOJN5Qo9YkKQfs3vh7f8X70AjwOqb_jpVe5QV1Bw1UVnbpCk5abghaeuz5U"
                )
                true
            }
            // NEU: SIGNAL Chat mit Wildnislehrer
            R.id.action_signal_wildnislehrer -> {
                openSignalTrainer(
                    name = "Hundetrainer und Wildnislehrer Jörg",
                    link = "https://signal.me/#eu/UquXr52-0uUIh1o40pI5jH6HLy6gb3eIWeaSLn8eTIuCTKmOtFRF9Ji2VDvGXqFo"
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openSignalTrainer(name: String, link: String) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
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

            // Button-Farben anpassen
            val isDarkTheme = (resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.parseColor("#2d5016")
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        } else {
            openSignalTrainerInternal(name, link)
        }
    }

    private fun openSignalTrainerInternal(name: String, link: String) {
        try {
            android.util.Log.d("MainActivity", "Öffne Signal-Link für $name: $link")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
            android.util.Log.d("MainActivity", "✓ Signal-Intent gestartet")

        } catch (e: android.content.ActivityNotFoundException) {
            android.util.Log.e("MainActivity", "Signal nicht installiert")
            showSignalNotInstalledDialogForTrainer()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Fehler beim Öffnen von Signal", e)
            Toast.makeText(this,
                "Fehler beim Öffnen von Signal: ${e.message}",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showSignalNotInstalledDialogForTrainer() {
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
                val websiteIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://signal.org/download/"))
                startActivity(websiteIntent)
            }
            .setNeutralButton("Play Store") { _, _ ->
                val playStoreIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=org.thoughtcrime.securesms"))
                startActivity(playStoreIntent)
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()

        val isDarkTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) {
            android.graphics.Color.WHITE
        } else {
            android.graphics.Color.parseColor("#2d5016")
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(buttonColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", getString(R.string.privacy_url))
            putExtra("title", getString(R.string.privacy_policy))
        }
        startActivity(intent)
    }

    private fun openImprint() {
        val intent = Intent(this, ImprintActivity::class.java)
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
        binding.contentContainer.alpha = 0.3f

        // Reihenfolge von oben nach unten (passend zur XML)
        showFabWithAnimation(binding.fabLocation, binding.fabLocationLabel, 0)
        showFabWithAnimation(binding.fabCompass, binding.fabCompassLabel, 60)
        showFabWithAnimation(binding.fabWeather, binding.fabWeatherLabel, 120)
        showFabWithAnimation(binding.fabAltimeter, binding.fabAltimeterLabel, 180)

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
        binding.contentContainer.alpha = 1f

        // Entferne Root-Klick-Listener
        binding.root.setOnClickListener(null)

        // Verstecke FABs
        hideFabWithAnimation(binding.fabLocation, binding.fabLocationLabel, 0)
        hideFabWithAnimation(binding.fabCompass, binding.fabCompassLabel, 60)
        hideFabWithAnimation(binding.fabWeather, binding.fabWeatherLabel, 120)
        hideFabWithAnimation(binding.fabAltimeter, binding.fabAltimeterLabel, 180)

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