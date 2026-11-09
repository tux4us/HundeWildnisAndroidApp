package com.hundewildnis.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Ein vom Hundeführer eingesprochenes Kommando.
 *
 * [samples] enthält die Dateinamen der Aufnahmen (WAV, 16 kHz, mono, 16 Bit) im
 * Verzeichnis [VoiceCommandStore.sampleDir]. Die Aufnahmen bilden die lokale Referenz-
 * datenbank, mit der Kommandos später während der Messung erkannt werden.
 *
 * Wird mit Gson serialisiert → Keep-Regel in proguard-rules.pro erforderlich.
 */
data class VoiceCommand(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    val samples: MutableList<String> = mutableListOf()
)

/**
 * Persistenz der Kommandoliste (SharedPreferences "voice_commands", JSON) und der
 * zugehörigen Audiodateien (filesDir/voice_commands/). Alle Daten bleiben lokal.
 */
object VoiceCommandStore {

    const val SAMPLE_RATE = 16000

    /** Mindestanzahl Aufnahmen, ab der ein Kommando als vollständig gilt. */
    const val REQUIRED_SAMPLES = 3

    /** Maximale Anzahl Aufnahmen je Kommando. */
    const val MAX_SAMPLES = 5

    private const val PREFS_NAME = "voice_commands"
    private const val KEY_COMMANDS = "commands"
    private const val DIR_NAME = "voice_commands"

    private val gson = Gson()

    fun load(context: Context): MutableList<VoiceCommand> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMMANDS, null) ?: return mutableListOf()
        val commands: MutableList<VoiceCommand> = try {
            val type = object : TypeToken<MutableList<VoiceCommand>>() {}.type
            gson.fromJson<MutableList<VoiceCommand>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

        // Verwaiste Einträge entfernen (Datei fehlt, z. B. nach manuellem Löschen)
        var changed = false
        for (command in commands) {
            if (command.samples.removeAll { !sampleFile(context, it).exists() }) changed = true
        }
        if (changed) save(context, commands)
        return commands
    }

    fun save(context: Context, commands: List<VoiceCommand>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_COMMANDS, gson.toJson(commands)).apply()
    }

    fun sampleDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    fun sampleFile(context: Context, fileName: String): File = File(sampleDir(context), fileName)

    /** Löscht alle Audiodateien eines Kommandos und leert dessen Aufnahmeliste. */
    fun deleteSamples(context: Context, command: VoiceCommand) {
        command.samples.forEach { sampleFile(context, it).delete() }
        command.samples.clear()
    }

    /** Anzahl vollständiger Kommandos (mindestens [REQUIRED_SAMPLES] Aufnahmen). */
    fun completeCount(commands: List<VoiceCommand>): Int =
        commands.count { it.samples.size >= REQUIRED_SAMPLES }

    /**
     * Erzeugt die Erkennungsvorlagen (MFCC) aus den Aufnahmen aller vollständigen Kommandos.
     * Dauert für eine typische Liste (6 Kommandos × 3 Aufnahmen) deutlich unter einer Sekunde.
     */
    fun buildTemplates(context: Context, commands: List<VoiceCommand>): List<CommandTemplate> {
        val mfcc = MfccExtractor(SAMPLE_RATE)
        val templates = mutableListOf<CommandTemplate>()
        for (command in commands) {
            if (command.samples.size < REQUIRED_SAMPLES) continue
            for (fileName in command.samples) {
                val pcm = readWav(sampleFile(context, fileName)) ?: continue
                CommandTemplate.fromPcm(command.id, command.name, pcm, mfcc)?.let { templates.add(it) }
            }
        }
        return templates
    }

    // ─────────────────────────────────────────────────────────────
    // WAV (PCM 16 Bit, mono, SAMPLE_RATE)
    // ─────────────────────────────────────────────────────────────

    fun writeWav(file: File, samples: ShortArray) {
        val dataLen = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)                    // Größe des fmt-Blocks
        bb.putShort(1)                   // PCM
        bb.putShort(1)                   // mono
        bb.putInt(SAMPLE_RATE)
        bb.putInt(SAMPLE_RATE * 2)       // Bytes pro Sekunde
        bb.putShort(2)                   // Blockgröße
        bb.putShort(16)                  // Bits pro Sample
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLen)
        for (s in samples) bb.putShort(s)
        file.writeBytes(bb.array())
    }

    fun readWav(file: File): ShortArray? {
        if (!file.exists() || file.length() <= 44) return null
        val bytes = file.readBytes()
        val count = (bytes.size - 44) / 2
        val bb = ByteBuffer.wrap(bytes, 44, count * 2).order(ByteOrder.LITTLE_ENDIAN)
        val out = ShortArray(count)
        for (i in 0 until count) out[i] = bb.short
        return out
    }
}
