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
    private val wordPressParser = WordPressParser()

    private var allBlogPosts = listOf<ContentItem>()
    private var allPages = listOf<ContentItem>()
    private var showingBlog = true

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

        binding.blogRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.blogRecyclerView.adapter = blogAdapter

        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.pagesRecyclerView.adapter = pageAdapter
    }

    private fun setupButtons() {
        binding.btnBlog.setOnClickListener {
            showBlogView()
        }

        binding.btnPages.setOnClickListener {
            showPagesView()
        }

        binding.btnLoadMore.setOnClickListener {
            loadMoreBlogPosts()
        }

        showBlogView()
    }

    private fun showBlogView() {
        showingBlog = true
        binding.btnBlog.isSelected = true
        binding.btnPages.isSelected = false
        binding.blogRecyclerView.visibility = View.VISIBLE
        binding.pagesRecyclerView.visibility = View.GONE
        binding.btnLoadMore.visibility = View.VISIBLE
    }

    private fun showPagesView() {
        showingBlog = false
        binding.btnBlog.isSelected = false
        binding.btnPages.isSelected = true
        binding.blogRecyclerView.visibility = View.GONE
        binding.pagesRecyclerView.visibility = View.VISIBLE
        binding.btnLoadMore.visibility = View.GONE
    }

    private fun loadContent() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val (posts, pages) = withContext(Dispatchers.IO) {
                    val posts = wordPressParser.parseBlogPosts()
                    val pages = wordPressParser.parsePages()
                    Pair(posts, pages)
                }

                allBlogPosts = posts
                allPages = pages

                blogAdapter.submitList(posts.take(20))
                pageAdapter.submitList(pages)

                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                Toast.makeText(this@MainActivity,
                    "${posts.size} Beiträge, ${pages.size} Seiten geladen",
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
        if (showingBlog) {
            val filtered = if (query.isEmpty()) {
                allBlogPosts.take(blogAdapter.itemCount)
            } else {
                allBlogPosts.filter { item ->
                    item.title.contains(query, ignoreCase = true) ||
                            item.excerpt.contains(query, ignoreCase = true)
                }
            }
            blogAdapter.submitList(filtered)
        } else {
            val filtered = if (query.isEmpty()) {
                allPages
            } else {
                allPages.filter { item ->
                    item.title.contains(query, ignoreCase = true)
                }
            }
            pageAdapter.submitList(filtered)
        }
    }
}