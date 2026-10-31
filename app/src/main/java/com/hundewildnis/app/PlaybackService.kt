package com.hundewildnis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.hundewildnis.app.podcast.Episode
import com.hundewildnis.app.podcast.PodcastLogic
import com.hundewildnis.app.podcast.PodcastStore
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Wiedergabedienst der Podcast-Seite. Er streamt eine Folge (HTTPS) mit dem Android-[MediaPlayer] und läuft
 * als Vordergrunddienst vom Typ „mediaPlayback“ mit Benachrichtigung und Mediensitzung (Sperrbildschirm,
 * Kopfhörertasten), solange abgespielt wird. Die Oberfläche startet ihn mit [start] und steuert ihn über die
 * Bindung. Die Wiedergabeposition wird in den Podcast-Daten gemerkt.
 */
class PlaybackService : Service() {

    enum class State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    /** Die abgespielte Folge. */
    class Item(val episodeId: String, val title: String, val podcastTitle: String, val audioUrl: String)

    inner class LocalBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    var item: Item? = null
        private set
    var state: State = State.IDLE
        private set
    var speed: Float = 1.0f
        private set
    var durationMs: Int = 0
        private set

    private var player: MediaPlayer? = null
    private var prepared = false
    private var pendingStartMs = 0
    private var ticks = 0
    private var resumeOnFocusGain = false
    private var noisyRegistered = false
    private var focusRequest: AudioFocusRequest? = null
    private lateinit var session: MediaSession
    private lateinit var audioManager: AudioManager

