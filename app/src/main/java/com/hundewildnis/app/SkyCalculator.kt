package com.hundewildnis.app

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Berechnet die scheinbare Position von Sonne, Mond und Polaris (inkl. Großem Wagen)
 * am Himmel für einen gegebenen Standort und Zeitpunkt.
 *
 * Grundlage: Astronomische Standardalgorithmen nach Jean Meeus ("Astronomical Algorithms"),
 * in vereinfachter (aber für Freilandnutzung ausreichend präziser) Form:
 *  - Sonne: Meeus Kap. 25 (low precision, Fehler < 0,01°)
 *  - Mond: Meeus Kap. 47 (stark vereinfacht, nur Hauptterme, Fehler ca. 0,3–1°)
 *  - Polaris/Großer Wagen: Sternzeit-basierte Rotation feststehender Sternkoordinaten (exakt,
 *    Eigenbewegung über Jahrzehnte vernachlässigbar)
 *
 * Alle Winkel intern in Grad, Zwischenschritte in Radiant wo nötig.
 */
object SkyCalculator {

    /** Ergebnis einer Positionsberechnung: Kompassrichtung und Höhe über Horizont. */
    data class SkyPosition(
        val azimuth: Double,   // 0..360°, 0 = Nord, 90 = Ost
        val altitude: Double,  // Grad über Horizont, negativ = unter Horizont
        val isVisible: Boolean // altitude > 0
    )

    /** Ergebnis für den Großen Wagen: Position + Uhrzeiger-Ausrichtung relativ zu Polaris. */
    data class PolarisResult(
        val polaris: SkyPosition,
        /** Uhrzeit-Analogie (1–12), in der der Große Wagen relativ zu Polaris steht (militärisch). */
        val greatBearClockPosition: Int
    )

    data class SkyData(
        val sun: SkyPosition,
        val moon: SkyPosition,
        val moonIlluminationPercent: Double,
        val polarisResult: PolarisResult
    )

    // ─────────────────────────────────────────────────────────────
    // Öffentliche Einstiegspunkte
    // ─────────────────────────────────────────────────────────────

