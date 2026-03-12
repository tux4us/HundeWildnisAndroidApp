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
import com.example.app.SecureBaseActivity

class ContentActivity : SecureBaseActivity() {
    private lateinit var binding: ActivityContentBinding

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

                // 1) Header/Footer ausblenden (dein bisheriger Code)
                view?.evaluateJavascript("""
                    (function() {
                        var selectors = [
                            'header', 'footer', '.header', '.footer', '#header', '#footer',
                            '.td-header-wrap', '.td-footer-wrap', '.td-sub-footer-wrap',
                            '.td-footer-template-wrap', '.mobile-header-menu-wrap'
                        ];
                        selectors.forEach(function(selector) {
                            document.querySelectorAll(selector).forEach(function(el) {
                                el.style.display = 'none';
                            });
                        });
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
            android.R.id.home -> { onBackPressed(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }
}