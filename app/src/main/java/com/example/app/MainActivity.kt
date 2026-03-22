package com.hundewildnis.app

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
import android.view.Gravity
import com.hundewildnis.app.LocationActivity
import com.hundewildnis.app.SecureBaseActivity
import com.hundewildnis.app.ContentUpdateWorker

class MainActivity : BaseBottomNavActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter
    private lateinit var youtubeAdapter: YouTubeAdapter

    private val wordPressApi = WordPressApiParser()
    private val youtubeParser = YouTubeParser()
    private lateinit var contentCache: ContentCache

    private var allBlogPosts = listOf<ContentItem>()
    private var allYouTubeVideos = listOf<YouTubeVideo>()

    private var currentView = VIEW_BLOG

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
        const val VIEW_YOUTUBE = 3
        const val VIEW_PODCASTS = 4
    }

    // BaseBottomNavActivity - Abstract Methoden
    override fun getBottomNavigationView() = binding.bottomNavigation

    override fun getCurrentNavigationItemId() = when (currentView) {
        VIEW_BLOG -> R.id.nav_blog
        VIEW_PAGES -> R.id.nav_pages
        VIEW_COURSES -> R.id.nav_courses
        VIEW_YOUTUBE -> R.id.nav_blog  // YouTube unter "Mehr", zeige Blog-Tab
        else -> R.id.nav_blog
    }

    // BaseBottomNavActivity - Callbacks überschreiben
    override fun onBlogSelected() {
        showBlogView()
    }

    override fun onPagesSelected() {
        showPagesView()
    }

    override fun onCoursesSelected() {
        showCoursesView()
    }

    override fun onYouTubeSelected() {
        showYouTubeView()
    }

    override fun onHomeSelected() {
        val intent = Intent(this, StartActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
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

                // Blog RecyclerView Padding
                binding.blogRecyclerView.setPadding(
                    binding.blogRecyclerView.paddingLeft,
                    binding.blogRecyclerView.paddingTop,
                    binding.blogRecyclerView.paddingRight,
                    totalBottomPadding
                )

                // YouTube RecyclerView Padding
                binding.youtubeRecyclerView.setPadding(
                    binding.youtubeRecyclerView.paddingLeft,
                    binding.youtubeRecyclerView.paddingTop,
                    binding.youtubeRecyclerView.paddingRight,
                    totalBottomPadding
                )

                // Pages Menu - Padding auf LinearLayout INNERHALB des ScrollView
                findViewById<View>(R.id.pagesMenu)?.let { scrollView ->
                    val linearLayout = (scrollView as? android.widget.ScrollView)?.getChildAt(0)
                    linearLayout?.setPadding(
                        linearLayout.paddingLeft,
                        linearLayout.paddingTop,
                        linearLayout.paddingRight,
                        totalBottomPadding
                    )
                }

                // Courses Menu - Padding auf LinearLayout INNERHALB des ScrollView
                findViewById<View>(R.id.coursesMenu)?.let { scrollView ->
                    val linearLayout = (scrollView as? android.widget.ScrollView)?.getChildAt(0)
                    linearLayout?.setPadding(
                        linearLayout.paddingLeft,
                        linearLayout.paddingTop,
                        linearLayout.paddingRight,
                        totalBottomPadding
                    )
                }

                // Load More Button Margin
                val buttonParams = binding.btnLoadMore.layoutParams as FrameLayout.LayoutParams
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
        setupPagesMenu()
        setupCoursesMenu()

        // Initialen View NACH dem Setup setzen
        val initialView = intent.getIntExtra("INITIAL_VIEW", VIEW_BLOG)
        when (initialView) {
            VIEW_BLOG -> showBlogView()
            VIEW_PAGES -> showPagesView()
            VIEW_COURSES -> showCoursesView()
            VIEW_YOUTUBE -> showYouTubeView()
        }

        binding.bottomNavigation.selectedItemId = when (initialView) {
            VIEW_PAGES -> R.id.nav_pages
            VIEW_COURSES -> R.id.nav_courses
            VIEW_YOUTUBE -> R.id.nav_blog
            else -> R.id.nav_blog
        }

        loadContent()
        scheduleContentUpdates()
        requestNotificationPermission()
    }


    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupRecyclerViews() {
        blogAdapter = ContentAdapter { item -> openContent(item) }
        youtubeAdapter = YouTubeAdapter { video -> openYouTubeVideo(video) }

        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter

        binding.youtubeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.youtubeRecyclerView.adapter = youtubeAdapter
    }

    private fun setupButtons() {
        // Bottom Navigation wird von BaseBottomNavActivity gehandhabt
        super.setupBottomNavigation()

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
    }

    private fun showBlogView() {
        currentView = VIEW_BLOG
        binding.blogRecyclerView.visibility = View.VISIBLE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
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
        findViewById<View>(R.id.pagesMenu).visibility = View.VISIBLE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showCoursesView() {
        currentView = VIEW_COURSES
        binding.blogRecyclerView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.VISIBLE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun showYouTubeView() {
        currentView = VIEW_YOUTUBE
        binding.blogRecyclerView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun setupPagesMenu() {
        // HUNDESCHULE - Hauptseite
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHundeWildnisSchule)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/", "HundeWildnis Schule")
        }

        // HUNDESCHULE
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAntiJagdTraining)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/anti-jagd-training/", "Anti-Jagd-Training")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAusbildungAssistenzhunde)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/ausbildung-von-paedagogischen-assistenzbegleithunden/", "Ausbildung Assistenzhunde")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnBeratung)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/beratung/", "Beratung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFahrradtraining)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/fahrradtraining/", "Fahrradtraining")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFaehrteUndSuche)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/faehrte-und-suche/", "Fährte und Suche")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGedankenUndGrundlagen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/gedanken-und-grundlagen/", "Gedanken und Grundlagen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGrundausbildung)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/grundausbildung/", "Grundausbildung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHundeleinen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/hundeleinen/", "Hundeleinen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLernfoerderungSchulen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/lernfoerderunganschulen/", "Lernförderung an Schulen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnProblemberatung)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/problemtherapie/", "Problemberatung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSeminare)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/seminare/", "Seminare")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTeamsport)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/teamsport/", "Teamsport")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTierschutzHunde)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/hunde-mit-lebensgeschichte/", "Tierschutz Hunde")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTiertherapeutischerSozialbesuchshundeservice)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/tiertherapeutischersozialbesuchshundeservice/", "Tiertherapeutischer Service")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWandernZumGutenZweck)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/trainingswanderungen/", "Wandern zum guten Zweck")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWelpenschule)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/hundeschule/welpenschule/", "Welpenschule")
        }

        // WILDNISSCHULE
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWildnisInUns)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/wildnisschule/die-wildnis-in-uns/", "Die Wildnis in uns")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSpurenUndZeichen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/wildnisschule/spuren-und-zeichen/", "Spuren und Zeichen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWildnishandwerkSchulen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/wildnisschule/wildnishandwerksschule/", "Wildnishandwerk für Schulen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWildnispaedagogikSchulen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/wildnisschule/schulwildnis/", "Wildnispädagogik für Schulen")
        }

        // WEITERE INFORMATIONEN
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDownloads)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/downloads/", "Downloads")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnHonorare)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/honorare/", "Honorare")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnImpressum)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/impressum/", "Impressum")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAGBs)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/impressum/agb/", "AGB")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDatenschutz)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/datenschutzerklaerung/", "Datenschutzerklärung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnInformationen)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/informationen/", "Informationen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnKontakt)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/kontakt/", "Kontakt")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUeberUns)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/ueber-uns/", "Über Uns")
        }
    }

    private fun setupCoursesMenu() {
        // Hauptseite & Verzeichnis
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCoursesMain)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/", "Onlinekurse Hundeschule")
        }

        //binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCoursesDirectory)?.setOnClickListener {
        //    openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/", "Kursverzeichnis")
        //}

        // Kurse
        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse01)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-01-grundlagen/", "01 Grundlagen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse02)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-02-motivation/", "02 Motivation")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse03)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-03-korrektur/", "03 Korrektur")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse04)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-04-harmonie/", "04 Harmonie")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse05)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-05-mentaltraining/", "05 Mentaltraining")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse06)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-06-koerpersprache/", "06 Körpersprache")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse07)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-07-sprachkommunikation/", "07 Sprachkommunikation")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse08)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-08-sinneswahrnehmung/", "08 Sinneswahrnehmung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse09)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-09-teamarbeit/", "09 Teamarbeit")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse10)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-10-routinen/", "10 Routinen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse11)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-11-unfallschutz/", "11 Unfallschutz")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse12)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/kursverzeichnis-hundeschule/onlinekurs-12-junghundepraegung/", "12 Junghundeprägung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse13)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-13-leinenarbeit/", "13 Leinenarbeit")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse14)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-14-unterbrechungen/", "14 Unterbrechungen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse15)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-15-fuss/", "15 Fuss")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse16)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-16-sitz/", "16 Sitz")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse17)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-17-platz/", "17 Platz")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse18)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-18-steh/", "18 Steh")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse19)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-19-sozialisierungstechniken/", "19 Sozialisierungstechniken")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse20)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-20-langleinentraining/", "20 Langleinentraining")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse21)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-21-distanzunterbrechungen/", "21 Distanzunterbrechungen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse22)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-22-freilauf/", "22 Freilauf")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse23)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-23-freifolge/", "23 Freifolge")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse24)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-24-teamwalk/", "24 Teamwalk")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse25)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-25-scouttechniken/", "25 Scouttechniken")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse26)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-26-jagdvermeidung/", "26 Jagdvermeidung")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse27)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-27-nasenarbeit/", "27 Nasenarbeit")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse28)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-28-leinenfuehrigkeit/", "28 Leinenfühigkeit")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse29)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-29-begegnungssituationen/", "29 Begegnungssituationen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse30)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-30-stresstoleranztraining/", "30 Stresstoleranztraining")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse31)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-31-strassenroutinen/", "31 Strassenroutinen")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse32)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-32-hundekonflikte/", "32 Hundekonflikte")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse33)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-33-menschenkonflikte/", "33 Menschenkonflikte")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse34)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-34-umweltkonflikte/", "34 Umweltkonflikte")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse35)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-35-besitzerkonflikte/", "35 Besitzerkonflikte")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse36)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-36-tierschutzhunde/", "36 Tierschutzhunde")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse37)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-37-hunde-mit-vorbesitzern/", "37 Hunde mit Vorbesitzern")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse38)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-38-kranke-hunde/", "38 Kranke Hunde")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse39)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-39-traumahunde/", "39 Traumahunde")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse40)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-40-erste-hilfe-set/", "40 Erste Hilfe Set")
        }

        binding.root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCourse41)?.setOnClickListener {
            openUrl("https://wildnislehrer.com/onlinekurse-hundeschule/onlinekurs-41-erste-hilfe-am-hund/", "41 Erste Hilfe am Hund")
        }
    }

    private fun openUrl(url: String, title: String) {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
        }
        startActivity(intent)
    }

    private fun loadContent() {
        val cachedPosts = contentCache.getBlogPosts()
        val cachedYouTube = contentCache.getYouTubeVideos()

        val hasCache = cachedPosts != null && cachedYouTube != null
        val isCacheExpired = contentCache.isCacheExpired()

        if (hasCache) {
            Log.d("MainActivity", "📦 Zeige Cache sofort")

            allBlogPosts = cachedPosts!!
            allYouTubeVideos = cachedYouTube!!

            blogAdapter.submitList(cachedPosts.take(20))
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
                Toast.makeText(this, "Aktualisiere Inhalte...", Toast.LENGTH_SHORT).show()
                loadContentFromNetwork(silent = true)
            } else {
                Toast.makeText(this, "Inhalte geladen", Toast.LENGTH_SHORT).show()
            }
            return
        }

        Log.d("MainActivity", "🌐 Erstmaliges Laden...")
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.text = "Lade Inhalte...\n\n⏳ Circa 5 Sekunden.\n\nDanach startet die App sofort! 🚀"
        binding.errorText.setTextColor(Color.parseColor("#3f6f1e"))
        binding.errorText.gravity = Gravity.CENTER_HORIZONTAL
        binding.errorText.visibility = View.VISIBLE

        binding.blogRecyclerView.visibility = View.GONE
        findViewById<View>(R.id.pagesMenu).visibility = View.GONE
        findViewById<View>(R.id.coursesMenu).visibility = View.GONE
        binding.youtubeRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.GONE

        loadContentFromNetwork(silent = false)
    }

    private fun loadContentFromNetwork(silent: Boolean) {
        lifecycleScope.launch {
            try {
                val totalStartTime = System.currentTimeMillis()
                Log.d("MainActivity", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("MainActivity", "⏱️ START Laden")

                val result = withContext(Dispatchers.IO) {
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
                        Log.d("MainActivity", "✓ Blog: ${result.size} Posts in ${duration}ms")
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

                    val posts = blogDeferred.await()
                    val videos = youtubeDeferred.await()

                    Pair(posts, videos)
                }

                val posts = result.first
                val videos = result.second

                contentCache.saveBlogPosts(posts)
                contentCache.saveYouTubeVideos(videos)

                allBlogPosts = posts
                allYouTubeVideos = videos

                blogAdapter.submitList(posts.take(20))
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
                        "${posts.size} Beiträge, ${videos.size} Videos geladen",
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
                    binding.errorText.text = "Fehler: ${e.message}"
                    binding.errorText.setTextColor(Color.RED)
                    binding.errorText.visibility = View.VISIBLE

                    Toast.makeText(this@MainActivity,
                        "Fehler: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.videoId}"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Öffnen des Videos", e)
            Toast.makeText(this, "Konnte Video nicht öffnen", Toast.LENGTH_LONG).show()
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

        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightMode == Configuration.UI_MODE_NIGHT_YES

        val textColor = if (isDarkMode) Color.WHITE else Color.BLACK

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val spanString = SpannableString(item.title.toString())
            spanString.setSpan(ForegroundColorSpan(textColor), 0, spanString.length, 0)
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
        Log.d("MainActivity", "Menu geklickt: ${item.itemId} / ${item.title}")

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
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_test_update -> {
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
            R.id.action_home -> {
                val intent = Intent(this, StartActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
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

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)

        val switchBackgroundUpdates = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBackgroundUpdates)
        val switchWifiOnly = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchWifiOnly)
        val switchNotifications = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchNotifications)

        switchBackgroundUpdates.isChecked = prefs.getBoolean("background_updates_enabled", true)
        switchWifiOnly.isChecked = prefs.getBoolean("background_updates_wifi_only", false)
        switchNotifications.isChecked = prefs.getBoolean("notifications_enabled", true)

        val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
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

        val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(buttonColor)
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(this, ContentActivity::class.java).apply {
            putExtra("url", "https://wildnislehrer.com/datenschutzerklaerung/")
            putExtra("title", "Datenschutzerklärung")
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
        }
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
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
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

        val morningDelay = calculateDelayUntil(8)
        val morningRequest = PeriodicWorkRequestBuilder<ContentUpdateWorker>(
            12, TimeUnit.HOURS,
            1, TimeUnit.HOURS
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

    private fun calculateDelayUntil(targetHour: Int): Long {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(currentDate)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return dueDate.timeInMillis - currentDate.timeInMillis
    }

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

                    val isDarkTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                            Configuration.UI_MODE_NIGHT_YES

                    val buttonColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

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
}

