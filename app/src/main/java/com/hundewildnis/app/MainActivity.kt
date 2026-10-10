package com.hundewildnis.app

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
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
    private var currentBlogPage = 1
    private var canLoadMoreFromNetwork = true
    private var isLoadingMore = false
    private var selectedCategoryId: Int? = null
    private var categories = listOf<BlogCategory>()

    private var currentView = VIEW_BLOG

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
        const val VIEW_START = 3
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.main_toolbar_title)
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_menu_hamburger)?.apply {
            // Themenkonformes Tinting: das Icon war fest auf Weiß kodiert und dadurch
            // im hellen Modus auf hellem Toolbar-Hintergrund unsichtbar (weiß auf weiß).
            setTint(Color.WHITE)
        }
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

        // Redesign Grids
        setupPagesGrid()
        setupCoursesGrid()
    }

    private fun Int.dpToPx(): Int { return (this * resources.displayMetrics.density).toInt() }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item -> openContent(item) }
        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter
    }

    private fun setupButtons() {
        super.setupBottomNavigation()

        // Filter-Buttons und Leiste werden über Styles/Theme (Glas) gefärbt

        binding.btnFilterAll.setOnClickListener {
            selectedCategoryId = null
            binding.btnFilterCategories.text = getString(R.string.main_filter_categories)
            loadContentFromNetwork(silent = false)
        }

        binding.btnFilterCategories.setOnClickListener {
            showCategoryFilterDialog()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadContentFromNetwork(silent = false)
        }
        binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.rd_overlay_accent))

        binding.btnLoadMore.setOnClickListener { loadMoreBlogPosts() }
        binding.blogRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (currentView != VIEW_BLOG) { binding.btnLoadMore.visibility = View.GONE; return }
                if (blogAdapter.itemCount >= allBlogPosts.size && !canLoadMoreFromNetwork) { binding.btnLoadMore.visibility = View.GONE; return }
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                if (layoutManager.findLastVisibleItemPosition() >= layoutManager.itemCount - 5) {
                    if (currentView == VIEW_BLOG && (blogAdapter.itemCount < allBlogPosts.size || canLoadMoreFromNetwork)) {
                        binding.btnLoadMore.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    private fun showBlogView() {
        currentView = VIEW_BLOG
        binding.blogRecyclerView.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.btnLoadMore.visibility = if (blogAdapter.itemCount < allBlogPosts.size || canLoadMoreFromNetwork) View.VISIBLE else View.GONE

        if (categories.isEmpty()) {
            lifecycleScope.launch {
                categories = wordPressApi.loadCategories()
            }
        }
    }

    private fun loadMoreBlogPosts() {
        if (isLoadingMore) return
        val currentSize = blogAdapter.itemCount

        if (currentSize < allBlogPosts.size) {
            val nextSize = (currentSize + 20).coerceAtMost(allBlogPosts.size)
            val newList = allBlogPosts.take(nextSize)
            blogAdapter.submitList(newList)
            if (nextSize >= allBlogPosts.size && !canLoadMoreFromNetwork) binding.btnLoadMore.visibility = View.GONE
        } else if (canLoadMoreFromNetwork) {
            loadNextPageFromNetwork()
        }
    }

    private fun loadNextPageFromNetwork() {
        if (isLoadingMore || !canLoadMoreFromNetwork) return
        isLoadingMore = true
        binding.btnLoadMore.text = getString(R.string.main_loading_more_posts)

        lifecycleScope.launch {
            try {
                val nextPage = (allBlogPosts.size / 100) + 1
                val result = withContext(Dispatchers.IO) {
                    wordPressApi.loadBlogPosts(page = nextPage, perPage = 100, categoryId = selectedCategoryId)
                }

                if (result.isEmpty()) {
                    canLoadMoreFromNetwork = false
                } else {
                    currentBlogPage = nextPage
                    allBlogPosts = allBlogPosts + result
                    val newList = allBlogPosts.take(blogAdapter.itemCount + 20)
                    blogAdapter.submitList(newList)
                }
            } catch (e: Exception) {
                canLoadMoreFromNetwork = false
            } finally {
                isLoadingMore = false
                binding.btnLoadMore.text = getString(R.string.main_load_more_posts)
                if (!canLoadMoreFromNetwork && blogAdapter.itemCount >= allBlogPosts.size) {
                    binding.btnLoadMore.visibility = View.GONE
                }
            }
        }
    }

    private fun showPagesView() {
        currentView = VIEW_PAGES
        binding.blogRecyclerView.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.VISIBLE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showCoursesView() {
        currentView = VIEW_COURSES
        binding.blogRecyclerView.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun setupPagesGrid() {
        val spanCount = calculateSpanCount(100)

        // Gruppe 1: Hauptseite
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridPagesGroup1),
            spanCount = 1,
            groupIndex = 4, // Blau
            tiles = listOf(StartTile("page_main", R.drawable.ic_home, getString(R.string.main_page_title_main)))
        )

        // Gruppe 2: Hundeschule
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridPagesGroup2),
            spanCount = spanCount,
            groupIndex = 2, // Grün
            tiles = listOf(
                StartTile("page_anti_jagd", R.drawable.ic_dog, getString(R.string.main_page_title_anti_jagd)),
                StartTile("page_assistenzhunde", R.drawable.ic_dog, getString(R.string.main_page_title_assistenzhunde)),
                StartTile("page_beratung", R.drawable.ic_info, getString(R.string.main_page_title_beratung)),
                StartTile("page_fahrrad", R.drawable.ic_stopwatch, getString(R.string.main_page_title_fahrrad)),
                StartTile("page_faehrte", R.drawable.ic_nose_work, getString(R.string.main_page_title_faehrte)),
                StartTile("page_gedanken", R.drawable.ic_notes, getString(R.string.main_page_title_gedanken)),
                StartTile("page_grundausbildung", R.drawable.ic_exercise, getString(R.string.main_page_title_grundausbildung)),
                StartTile("page_hundeleinen", R.drawable.ic_dog, getString(R.string.main_page_title_hundeleinen)),
                StartTile("page_lernfoerderung", R.drawable.ic_info, getString(R.string.main_page_title_lernfoerderung)),
                StartTile("page_problemberatung", R.drawable.ic_info, getString(R.string.main_page_title_problemberatung)),
                StartTile("page_seminare", R.drawable.ic_calendar_moon, getString(R.string.main_page_title_seminare)),
                StartTile("page_teamsport", R.drawable.ic_exercise, getString(R.string.main_page_title_teamsport)),
                StartTile("page_tierschutz", R.drawable.ic_dog, getString(R.string.main_page_title_tierschutz)),
                StartTile("page_sozialbesuch", R.drawable.ic_heart_support, getString(R.string.main_page_title_sozialbesuch)),
                StartTile("page_wandern", R.drawable.ic_navigation, getString(R.string.main_page_title_wandern)),
                StartTile("page_welpen", R.drawable.ic_dog, getString(R.string.main_page_title_welpen))
            )
        )

        // Gruppe 3: Wildnisschule
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridPagesGroup3),
            spanCount = spanCount,
            groupIndex = 3, // Ocker/Braun
            tiles = listOf(
                StartTile("page_wildnis_in_uns", R.drawable.ic_info, getString(R.string.main_page_title_wildnis_in_uns)),
                StartTile("page_spuren", R.drawable.ic_nose_work, getString(R.string.main_page_title_spuren)),
                StartTile("page_handwerk", R.drawable.ic_exercise, getString(R.string.main_page_title_handwerk)),
                StartTile("page_paedagogik", R.drawable.ic_info, getString(R.string.main_page_title_paedagogik))
            )
        )

        // Gruppe 4: Weitere Informationen
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridPagesGroup4),
            spanCount = spanCount,
            groupIndex = 5, // Violett
            tiles = listOf(
                StartTile("page_downloads", R.drawable.ic_info, getString(R.string.main_page_title_downloads)),
                StartTile("page_honorare", R.drawable.ic_info, getString(R.string.main_page_title_honorare)),
                StartTile("page_impressum", R.drawable.ic_info, getString(R.string.main_page_title_impressum)),
                StartTile("page_agb", R.drawable.ic_info, getString(R.string.main_page_title_agb)),
                StartTile("page_datenschutz", R.drawable.ic_info, getString(R.string.main_page_title_datenschutz)),
                StartTile("page_info", R.drawable.ic_info, getString(R.string.main_page_title_info)),
                StartTile("page_kontakt", R.drawable.ic_info, getString(R.string.main_page_title_kontakt)),
                StartTile("page_ueber_uns", R.drawable.ic_dog, getString(R.string.main_page_title_ueber_uns))
            )
        )
    }

    private fun setupCoursesGrid() {
        val spanCount = calculateSpanCount(100)

        // Gruppe 1: Info
        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridCoursesGroup1),
            spanCount = 1,
            groupIndex = 4, // Blau
            tiles = listOf(StartTile("course_main", R.drawable.ic_courses, getString(R.string.main_course_title_main)))
        )

        // Gruppe 2: Kurse 1-41
        val courseTiles = (1..41).map { i ->
            StartTile("course_${String.format("%02d", i)}", R.drawable.ic_courses, "${String.format("%02d", i)} ${getCourseName(i)}")
        }

        bindGrid(
            recyclerView = findViewById<RecyclerView>(R.id.gridCoursesGroup2),
            spanCount = spanCount,
            groupIndex = 0, // Gelb/Ocker
            tiles = courseTiles
        )
    }

    private fun bindGrid(
        recyclerView: RecyclerView?,
        spanCount: Int,
        groupIndex: Int,
        tiles: List<StartTile>
    ) {
        recyclerView?.let { rv ->
            val colors = StartGroupColors.forGroup(this, groupIndex)
            rv.layoutManager = GridLayoutManager(this, spanCount)
            rv.setHasFixedSize(true)
            rv.adapter = StartTileAdapter(
                tiles = tiles,
                chipColorRes = colors.chipColorRes,
                accentColorRes = colors.accentColorRes,
                onTileClick = ::handleGridTileClick
            )
        }
    }

    private fun handleGridTileClick(tileId: String) {
        when {
            tileId == "page_main" -> openUrl("https://wildnislehrer.com/", getString(R.string.main_page_title_main))
            tileId == "page_anti_jagd" -> openUrl("https://wildnislehrer.com/hundeschule/anti-jagd-training/", getString(R.string.main_link_title_anti_jagd))
            tileId == "page_assistenzhunde" -> openUrl("https://wildnislehrer.com/hundeschule/ausbildung-von-paedagogischen-assistenzbegleithunden/", getString(R.string.main_link_title_assistenzhunde))
            tileId == "page_beratung" -> openUrl("https://wildnislehrer.com/hundeschule/beratung/", getString(R.string.main_page_title_beratung))
            tileId == "page_fahrrad" -> openUrl("https://wildnislehrer.com/hundeschule/fahrradtraining/", getString(R.string.main_page_title_fahrrad))
            tileId == "page_faehrte" -> openUrl("https://wildnislehrer.com/hundeschule/faehrte-und-suche/", getString(R.string.main_link_title_faehrte))
            tileId == "page_gedanken" -> openUrl("https://wildnislehrer.com/hundeschule/gedanken-und-grundlagen/", getString(R.string.main_link_title_gedanken))
            tileId == "page_grundausbildung" -> openUrl("https://wildnislehrer.com/hundeschule/grundausbildung/", getString(R.string.main_page_title_grundausbildung))
            tileId == "page_hundeleinen" -> openUrl("https://wildnislehrer.com/hundeschule/hundeleinen/", getString(R.string.main_page_title_hundeleinen))
            tileId == "page_lernfoerderung" -> openUrl("https://wildnislehrer.com/hundeschule/lernfoerderunganschulen/", getString(R.string.main_link_title_lernfoerderung))
            tileId == "page_problemberatung" -> openUrl("https://wildnislehrer.com/hundeschule/problemtherapie/", getString(R.string.main_page_title_problemberatung))
            tileId == "page_seminare" -> openUrl("https://wildnislehrer.com/hundeschule/seminare/", getString(R.string.main_page_title_seminare))
            tileId == "page_teamsport" -> openUrl("https://wildnislehrer.com/hundeschule/teamsport/", getString(R.string.main_page_title_teamsport))
            tileId == "page_tierschutz" -> openUrl("https://wildnislehrer.com/hundeschule/hunde-mit-lebensgeschichte/", getString(R.string.main_link_title_tierschutz))
            tileId == "page_sozialbesuch" -> openUrl("https://wildnislehrer.com/hundeschule/tiertherapeutischersozialbesuchshundeservice/", getString(R.string.main_link_title_sozialbesuch))
            tileId == "page_wandern" -> openUrl("https://wildnislehrer.com/hundeschule/trainingswanderungen/", getString(R.string.main_link_title_wandern))
            tileId == "page_welpen" -> openUrl("https://wildnislehrer.com/hundeschule/welpenschule/", getString(R.string.main_page_title_welpen))
            tileId == "page_wildnis_in_uns" -> openUrl("https://wildnislehrer.com/wildnisschule/die-wildnis-in-uns/", getString(R.string.main_page_title_wildnis_in_uns))
            tileId == "page_spuren" -> openUrl("https://wildnislehrer.com/wildnisschule/spuren-und-zeichen/", getString(R.string.main_link_title_spuren))
            tileId == "page_handwerk" -> openUrl("https://wildnislehrer.com/wildnisschule/wildnishandwerksschule/", getString(R.string.main_link_title_handwerk))
            tileId == "page_paedagogik" -> openUrl("https://wildnislehrer.com/wildnisschule/schulwildnis/", getString(R.string.main_link_title_paedagogik))
            tileId == "page_downloads" -> openUrl("https://wildnislehrer.com/downloads/", getString(R.string.main_page_title_downloads))
            tileId == "page_honorare" -> openUrl("https://wildnislehrer.com/honorare/", getString(R.string.main_page_title_honorare))
            tileId == "page_impressum" -> openUrl("https://wildnislehrer.com/impressum/", getString(R.string.main_page_title_impressum))
            tileId == "page_agb" -> openUrl("https://wildnislehrer.com/impressum/agb/", getString(R.string.main_page_title_agb))
            tileId == "page_datenschutz" -> openUrl("https://wildnislehrer.com/datenschutzerklaerung/", getString(R.string.main_link_title_datenschutz))
            tileId == "page_info" -> openUrl("https://wildnislehrer.com/informationen/", getString(R.string.main_page_title_info))
            tileId == "page_kontakt" -> openUrl("https://wildnislehrer.com/kontakt/", getString(R.string.main_page_title_kontakt))
            tileId == "page_ueber_uns" -> openUrl("https://wildnislehrer.com/ueber-uns/", getString(R.string.main_page_title_ueber_uns))

            tileId == "course_main" -> openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/", getString(R.string.main_link_title_course_main))
            tileId.startsWith("course_") -> {
                val i = tileId.substringAfter("course_").toInt()
                val url = when(i) {
                    12 -> "https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/onlinekurs-12-junghundepraegung/"
                    else -> "https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-${String.format("%02d", i)}-${getCourseSlug(i)}/"
                }
                openUrl(url, "$i ${getCourseName(i)}")
            }
        }
    }

    private fun calculateSpanCount(referenceItemWidthDp: Int): Int {
        val screenWidthDp = resources.configuration.screenWidthDp
        val fontScale = resources.configuration.fontScale
        val effectiveItemWidthDp = referenceItemWidthDp * fontScale
        val rawSpan = (screenWidthDp / effectiveItemWidthDp).toInt()
        return if (screenWidthDp < 600) rawSpan.coerceIn(2, 2) else rawSpan.coerceIn(3, 5)
    }

    private fun setupPagesMenu() {
        // Veraltet, wird durch setupPagesGrid ersetzt
    }

    private fun setupCoursesMenu() {
        // Veraltet, wird durch setupCoursesGrid ersetzt
    }

    // Slug: stabiler URL-Bestandteil (nicht übersetzen, verweist auf deutschsprachige Website)
    private fun getCourseSlug(i: Int) = when(i) { 1 -> "grundlagen"; 2 -> "motivation"; 3 -> "korrektur"; 4 -> "harmonie"; 5 -> "mentaltraining"; 6 -> "koerpersprache"; 7 -> "sprachkommunikation"; 8 -> "sinneswahrnehmung"; 9 -> "teamarbeit"; 10 -> "routinen"; 11 -> "unfallschutz"; 13 -> "leinenarbeit"; 14 -> "unterbrechungen"; 15 -> "fuss"; 16 -> "sitz"; 17 -> "platz"; 18 -> "steh"; 19 -> "sozialisierungstechniken"; 20 -> "langleinentraining"; 21 -> "distanzunterbrechungen"; 22 -> "freilauf"; 23 -> "freifolge"; 24 -> "teamwalk"; 25 -> "scouttechniken"; 26 -> "jagdvermeidung"; 27 -> "nasenarbeit"; 28 -> "leinenfuehrigkeit"; 29 -> "begegnungssituationen"; 30 -> "stresstoleranztraining"; 31 -> "strassenroutinen"; 32 -> "hundekonflikte"; 33 -> "menschenkonflikte"; 34 -> "umweltkonflikte"; 35 -> "besitzerkonflikte"; 36 -> "tierschutzhunde"; 37 -> "hunde-mit-vorbesitzern"; 38 -> "kranke-hunde"; 39 -> "traumahunde"; 40 -> "erste-hilfe-set"; 41 -> "erste-hilfe-am-hund"; else -> "" }
    // Anzeigename: lokalisiert
    private fun getCourseName(i: Int) = when(i) {
        1 -> getString(R.string.main_course_name_01); 2 -> getString(R.string.main_course_name_02); 3 -> getString(R.string.main_course_name_03); 4 -> getString(R.string.main_course_name_04); 5 -> getString(R.string.main_course_name_05); 6 -> getString(R.string.main_course_name_06); 7 -> getString(R.string.main_course_name_07); 8 -> getString(R.string.main_course_name_08); 9 -> getString(R.string.main_course_name_09); 10 -> getString(R.string.main_course_name_10);
        11 -> getString(R.string.main_course_name_11); 12 -> getString(R.string.main_course_name_12); 13 -> getString(R.string.main_course_name_13); 14 -> getString(R.string.main_course_name_14); 15 -> getString(R.string.main_course_name_15); 16 -> getString(R.string.main_course_name_16); 17 -> getString(R.string.main_course_name_17); 18 -> getString(R.string.main_course_name_18); 19 -> getString(R.string.main_course_name_19); 20 -> getString(R.string.main_course_name_20);
        21 -> getString(R.string.main_course_name_21); 22 -> getString(R.string.main_course_name_22); 23 -> getString(R.string.main_course_name_23); 24 -> getString(R.string.main_course_name_24); 25 -> getString(R.string.main_course_name_25); 26 -> getString(R.string.main_course_name_26); 27 -> getString(R.string.main_course_name_27); 28 -> getString(R.string.main_course_name_28); 29 -> getString(R.string.main_course_name_29); 30 -> getString(R.string.main_course_name_30);
        31 -> getString(R.string.main_course_name_31); 32 -> getString(R.string.main_course_name_32); 33 -> getString(R.string.main_course_name_33); 34 -> getString(R.string.main_course_name_34); 35 -> getString(R.string.main_course_name_35); 36 -> getString(R.string.main_course_name_36); 37 -> getString(R.string.main_course_name_37); 38 -> getString(R.string.main_course_name_38); 39 -> getString(R.string.main_course_name_39); 40 -> getString(R.string.main_course_name_40);
        41 -> getString(R.string.main_course_name_41); else -> ""
    }

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
            canLoadMoreFromNetwork = cachedPosts.size >= 100
            blogAdapter.submitList(cachedPosts.take(20))
            binding.progressBar.visibility = View.GONE; binding.errorText.visibility = View.GONE
            when (currentView) { VIEW_BLOG -> showBlogView(); VIEW_PAGES -> showPagesView(); VIEW_COURSES -> showCoursesView() }
            if (contentCache.isCacheExpired()) loadContentFromNetwork(silent = true)
            return
        }
        binding.progressBar.visibility = View.VISIBLE; binding.errorText.text = getString(R.string.main_loading_content_message)
        binding.errorText.visibility = View.VISIBLE
        loadContentFromNetwork(silent = false)
    }

    private fun loadContentFromNetwork(silent: Boolean) {
        currentBlogPage = 1
        canLoadMoreFromNetwork = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val posts = try { wordPressApi.loadBlogPosts(page = 1, perPage = 100, categoryId = selectedCategoryId) } catch (e: Exception) { emptyList() }
                    posts
                }
                allBlogPosts = result
                canLoadMoreFromNetwork = result.size >= 100
                if (selectedCategoryId == null) {
                    contentCache.saveBlogPosts(result)
                }
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
                    binding.progressBar.visibility = View.GONE; binding.errorText.text = getString(R.string.main_generic_error_format, e.message); binding.errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val textColor = Color.WHITE
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
            R.id.action_settings -> { showSettingsDialog(); true }
            R.id.action_instructions -> { showInstructionsDialog(); true }
            R.id.action_permissions -> { showPermissionsDialog(); true }
            R.id.action_privacy -> { openPrivacyPolicy(); true }
            R.id.action_imprint -> { openImprint(); true }
            R.id.action_about -> { showAboutDialog(); true }
            R.id.action_home -> { startActivity(Intent(this, StartActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP }); true }
            R.id.action_backup_create -> { lifecycleScope.launch { val file = withContext(Dispatchers.IO) { BackupManager.createBackup(this@MainActivity) }; Toast.makeText(this@MainActivity, getString(R.string.main_toast_backup_created_format, file.name), Toast.LENGTH_LONG).show() }; true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showCategoryFilterDialog() {
        Log.d("MainActivity", "showCategoryFilterDialog called, categories size: ${categories.size}")
        if (categories.isEmpty()) {
            Toast.makeText(this, getString(R.string.main_toast_categories_loading), Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val result = wordPressApi.loadCategories()
                    Log.d("MainActivity", "Categories loaded: ${result.size}")
                    if (result.isNotEmpty()) {
                        categories = result
                        showCategoryFilterDialog()
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.main_toast_categories_none_found), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error loading categories", e)
                    Toast.makeText(this@MainActivity, getString(R.string.main_toast_categories_load_error), Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val categoryNames = categories.map { "${it.name} (${it.count})" }.toTypedArray()

        try {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_category_dialog_title))
                .setItems(categoryNames) { _, which ->
                    val selected = categories[which]
                    selectedCategoryId = selected.id
                    binding.btnFilterCategories.text = getString(R.string.main_category_selected_format, selected.name)
                    loadContentFromNetwork(silent = false)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
                .apply {
                }
            Log.d("MainActivity", "AlertDialog shown")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error showing AlertDialog", e)
        }
    }

    private fun showInstructionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_instructions, null)
        view.findViewById<TextView>(R.id.tvInsTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.main_button_understood), null).create()
        dialog.show()
    }

    private fun showPermissionsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_permissions, null)
        view.findViewById<TextView>(R.id.tvPermTitle).setTextColor(Color.WHITE)
        val dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(getString(R.string.ok), null).create()
        dialog.show()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)

        val titleColor = Color.WHITE
        val linkColor = Color.parseColor("#8fad6f")

        dialogView.findViewById<TextView>(R.id.tvAboutTitle).setTextColor(titleColor)
        val tvWeb = dialogView.findViewById<TextView>(R.id.tvAboutWeb)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tvAboutEmail)
        tvWeb.setTextColor(linkColor); tvEmail.setTextColor(linkColor)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        dialogView.findViewById<TextView>(R.id.tvAboutVersion).text = getString(R.string.main_about_version_format, versionName)

        tvWeb.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.wildnislehrer.com"))) }
        tvEmail.setOnClickListener { startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:hundewildnisschuleapp@online.de"); putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_about_email_subject)) }, getString(R.string.main_about_email_chooser_title))) }

        val dialog = AlertDialog.Builder(this).setView(dialogView).setPositiveButton(getString(R.string.main_button_close), null).create()
        dialog.show()
    }

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val appSettings = AppSettings(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val s1 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val s2 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val s3 = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)
        val orsEdit = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.editOrsApiKey)
        val orsLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutOrsApiKey)
        val btnClearOrs = view.findViewById<View>(R.id.btnClearOrsKey)

        orsLayout.setEndIconOnClickListener {
            showOrsInfoDialog()
        }

        btnClearOrs.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_ors_key_delete_title))
                .setMessage(getString(R.string.main_ors_key_delete_message))
                .setPositiveButton(getString(R.string.main_ors_key_delete_confirm)) { _, _ ->
                    appSettings.clearOrsApiKey()
                    orsEdit.setText("")
                    Toast.makeText(this, getString(R.string.main_toast_key_deleted), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        s1.isChecked = prefs.getBoolean("background_updates_enabled", true)
        s2.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        s3.isChecked = prefs.getBoolean("notifications_enabled", true)

        // Aktuellen Key (falls vorhanden) im Feld anzeigen (verschleiert)
        if (appSettings.hasCustomOrsKey()) {
            orsEdit.setText("********")
        }

        val color = Color.WHITE
        val csl = android.content.res.ColorStateList.valueOf(color)
        listOf(s1, s2, s3).forEach { it.thumbTintList = csl; it.trackTintList = csl.withAlpha(77) }

        s1.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_enabled", isChecked).apply(); if (isChecked) UpdateHelper.scheduleContentUpdates(this) else WorkManager.getInstance(applicationContext).cancelUniqueWork("content_updates_morning"); s2.isEnabled = isChecked; s3.isEnabled = isChecked }
        s2.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("background_updates_wifi_only", isChecked).apply(); UpdateHelper.scheduleContentUpdates(this) }
        s3.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notifications_enabled", isChecked).apply() }
        s2.isEnabled = s1.isChecked; s3.isEnabled = s1.isChecked

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_settings_dialog_title))
            .setView(view)
            .setPositiveButton(getString(R.string.main_button_done)) { _, _ ->
                val newKey = orsEdit.text?.toString() ?: ""
                if (newKey.isNotBlank() && newKey != "********") {
                    appSettings.saveOrsApiKey(newKey)
                }
            }
            .create()
        dialog.show()
    }

    private fun showOrsInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.main_ors_info_title))
            .setMessage(getString(R.string.main_ors_info_message))
            .setPositiveButton(getString(R.string.ok), null)
            .create()
            .apply {
                show()
            }
    }

    private fun openPrivacyPolicy() { openUrl("https://wildnislehrer.com/datenschutzerklaerung/", getString(R.string.main_link_title_datenschutz)) }
    private fun openImprint() { startActivity(Intent(this, ImprintActivity::class.java)) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            if (!prefs.getBoolean("notification_permission_asked", false)) {
                val dialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_notification_permission_title))
                    .setMessage(getString(R.string.main_notification_permission_message))
                    .setPositiveButton(getString(R.string.main_notification_permission_confirm)) { _, _ ->
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                    }
                    .setNegativeButton(getString(R.string.main_notification_permission_decline)) { _, _ ->
                        prefs.edit().putBoolean("notification_permission_asked", true).apply()
                        prefs.edit().putBoolean("notifications_enabled", false).apply()
                    }
                    .create()
                dialog.show()
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }
}