    /** Aktuelle Position in Millisekunden. */
    val positionMs: Int
        get() = try {
            if (prepared) player?.currentPosition ?: pendingStartMs else pendingStartMs
        } catch (e: IllegalStateException) {
            pendingStartMs
        }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    // region Lebenszyklus ---------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.pod_notif_channel), NotificationManager.IMPORTANCE_LOW)
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        session = MediaSession(this, "HundeWildnisPodcast")
        @Suppress("DEPRECATION")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = resume()
            override fun onPause() = pause()
            override fun onStop() = stopPlayback()
            override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
            override fun onSkipToNext() = seekBy(FORWARD_MS)
            override fun onSkipToPrevious() = seekBy(-BACK_MS)
            override fun onFastForward() = seekBy(FORWARD_MS)
            override fun onRewind() = seekBy(-BACK_MS)
        })
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val id = intent.getStringExtra(EXTRA_ID)
                val url = intent.getStringExtra(EXTRA_URL)
                if (id != null && url != null) {
                    speed = intent.getFloatExtra(EXTRA_SPEED, speed)
                    val next = Item(id, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_PODCAST).orEmpty(), url)
                    startPlayback(next, intent.getIntExtra(EXTRA_START_MS, 0))
                }
            }
            ACTION_TOGGLE -> togglePlayPause()
            ACTION_STOP -> stopPlayback()
        }
        // Wurde der Dienst ohne Folge gestartet (z. B. nach einem Neustart), endet er sofort wieder.
        if (item == null) {
            // Wer den Dienst mit startForegroundService startet, muss startForeground aufrufen.
            if (intent?.action == ACTION_PLAY) enterForeground()
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Wird die App aus der Übersicht entfernt, endet der Dienst, wenn nichts abgespielt wird.
        if (state != State.PLAYING && state != State.LOADING) stopPlayback()
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacks(ticker)
        releasePlayer()
        abandonFocus()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    // endregion

    // region Steuerung ------------------------------------------------------------------------------

    private fun startPlayback(next: Item, startMs: Int) {
        savePosition()
        releasePlayer()
        item = next
        durationMs = 0
        pendingStartMs = startMs.coerceAtLeast(0)
        prepared = false
        session.isActive = true
        setState(State.LOADING)
        enterForeground()

        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setOnPreparedListener { onPrepared(it) }
            mp.setOnCompletionListener { onCompleted() }
            mp.setOnErrorListener { _, _, _ ->
                fail()
                true
            }
            mp.setDataSource(applicationContext, Uri.parse(next.audioUrl), mapOf("User-Agent" to AppNetwork.USER_AGENT))
            mp.prepareAsync()
        } catch (e: Exception) {
            fail()
        }
    }

    private fun onPrepared(mp: MediaPlayer) {
        if (player !== mp) return
        prepared = true
        durationMs = mp.duration.coerceAtLeast(0)
        if (pendingStartMs > 0 && (durationMs == 0 || pendingStartMs < durationMs)) mp.seekTo(pendingStartMs)
        if (!requestFocus()) {
            setState(State.PAUSED)
            return
        }
        startPlayer(mp)
    }

    private fun startPlayer(mp: MediaPlayer) {
        try {
            // Das Tempo wird unmittelbar vor dem Start gesetzt (ein Tempo über 0 startet die Wiedergabe).
            mp.playbackParams = PlaybackParams().setSpeed(speed)
            mp.start()
        } catch (e: Exception) {
            fail()
            return
        }
        registerNoisy()
        setState(State.PLAYING)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 1000)
    }

    fun togglePlayPause() {
        when (state) {
            State.PLAYING -> pause()
            State.PAUSED -> resume()
            else -> Unit
        }
    }

    fun pause() {
        val mp = player ?: return
        if (state != State.PLAYING) return
        resumeOnFocusGain = false
        try {
            mp.pause()
        } catch (e: IllegalStateException) {
            return
        }
        pendingStartMs = positionMs
        savePosition()
        handler.removeCallbacks(ticker)
        setState(State.PAUSED)
        stopForeground(false)
    }

    fun resume() {
        val mp = player ?: return
        if (state != State.PAUSED || !prepared) return
        if (!requestFocus()) return
        enterForeground()
        startPlayer(mp)
    }

    fun seekTo(ms: Int) {
        val mp = player ?: return
        if (!prepared) return
        val target = ms.coerceIn(0, if (durationMs > 0) durationMs else Int.MAX_VALUE)
        try {
            mp.seekTo(target)
        } catch (e: IllegalStateException) {
            return
        }
        pendingStartMs = target
        updateSession()
        notifyListeners()
    }

    fun seekBy(deltaMs: Int) = seekTo(positionMs + deltaMs)

    fun setSpeedValue(value: Float) {
        speed = value
        val mp = player
        if (mp != null && prepared && state == State.PLAYING) {
            try {
                mp.playbackParams = PlaybackParams().setSpeed(value)
            } catch (e: Exception) {
                // Das Tempo wird beim nächsten Start gesetzt.
            }
        }
        updateSession()
        notifyListeners()
    }

    fun stopPlayback() {
        savePosition()
        handler.removeCallbacks(ticker)
        releasePlayer()
        abandonFocus()
        item = null
        durationMs = 0
        pendingStartMs = 0
        session.isActive = false
        setState(State.IDLE)
        stopForeground(true)
        stopSelf()
    }

    private fun onCompleted() {
        val finished = item
        if (finished != null) {
            PodcastStore.updateInBackground(this) { PodcastLogic.setPlayed(it, finished.episodeId, true) }
        }
        handler.removeCallbacks(ticker)
        releasePlayer()
        abandonFocus()
        item = null
        durationMs = 0
        pendingStartMs = 0
        session.isActive = false
        setState(State.IDLE)
        stopForeground(true)
        stopSelf()
    }

    private fun fail() {
        handler.removeCallbacks(ticker)
        releasePlayer()
        abandonFocus()
        setState(State.ERROR)
        item = null
        durationMs = 0
        pendingStartMs = 0
        session.isActive = false
        setState(State.IDLE)
        stopForeground(true)
        stopSelf()
    }

    private fun releasePlayer() {
        unregisterNoisy()
        player?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            try {
                it.release()
            } catch (e: Exception) {
                // bereits freigegeben
            }
        }
        player = null
        prepared = false
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (state != State.PLAYING) return
            if (++ticks % SAVE_EVERY_TICKS == 0) savePosition()
            updateSession()
            notifyListeners()
            handler.postDelayed(this, 1000)
        }
    }

    /** Merkt die Position der laufenden Folge (nach 5 Sekunden; am Ende gilt sie als gehört). */
    private fun savePosition() {
        val current = item ?: return
        if (!prepared) return
        val position = positionMs.toLong()
        val duration = durationMs.toLong()
        PodcastStore.updateInBackground(this) { PodcastLogic.savePosition(it, current.episodeId, position, duration) }
    }

    // endregion

    // region Audiofokus und Kopfhörer ---------------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (state == State.PLAYING) {
                    pause()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(0.3f, 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(1f, 1f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: IllegalArgumentException) {
            // nicht registriert
        }
        noisyRegistered = false
    }

    // endregion

    // region Benachrichtigung und Mediensitzung -----------------------------------------------------

    private fun setState(value: State) {
        state = value
        updateSession()
        if (item != null && value != State.IDLE && value != State.ERROR) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification())
        }
        notifyListeners()
    }

    private fun enterForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val playing = state == State.PLAYING
        val toggle = Notification.Action.Builder(
            Icon.createWithResource(this, if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play),
            getString(if (playing) R.string.pod_pause else R.string.pod_play),
            serviceIntent(ACTION_TOGGLE, 1),
        ).build()
        val stop = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_media_stop),
            getString(R.string.pod_notif_stop),
            serviceIntent(ACTION_STOP, 2),
        ).build()
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, PodcastActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder
            .setSmallIcon(R.drawable.ic_notification_podcast)
            .setContentTitle(item?.title.orEmpty())
            .setContentText(if (state == State.LOADING) getString(R.string.pod_player_loading) else item?.podcastTitle.orEmpty())
            .setContentIntent(open)
            .setOngoing(playing || state == State.LOADING)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .addAction(stop)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
        return builder.build()
    }

    private fun updateSession() {
        val current = item
        if (current != null) {
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, current.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, current.podcastTitle)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs.toLong())
                    .build(),
            )
        }
        val sessionState = when (state) {
            State.PLAYING -> PlaybackState.STATE_PLAYING
            State.PAUSED -> PlaybackState.STATE_PAUSED
            State.LOADING -> PlaybackState.STATE_BUFFERING
            State.ERROR -> PlaybackState.STATE_ERROR
            State.IDLE -> PlaybackState.STATE_NONE
        }
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(sessionState, positionMs.toLong(), if (state == State.PLAYING) speed else 0f)
                .build(),
        )
    }

    // endregion

    companion object {
        const val ACTION_PLAY = "com.hundewildnis.app.podcast.PLAY"
        const val ACTION_TOGGLE = "com.hundewildnis.app.podcast.TOGGLE"
        const val ACTION_STOP = "com.hundewildnis.app.podcast.STOP"
        private const val EXTRA_ID = "id"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_PODCAST = "podcast"
        private const val EXTRA_START_MS = "start_ms"
        private const val EXTRA_SPEED = "speed"
        private const val CHANNEL_ID = "podcast_playback"
        private const val NOTIFICATION_ID = 4711
        const val BACK_MS = 10_000
        const val FORWARD_MS = 30_000
        private const val SAVE_EVERY_TICKS = 15

        /** Startet die Wiedergabe von [episode] ab [startMs]. Muss aus der sichtbaren Oberfläche heraus aufgerufen werden. */
        fun start(context: Context, episode: Episode, startMs: Long, speed: Float) {
            val intent = Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_ID, episode.id)
                .putExtra(EXTRA_URL, episode.audioUrl)
                .putExtra(EXTRA_TITLE, episode.title)
                .putExtra(EXTRA_PODCAST, episode.podcastTitle)
                .putExtra(EXTRA_START_MS, startMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                .putExtra(EXTRA_SPEED, speed)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
