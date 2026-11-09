package com.hundewildnis.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Nimmt genau eine Äußerung (ein Kommando) auf.
 *
 * Ablauf: ca. 300 ms Umgebungsgeräusch messen (PREPARING) → auf Sprache warten (LISTENING)
 * → Aufnahme (SPEAKING) → Ende nach ca. 440 ms Stille. Anfang und Ende werden mit je
 * 100 ms Puffer zugeschnitten. Der Ergebnis-Callback kommt im Main-Thread.
 *
 * AudioSource-Fallback wie in SimplePitchDetector: MIC → VOICE_RECOGNITION →
 * VOICE_COMMUNICATION → DEFAULT (VOICE_COMMUNICATION muss in der Kette bleiben).
 */
class CommandRecorder(private val sampleRate: Int = VoiceCommandStore.SAMPLE_RATE) {

    enum class State { PREPARING, LISTENING, SPEAKING }
    enum class Result { OK, NO_SPEECH, TOO_SHORT, CLIPPED, ERROR }

    private companion object {
        const val CHUNK_MS = 20
        const val CALIBRATION_CHUNKS = 15            // 300 ms Umgebungsgeräusch
        const val NOISE_FACTOR = 3.0                 // Sprachschwelle = 3 × Umgebungs-RMS (ca. +9.5 dB)
        const val MIN_SPEECH_RMS = 0.0018            // absolute Untergrenze (ca. -55 dBFS)
        const val END_SILENCE_CHUNKS = 22            // 440 ms Stille beendet die Aufnahme
        const val MAX_UTTERANCE_CHUNKS = 150         // maximal 3 s Sprache
        const val NO_SPEECH_TIMEOUT_CHUNKS = 250     // nach 5 s ohne Sprache abbrechen
        const val MIN_SPEECH_CHUNKS = 8              // mindestens 160 ms Sprache
        const val PRE_ROLL_CHUNKS = 5                // 100 ms vor der Sprache
        const val POST_ROLL_CHUNKS = 5               // 100 ms nach der Sprache
        const val CLIP_PEAK = 32000                  // ab hier gilt die Aufnahme als übersteuert
        const val MAX_SECONDS = 8
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO wird vom Aufrufer geprüft
    fun start(
        onState: (State) -> Unit,
        onLevel: (rmsDbfs: Float) -> Unit,
        onResult: (Result, ShortArray?, String?) -> Unit
    ) {
        cancelled = false
        thread = Thread {
            var record: AudioRecord? = null
            try {
                record = createAudioRecord()
                    ?: throw IllegalStateException("AudioRecord konnte nicht initialisiert werden")
                val (result, samples) = capture(record, onState, onLevel)
                postResult(result, samples, null, onResult)
            } catch (e: Exception) {
                postResult(Result.ERROR, null, e.message, onResult)
            } finally {
                try {
                    record?.stop()
                } catch (ignored: Exception) {
                }
                record?.release()
            }
        }.also {
            it.name = "CommandRecorder"
            it.start()
        }
    }

    /** Bricht ab; es wird kein Ergebnis-Callback mehr geliefert. */
    fun cancel() {
        cancelled = true
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val bufferSize = max(minBuffer, sampleRate) // mindestens 0,5 s (Bytes)

        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        )
        for (source in sources) {
            try {
                val record = AudioRecord(
                    source, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) return record
                record.release()
            } catch (ignored: Exception) {
            }
        }
        return null
    }

    private fun capture(
        record: AudioRecord,
        onState: (State) -> Unit,
        onLevel: (Float) -> Unit
    ): Pair<Result, ShortArray?> {
        val chunk = sampleRate * CHUNK_MS / 1000
        val maxSamples = sampleRate * MAX_SECONDS
        val data = ShortArray(maxSamples)
        val buf = ShortArray(chunk)

        var nChunks = 0
        var noiseSum = 0.0
        var threshold = 0.0
        var speechStart = -1
        var lastSpeech = -1

        postState(State.PREPARING, onState)
        record.startRecording()

        while (!cancelled && (nChunks + 1) * chunk <= maxSamples) {
            var got = 0
            while (got < chunk && !cancelled) {
                val r = record.read(buf, got, chunk - got)
                if (r < 0) throw IllegalStateException("AudioRecord.read: $r")
                got += r
            }
            if (cancelled) break

            val idx = nChunks
            System.arraycopy(buf, 0, data, idx * chunk, chunk)
            nChunks++

            var sum = 0.0
            for (i in 0 until chunk) {
                val s = buf[i] / 32768.0
                sum += s * s
            }
            val rms = sqrt(sum / chunk)
            if (idx % 2 == 0) {
                val db = (20.0 * log10(max(rms, 1e-6))).toFloat()
                mainHandler.post { if (!cancelled) onLevel(db) }
            }

            if (idx < CALIBRATION_CHUNKS) {
                noiseSum += rms
                if (idx == CALIBRATION_CHUNKS - 1) {
                    threshold = max(noiseSum / CALIBRATION_CHUNKS * NOISE_FACTOR, MIN_SPEECH_RMS)
                    postState(State.LISTENING, onState)
                }
                continue
            }

            if (rms > threshold) {
                if (speechStart < 0) {
                    speechStart = idx
                    postState(State.SPEAKING, onState)
                }
                lastSpeech = idx
            }

            if (speechStart >= 0) {
                if (idx - lastSpeech >= END_SILENCE_CHUNKS ||
                    idx - speechStart >= MAX_UTTERANCE_CHUNKS
                ) break
            } else if (idx >= NO_SPEECH_TIMEOUT_CHUNKS) {
                break
            }
        }

        if (cancelled) return Pair(Result.ERROR, null) // wird nicht ausgeliefert
        if (speechStart < 0) return Pair(Result.NO_SPEECH, null)
        if (lastSpeech - speechStart + 1 < MIN_SPEECH_CHUNKS) return Pair(Result.TOO_SHORT, null)

        val from = max(0, speechStart - PRE_ROLL_CHUNKS) * chunk
        val to = (min(nChunks - 1, lastSpeech + POST_ROLL_CHUNKS) + 1) * chunk
        val segment = data.copyOfRange(from, to)

        var peak = 0
        for (s in segment) peak = max(peak, abs(s.toInt()))
        if (peak >= CLIP_PEAK) return Pair(Result.CLIPPED, null)

        return Pair(Result.OK, segment)
    }

    private fun postState(state: State, onState: (State) -> Unit) {
        mainHandler.post { if (!cancelled) onState(state) }
    }

    private fun postResult(
        result: Result,
        samples: ShortArray?,
        message: String?,
        onResult: (Result, ShortArray?, String?) -> Unit
    ) {
        mainHandler.post { if (!cancelled) onResult(result, samples, message) }
    }
}
