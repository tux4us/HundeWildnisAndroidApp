package com.hundewildnis.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.hundewildnis.app.databinding.ActivityCommandListBinding
import com.hundewildnis.app.databinding.ItemVoiceCommandBinding

/**
 * Verwaltung der Kommandos des Sprach-Trainers.
 *
 * Der Hundeführer legt Kommandos an und spricht jedes mindestens
 * [VoiceCommandStore.REQUIRED_SAMPLES]-mal selbst ein. Die Aufnahmen bleiben lokal und bilden
 * die Referenzdatenbank für die spätere Erkennung während der Messung.
 */
class CommandListActivity : SecureBaseActivity() {

    private lateinit var binding: ActivityCommandListBinding
    private var commands = mutableListOf<VoiceCommand>()

    private val handler = Handler(Looper.getMainLooper())

    // Aufnahme
    private var recorder: CommandRecorder? = null
    private var recordDialog: AlertDialog? = null
    private var recordStatus: TextView? = null
    private var recordLevel: ProgressBar? = null
    private var pendingRecordId: String? = null

    // Wiedergabe
    private var audioTrack: AudioTrack? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            commands.find { it.id == pendingRecordId }?.let { startRecordingFor(it) }
        } else {
            Toast.makeText(this, R.string.cmd_mic_required, Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.cmd_toolbar_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        binding.btnAddCommand.setOnClickListener {
            askForName(R.string.cmd_dialog_add_title, "", null) { name ->
                commands.add(VoiceCommand(name = name))
                persistAndRender()
            }
        }
        binding.btnLoadDefaults.setOnClickListener { addDefaultCommands() }

        commands = VoiceCommandStore.load(this)
        render()
    }

    override fun onStop() {
        // Das Mikrofon darf im Hintergrund nicht offen bleiben
        cancelRecording()
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        cancelRecording()
        stopPlayback()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Liste
    // ─────────────────────────────────────────────────────────────

    private fun persistAndRender() {
        VoiceCommandStore.save(this, commands)
        render()
    }

    private fun render() {
        binding.listCommands.removeAllViews()
        binding.tvEmpty.visibility = if (commands.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        for (command in commands) {
            val row = ItemVoiceCommandBinding.inflate(layoutInflater, binding.listCommands, false)
            val count = command.samples.size
            val complete = count >= VoiceCommandStore.REQUIRED_SAMPLES

            row.tvCommandName.text = command.name
            row.tvSampleCount.text = getString(
                R.string.cmd_samples_format, count, VoiceCommandStore.REQUIRED_SAMPLES
            ) + if (complete) " ✓" else ""
            row.btnRecord.text = getString(
                if (complete) R.string.cmd_btn_record_more else R.string.cmd_btn_record
            )
            row.btnPlay.isEnabled = count > 0

            row.btnRecord.setOnClickListener {
                if (count >= VoiceCommandStore.MAX_SAMPLES) {
                    Toast.makeText(
                        this,
                        getString(R.string.cmd_max_samples, VoiceCommandStore.MAX_SAMPLES),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    requestRecording(command)
                }
            }
            row.btnPlay.setOnClickListener { play(command) }
            row.btnMore.setOnClickListener { showMoreMenu(it, command) }

            binding.listCommands.addView(row.root)
        }
    }

    private fun showMoreMenu(anchor: android.view.View, command: VoiceCommand) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, R.string.cmd_menu_rename)
        menu.menu.add(0, 2, 1, R.string.cmd_menu_delete_samples)
        menu.menu.add(0, 3, 2, R.string.cmd_menu_delete)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> askForName(R.string.cmd_dialog_rename_title, command.name, command.id) { name ->
                    command.name = name
                    persistAndRender()
                }
                2 -> {
                    VoiceCommandStore.deleteSamples(this, command)
                    persistAndRender()
                }
                3 -> confirmDelete(command)
            }
            true
        }
        menu.show()
    }

    private fun confirmDelete(command: VoiceCommand) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cmd_confirm_delete_title)
            .setMessage(getString(R.string.cmd_confirm_delete_message, command.name))
            .setPositiveButton(R.string.cmd_menu_delete) { _, _ ->
                VoiceCommandStore.deleteSamples(this, command)
                commands.remove(command)
                persistAndRender()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Namenseingabe. [excludeId] ist die ID des Kommandos beim Umbenennen,
     * damit der eigene Name nicht als Duplikat gilt.
     */
    private fun askForName(
        titleRes: Int,
        initial: String,
        excludeId: String?,
        onOk: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            hint = getString(R.string.cmd_hint_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() ->
                        Toast.makeText(this, R.string.cmd_name_empty, Toast.LENGTH_SHORT).show()
                    commands.any { it.id != excludeId && it.name.equals(name, ignoreCase = true) } ->
                        Toast.makeText(this, R.string.cmd_name_exists, Toast.LENGTH_SHORT).show()
                    else -> onOk(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addDefaultCommands() {
        val defaults = listOf(
            R.string.cmd_default_1, R.string.cmd_default_2, R.string.cmd_default_3,
            R.string.cmd_default_4, R.string.cmd_default_5, R.string.cmd_default_6
        ).map { getString(it) }

        var added = 0
        for (name in defaults) {
            if (commands.none { it.name.equals(name, ignoreCase = true) }) {
                commands.add(VoiceCommand(name = name))
                added++
            }
        }
        persistAndRender()
        Toast.makeText(this, getString(R.string.cmd_defaults_added, added), Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────
    // Aufnahme
    // ─────────────────────────────────────────────────────────────

    private fun requestRecording(command: VoiceCommand) {
        pendingRecordId = command.id
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRecordingFor(command)
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecordingFor(command: VoiceCommand) {
        if (recorder != null || isFinishing || isDestroyed) return
        if (command.samples.size >= VoiceCommandStore.MAX_SAMPLES) return
        stopPlayback()

        val sampleNumber = command.samples.size + 1
        showRecordDialog(command, sampleNumber)

        recorder = CommandRecorder().also { rec ->
            rec.start(
                onState = { state ->
                    recordStatus?.text = when (state) {
                        CommandRecorder.State.PREPARING -> getString(R.string.cmd_state_preparing)
                        CommandRecorder.State.LISTENING -> getString(R.string.cmd_state_listening, command.name)
                        CommandRecorder.State.SPEAKING -> getString(R.string.cmd_state_speaking)
                    }
                },
                onLevel = { db ->
                    recordLevel?.progress = (((db + 70f) / 60f) * 100f).toInt().coerceIn(0, 100)
                },
                onResult = { result, samples, message ->
                    handleRecordResult(command, result, samples, message)
                }
            )
        }
    }

    private fun showRecordDialog(command: VoiceCommand, sampleNumber: Int) {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val status = TextView(this).apply {
            text = getString(R.string.cmd_state_preparing)
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        val level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding / 2)
            addView(status)
            addView(level, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = padding / 2 })
        }
        recordStatus = status
        recordLevel = level

        recordDialog = AlertDialog.Builder(this)
            .setTitle(
                getString(
                    R.string.cmd_record_title,
                    command.name, sampleNumber, VoiceCommandStore.REQUIRED_SAMPLES
                )
            )
            .setView(container)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> cancelRecording() }
            .show()
    }

    private fun handleRecordResult(
        command: VoiceCommand,
        result: CommandRecorder.Result,
        samples: ShortArray?,
        message: String?
    ) {
        dismissRecordDialog()
        recorder = null

        when (result) {
            CommandRecorder.Result.OK -> {
                if (samples == null) return
                val fileName = "${command.id}_${System.currentTimeMillis()}.wav"
                try {
                    VoiceCommandStore.writeWav(VoiceCommandStore.sampleFile(this, fileName), samples)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.cmd_err_mic, e.message), Toast.LENGTH_LONG).show()
                    return
                }
                command.samples.add(fileName)
                persistAndRender()
                Toast.makeText(this, R.string.cmd_saved, Toast.LENGTH_SHORT).show()

                // Bis zur Mindestanzahl automatisch mit der nächsten Aufnahme fortfahren
                if (command.samples.size < VoiceCommandStore.REQUIRED_SAMPLES) {
                    handler.postDelayed({ startRecordingFor(command) }, 700)
                }
            }
            CommandRecorder.Result.NO_SPEECH ->
                Toast.makeText(this, R.string.cmd_err_no_speech, Toast.LENGTH_LONG).show()
            CommandRecorder.Result.TOO_SHORT ->
                Toast.makeText(this, R.string.cmd_err_too_short, Toast.LENGTH_LONG).show()
            CommandRecorder.Result.CLIPPED ->
                Toast.makeText(this, R.string.cmd_err_clipped, Toast.LENGTH_LONG).show()
            CommandRecorder.Result.ERROR ->
                Toast.makeText(this, getString(R.string.cmd_err_mic, message ?: "?"), Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelRecording() {
        handler.removeCallbacksAndMessages(null)
        recorder?.cancel()
        recorder = null
        dismissRecordDialog()
    }

    private fun dismissRecordDialog() {
        recordDialog?.dismiss()
        recordDialog = null
        recordStatus = null
        recordLevel = null
    }

    // ─────────────────────────────────────────────────────────────
    // Wiedergabe (alle Aufnahmen eines Kommandos nacheinander, 300 ms Pause)
    // ─────────────────────────────────────────────────────────────

    private fun play(command: VoiceCommand) {
        stopPlayback()

        val parts = command.samples.mapNotNull {
            VoiceCommandStore.readWav(VoiceCommandStore.sampleFile(this, it))
        }
        if (parts.isEmpty()) {
            Toast.makeText(this, R.string.cmd_no_samples, Toast.LENGTH_SHORT).show()
            return
        }

        val rate = VoiceCommandStore.SAMPLE_RATE
        val gap = rate * 3 / 10
        val all = ShortArray(parts.sumOf { it.size } + gap * (parts.size - 1))
        var pos = 0
        parts.forEachIndexed { i, part ->
            System.arraycopy(part, 0, all, pos, part.size)
            pos += part.size
            if (i < parts.size - 1) pos += gap
        }

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(all.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(all, 0, all.size)
            track.play()
            audioTrack = track

            val durationMs = all.size * 1000L / rate
            handler.postDelayed({ if (audioTrack === track) stopPlayback() }, durationMs + 300)
        } catch (e: Exception) {
            stopPlayback()
            Toast.makeText(this, getString(R.string.cmd_err_mic, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopPlayback() {
        val track = audioTrack ?: return
        audioTrack = null
        try {
            track.stop()
        } catch (ignored: Exception) {
        }
        track.release()
    }
}
