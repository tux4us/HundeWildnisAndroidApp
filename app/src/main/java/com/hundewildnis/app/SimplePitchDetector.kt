package com.hundewildnis.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.*

/**
 * Einfache Pitch Detection ohne externe Libraries.
 * Verwendet Autocorrelation-Algorithmus für Frequenz-Erkennung.
 *
 * Optimiert für Sprachfrequenzen: 80–800 Hz
 *
 * Änderungen gegenüber der Vorversion:
 * - AudioSource-Fallback-Kette: VOICE_RECOGNITION → MIC → DEFAULT
 *   (VOICE_COMMUNICATION ist auf vielen Geräten ohne aktiven Anruf gesperrt
 *    oder unterdrückt per Hardware-AGC genau die gemessenen Frequenzen)
 * - Buffergröße wird korrekt via getMinBufferSize() berechnet
 * - Fehler im Recording-Thread werden per onError-Callback nach oben gemeldet
 * - Thread.sleep() entfernt; kontinuierliches Lesen ohne künstliche Pause
 * - isAvailable() nutzt dieselbe Fallback-Logik
 */
class SimplePitchDetector(
    private val sampleRate: Int = 22050,
    private val bufferSize: Int = 4096
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ShortArray(bufferSize)
    private var recordingThread: Thread? = null

    companion object {
        private const val TAG = "SimplePitchDetector"

        /**
         * AudioSource-Fallback-Kette.
         * MIC: Universell verfügbar, oft das am wenigsten verarbeitete Signal.
         * VOICE_RECOGNITION: Optimiert für Spracherkennung, meist ohne aggressive Filter.
         * VOICE_COMMUNICATION: Ursprünglich primär, aber auf manchen Geräten ohne Anruf restriktiv.
         * DEFAULT: Letzter Ausweg.
         */
        private val AUDIO_SOURCE_FALLBACKS = listOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.DEFAULT
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Öffentliche API
    // ─────────────────────────────────────────────────────────────

    /**
     * Startet die Aufnahme und Pitch Detection.
     *
     * @param onPitchDetected Callback mit (frequency: Float, amplitude: Float);
     *                        wird im Recording-Thread aufgerufen – Caller muss
     *                        ggf. auf den UI-Thread wechseln.
     * @param onError         Optionaler Fehler-Callback; wird aufgerufen wenn
     *                        die Aufnahme im laufenden Betrieb abbricht.
     */
    fun startRecording(
        onPitchDetected: (frequency: Float, amplitude: Float) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        if (isRecording) return

        val record = createAudioRecord()
            ?: throw IllegalStateException(
                "AudioRecord konnte mit keiner AudioSource initialisiert werden. " +
                        "Bitte Mikrofonberechtigung prüfen."
            )

        audioRecord = record
        isRecording = true
        record.startRecording()

        recordingThread = Thread {
            try {
                while (isRecording) {
                    val readSize = record.read(audioBuffer, 0, bufferSize)
                    when {
                        readSize > 0 -> {
                            val pitch     = detectPitch(audioBuffer, readSize)
                            val amplitude = calculateAmplitude(audioBuffer, readSize)
                            onPitchDetected(pitch, amplitude)
                        }
                        readSize == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.w(TAG, "read() → ERROR_INVALID_OPERATION, Aufnahme beendet")
                            break
                        }
                        readSize == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.w(TAG, "read() → ERROR_BAD_VALUE")
                            // Kurze Pause um CPU-Spinning zu vermeiden, dann weiter
                            Thread.sleep(10)
                        }
                        else -> {
                            // readSize == 0 oder anderer negativer Wert → kurz warten
                            Thread.sleep(10)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // Normales Stoppen via interrupt()
                Log.d(TAG, "Recording-Thread unterbrochen (normales Stoppen)")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler im Recording-Thread: ${e.message}", e)
                onError?.invoke(e)
            }
        }.apply {
            name = "PitchDetector-Thread"
            isDaemon = true
            start()
        }
    }

    /**
     * Stoppt die Aufnahme sauber.
     */
    fun stopRecording() {
        isRecording = false

        recordingThread?.interrupt()
        recordingThread = null

        try { audioRecord?.stop()    } catch (e: Exception) { Log.w(TAG, "stop(): ${e.message}") }
        try { audioRecord?.release() } catch (e: Exception) { Log.w(TAG, "release(): ${e.message}") }
        audioRecord = null
    }

    /**
     * Prüft ob AudioRecord mit mindestens einer AudioSource initialisierbar ist.
     */
    fun isAvailable(): Boolean {
        return try {
            val test = createAudioRecord()
            test?.release()
            test != null
        } catch (e: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AudioRecord-Initialisierung mit Fallback
    // ─────────────────────────────────────────────────────────────

    /**
     * Versucht AudioRecord mit der Fallback-Kette zu erstellen.
     * Gibt null zurück wenn keine Source funktioniert.
     *
     * Buffergröße: Maximum aus gewünschter Größe und gerätespezifischem Minimum.
     * Ein zu kleiner Buffer führt zu STATE_UNINITIALIZED ohne Exception.
     */
    private fun createAudioRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "getMinBufferSize() fehlgeschlagen – Gerät unterstützt diese Konfiguration nicht")
            return null
        }

        // Buffer mindestens doppelte Mindestgröße für Stabilität
        val actualBuffer = maxOf(bufferSize * 2, minBuffer * 2)

        for (source in AUDIO_SOURCE_FALLBACKS) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBuffer
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialisiert mit source=$source, buffer=$actualBuffer")
                    return record
                } else {
                    Log.w(TAG, "AudioRecord mit source=$source nicht initialisiert, versuche nächste...")
                    record.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord mit source=$source fehlgeschlagen: ${e.message}")
            }
        }

        return null
    }

    // ─────────────────────────────────────────────────────────────
    // Pitch Detection & Amplitude
    // ─────────────────────────────────────────────────────────────

    /**
     * Pitch Detection via Autocorrelation.
     * Funktioniert gut für harmonische Signale wie Sprache.
     */
    private fun detectPitch(buffer: ShortArray, size: Int): Float {
        // Normalisierung auf -1.0 bis 1.0
        val normalized = FloatArray(size) { buffer[it] / 32768f }

        // Autocorrelation
        val r = FloatArray(size / 2)
        for (lag in 0 until size / 2) {
            var sum = 0f
            for (i in 0 until size / 2) {
                if (i + lag < size) {
                    sum += normalized[i] * normalized[i + lag]
                }
            }
            r[lag] = sum
        }

        // Für Sprachfrequenzen: 80 Hz (tief) bis 800 Hz (hoch)
        val minLag = sampleRate / 800  // entspricht max. 800 Hz
        val maxLag = sampleRate / 80   // entspricht min.  80 Hz

        var bestLag = 0
        var bestValue = 0f

        for (lag in minLag until minOf(maxLag, r.size)) {
            if (r[lag] > bestValue) {
                bestValue = r[lag]
                bestLag = lag
            }
        }

        return if (bestLag > 0 && bestValue > 0.03f) {
            sampleRate.toFloat() / bestLag.toFloat()
        } else {
            0f
        }
    }

    /**
     * Berechnet die Amplitude (RMS).
     * Wert zwischen 0.0 (still) und ~1.0 (laut).
     * 30x Verstärkung für 1–2 Meter Abstand.
     */
    private fun calculateAmplitude(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i] / 32768.0
            sum += sample * sample
        }
        return (sqrt(sum / size) * 30.0).toFloat().coerceAtMost(1.0f)
    }
}