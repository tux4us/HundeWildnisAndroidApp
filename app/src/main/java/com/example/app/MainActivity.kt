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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var blogAdapter: ContentAdapter
    private lateinit var pageAdapter: ContentAdapter
    private lateinit var courseAdapter: ContentAdapter
    private val wordPressParser = WordPressParser()

    private var allBlogPosts = listOf<ContentItem>()
    private var allPages = listOf<ContentItem>()
    private var allCourses = listOf<ContentItem>()
    private var currentView = VIEW_BLOG

    companion object {
        const val VIEW_BLOG = 0
        const val VIEW_PAGES = 1
        const val VIEW_COURSES = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadThemePreference()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupRecyclerViews()
        setupButtons()
        loadContent()

        binding.swipeRefresh.setOnRefreshListener {
            refreshContent()
        }
    }

    private fun loadThemePreference() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val isDark = prefs.getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
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
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val (posts, pages, courses) = withContext(Dispatchers.IO) {
                    val posts = wordPressParser.parseBlogPosts()
                    val pages = wordPressParser.parsePages()
                    val courses = wordPressParser.parseCourses()
                    Triple(posts, pages, courses)
                }

                allBlogPosts = posts
                allPages = pages
                allCourses = courses

                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)
                courseAdapter.submitList(courses)

                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                Toast.makeText(this@MainActivity,
                    "${posts.size} Beiträge, ${pages.size} Seiten, ${courses.size} Kurse geladen",
                    Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.errorText.text = getString(R.string.error_loading, e.message)
                binding.errorText.visibility = View.VISIBLE
                Toast.makeText(this@MainActivity,
                    getString(R.string.error_loading, e.message),
                    Toast.LENGTH_LONG).show()
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
                refreshContent()
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleTheme() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val currentDark = prefs.getBoolean("dark_theme", false)
        prefs.edit().putBoolean("dark_theme", !currentDark).apply()

        AppCompatDelegate.setDefaultNightMode(
            if (!currentDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        recreate()
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
}