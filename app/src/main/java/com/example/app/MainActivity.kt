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

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter
    private lateinit var pageAdapter: ContentAdapter
    private lateinit var courseAdapter: ContentAdapter
    private val wordPressParser = WordPressParser()
    private lateinit var contentCache: ContentCache

    private var allBlogPosts = listOf<ContentItem>()
    private var allPages = listOf<ContentItem>()
    private var allCourses = listOf<ContentItem>()
    private var currentView = VIEW_BLOG

    private var isFabOpen = false
    //private var overlayView: View? = null  // NEU

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WICHTIG: Theme ZUERST laden!
        loadThemePreference()

        // Edge-to-Edge aktivieren (für alle Android-Versionen)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Insets für Navigationsleiste berücksichtigen
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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

    private fun loadThemePreference() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_theme", true)

        android.util.Log.d("MainActivity", "loadThemePreference: isDark = $isDark")

        // Setze lokales Theme (nur für diese Activity)
        delegate.localNightMode = if (isDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentDark = prefs.getBoolean("dark_theme", true)
        val newDark = !currentDark

        android.util.Log.d("MainActivity", "toggleTheme: currentDark = $currentDark, newDark = $newDark")

        // Speichere SOFORT
        prefs.edit()
            .putBoolean("dark_theme", newDark)
            .commit()

        android.util.Log.d("MainActivity", "toggleTheme: saved value = ${prefs.getBoolean("dark_theme", true)}")

        // Setze Theme
        AppCompatDelegate.setDefaultNightMode(
            if (newDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Starte Activity neu
        recreate()
    }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item ->
            openContent(item)
        }

        pageAdapter = ContentAdapter { item ->
            openContent(item)
        }

        courseAdapter = ContentAdapter { item ->
            openContent(item)
        }

        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter

        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pagesRecyclerView.adapter = pageAdapter

        binding.coursesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.coursesRecyclerView.adapter = courseAdapter
    }

    private fun setupButtons() {
        binding.btnBlog.setOnClickListener {
            showBlogView()
        }

        binding.btnPages.setOnClickListener {
            showPagesView()
        }

        binding.btnCourses.setOnClickListener {
            showCoursesView()
        }

        binding.btnLoadMore.setOnClickListener {
            loadMoreBlogPosts()
        }

        showBlogView()
    }

    private fun showBlogView() {
        currentView = VIEW_BLOG
        binding.btnBlog.isSelected = true
        binding.btnPages.isSelected = false
        binding.btnCourses.isSelected = false
        binding.blogRecyclerView.visibility = View.VISIBLE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.VISIBLE
    }

    private fun showPagesView() {
        currentView = VIEW_PAGES
        binding.btnBlog.isSelected = false
        binding.btnPages.isSelected = true
        binding.btnCourses.isSelected = false
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.VISIBLE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showCoursesView() {
        currentView = VIEW_COURSES
        binding.btnBlog.isSelected = false
        binding.btnPages.isSelected = false
        binding.btnCourses.isSelected = true
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun loadContent() {
        // Versuche zuerst Cache zu laden
        val cachedPosts = contentCache.getBlogPosts()
        val cachedPages = contentCache.getPages()
        val cachedCourses = contentCache.getCourses()

        if (cachedPosts != null && cachedPages != null && cachedCourses != null) {
            // Cache ist gültig - zeige sofort
            allBlogPosts = cachedPosts
            allPages = cachedPages
            allCourses = cachedCourses

            blogAdapter.submitList(cachedPosts.take(20))
            pageAdapter.submitList(cachedPages)
            courseAdapter.submitList(cachedCourses)

            binding.progressBar.visibility = View.GONE
            binding.errorText.visibility = View.GONE

            when (currentView) {
                VIEW_BLOG -> showBlogView()
                VIEW_PAGES -> showPagesView()
                VIEW_COURSES -> showCoursesView()
            }

            Toast.makeText(this,
                "Inhalte aus Cache geladen",
                Toast.LENGTH_SHORT).show()

            // Lade im Hintergrund neue Daten
            loadContentFromNetwork(silent = true)
            return
        }

        // Kein Cache - zeige Ladeanimation
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.text = "Inhalte werden aktualisiert..."
        binding.errorText.setTextColor(android.graphics.Color.parseColor("#2d5016"))
        binding.errorText.visibility = View.VISIBLE

        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.coursesRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE

        loadContentFromNetwork(silent = false)
    }

    private fun loadContentFromNetwork(silent: Boolean) {
        lifecycleScope.launch {
            try {
                val result: Triple<List<ContentItem>, List<ContentItem>, List<ContentItem>> =
                    withContext(Dispatchers.IO) {
                        val postsDeferred = async { wordPressParser.parseBlogPosts() }
                        val pagesDeferred = async { wordPressParser.parsePages() }
                        val coursesDeferred = async { wordPressParser.parseCourses() }

                        Triple(
                            postsDeferred.await(),
                            pagesDeferred.await(),
                            coursesDeferred.await()
                        )
                    }

                val posts = result.first
                val pages = result.second
                val courses = result.third

                // Speichere in Cache
                contentCache.saveBlogPosts(posts)
                contentCache.savePages(pages)
                contentCache.saveCourses(courses)

                allBlogPosts = posts
                allPages = pages
                allCourses = courses

                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)
                courseAdapter.submitList(courses)

                if (!silent) {
                    binding.progressBar.visibility = View.GONE
                    binding.errorText.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false

                    when (currentView) {
                        VIEW_BLOG -> showBlogView()
                        VIEW_PAGES -> showPagesView()
                        VIEW_COURSES -> showCoursesView()
                    }

                    Toast.makeText(this@MainActivity,
                        "${posts.size} Beiträge, ${pages.size} Seiten, ${courses.size} Kurse geladen",
                        Toast.LENGTH_SHORT).show()
                } else {
                    binding.swipeRefresh.isRefreshing = false
                    // Stiller Update - kein Toast
                }

            } catch (e: Exception) {
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

    private fun loadMoreBlogPosts() {
        val currentSize = blogAdapter.itemCount
        val nextBatch = allBlogPosts.drop(currentSize).take(20)

        if (nextBatch.isEmpty()) {
            Toast.makeText(this, "Keine weiteren Beiträge", Toast.LENGTH_SHORT).show()
            return
        }

        val newList = allBlogPosts.take(currentSize + 20)
        blogAdapter.submitList(newList)
        Toast.makeText(this, "${nextBatch.size} weitere Beiträge geladen", Toast.LENGTH_SHORT).show()
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
            putExtra("title", "Datenschutzerklärung")
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
}