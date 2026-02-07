package com.example.app

import android.content.ActivityNotFoundException
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.AltimeterActivity
import com.hundewildnis.app.CompassActivity
import com.hundewildnis.app.ContentActivity
import com.hundewildnis.app.ContentAdapter
import com.hundewildnis.app.ContentCache
import com.hundewildnis.app.ContentItem
import com.hundewildnis.app.ImprintActivity
import com.hundewildnis.app.R
import com.hundewildnis.app.WeatherActivity
import com.hundewildnis.app.WordPressApiParser
import com.hundewildnis.app.WordPressParser
import com.hundewildnis.app.YouTubeAdapter
import com.hundewildnis.app.YouTubeParser
import com.hundewildnis.app.YouTubeVideo
import com.hundewildnis.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.os.Build
import com.hundewildnis.app.ContentUpdateWorker

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter
    private lateinit var pageAdapter: ContentAdapter
    private lateinit var courseAdapter: ContentAdapter
    private lateinit var youtubeAdapter: YouTubeAdapter

    // OPTIMIERT: Nur noch API-Parser für Blog, Kein API für Seiten UND Kurse!
    private val wordPressApi = WordPressApiParser()
    private val wordPressParser = WordPressParser()
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
        const val VIEW_PODCASTS = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadThemePreference()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom)

            binding.bottomNavigation.post {
                val bottomNavHeight = binding.bottomNavigation.height
                val totalBottomPadding = bottomNavHeight + systemBars.bottom

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

                val buttonParams = binding.btnLoadMore.layoutParams as
                        FrameLayout.LayoutParams
                buttonParams.bottomMargin = bottomNavHeight + systemBars.bottom + 16.dpToPx()
                binding.btnLoadMore.layoutParams = buttonParams
            }

            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "HundeWildnis"
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)

        contentCache = ContentCache(this)

        setupRecyclerViews()
        setupButtons()
        setupFab()
        loadContent()

        // NEU: Registriere Hintergrund-Updates
        scheduleContentUpdates()

        // NEU: Frage Benachrichtigungs-Berechtigung (Android 13+)
        requestNotificationPermission()
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item -> openContent(item) }
        pageAdapter = ContentAdapter { item -> openContent(item) }
        courseAdapter = ContentAdapter { item -> openContent(item) }
        youtubeAdapter = YouTubeAdapter { video -> openYouTubeVideo(video) }

        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter

        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pagesRecyclerView.adapter = pageAdapter

        binding.coursesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.coursesRecyclerView.adapter = courseAdapter

        binding.youtubeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.youtubeRecyclerView.adapter = youtubeAdapter
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
                R.id.nav_podcasts -> {  // NEU: statt nav_twitter
                    openPodcasts()
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

                if (currentView != VIEW_BLOG) {
                    binding.btnLoadMore.visibility = View.GONE
                    return
                }

                if (blogAdapter.itemCount >= allBlogPosts.size) {
                    binding.btnLoadMore.visibility = View.GONE
                    return
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItems = layoutManager.itemCount

                if (lastVisibleItem >= totalItems - 5) {
                    binding.btnLoadMore.visibility = View.VISIBLE
                }
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

    private fun showYouTubeView() {
        currentView = VIEW_YOUTUBE
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun openPodcasts() {
        try {
            val intent = Intent(this, ContentActivity::class.java).apply {
                putExtra("url", "https://wildnislehrer.com/podcasts-audiobeitraege/")
                putExtra("title", "HundeWildnis Podcast")
            }
            startActivity(intent)

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
            Log.e("MainActivity", "Fehler beim Öffnen der Podcasts", e)
            Toast.makeText(this,
                "Konnte Podcasts nicht öffnen",
                Toast.LENGTH_LONG).show()
        }
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
            Log.d("MainActivity", "📦 Lade Inhalte aus Cache (Alter: ${if(isCacheExpired) "veraltet" else "aktuell"})")

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
                Toast.makeText(this, "Inhalte werden geladen...", Toast.LENGTH_SHORT).show()
                loadContentFromNetwork(silent = true)
            } else {
                Toast.makeText(this, "Inhalte aus Cache geladen", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("MainActivity", "🌐 Kein Cache vorhanden - lade vom Server")
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.text = "Wir machen uns jetzt für Dich auf die Spur \nund suchen \uD83D\uDC36 alle Seiten nach neuen Inhalten ab. \n\nErstmalig kann dieser Vorgang 1 Minute ⏳ dauern.\n\nWir danken \uD83D\uDE4F\uD83C\uDFFD für Deine Geduld."
        binding.errorText.setTextColor(Color.parseColor("#3f6f1e"))
        binding.errorText.visibility = View.VISIBLE

        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE

        loadContentFromNetwork(silent = false)
    }

    // ⚡ OPTIMIERT: Nutzt nur noch API-Calls (außer Kurse Fallback)
    private fun loadContentFromNetwork(silent: Boolean) {
        lifecycleScope.launch {
            try {
                val totalStartTime = System.currentTimeMillis()
                Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("MainActivity", "⏱️ START Laden: ${System.currentTimeMillis()}")

                val result = withContext(Dispatchers.IO) {
                    // ⚡ Blog: API (schnell)
                    val blogDeferred = async<List<ContentItem>> {
                        val start = System.currentTimeMillis()
                        Log.d("MainActivity", "📝 Blog: Starte API-Call...")
                        val result = try {
                            wordPressApi.loadBlogPosts(perPage = 100)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Blog-Fehler: ${e.message}")
                            emptyList()
                        }
                        val duration = System.currentTimeMillis() - start
                        Log.d("MainActivity", "✓ Blog (API): ${result.size} Posts in ${duration}ms")
                        result
                    }

                    // 🎯 Seiten: Jsoup (korrekte Sortierung) ← NEU!
                    val pagesDeferred = async<List<ContentItem>> {
                        val start = System.currentTimeMillis()
                        Log.d("MainActivity", "📄 Seiten: Starte Jsoup-Parsing...")  // ← GEÄNDERT
                        val result = try {
                            wordPressParser.parsePages()  // ← GEÄNDERT (war: wordPressApi.loadPages())
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Seiten-Fehler: ${e.message}")
                            emptyList()
                        }
                        val duration = System.currentTimeMillis() - start
                        Log.d("MainActivity", "✓ Seiten (Jsoup): ${result.size} Seiten in ${duration}ms")  // ← GEÄNDERT
                        result
                    }

// 🎯 Kurse: Jsoup (bleibt wie es ist!)
                    val coursesDeferred = async<List<ContentItem>> {
                        val start = System.currentTimeMillis()
                        Log.d("MainActivity", "🎓 Kurse: Starte Jsoup-Parsing...")
                        val result = try {
                            wordPressParser.parseCourses()  // ← Bleibt Jsoup!
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Kurse-Fehler: ${e.message}")
                            emptyList()
                        }
                        val duration = System.currentTimeMillis() - start
                        Log.d("MainActivity", "✓ Kurse (Jsoup): ${result.size} Kurse in ${duration}ms")
                        result
                    }

                    val youtubeDeferred = async<List<YouTubeVideo>> {
                        val start = System.currentTimeMillis()
                        Log.d("MainActivity", "🎥 YouTube: Starte Laden...")
                        val result = try {
                            youtubeParser.loadLatestVideos()
                        } catch (e: Exception) {
                            Log.e("MainActivity", "YouTube-Fehler: ${e.message}")
                            emptyList()
                        }
                        val duration = System.currentTimeMillis() - start
                        Log.d("MainActivity", "✓ YouTube: ${result.size} Videos in ${duration}ms")
                        result
                    }

                    // Warte auf ALLE parallel
                    val posts = blogDeferred.await()
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
                Log.d("MainActivity", "💾 Cache gespeichert in ${cacheDuration}ms")

                allBlogPosts = posts
                allPages = pages
                allCourses = courses
                allYouTubeVideos = videos

                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)
                courseAdapter.submitList(courses)
                youtubeAdapter.submitList(videos)

                val totalDuration = System.currentTimeMillis() - totalStartTime
                Log.d("MainActivity", "⏱️ GESAMT: ${totalDuration}ms (${totalDuration/1000.0}s)")
                Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                if (!silent) {
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
                    Toast.makeText(this@MainActivity,
                        "✓ Inhalte aktualisiert",
                        Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Fehler beim Laden", e)
                e.printStackTrace()
                if (!silent) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.text = getString(R.string.error_loading, e.message)
                    binding.errorText.setTextColor(Color.RED)
                    binding.errorText.visibility = View.VISIBLE

                    Toast.makeText(this@MainActivity,
                        getString(R.string.error_loading, e.message),
                        Toast.LENGTH_LONG).show()
                } else {
                    Log.e("MainActivity", "Hintergrund-Update fehlgeschlagen")
                }
            }
        }
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    // ... Rest der Methoden bleibt GENAU GLEICH ...
    // (openYouTubeVideo, openTwitter, openContent, setupFab, etc.)

    private fun openYouTubeVideo(video: YouTubeVideo) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
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
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://policies.google.com/privacy")
                    )
                    startActivity(intent)
                }
                .create()

            dialog.show()

            val isDarkTheme = (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                Color.WHITE
            } else {
                Color.parseColor("#2d5016")
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
                Log.d("MainActivity", "Öffne in YouTube App")
                startActivity(appIntent)
            } else {
                Log.d("MainActivity", "YouTube App nicht gefunden, öffne im Browser")
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=${video.videoId}")
                )
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Öffnen des Videos", e)
            Toast.makeText(this,
                "Konnte Video nicht öffnen",
                Toast.LENGTH_LONG).show()
        }
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
                .setNegativeButton("Abbrechen", null)  // ENTFERNE die Bottom Nav Zurück-Logik!
                .setNeutralButton("Datenschutz") { _, _ ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://twitter.com/privacy")
                    )
                    startActivity(intent)
                }
                .create()

            dialog.show()

            val isDarkTheme = (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                Color.WHITE
            } else {
                Color.parseColor("#2d5016")
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
            val twitterUsername = "@teamnaturedogs"

            val appIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("twitter://user?screen_name=$twitterUsername")
            )
            appIntent.setPackage("com.twitter.android")

            if (appIntent.resolveActivity(packageManager) != null) {
                Log.d("MainActivity", "Öffne in Twitter App")
                startActivity(appIntent)
            } else {
                Log.d("MainActivity", "Twitter App nicht gefunden, öffne im Browser")
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://twitter.com/$twitterUsername")
                )
                startActivity(webIntent)
            }

            // ENTFERNE die postDelayed Bottom Nav Zurück-Logik!

        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Öffnen von Twitter", e)
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
                Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == Configuration.UI_MODE_NIGHT_YES

        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spanString = SpannableString(item.title.toString())
            spanString.setSpan(
                ForegroundColorSpan(textColor),
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
                Log.d("MainActivity", "Theme button clicked!")
                toggleTheme()
                true
            }

            R.id.action_settings -> {  // NEU
                showSettingsDialog()
                true
            }

            R.id.action_test_update -> {  // NEU
                testBackgroundUpdate()
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
            // NEU: Twitter im Menü
            R.id.action_twitter -> {
                openTwitter()
                true
            }
            R.id.action_signal_wildnislehrerin -> {
                openSignalTrainer(
                    name = "Hundetrainerin und Wildnislehrerin Manuela",
                    link = "https://signal.me/#eu/Ia1RmzOJN5Qo9YkKQfs3vh7f8X70AjwOqb_jpVe5QV1Bw1UVnbpCk5abghaeuz5U"
                )
                true
            }
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

    private fun testBackgroundUpdate() {
        Toast.makeText(this, "Starte Test-Update in 10 Sekunden...", Toast.LENGTH_LONG).show()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val testRequest = OneTimeWorkRequestBuilder<ContentUpdateWorker>()
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(testRequest)

        Log.d("MainActivity", "🧪 Test-Update gestartet - Check Logcat in 10s")
    }

    // NEU: Einstellungs-Dialog
    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val switchBackgroundUpdates = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val switchWifiOnly = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val switchNotifications = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)

        // Lade gespeicherte Einstellungen
        switchBackgroundUpdates.isChecked = prefs.getBoolean("background_updates_enabled", true)
        switchWifiOnly.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)

        // Theme-abhängige Switch-Farben setzen
        val isDarkTheme = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        if (isDarkTheme) {
            val whiteColorStateList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            switchBackgroundUpdates.thumbTintList = whiteColorStateList
            switchBackgroundUpdates.trackTintList = whiteColorStateList.withAlpha(77)
            switchWifiOnly.thumbTintList = whiteColorStateList
            switchWifiOnly.trackTintList = whiteColorStateList.withAlpha(77)
            switchNotifications.thumbTintList = whiteColorStateList
            switchNotifications.trackTintList = whiteColorStateList.withAlpha(77)
        } else {
            val greenColor = Color.parseColor("#2d5016")
            val greenColorStateList = android.content.res.ColorStateList.valueOf(greenColor)
            switchBackgroundUpdates.thumbTintList = greenColorStateList
            switchBackgroundUpdates.trackTintList = greenColorStateList.withAlpha(77)
            switchWifiOnly.thumbTintList = greenColorStateList
            switchWifiOnly.trackTintList = greenColorStateList.withAlpha(77)
            switchNotifications.thumbTintList = greenColorStateList
            switchNotifications.trackTintList = greenColorStateList.withAlpha(77)
        }

        // Listeners
        switchBackgroundUpdates.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_enabled", isChecked).apply()
            if (isChecked) {
                scheduleContentUpdates()
            } else {
                WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates_morning")
            }

            switchWifiOnly.isEnabled = isChecked
            switchNotifications.isEnabled = isChecked
        }

        switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply()
            scheduleContentUpdates()
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply()
        }

        switchWifiOnly.isEnabled = switchBackgroundUpdates.isChecked
        switchNotifications.isEnabled = switchBackgroundUpdates.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙️ Einstellungen")
            .setView(dialogView)
            .setPositiveButton("Fertig", null)
            .create()

        dialog.show()

        // Theme-abhängige Button-Farbe
        val buttonColor = if (isDarkTheme) {
            Color.WHITE
        } else {
            Color.parseColor("#2d5016")
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
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

            val isDarkTheme = (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES

            val buttonColor = if (isDarkTheme) {
                Color.WHITE
            } else {
                Color.parseColor("#2d5016")
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
            Log.d("MainActivity", "Öffne Signal-Link für $name: $link")

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
            Log.d("MainActivity", "✓ Signal-Intent gestartet")

        } catch (e: ActivityNotFoundException) {
            Log.e("MainActivity", "Signal nicht installiert")
            showSignalNotInstalledDialogForTrainer()
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Öffnen von Signal", e)
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
                val websiteIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://signal.org/download/")
                )
                startActivity(websiteIntent)
            }
            .setNeutralButton("Play Store") { _, _ ->
                val playStoreIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=org.thoughtcrime.securesms")
                )
                startActivity(playStoreIntent)
            }
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.show()

        val isDarkTheme = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val buttonColor = if (isDarkTheme) {
            Color.WHITE
        } else {
            Color.parseColor("#2d5016")
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
        Log.d("MainActivity", "setupFab() wird aufgerufen")

        binding.fabMain.setOnClickListener { view ->
            Log.d("MainActivity", "Haupt-FAB geklickt!")
            view.isClickable = false
            toggleFab()
            view.postDelayed({ view.isClickable = true }, 300)
        }

        binding.fabWeather.setOnClickListener { view ->
            Log.d("MainActivity", "Wetter FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openWeather()
                view.isClickable = true
            }, 300)
        }

        binding.fabLocation.setOnClickListener { view ->
            Log.d("MainActivity", "Standort FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openLocation()
                view.isClickable = true
            }, 300)
        }

        binding.fabCompass.setOnClickListener { view ->
            Log.d("MainActivity", "Kompass FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openCompass()
                view.isClickable = true
            }, 300)
        }

        binding.fabAltimeter.setOnClickListener { view ->
            Log.d("MainActivity", "Höhenmesser FAB geklickt!")
            view.isClickable = false
            closeFab()
            view.postDelayed({
                openAltimeter()
                view.isClickable = true
            }, 300)
        }

        Log.d("MainActivity", "setupFab() abgeschlossen")
    }

    private fun toggleFab() {
        if (isFabOpen) {
            closeFab()
        } else {
            openFab()
        }
    }

    private fun openFab() {
        Log.d("MainActivity", "openFab() aufgerufen")
        isFabOpen = true

        binding.contentContainer.alpha = 0.3f

        showFabWithAnimation(binding.fabLocation, binding.fabLocationLabel, 0)
        showFabWithAnimation(binding.fabCompass, binding.fabCompassLabel, 60)
        showFabWithAnimation(binding.fabWeather, binding.fabWeatherLabel, 120)
        showFabWithAnimation(binding.fabAltimeter, binding.fabAltimeterLabel, 180)

        binding.fabMain.animate().rotation(45f).setDuration(200).start()

        binding.root.setOnClickListener {
            closeFab()
        }

        Log.d("MainActivity", "openFab() abgeschlossen")
    }

    private fun closeFab() {
        isFabOpen = false

        binding.contentContainer.alpha = 1f

        binding.root.setOnClickListener(null)

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
        val sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("is_dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun saveThemePreference(isDarkMode: Boolean) {
        val sharedPreferences = getSharedPreferences("theme_prefs", MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("is_dark_mode", isDarkMode)
            apply()
        }
    }

    private fun toggleTheme() {
        val nightMode = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == Configuration.UI_MODE_NIGHT_YES

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            saveThemePreference(false)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            saveThemePreference(true)
        }
        recreate()
    }

    /**
     * Plant 2x tägliche Content-Updates (8 Uhr morgens & 20 Uhr abends)
     */
    private fun scheduleContentUpdates() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val backgroundUpdatesEnabled = prefs.getBoolean("background_updates_enabled", true)

        if (!backgroundUpdatesEnabled) {
            Log.d("MainActivity", "Hintergrund-Updates deaktiviert")
            WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates_morning")
            WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates_evening")
            return
        }

        val wifiOnly = prefs.getBoolean("background_updates_wifi_only", false)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()

        // ===== MORGENS 8 UHR =====
        val morningDelay = calculateDelayUntil(8)
        val morningRequest = PeriodicWorkRequestBuilder<ContentUpdateWorker>(
            12, TimeUnit.HOURS, // Alle 12 Stunden (wird zu 8 Uhr und 20 Uhr)
            1, TimeUnit.HOURS   // Flexibilität: 8-9 Uhr bzw. 20-21 Uhr
        )
            .setConstraints(constraints)
            .setInitialDelay(morningDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "content_updates_morning",
            ExistingPeriodicWorkPolicy.KEEP,
            morningRequest
        )

        Log.d("MainActivity", "✓ Update geplant: 8 Uhr & 20 Uhr (WLAN-only: $wifiOnly)")
        Log.d("MainActivity", "Nächstes Update in ${morningDelay / 1000 / 60} Minuten")
    }

    /**
     * Berechnet Verzögerung bis zur nächsten Zielstunde (z.B. 8 Uhr oder 20 Uhr)
     */
    private fun calculateDelayUntil(targetHour: Int): Long {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Wenn die Zielstunde heute schon vorbei ist, plane für morgen
            if (before(currentDate)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return dueDate.timeInMillis - currentDate.timeInMillis
    }

    /**
     * Fragt Benachrichtigungs-Berechtigung ab (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {

                val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                val hasAskedBefore = prefs.getBoolean("notification_permission_asked", false)

                if (!hasAskedBefore) {
                    val dialog = AlertDialog.Builder(this)
                        .setTitle("Benachrichtigungen aktivieren?")
                        .setMessage(
                            "HundeWildnis kann dich benachrichtigen, wenn neue Inhalte verfügbar sind:\n\n" +
                                    "• Neue Blogbeiträge\n" +
                                    "• Neue Seiten\n" +
                                    "• Neue Kurse\n" +
                                    "• Neue Videos\n\n" +
                                    "Die App prüft zweimal täglich (8 Uhr morgens & 20 Uhr abends) auf neue Inhalte.\n\n" +
                                    "Du kannst das jederzeit in den Einstellungen ändern."
                        )
                        .setPositiveButton("Aktivieren") { _, _ ->
                            prefs.edit().putBoolean("notification_permission_asked", true).apply()
                            requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                1001
                            )
                        }
                        .setNegativeButton("Nicht jetzt") { _, _ ->
                            prefs.edit().putBoolean("notification_permission_asked", true).apply()
                            prefs.edit().putBoolean("notifications_enabled", false).apply()
                        }
                        .create()

                    dialog.show()

                    // Theme-abhängige Button-Farben
                    val isDarkTheme = (resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES

                    val buttonColor = if (isDarkTheme) {
                        Color.WHITE
                    } else {
                        Color.parseColor("#2d5016")
                    }

                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(buttonColor)

                } else {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        1001
                    )
                }
            }
        }
    }

    /**
     * Aktiviert/Deaktiviert Hintergrund-Updates (wird von Settings aufgerufen)
     */
    fun toggleBackgroundUpdates(enabled: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("background_updates_enabled", enabled).apply()

        if (enabled) {
            scheduleContentUpdates()
        } else {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates")
            Log.d("MainActivity", "Hintergrund-Updates deaktiviert")
        }
    }

    /**
     * Aktiviert/Deaktiviert "Nur WLAN" für Updates
     */
    fun toggleWifiOnly(wifiOnly: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("background_updates_wifi_only", wifiOnly).apply()

        // Neu planen mit geänderten Constraints
        scheduleContentUpdates()
    }

    /**
     * Aktiviert/Deaktiviert Benachrichtigungen
     */
    fun toggleNotifications(enabled: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        Log.d("MainActivity", "Benachrichtigungen ${if (enabled) "aktiviert" else "deaktiviert"}")
    }
}