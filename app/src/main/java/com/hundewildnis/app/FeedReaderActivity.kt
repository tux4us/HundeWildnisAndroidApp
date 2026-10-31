package com.hundewildnis.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityFeedReaderBinding
import com.hundewildnis.app.feed.FeedArticle
import com.hundewildnis.app.feed.FeedHtml
import com.hundewildnis.app.feed.FeedLogic
import com.hundewildnis.app.feed.FeedStore
import com.hundewildnis.app.feed.ReaderColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * Leseansicht eines Artikels. Der Artikel ist fremder Inhalt: Die WebView läuft ohne JavaScript, ohne Zugriff auf
 * Dateien und Speicher, mit Content-Security-Policy; Links öffnen im Browser.
 */
class FeedReaderActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityFeedReaderBinding
    private var article: FeedArticle? = null
    private var loadImages = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.feed_reader_title)

        loadImages = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOAD_IMAGES, true)

        binding.webView.apply {
            setBackgroundColor(Color.parseColor(BACKGROUND))
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                setSupportZoom(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.isForMainFrame) openExternally(request.url)
                    return true
                }
            }
        }

        binding.btnBrowser.setOnClickListener { article?.url?.let { openExternally(Uri.parse(it)) } }
        binding.btnImages.setOnClickListener {
            loadImages = !loadImages
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOAD_IMAGES, loadImages).apply()
            showArticle()
        }
        binding.btnUnread.setOnClickListener {
            val id = article?.id ?: return@setOnClickListener
            lifecycleScope.launch {
                FeedStore.update(applicationContext) { FeedLogic.markRead(it, id, read = false) }
                finish()
            }
        }

        val id = intent.getStringExtra(EXTRA_ARTICLE_ID)
        lifecycleScope.launch {
            val data = FeedStore.load(applicationContext)
            article = data.articles.firstOrNull { it.id == id }
            val current = article
            if (current == null) {
                Toast.makeText(this@FeedReaderActivity, R.string.feed_reader_missing, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            FeedStore.update(applicationContext) { FeedLogic.markRead(it, current.id) }
            showArticle()
        }
    }

    private fun showArticle() {
        val current = article ?: return
        binding.btnImages.setText(if (loadImages) R.string.feed_reader_images_on else R.string.feed_reader_images_off)
        binding.btnBrowser.isEnabled = current.url != null
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(current.publishedSeconds * 1000))
        val meta = listOfNotNull(current.feedTitle.ifBlank { null }, current.author?.ifBlank { null }, date).joinToString(" · ")
        val document = FeedHtml.readerDocument(
            title = current.title,
            meta = meta,
            bodyHtml = current.body,
            colors = ReaderColors(BACKGROUND, "#E8E6E3", "#9AA0A6", "#C9A26A"),
            loadImages = loadImages,
        )
        // Relative Adressen im Artikel beziehen sich auf die Seite des Artikels (nur HTTPS)
        val baseUrl = current.url?.takeIf { it.startsWith("https://", ignoreCase = true) }
        binding.webView.settings.blockNetworkImage = !loadImages
        binding.webView.loadDataWithBaseURL(baseUrl, document, "text/html", "utf-8", null)
    }

    private fun openExternally(uri: Uri) {
        if (uri.scheme?.lowercase() !in setOf("http", "https", "mailto")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.feed_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            (parent as? android.view.ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ARTICLE_ID = "article_id"
        private const val PREFS = "feed_prefs"
        private const val KEY_LOAD_IMAGES = "load_images"
        private const val BACKGROUND = "#0D0E10"
    }
}
