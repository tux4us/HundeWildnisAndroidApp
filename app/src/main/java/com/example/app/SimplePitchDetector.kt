package com.hundewildnis.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.*

/**
 * Einfache Pitch Detection ohne externe Libraries
 * Verwendet Autocorrelation-Algorithmus für Frequenz-Erkennung
 *
 * Optimiert für Sprachfrequenzen: 80-800 Hz
 */
class SimplePitchDetector(
    private val sampleRate: Int = 22050,
    private val bufferSize: Int = 4096
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ShortArray(bufferSize)
    private var recordingThread: Thread? = null

    /**
     * Startet die Aufnahme und Pitch Detection
     * @param onPitchDetected Callback mit (frequency: Float, amplitude: Float)
     */
    fun startRecording(onPitchDetected: (frequency: Float, amplitude: Float) -> Unit) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // ✅ Optimiert für Kommunikation mit AGC & Noise Reduction
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord nicht initialisiert")
            }

            isRecording = true
            audioRecord?.startRecording()

            recordingThread = Thread {
                while (isRecording) {
                    try {
                        val readSize = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                        if (readSize > 0) {
                            val pitch = detectPitch(audioBuffer, readSize)
                            val amplitude = calculateAmplitude(audioBuffer, readSize)
                            onPitchDetected(pitch, amplitude)
                        }
                        Thread.sleep(100) // 10 Updates pro Sekunde
                    } catch (e: InterruptedException) {
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            recordingThread?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            throw e
        }
    }

    /**
     * Stoppt die Aufnahme
     */
    fun stopRecording() {
        isRecording = false

        try {
            recordingThread?.interrupt()
            recordingThread = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioRecord = null
    }

    /**
     * Pitch Detection via Autocorrelation
     * Funktioniert gut für harmonische Signale wie Sprache
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

        // Finde ersten Peak nach initialem Drop
        // Für Sprachfrequenzen: 80 Hz (niedrig) bis 800 Hz (hoch)
        val minLag = sampleRate / 800  // Max 800 Hz
        val maxLag = sampleRate / 80   // Min 80 Hz

        var bestLag = 0
        var bestValue = 0f

        // Suche das Maximum im relevanten Bereich
        for (lag in minLag until minOf(maxLag, r.size)) {
            if (r[lag] > bestValue) {
                bestValue = r[lag]
                bestLag = lag
            }
        }

        // Frequenz berechnen
        return if (bestLag > 0 && bestValue > 0.03f) {  // ✅ Gesenkt von 0.1f auf 0.03f (empfindlicher)
            sampleRate.toFloat() / bestLag.toFloat()
        } else {
            0f  // Keine klare Frequenz erkannt
        }
    }

    /**
     * Berechnet die Amplitude (RMS - Root Mean Square)
     * Wert zwischen 0.0 (still) und ~1.0 (laut)
     * Mit 30x Verstärkung für 1-2 Meter Abstand
     */
    private fun calculateAmplitude(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i] / 32768.0
            sum += sample * sample
        }
        // ✅ 30x Verstärkung für 1-2 Meter Abstand (Phone in Tasche/Hand)
        return (sqrt(sum / size) * 30.0).toFloat().coerceAtMost(1.0f)
    }

    /**
     * Prüft ob AudioRecord verfügbar ist
     */
    fun isAvailable(): Boolean {
        return try {
            val test = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,  // ✅ Konsistent
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            val available = test.state == AudioRecord.STATE_INITIALIZED
            test.release()
            available
        } catch (e: Exception) {
            false
        }
    }
}