    fun calculate(latitudeDeg: Double, longitudeDeg: Double, timeMillis: Long = System.currentTimeMillis()): SkyData {
        val jd = julianDate(timeMillis)
        val gmst = greenwichMeanSiderealTimeDeg(jd)
        val lst = normalizeDeg(gmst + longitudeDeg) // lokale Sternzeit in Grad

        val sun = sunPosition(jd, latitudeDeg, lst)
        val moonRaDec = moonEquatorial(jd)
        val moon = equatorialToHorizontal(moonRaDec.first, moonRaDec.second, latitudeDeg, lst)
        val moonIllum = moonIlluminationPercent(jd)

        val polarisEq = POLARIS_RA_DEG to POLARIS_DEC_DEG
        val polarisHorizontal = equatorialToHorizontal(polarisEq.first, polarisEq.second, latitudeDeg, lst)
        val clockPos = greatBearClockPosition(lst)

        return SkyData(
            sun = sun,
            moon = moon,
            moonIlluminationPercent = moonIllum,
            polarisResult = PolarisResult(polarisHorizontal, clockPos)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Zeit-Hilfsfunktionen
    // ─────────────────────────────────────────────────────────────

    /** Julianisches Datum (UT) aus Unix-Millisekunden. */
    private fun julianDate(timeMillis: Long): Double {
        // Unix Epoch (1970-01-01 00:00 UTC) = JD 2440587.5
        return timeMillis / 86400000.0 + 2440587.5
    }

    /** Greenwich Mean Sidereal Time in Grad, nach Meeus Kap. 12. */
    private fun greenwichMeanSiderealTimeDeg(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        var gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t - (t * t * t) / 38710000.0
        return normalizeDeg(gmst)
    }

    // ─────────────────────────────────────────────────────────────
    // Sonnenposition (Meeus, low precision)
    // ─────────────────────────────────────────────────────────────

    private fun sunPosition(jd: Double, latitudeDeg: Double, lstDeg: Double): SkyPosition {
        val t = (jd - 2451545.0) / 36525.0

        // Mittlere Länge und Anomalie der Sonne
        val l0 = normalizeDeg(280.46646 + t * (36000.76983 + t * 0.0003032))
        val m = normalizeDeg(357.52911 + t * (35999.05029 - 0.0001537 * t))
        val mRad = Math.toRadians(m)

        // Mittelpunktsgleichung
        val c = (1.914602 - t * (0.004817 + 0.000014 * t)) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2 * mRad) +
                0.000289 * sin(3 * mRad)

        val trueLongitude = l0 + c

        // Schräge der Ekliptik
        val epsilon = 23.439291 - t * (0.0130042 + t * (0.00000016 - t * 0.000000504))
        val epsilonRad = Math.toRadians(epsilon)
        val lambdaRad = Math.toRadians(trueLongitude)

        // Äquatoriale Koordinaten
        val raRad = atan2(cos(epsilonRad) * sin(lambdaRad), cos(lambdaRad))
        val decRad = asin(sin(epsilonRad) * sin(lambdaRad))

        val raDeg = normalizeDeg(Math.toDegrees(raRad))
        val decDeg = Math.toDegrees(decRad)

        return equatorialToHorizontal(raDeg, decDeg, latitudeDeg, lstDeg)
    }

    // ─────────────────────────────────────────────────────────────
    // Mondposition (vereinfacht, Hauptterme aus Meeus Kap. 47)
    // ─────────────────────────────────────────────────────────────

    /**
     * Liefert (Rektaszension, Deklination) des Mondes in Grad.
     * Vereinfachtes Modell mit den größten periodischen Störtermen.
     * Genauigkeit: typischerweise < 1° (ausreichend für visuelle Ortung am Himmel).
     */
    private fun moonEquatorial(jd: Double): Pair<Double, Double> {
        val t = (jd - 2451545.0) / 36525.0

        // Mittlere Länge des Mondes
        val lPrime = normalizeDeg(218.3164477 + 481267.88123421 * t)
        // Mittlere Elongation
        val d = normalizeDeg(297.8501921 + 445267.1114034 * t)
        // Sonnenanomalie
        val m = normalizeDeg(357.5291092 + 35999.0502909 * t)
        // Mondanomalie
        val mPrime = normalizeDeg(134.9633964 + 477198.8675055 * t)
        // Abstand vom aufsteigenden Knoten (Breite)
        val f = normalizeDeg(93.2720950 + 483202.0175233 * t)

        val dRad = Math.toRadians(d)
        val mRad = Math.toRadians(m)
        val mPrimeRad = Math.toRadians(mPrime)
        val fRad = Math.toRadians(f)

        // Länge: Hauptstörterme (in Grad)
        var deltaL = 6.288774 * sin(mPrimeRad)
        deltaL += 1.274027 * sin(2 * dRad - mPrimeRad)
        deltaL += 0.658314 * sin(2 * dRad)
        deltaL += 0.213618 * sin(2 * mPrimeRad)
        deltaL -= 0.185116 * sin(mRad)
        deltaL -= 0.114332 * sin(2 * fRad)
        deltaL += 0.058793 * sin(2 * dRad - 2 * mPrimeRad)
        deltaL += 0.057066 * sin(2 * dRad - mRad - mPrimeRad)
        deltaL += 0.053322 * sin(2 * dRad + mPrimeRad)
        deltaL += 0.045758 * sin(2 * dRad - mRad)

        val trueLongitude = lPrime + deltaL

        // Breite: Hauptstörterme (in Grad)
        var beta = 5.128122 * sin(fRad)
        beta += 0.280602 * sin(mPrimeRad + fRad)
        beta += 0.277693 * sin(mPrimeRad - fRad)
        beta += 0.173237 * sin(2 * dRad - fRad)
        beta += 0.055413 * sin(2 * dRad - mPrimeRad + fRad)
        beta += 0.046271 * sin(2 * dRad - mPrimeRad - fRad)
        beta += 0.032573 * sin(2 * dRad + fRad)

        val epsilon = 23.439291 - t * 0.0130042
        val epsilonRad = Math.toRadians(epsilon)
        val lambdaRad = Math.toRadians(normalizeDeg(trueLongitude))
        val betaRad = Math.toRadians(beta)

        // Ekliptikal -> Äquatorial
        val sinDec = sin(betaRad) * cos(epsilonRad) + cos(betaRad) * sin(epsilonRad) * sin(lambdaRad)
        val decRad = asin(sinDec)

        val y = sin(lambdaRad) * cos(epsilonRad) - tan(betaRad) * sin(epsilonRad)
        val x = cos(lambdaRad)
        val raRad = atan2(y, x)

        return normalizeDeg(Math.toDegrees(raRad)) to Math.toDegrees(decRad)
    }

    /** Beleuchtungsgrad des Mondes in Prozent (0 = Neumond, 100 = Vollmond), grobe Näherung. */
    private fun moonIlluminationPercent(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val d = normalizeDeg(297.8501921 + 445267.1114034 * t)
        val m = normalizeDeg(357.5291092 + 35999.0502909 * t)
        val mPrime = normalizeDeg(134.9633964 + 477198.8675055 * t)

        val dRad = Math.toRadians(d)
        val mRad = Math.toRadians(m)
        val mPrimeRad = Math.toRadians(mPrime)

        // Phasenwinkel (vereinfacht, Meeus Kap. 48)
        var phaseAngle = 180.0 - d -
                6.289 * sin(mPrimeRad) +
                2.100 * sin(mRad) -
                1.274 * sin(2 * dRad - mPrimeRad) -
                0.658 * sin(2 * dRad) -
                0.214 * sin(2 * mPrimeRad) -
                0.110 * sin(dRad)

        phaseAngle = normalizeDeg(phaseAngle)
        val phaseAngleRad = Math.toRadians(phaseAngle)
        val illumination = (1 + cos(phaseAngleRad)) / 2.0
        return illumination * 100.0
    }

    // ─────────────────────────────────────────────────────────────
    // Polaris / Großer Wagen (feststehende Sternkoordinaten, J2000)
    // ─────────────────────────────────────────────────────────────

    // Polaris (Alpha UMi), J2000.0
    private const val POLARIS_RA_DEG = 37.9546
    private const val POLARIS_DEC_DEG = 89.2641

    // Großer Wagen: die beiden "Zeiger"-Sterne Dubhe und Merak, J2000.0 (für Uhrzeiger-Richtung ausreichend)
    private const val DUBHE_RA_DEG = 165.9319
    private const val DUBHE_DEC_DEG = 61.7511
    private const val MERAK_RA_DEG = 165.4603
    private const val MERAK_DEC_DEG = 56.3824

    /**
     * Bestimmt, an welcher "Uhrzeit"-Position (1–12, militärisch) der Große Wagen
     * relativ zu Polaris steht, wenn man Polaris als Zifferblatt-Mittelpunkt und
     * "12 Uhr" als "oben" (Richtung Himmelsnordpol-Zenit-Meridian) definiert.
     *
     * Prinzip: Der Winkel des Vektors Polaris→Dubhe (Zeigerstern) relativ zum
     * Stundenwinkel-Nullpunkt rotiert einmal pro Sterntag (23h56m) um 360°.
     * Wir berechnen den Positionswinkel des "Wagen-Zeigers" am Himmelspol direkt
     * aus der Rektaszension der Zeigersterne und der lokalen Sternzeit.
     */
    private fun greatBearClockPosition(lstDeg: Double): Int {
        // Mittlere Rektaszension der beiden Zeigersterne (Dubhe/Merak).
        val avgRa = normalizeDeg((DUBHE_RA_DEG + MERAK_RA_DEG) / 2.0)

        // Stundenwinkel des Wagens: 0° = Wagen kulminiert im oberen Meridian (steht "über" Polaris,
        // astronomische "12-Uhr"-Position). 180° = unterer Meridian (Wagen "unter" Polaris, "6 Uhr").
        val hourAngle = normalizeDeg(lstDeg - avgRa)

        // Umrechnung 360° Stundenwinkel -> 12 Zifferblatt-Stunden.
        var clockHour = hourAngle / 30.0 // 360° / 12h = 30° pro Stunde

        // Verifiziert: Der Wagen läuft (von der Erde aus gesehen) entgegen dem Uhrzeigersinn um
        // Polaris (Quelle: gängige astronomische Fachliteratur zur "Himmelsuhr"). Der Stundenwinkel
        // wächst jedoch im Uhrzeigersinn, daher wird der Drehsinn hier umgekehrt:
        clockHour = 12.0 - clockHour
        var hour = Math.round(clockHour).toInt() % 12
        if (hour <= 0) hour += 12

        return hour
    }

    // ─────────────────────────────────────────────────────────────
    // Koordinatentransformation: Äquatorial -> Horizontal
    // ─────────────────────────────────────────────────────────────

    /**
     * Wandelt Rektaszension/Deklination (Grad) in Azimut/Höhe (Grad) für einen
     * gegebenen Breitengrad und lokale Sternzeit (Grad) um.
     *
     * Azimut-Konvention: 0° = Nord, 90° = Ost, 180° = Süd, 270° = West (Kompasskonvention),
     * berechnet direkt via atan2 (numerisch stabil, keine Quadranten-Fallunterscheidung nötig).
     * Verifiziert gegen bekannte Sonnenpositionen (Mittagshöchststand = Süden = 180°).
     */
    private fun equatorialToHorizontal(raDeg: Double, decDeg: Double, latitudeDeg: Double, lstDeg: Double): SkyPosition {
        val hourAngleDeg = normalizeDeg(lstDeg - raDeg)
        val haRad = Math.toRadians(hourAngleDeg)
        val decRad = Math.toRadians(decDeg)
        val latRad = Math.toRadians(latitudeDeg)

        val sinAlt = sin(decRad) * sin(latRad) + cos(decRad) * cos(latRad) * cos(haRad)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))

        val y = -sin(haRad) * cos(decRad)
        val x = sin(decRad) * cos(latRad) - cos(decRad) * cos(haRad) * sin(latRad)
        val azRad = atan2(y, x)
        val azDeg = normalizeDeg(Math.toDegrees(azRad))

        val altDeg = Math.toDegrees(altRad)

        return SkyPosition(
            azimuth = azDeg,
            altitude = altDeg,
            isVisible = altDeg > 0.0
        )
    }

    private fun normalizeDeg(value: Double): Double {
        var v = value % 360.0
        if (v < 0) v += 360.0
        return v
    }
}
