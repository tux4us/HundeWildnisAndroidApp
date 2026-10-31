package com.hundewildnis.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.hundewildnis.app.databinding.ActivityPodcastBinding
import com.hundewildnis.app.databinding.DialogSubManageBinding
import com.hundewildnis.app.databinding.DialogUrlInputBinding
import com.hundewildnis.app.feed.FeedFetcher
import com.hundewildnis.app.feed.FeedLogic
import com.hundewildnis.app.feed.FeedParseException
import com.hundewildnis.app.feed.FeedParser
import com.hundewildnis.app.feed.FeedUrl
import com.hundewildnis.app.feed.FeedUrlResult
import com.hundewildnis.app.podcast.Episode
import com.hundewildnis.app.podcast.Podcast
import com.hundewildnis.app.podcast.PodcastData
import com.hundewildnis.app.podcast.PodcastLogic
import com.hundewildnis.app.podcast.PodcastOpml
import com.hundewildnis.app.podcast.PodcastRepository
import com.hundewildnis.app.podcast.PodcastStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Podcasts: Der Nutzer abonniert selbst Podcasts (RSS), importiert und exportiert Listen (OPML, Textliste),
 * sieht die Folgen je Podcast und die Folgen der letzten 4 Wochen aller Abos (neueste zuerst) und hört sie über
 * den [PlaybackService]. Alle Daten bleiben auf dem Gerät.
 */
class PodcastActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityPodcastBinding
    private lateinit var podcastAdapter: PodcastRowAdapter
    private lateinit var episodeAdapter: EpisodeAdapter
    private var data = PodcastData()
    private var tab = TAB_SUBS
    private var selectedPodcast: String? = null
    private var refreshJob: Job? = null
    private var manageDialog: AlertDialog? = null
    private var manageBinding: DialogSubManageBinding? = null
    private var manageAdapter: SubRowAdapter? = null

    private var service: PlaybackService? = null
    private var lastServiceState = PlaybackService.State.IDLE
    private var lastServiceItemId: String? = null
    private var seeking = false

    private val serviceListener: () -> Unit = { onServiceChanged() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? PlaybackService.LocalBinder)?.service() ?: return
            service = s
            s.addListener(serviceListener)
            onServiceChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            updatePlayer()
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(uri)
    }
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) exportTo(uri)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selectedPodcast = null
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPodcastBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FeedFetcher.userAgent = AppNetwork.USER_AGENT

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.pod_title)
        onBackPressedDispatcher.addCallback(this, backCallback)

        podcastAdapter = PodcastRowAdapter { podcast -> openPodcast(podcast) }
        episodeAdapter = EpisodeAdapter(
            onPlay = { playEpisode(it) },
            onInfo = { showInfo(it) },
            onLongClick = { toggleListened(it) },
        )
        binding.list.layoutManager = LinearLayoutManager(this)

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.pod_tab_subs))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.pod_tab_recent))
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                tab = t.position
                render()
            }

            override fun onTabUnselected(t: TabLayout.Tab) = Unit
            override fun onTabReselected(t: TabLayout.Tab) {
                if (t.position == TAB_SUBS && selectedPodcast != null) {
                    selectedPodcast = null
                    render()
                }
            }
        })

        binding.btnAdd.setOnClickListener { showAddDialog() }
        binding.btnRefresh.setOnClickListener { refreshAll() }
        binding.btnManage.setOnClickListener { showManage() }
        binding.btnHelp.setOnClickListener { showHelp() }
        binding.btnBackToList.setOnClickListener {
            selectedPodcast = null
            render()
        }

        binding.btnPlayPause.setOnClickListener { service?.togglePlayPause() }
        binding.btnBack10.setOnClickListener { service?.seekBy(-PlaybackService.BACK_MS) }
        binding.btnForward30.setOnClickListener { service?.seekBy(PlaybackService.FORWARD_MS) }
        binding.btnSpeed.setOnClickListener { changeSpeed() }
        binding.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.playerTime.text = timeText(progress, service?.durationMs ?: 0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seeking = false
                service?.seekTo(binding.seek.progress)
            }
        })

        lifecycleScope.launch {
            data = PodcastStore.load(applicationContext)
            render()
            val now = System.currentTimeMillis()
            if (data.subscriptions.isNotEmpty() && now - lastAutoRefreshMillis > AUTO_REFRESH_INTERVAL_MS) refreshAll()
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            data = PodcastStore.load(applicationContext)
            render()
        }
    }

    override fun onStop() {
        service?.removeListener(serviceListener)
        service = null
        try {
            unbindService(connection)
        } catch (e: IllegalArgumentException) {
            // war nicht gebunden
        }
        super.onStop()
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

    private fun playingId(): String? {
        val s = service ?: return null
        return if (s.state == PlaybackService.State.PLAYING) s.item?.episodeId else null
    }

    private fun render() {
        if (selectedPodcast != null && data.subscriptions.none { it.feedUrl == selectedPodcast }) selectedPodcast = null
        val hasSubs = data.subscriptions.isNotEmpty()
        binding.welcomeScroll.visibility = if (hasSubs) View.GONE else View.VISIBLE
        binding.tabs.visibility = if (hasSubs) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = hasSubs && refreshJob?.isActive != true
        backCallback.isEnabled = hasSubs && tab == TAB_SUBS && selectedPodcast != null
        binding.btnBackToList.visibility = if (backCallback.isEnabled) View.VISIBLE else View.GONE

        if (!hasSubs) {
            binding.list.visibility = View.GONE
            binding.emptyText.visibility = View.GONE
            return
        }

        val now = System.currentTimeMillis() / 1000
        val showEpisodes = tab == TAB_RECENT || selectedPodcast != null
        if (!showEpisodes) {
            if (binding.list.adapter !== podcastAdapter) binding.list.adapter = podcastAdapter
            podcastAdapter.submit(data.subscriptions, data)
            binding.list.visibility = View.VISIBLE
            binding.emptyText.visibility = View.GONE
            return
        }
        val episodes = if (tab == TAB_RECENT) PodcastLogic.recentEpisodes(data, now) else PodcastLogic.episodesOf(data, selectedPodcast.orEmpty())
        if (binding.list.adapter !== episodeAdapter) binding.list.adapter = episodeAdapter
        episodeAdapter.submit(episodes, data, withPodcastName = tab == TAB_RECENT, playingId = playingId())
        binding.list.visibility = if (episodes.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyText.visibility = if (episodes.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyText.setText(if (tab == TAB_RECENT) R.string.pod_recent_empty else R.string.pod_episodes_empty)
    }

    private fun openPodcast(podcast: Podcast) {
        selectedPodcast = podcast.feedUrl
        render()
        if (data.episodes[podcast.feedUrl].isNullOrEmpty()) refreshAll(setOf(podcast.feedUrl))
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnAdd.isEnabled = !busy
        binding.btnRefresh.isEnabled = !busy && data.subscriptions.isNotEmpty()
    }

    private fun toast(resId: Int, vararg args: Any) {
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_LONG).show()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pod_btn_help)
            .setMessage(R.string.pod_guide)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showInfo(episode: Episode) {
        val html = episode.notesHtml.replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        val text = TextView(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(this@PodcastActivity, R.color.rd_text_primary))
            movementMethod = LinkMovementMethod.getInstance()
            this.text = if (html.isBlank()) {
                getString(R.string.pod_shownotes_empty)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(html)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(episode.title)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton(R.string.common_close, null)
            .show()
    }

    private fun toggleListened(episode: Episode) {
        val played = episode.id in data.played
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(if (played) R.string.pod_mark_unplayed else R.string.pod_mark_played))) { _, _ ->
                lifecycleScope.launch {
                    data = PodcastStore.update(applicationContext) { PodcastLogic.setPlayed(it, episode.id, !played) }
                    render()
                }
            }
            .show()
    }

    // endregion

    // region Wiedergabe ---------------------------------------------------------------------------

    private fun playEpisode(episode: Episode) {
        val s = service
        if (s != null && s.item?.episodeId == episode.id && (s.state == PlaybackService.State.PLAYING || s.state == PlaybackService.State.PAUSED)) {
            s.togglePlayPause()
            return
        }
        requestNotificationPermissionOnce()
        val resume = if (episode.id in data.played) 0L else PodcastLogic.resumePosition(data, episode.id)
        PlaybackService.start(this, episode, resume, data.speed)
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun changeSpeed() {
        val next = PodcastLogic.nextSpeed(service?.speed ?: data.speed)
        service?.setSpeedValue(next)
        lifecycleScope.launch {
            data = PodcastStore.update(applicationContext) { it.copy(speed = next) }
            updatePlayer()
        }
    }

    private fun onServiceChanged() {
        val s = service ?: return
        if (s.state == PlaybackService.State.ERROR) toast(R.string.pod_playback_failed)
        val changed = s.state != lastServiceState || s.item?.episodeId != lastServiceItemId
        val reload = changed && (s.state == PlaybackService.State.PAUSED || s.state == PlaybackService.State.IDLE)
        lastServiceState = s.state
        lastServiceItemId = s.item?.episodeId
        updatePlayer()
        if (changed) {
            if (reload) {
                lifecycleScope.launch {
                    data = PodcastStore.load(applicationContext)
                    render()
                }
            } else {
                render()
            }
        }
    }

    private fun timeText(positionMs: Int, durationMs: Int): String =
        if (durationMs > 0) {
            "${PodcastLogic.formatClock(positionMs / 1000L)} / ${PodcastLogic.formatClock(durationMs / 1000L)}"
        } else {
            PodcastLogic.formatClock(positionMs / 1000L)
        }

    private fun updatePlayer() {
        val s = service
        val item = s?.item
        if (s == null || item == null || s.state == PlaybackService.State.IDLE || s.state == PlaybackService.State.ERROR) {
            binding.playerBar.visibility = View.GONE
            return
        }
        binding.playerBar.visibility = View.VISIBLE
        binding.playerTitle.text = item.title
        binding.playerSubtitle.text = item.podcastTitle
        val duration = s.durationMs
        binding.seek.max = if (duration > 0) duration else 1
        if (!seeking) {
            binding.seek.progress = s.positionMs.coerceIn(0, binding.seek.max)
            binding.playerTime.text = if (s.state == PlaybackService.State.LOADING) getString(R.string.pod_player_loading) else timeText(s.positionMs, duration)
        }
        val playing = s.state == PlaybackService.State.PLAYING
        binding.btnPlayPause.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        binding.btnPlayPause.contentDescription = getString(if (playing) R.string.pod_pause else R.string.pod_play)
        binding.btnSpeed.text = getString(R.string.pod_speed, s.speed.toString().removeSuffix(".0"))
    }

    // endregion

    // region Abrufen ------------------------------------------------------------------------------

    private suspend fun fetchOne(podcast: Podcast): Boolean = try {
        val parsed = PodcastRepository.loadFeed(podcast.feedUrl)
        PodcastStore.update(applicationContext) { current ->
            if (current.subscriptions.none { it.feedUrl == podcast.feedUrl }) current else PodcastLogic.applyFeed(current, parsed)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Aktualisiert alle Podcasts (oder nur die mit den Adressen in [only]); höchstens drei Abrufe gleichzeitig. */
    private fun refreshAll(only: Set<String>? = null) {
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch {
            setBusy(true)
            try {
                val subs = data.subscriptions.filter { only == null || it.feedUrl in only }
                val gate = Semaphore(3)
                val results = subs.map { p -> async { gate.withPermit { fetchOne(p) } } }.awaitAll()
                lastAutoRefreshMillis = System.currentTimeMillis()
                data = PodcastStore.load(applicationContext)
                val failed = results.count { !it }
                if (failed > 0) toast(R.string.pod_refresh_partial, failed, results.size)
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
        view.message.setText(R.string.pod_add_message)
        view.editUrl.hint = getString(R.string.pod_add_hint)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pod_add_title)
            .setView(view.root)
            .setPositiveButton(R.string.common_add, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when (val result = FeedUrl.parse(view.editUrl.text.toString())) {
                    is FeedUrlResult.Ok -> {
                        dialog.dismiss()
                        addPodcast(result.url)
                    }
                    FeedUrlResult.NotHttps -> toast(R.string.pod_err_not_https)
                    FeedUrlResult.Empty, FeedUrlResult.Invalid -> toast(R.string.pod_err_invalid)
                }
            }
        }
        dialog.show()
    }

    private fun addPodcast(url: String) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                val parsed = PodcastRepository.loadOrDiscover(url)
                var outcome = 0
                data = PodcastStore.update(applicationContext) { current ->
                    if (current.subscriptions.any { it.feedUrl.equals(parsed.podcast.feedUrl, ignoreCase = true) }) {
                        outcome = OUTCOME_ALREADY
                        current
                    } else {
                        val next = PodcastLogic.subscribe(current, parsed.podcast)
                        if (next == null) {
                            outcome = OUTCOME_LIMIT
                            current
                        } else {
                            outcome = OUTCOME_ADDED
                            PodcastLogic.applyFeed(next, parsed)
                        }
                    }
                }
                when (outcome) {
                    OUTCOME_ADDED -> toast(R.string.pod_added, parsed.podcast.title)
                    OUTCOME_LIMIT -> toast(R.string.pod_limit, PodcastLogic.MAX_SUBSCRIPTIONS)
                    else -> toast(R.string.pod_already)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FeedParseException) {
                toast(R.string.pod_err_not_podcast)
            } catch (e: IOException) {
                toast(R.string.pod_err_network)
            } finally {
                setBusy(false)
                render()
            }
        }
    }

    // endregion

    // region Verwalten, Import, Export ------------------------------------------------------------

    private fun rows(): List<SubRow> = data.subscriptions.map {
        SubRow(it.feedUrl, it.title.ifBlank { it.feedUrl.substringAfter("://").substringBefore('/') }, it.feedUrl.substringAfter("://"))
    }

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
            exportLauncher.launch("hundewildnis-podcasts.opml")
        }
        manageDialog = AlertDialog.Builder(this)
            .setTitle(R.string.pod_manage_title)
            .setView(view.root)
            .setPositiveButton(R.string.common_close, null)
            .show()
    }

    private fun confirmRemove(row: SubRow) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.pod_remove_confirm, row.name))
            .setPositiveButton(R.string.common_remove) { _, _ ->
                lifecycleScope.launch {
                    data = PodcastStore.update(applicationContext) { PodcastLogic.unsubscribe(it, row.key) }
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
        view.emptyText.setText(R.string.pod_manage_empty)
        view.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        view.list.layoutParams = view.list.layoutParams.apply {
            height = if (list.size > 5) (300 * resources.displayMetrics.density).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                val text = withContext(Dispatchers.IO) { readText(uri) }
                val parsed = FeedLogic.parseImport(text)
                val gate = Semaphore(3)
                val loaded = parsed.feeds.map { feed ->
                    async {
                        gate.withPermit {
                            try {
                                PodcastRepository.loadFeed(feed.url)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll()
                var added = 0
                var duplicates = 0
                var unreadable = 0
                var overLimit = 0
                data = PodcastStore.update(applicationContext) { current ->
                    var state = current
                    for (podcast in loaded) {
                        if (podcast == null) {
                            unreadable++
                        } else if (state.subscriptions.any { it.feedUrl.equals(podcast.podcast.feedUrl, ignoreCase = true) }) {
                            duplicates++
                        } else {
                            val next = PodcastLogic.subscribe(state, podcast.podcast)
                            if (next == null) {
                                overLimit++
                            } else {
                                state = PodcastLogic.applyFeed(next, podcast)
                                added++
                            }
                        }
                    }
                    state
                }
                AlertDialog.Builder(this@PodcastActivity)
                    .setMessage(
                        getString(
                            R.string.pod_import_result,
                            added,
                            duplicates,
                            parsed.skippedInsecure + parsed.skippedInvalid,
                            unreadable,
                            overLimit,
                        ),
                    )
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.pod_import_failed)
            } finally {
                setBusy(false)
                render()
            }
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
                val text = PodcastOpml.export(data.subscriptions, getString(R.string.pod_export_title))
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException("Datei nicht schreibbar")
                }
                toast(R.string.pod_export_done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.pod_export_failed)
            }
        }
    }

    // endregion

    companion object {
        private const val TAB_SUBS = 0
        private const val TAB_RECENT = 1
        private const val OUTCOME_ADDED = 1
        private const val OUTCOME_ALREADY = 2
        private const val OUTCOME_LIMIT = 3
        private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024
        private const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val PREFS = "podcast_prefs"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

        @Volatile
        private var lastAutoRefreshMillis = 0L
    }
}
