package com.hundewildnis.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.hundewildnis.app.databinding.ActivityContentBinding
import androidx.core.view.WindowCompat
import com.hundewildnis.app.SecureBaseActivity

class ContentActivity : SecureBaseActivity() {
    private lateinit var binding: ActivityContentBinding

    // ✅ NEU: Quelle merken (woher kommt der User?)
    private var sourceView: Int = MainActivity.VIEW_BLOG

    companion object {
        const val EXTRA_SOURCE_VIEW = "source_view"
    }

    // ── Share-Interface ──────────────────────────────────────────────────────
    inner class AndroidShareInterface {
        @JavascriptInterface
        fun share(title: String, text: String, url: String) {
            val shareText = buildString {
                if (title.isNotEmpty()) append("$title\n\n")
                if (text.isNotEmpty())  append("$text\n\n")
                if (url.isNotEmpty())   append(url)
            }.trim()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                if (title.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, title)
            }
            // runOnUiThread nötig, da @JavascriptInterface auf einem Background-Thread läuft
            runOnUiThread {
                startActivity(Intent.createChooser(intent, "Teilen via"))
            }
        }
    }
    // ────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url   = intent.getStringExtra("url")   ?: ""
        val title = intent.getStringExtra("title") ?: ""

        // ✅ NEU: Quelle merken (Blog/Pages/Courses)
        sourceView = intent.getIntExtra(EXTRA_SOURCE_VIEW, MainActivity.VIEW_BLOG)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        supportActionBar?.title = title

        setupWebView()
        loadContent(url)
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled    = true
            builtInZoomControls  = true
            displayZoomControls  = false
            setSupportZoom(true)
            loadWithOverviewMode = true
            useWideViewPort      = true
            domStorageEnabled    = true
        }

        // Interface registrieren – Name "AndroidBridge" muss mit dem JS unten übereinstimmen
        binding.webView.addJavascriptInterface(AndroidShareInterface(), "AndroidBridge")

        binding.webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE

                // ✅ NUR Header, Footer, Navigation - NICHT den Content!
                view?.evaluateJavascript("""
                    (function() {
                        // ✅ PRÄZISE: Nur diese Elemente verstecken
                        var hideSelectors = [
                            // WordPress Admin Bar
                            '#wpadminbar',
                            
                            // ShiftNav (das Hamburger-Menü Plugin)
                            '#shiftnav-toggle-main',
                            '.shiftnav-toggle',
                            '.shiftnav',
                            
                            // Header & Navigation
                            '#masthead',
                            '.site-header',
                            '.site-header-main',
                            '#site-header-menu',
                            '#primary-menu-wrapper',
                            '#site-navigation',
                            '.main-navigation',
                            '#menu-toggle',
                            '.menu-toggle',
                            '.menu-toggle-wrapper',
                            
                            // Footer
                            '#colophon',
                            '.site-footer',
                            '#site-generator',
                            '.site-info',
                            
                            // Scroll-up Button
                            '#scrollup',
                            '.backtotop',
                            
                            // Breadcrumbs
                            '.breadcrumb',
                            '.breadcrumbs'
                        ];
                        
                        hideSelectors.forEach(function(selector) {
                            try {
                                document.querySelectorAll(selector).forEach(function(el) {
                                    el.style.display = 'none';
                                    el.style.visibility = 'hidden';
                                });
                            } catch(e) {}
                        });
                        
                        // Body-Padding anpassen
                        document.body.style.paddingTop = '0';
                        document.body.style.marginTop = '0';
                    })();
                """.trimIndent(), null)

                // 2) navigator.share durch Android-Bridge ersetzen
                view?.evaluateJavascript("""
                    (function() {
                        navigator.share = function(data) {
                            var title = (data && data.title) ? data.title : '';
                            var text  = (data && data.text)  ? data.text  : '';
                            var url   = (data && data.url)   ? data.url   : '';
                            AndroidBridge.share(title, text, url);
                            return Promise.resolve();
                        };
                        // Damit feature-detection auf der Seite positiv ausfällt
                        Object.defineProperty(navigator, 'canShare', {
                            value: function() { return true; },
                            writable: false
                        });
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun loadContent(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // ✅ GEÄNDERT: Nicht mehr onBackPressed(), sondern direkt zurück
                returnToMainActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        // ✅ GEÄNDERT: IMMER zurück zur MainActivity, nie WebView-History
        returnToMainActivity()
    }

    // ✅ NEU: Zurück zur MainActivity mit richtiger View
    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Zur richtigen Ansicht zurück (Blog/Pages/Courses)
            putExtra("INITIAL_VIEW", sourceView)
            // Alle vorherigen Activities aus dem Stack entfernen
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}
