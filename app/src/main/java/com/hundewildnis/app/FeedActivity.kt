package com.hundewildnis.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hundewildnis.app.databinding.ActivityFeedBinding
import com.hundewildnis.app.databinding.DialogSubManageBinding
import com.hundewildnis.app.databinding.DialogUrlInputBinding
import com.hundewildnis.app.feed.FeedData
import com.hundewildnis.app.feed.FeedFetcher
import com.hundewildnis.app.feed.FeedLogic
import com.hundewildnis.app.feed.FeedParseException
import com.hundewildnis.app.feed.FeedParser
import com.hundewildnis.app.feed.FeedStore
import com.hundewildnis.app.feed.FeedSub
import com.hundewildnis.app.feed.FeedUrl
import com.hundewildnis.app.feed.FeedUrlResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Feed-Leser: Der Nutzer abonniert selbst Feeds (RSS, Atom, JSON Feed), importiert und exportiert Listen (OPML,
 * Textliste), liest Artikel und markiert sie als gelesen. Alle Daten bleiben auf dem Gerät.
 */
class FeedActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityFeedBinding
    private lateinit var adapter: FeedArticleAdapter
    private var data = FeedData()
    private var selectedFeed: String? = null
    private var unreadOnly = false
    private var refreshJob: Job? = null
    private var manageDialog: AlertDialog? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(uri)
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) exportTo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FeedFetcher.userAgent = AppNetwork.USER_AGENT

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.feed_title)

        adapter = FeedArticleAdapter { article ->
            startActivity(Intent(this, FeedReaderActivity::class.java).putExtra(FeedReaderActivity.EXTRA_ARTICLE_ID, article.id))
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnRefresh.setOnClickListener { refreshAll() }
        binding.btnManage.setOnClickListener { showManage() }
        binding.btnSource.setOnClickListener { showSourceDialog() }
        binding.btnHelp.setOnClickListener { showHelp() }
        binding.checkUnreadOnly.setOnCheckedChangeListener { _, checked ->
            unreadOnly = checked
            render()
        }

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(Menu.NONE, MENU_MARK_ALL_READ, Menu.NONE, R.string.feed_mark_all_read)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != MENU_MARK_ALL_READ) return false
                lifecycleScope.launch {
                    data = FeedStore.update(applicationContext) { FeedLogic.markAllRead(it, selectedFeed) }
                    render()
                }
                return true
            }
        })

        lifecycleScope.launch {
            data = FeedStore.load(applicationContext)
            render()
            // Beim ersten Öffnen (und danach höchstens alle 30 Minuten) neue Artikel laden
            val now = System.currentTimeMillis()
            if (data.subs.isNotEmpty() && now - lastAutoRefreshMillis > AUTO_REFRESH_INTERVAL_MS) refreshAll()
        }
    }

    override fun onResume() {
        super.onResume()
        // Nach der Leseansicht: Gelesen-Markierungen neu anzeigen
        lifecycleScope.launch {
            data = FeedStore.load(applicationContext)
            render()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // region Anzeige ------------------------------------------------------------------------------

    private fun render() {
        if (selectedFeed != null && data.subs.none { it.url == selectedFeed }) selectedFeed = null
        val hasSubs = data.subs.isNotEmpty()
        binding.welcomeScroll.visibility = if (hasSubs) View.GONE else View.VISIBLE
        binding.btnRefresh.isEnabled = hasSubs
        binding.btnSource.isEnabled = hasSubs
        binding.checkUnreadOnly.isEnabled = hasSubs

        val sourceName = selectedFeed?.let { url -> data.subs.firstOrNull { it.url == url }?.let(FeedLogic::displayTitle) }
            ?: getString(R.string.feed_source_all)
        binding.btnSource.text = getString(R.string.feed_source_button, sourceName)

        val articles = if (hasSubs) FeedLogic.visible(data, selectedFeed, unreadOnly) else emptyList()
        adapter.submit(articles, data.readIds)
        binding.list.visibility = if (hasSubs && articles.isNotEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.visibility = if (hasSubs && articles.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.setText(if (unreadOnly && FeedLogic.visible(data, selectedFeed, false).isNotEmpty()) R.string.feed_empty_unread else R.string.feed_empty_list)
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnAdd.isEnabled = !busy
        binding.btnRefresh.isEnabled = !busy && data.subs.isNotEmpty()
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.feed_btn_help)
            .setMessage(R.string.feed_guide)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showSourceDialog() {
        val subs = data.subs
        val labels = ArrayList<String>()
        labels.add(getString(R.string.feed_source_item, getString(R.string.feed_source_all), FeedLogic.unreadCount(data, null)))
        subs.forEach { labels.add(getString(R.string.feed_source_item, FeedLogic.displayTitle(it), FeedLogic.unreadCount(data, it.url))) }
        AlertDialog.Builder(this)
            .setTitle(R.string.feed_source_dialog_title)
            .setItems(labels.toTypedArray()) { _, which ->
                selectedFeed = if (which == 0) null else subs[which - 1].url
                render()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // endregion

    // region Abrufen ------------------------------------------------------------------------------

    /** Lädt einen Feed und übernimmt ihn. `true` bei Erfolg. */
    private suspend fun fetchOne(sub: FeedSub): Boolean = try {
        val parsed = FeedFetcher.loadFeed(sub.url)
        FeedStore.update(applicationContext) { FeedLogic.applyFeed(it, sub.url, parsed, System.currentTimeMillis() / 1000) }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Aktualisiert alle Feeds (oder nur die mit den Adressen in [only]); höchstens drei Abrufe gleichzeitig. */
    private fun refreshAll(only: Set<String>? = null) {
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            setBusy(true)
            try {
                val subs = data.subs.filter { only == null || it.url in only }
                val gate = Semaphore(3)
                val results = subs.map { sub -> async { gate.withPermit { fetchOne(sub) } } }.awaitAll()
                lastAutoRefreshMillis = System.currentTimeMillis()
                data = FeedStore.load(applicationContext)
                val failed = results.count { !it }
                if (failed > 0) toast(R.string.feed_refresh_partial, failed, results.size)
            } finally {
                setBusy(false)
                render()
            }
        }
    }

    // endregion

    // region Hinzufügen ---------------------------------------------------------------------------

    private fun showAddDialog() {
        val view = DialogUrlInputBinding.inflate(layoutInflater)
        view.message.setText(R.string.feed_add_message)
        view.editUrl.hint = getString(R.string.feed_add_hint)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.feed_add_title)
            .setView(view.root)
            .setPositiveButton(R.string.common_add, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (val result = FeedUrl.parse(view.editUrl.text.toString())) {
                    is FeedUrlResult.Ok -> {
                        dialog.dismiss()
                        addFeed(result.url)
                    }
                    FeedUrlResult.NotHttps -> toast(R.string.feed_err_not_https)
                    FeedUrlResult.Empty, FeedUrlResult.Invalid -> toast(R.string.feed_err_invalid)
                }
            }
        }
        dialog.show()
    }

    private fun addFeed(url: String) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                val (feedUrl, parsed) = FeedFetcher.loadOrDiscover(url)
                var outcome: FeedLogic.AddResult? = null
                data = FeedStore.update(applicationContext) { current ->
                    val added = FeedLogic.addSubs(current, listOf(FeedSub(feedUrl, parsed.title)))
                    outcome = added
                    if (added.added > 0) FeedLogic.applyFeed(added.data, feedUrl, parsed, System.currentTimeMillis() / 1000) else current
                }
                val r = outcome
                when {
                    r == null -> Unit
                    r.added > 0 -> toast(R.string.feed_added, parsed.title.ifBlank { feedUrl.substringAfter("://").substringBefore('/') })
                    r.overLimit > 0 -> toast(R.string.feed_limit, FeedLogic.MAX_SUBSCRIPTIONS)
                    else -> toast(R.string.feed_already)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FeedParseException) {
                toast(R.string.feed_err_not_feed)
            } catch (e: IOException) {
                toast(R.string.feed_err_network)
            } finally {
                setBusy(false)
                render()
            }
        }
    }

    // endregion

    // region Verwalten, Import, Export ------------------------------------------------------------

    private fun rows(): List<SubRow> = data.subs.map { SubRow(it.url, FeedLogic.displayTitle(it), it.url.substringAfter("://")) }

    private var manageBinding: DialogSubManageBinding? = null
    private var manageAdapter: SubRowAdapter? = null

    private fun showManage() {
        val view = DialogSubManageBinding.inflate(layoutInflater)
        manageBinding = view
        val rowAdapter = SubRowAdapter(rows()) { row -> confirmRemove(row) }
        manageAdapter = rowAdapter
        view.list.layoutManager = LinearLayoutManager(this)
        view.list.adapter = rowAdapter
        updateManage()
        view.btnImport.setOnClickListener {
            manageDialog?.dismiss()
            importLauncher.launch(arrayOf("*/*"))
        }
        view.btnExport.setOnClickListener {
            manageDialog?.dismiss()
            exportLauncher.launch("hundewildnis-feeds.opml")
        }
        manageDialog = AlertDialog.Builder(this)
            .setTitle(R.string.feed_manage_title)
            .setView(view.root)
            .setPositiveButton(R.string.common_close, null)
            .show()
    }

    private fun confirmRemove(row: SubRow) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.feed_remove_confirm, row.name))
            .setPositiveButton(R.string.common_remove) { _, _ ->
                lifecycleScope.launch {
                    data = FeedStore.update(applicationContext) { FeedLogic.removeSub(it, row.key) }
                    render()
                    updateManage()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateManage() {
        val view = manageBinding ?: return
        val list = rows()
        manageAdapter?.submit(list)
        view.emptyText.setText(R.string.feed_manage_empty)
        view.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        view.list.layoutParams = view.list.layoutParams.apply {
            height = if (list.size > 5) (300 * resources.displayMetrics.density).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            var refreshAfter: Set<String>? = null
            setBusy(true)
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readText(uri) }
                val parsed = FeedLogic.parseImport(text)
                var outcome: FeedLogic.AddResult? = null
                data = FeedStore.update(applicationContext) { current ->
                    val added = FeedLogic.addSubs(current, parsed.feeds.map { FeedSub(it.url, it.title) })
                    outcome = added
                    added.data
                }
                val r = outcome ?: return@launch
                val newUrls = parsed.feeds.map { it.url }.toSet()
                AlertDialog.Builder(this@FeedActivity)
                    .setMessage(getString(R.string.feed_import_result, r.added, r.duplicates, parsed.skippedInsecure + parsed.skippedInvalid, r.overLimit))
                    .setPositiveButton(R.string.ok, null)
                    .show()
                if (r.added > 0) refreshAfter = newUrls
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.feed_import_failed)
            } finally {
                setBusy(false)
                render()
            }
            // Neue Feeds erst nach dem Import laden (eigener Ladevorgang mit eigener Anzeige)
            refreshAfter?.let { refreshAll(it) }
        }
    }

    /** Liest die Datei als Text (höchstens 4 MB). */
    private fun readText(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_IMPORT_BYTES) throw IOException("Datei zu groß")
            }
            out.toByteArray()
        } ?: throw IOException("Datei nicht lesbar")
        return FeedParser.decode(bytes, null)
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = FeedLogic.exportOpml(data.subs, getString(R.string.feed_export_title))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("Datei nicht schreibbar")
                }
                toast(R.string.feed_export_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.feed_export_failed)
            }
        }
    }

    // endregion

    companion object {
        private const val MENU_MARK_ALL_READ = 9001
        private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024
        private const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L

        @Volatile
        private var lastAutoRefreshMillis = 0L
    }
}
