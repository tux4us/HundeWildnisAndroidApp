package com.hundewildnis.app

/**
 * Ein während der Messung erkanntes Kommando.
 *
 * [timestamp]: Beginn der Äußerung in ms seit Beginn des Audiostroms (entspricht dem
 * Session-Start bis auf die Anlaufzeit des Mikrofons, ca. 0,1–0,2 s).
 * [counted]: false, wenn dasselbe Kommando innerhalb der Wiederholungsfrist erneut gesprochen wurde.
 * [distance]: DTW-Distanz zur besten Vorlage (kleiner = sicherer erkannt).
 *
 * Wird mit Gson serialisiert → Keep-Regel in proguard-rules.pro erforderlich.
 */
data class CommandEvent(
    val commandId: String = "",
    val name: String = "",
    val timestamp: Long = 0L,
    val counted: Boolean = true,
    val distance: Float = 0f
)

/**
 * Wiederholungsregel: Wiederholt der Hundeführer dasselbe Kommando, zum Beispiel zur Motivation
 * des Hundes, zählt nur das erste. Das Zeitfenster ist gleitend: Jedes Auftreten des Kommandos,
 * auch ein nicht gezähltes, startet die Frist neu. Erst nach einer Pause von mindestens
 * [repeatWindowMs] zählt das Kommando wieder. Verschiedene Kommandos zählen unabhängig voneinander.
 */
class CommandCounter(private val repeatWindowMs: Long = REPEAT_WINDOW_MS) {

    companion object {
        const val REPEAT_WINDOW_MS = 5000L
    }

    private val lastSeen = HashMap<String, Long>()

    /** Registriert ein Auftreten und gibt zurück, ob es gezählt wird. */
    fun register(commandId: String, timeMs: Long): Boolean {
        val last = lastSeen[commandId]
        lastSeen[commandId] = timeMs
        return last == null || timeMs - last >= repeatWindowMs
    }

    fun reset() = lastSeen.clear()
}

data class CommandCount(val name: String, val counted: Int, val ignored: Int)

/**
 * Auswertung einer Ereignisliste für Anzeige und PDF.
 * Abstände werden zwischen aufeinanderfolgenden gezählten Kommandos (beliebiger Art) gebildet.
 */
data class CommandStats(
    val perCommand: List<CommandCount>,
    val totalCounted: Int,
    val totalIgnored: Int,
    val intervalCount: Int,
    val meanIntervalMs: Long,
    val minIntervalMs: Long,
    val maxIntervalMs: Long
) {
    companion object {
        fun from(events: List<CommandEvent>): CommandStats {
            val order = LinkedHashMap<String, String>()   // commandId → Name, in Reihenfolge des Auftretens
            val counted = HashMap<String, Int>()
            val ignored = HashMap<String, Int>()
            for (e in events) {
                order.getOrPut(e.commandId) { e.name }
                if (e.counted) counted[e.commandId] = (counted[e.commandId] ?: 0) + 1
                else ignored[e.commandId] = (ignored[e.commandId] ?: 0) + 1
            }
            val perCommand = order.map { (id, name) ->
                CommandCount(name, counted[id] ?: 0, ignored[id] ?: 0)
            }

            val times = events.filter { it.counted }.map { it.timestamp }.sorted()
            val intervals = times.zipWithNext { a, b -> b - a }

            return CommandStats(
                perCommand = perCommand,
                totalCounted = times.size,
                totalIgnored = events.size - times.size,
                intervalCount = intervals.size,
                meanIntervalMs = if (intervals.isEmpty()) 0L else intervals.sum() / intervals.size,
                minIntervalMs = intervals.minOrNull() ?: 0L,
                maxIntervalMs = intervals.maxOrNull() ?: 0L
            )
        }
    }
}
