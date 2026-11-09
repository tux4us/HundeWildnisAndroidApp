package com.hundewildnis.app

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Summiert die zurückgelegte Strecke aus GPS-Fixes und liefert die Strecke zu einem beliebigen
 * Zeitpunkt der Messung (kein Android-Bezug).
 *
 * Filter gegen GPS-Rauschen:
 * - Fixes mit Genauigkeit schlechter als [MAX_ACCURACY_M] werden verworfen.
 * - Eine Bewegung zählt erst, wenn sie mindestens [MIN_STEP_M] und mindestens die gemeldete
 *   Genauigkeit des neuen Fixes beträgt; sonst bleibt der Bezugspunkt stehen. Stehen am Ort
 *   summiert dadurch keine Strecke auf. Folge: Die Strecke wird um höchstens eine
 *   Genauigkeitsweite (typisch 3–8 m) zu kurz gemessen, weil der letzte Teilschritt fehlt.
 * - Meldet der Fix eine Geschwindigkeit (Doppler, deutlich genauer als die Positionsdifferenz)
 *   unter [STANDSTILL_MPS], gilt das als Stillstand: Der Bezugspunkt bleibt stehen.
 * - Sprünge über [MAX_SPEED_MPS] (Ausreißer) werden verworfen.
 *
 * Zeitangaben sind Millisekunden seit Beginn der Messung (dieselbe Zeitbasis wie
 * [CommandEvent.timestamp]).
 */
class DistanceTracker {

    companion object {
        const val MAX_ACCURACY_M = 25f
        const val MIN_STEP_M = 3.0
        const val MAX_SPEED_MPS = 15.0
        const val STANDSTILL_MPS = 0.4f
        private const val EARTH_RADIUS_M = 6371000.0

        /** Großkreis-Entfernung in Metern (Haversine). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
        }
    }

    private class Sample(val timeMs: Long, val cumulativeM: Double)

    private val samples = ArrayList<Sample>()
    private var anchorLat = 0.0
    private var anchorLon = 0.0
    private var anchorTimeMs = 0L
    private var cumulative = 0.0

    /** Anzahl der verwendeten (ausreichend genauen) Fixes. */
    val fixCount: Int get() = samples.size

    /** Gesamtstrecke in Metern; 0, solange kein Fix vorliegt. */
    val totalMeters: Float get() = cumulative.toFloat()

    /** true, sobald mindestens ein verwertbarer Fix vorliegt. */
    val hasFix: Boolean get() = samples.isNotEmpty()

    fun reset() {
        samples.clear()
        cumulative = 0.0
        anchorTimeMs = 0L
    }

    /**
     * Nimmt einen Fix auf. Gibt true zurück, wenn der Fix verwertet wurde.
     * [speedMps]: vom Empfänger gemeldete Geschwindigkeit in m/s, negativ = unbekannt.
     * Zeiten müssen nicht abnehmen; ein Fix mit früherem Zeitstempel als der letzte wird verworfen.
     */
    fun addFix(
        timeMs: Long, lat: Double, lon: Double, accuracyM: Float, speedMps: Float = -1f
    ): Boolean {
        if (accuracyM <= 0f || accuracyM > MAX_ACCURACY_M) return false
        if (lat.isNaN() || lon.isNaN()) return false

        if (samples.isEmpty()) {
            anchorLat = lat; anchorLon = lon; anchorTimeMs = timeMs
            samples.add(Sample(timeMs, 0.0))
            return true
        }
        if (timeMs < samples.last().timeMs) return false

        val d = distanceMeters(anchorLat, anchorLon, lat, lon)
        val dtSec = max(0.001, (timeMs - anchorTimeMs) / 1000.0)
        if (d / dtSec > MAX_SPEED_MPS) return false

        val standingStill = speedMps in 0f..STANDSTILL_MPS
        if (!standingStill && d >= max(MIN_STEP_M, accuracyM.toDouble())) {
            cumulative += d
            anchorLat = lat; anchorLon = lon; anchorTimeMs = timeMs
        }
        samples.add(Sample(timeMs, cumulative))
        return true
    }

    /**
     * Strecke in Metern seit Messbeginn zum Zeitpunkt [timeMs]; linear zwischen den Fixes
     * interpoliert, nach dem letzten Fix konstant. Null, wenn zu diesem Zeitpunkt noch kein
     * Fix vorlag (Kaltstart des GPS) – die Strecke ist dann unbekannt.
     */
    fun metersAt(timeMs: Long): Float? {
        if (samples.isEmpty() || timeMs < samples.first().timeMs) return null
        val last = samples.last()
        if (timeMs >= last.timeMs) return last.cumulativeM.toFloat()

        // Binäre Suche: letzter Fix mit timeMs <= gesuchter Zeit
        var lo = 0
        var hi = samples.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (samples[mid].timeMs <= timeMs) lo = mid else hi = mid - 1
        }
        val a = samples[lo]
        val b = samples[lo + 1]
        val span = b.timeMs - a.timeMs
        if (span <= 0L) return a.cumulativeM.toFloat()
        val f = (timeMs - a.timeMs).toDouble() / span
        return (a.cumulativeM + f * (b.cumulativeM - a.cumulativeM)).toFloat()
    }
}
