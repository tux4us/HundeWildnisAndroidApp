package com.hundewildnis.app

import android.graphics.Bitmap
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.hundewildnis.app.databinding.ActivityContentBinding
import androidx.core.view.WindowCompat
import com.example.app.SecureBaseActivity

class ContentActivity : SecureBaseActivity() {
    private lateinit var binding: ActivityContentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-Edge aktivieren
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityContentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra("url") ?: ""
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
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            loadWithOverviewMode = true
            useWideViewPort = true
            domStorageEnabled = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                val javascript = """
                    javascript:(function() {
                        var selectors = [
                            'header', 'footer', '.header', '.footer', '#header', '#footer',
                            '.td-header-wrap', '.td-footer-wrap', '.td-sub-footer-wrap',
                            '.td-footer-template-wrap', '.mobile-header-menu-wrap'
                        ];
                        selectors.forEach(function(selector) {
                            var elements = document.querySelectorAll(selector);
                            for (var i = 0; i < elements.length; i++) {
                                elements[i].style.display = 'none';
                            }
                        });
                    })();
                """
                view?.loadUrl(javascript)
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
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}