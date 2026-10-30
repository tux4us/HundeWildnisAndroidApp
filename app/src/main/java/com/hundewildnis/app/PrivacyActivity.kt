package com.hundewildnis.app

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityPrivacyBinding

/**
 * Zeigt die Datenschutzerklärung aus den App-Ressourcen (values / values-en: privacy_content).
 * Kein Netzwerkzugriff; Links in der Erklärung öffnen im Browser.
 */
class PrivacyActivity : SecureBaseActivity() {
    private lateinit var binding: ActivityPrivacyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_title)

        binding.privacyText.apply {
            text = Html.fromHtml(getString(R.string.privacy_content), Html.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(ContextCompat.getColor(this@PrivacyActivity, R.color.rd_group1_accent))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
