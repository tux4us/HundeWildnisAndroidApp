package com.hundewildnis.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wandelt die Antworten der kostenlosen OpenWeatherMap-Schnittstellen (aktuelles Wetter,
 * 5-Tage/3-Stunden-Vorhersage, Luftqualität) in die JSON-Form um, mit der die App-Bildschirme
 * arbeiten (Felder wie `current.temperature_2m`, `hourly.time`, `daily.sunrise`).
 *
 * Reine Datenumwandlung ohne Android-Abhängigkeit. Grenzen des kostenlosen Tarifs:
 *  - Keine UV-Werte, keine Sonnenscheindauer, kein CAPE, keine Pollen: Diese Felder werden
 *    absichtlich nicht erzeugt, die Oberfläche blendet sie dann aus.
 *  - Die Vorhersage kommt in 3-Stunden-Schritten und wird stündlich interpoliert.
 *  - Es gibt keine Vergangenheitsdaten: Der Stundenverlauf beginnt mit der aktuellen Stunde.
 *  - Tageswerte (Min/Max) heute umfassen nur die Zeit ab jetzt.
 *  - Sonnenauf-/-untergang werden astronomisch berechnet (NOAA-Näherung, Abweichung wenige Minuten).
 */
object OpenWeatherMapAdapter {

    private const val TIME_PATTERN = "yyyy-MM-dd'T'HH:mm"
    private const val DAY_PATTERN = "yyyy-MM-dd"
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24 * HOUR_MS

    /** OpenWeatherMap-Wetterkennung → WMO-Code (wie ihn Open-Meteo liefert). */
    fun wmoCode(owmId: Int): Int = when (owmId) {
        200, 201, 202, 210, 211, 212, 221, 230, 231, 232 -> 95
        300, 310 -> 51
        301, 311, 321 -> 53
        302, 312 -> 55
        313 -> 80
        314 -> 82
        500 -> 61
        501 -> 63
        502, 503, 504 -> 65
        511 -> 66
        520 -> 80
        521 -> 81
        522, 531 -> 82
        600 -> 71
        601 -> 73
        602 -> 75
        611, 612, 613 -> 66
        615 -> 61
        616 -> 63
        620 -> 85
        621 -> 85
        622 -> 86
        701, 741 -> 45
        800 -> 0
        801 -> 1
        802 -> 2
        803, 804 -> 3
        else -> 3   // übrige Dunst-/Staub-/Windlagen und Unbekanntes: bedeckt
    }

    /** Ein Messpunkt (aktuell oder 3-Stunden-Vorhersage), alles in metrischen Einheiten. */
    private class Point(
        val t: Long,                // Unix-Millisekunden
        val temp: Double,
        val feels: Double,
        val pressure: Double,
        val humidity: Double,
        val windKmh: Double,
        val gustKmh: Double,
        val windDeg: Double,
        val clouds: Double,
        val visibility: Double,
        val code: Int,
        val pop: Double,            // 0..1
        val precip3h: Double        // mm je 3 Stunden (nur Vorhersage)
    )

    private fun JSONObject.num(name: String, default: Double): Double =
        if (has(name) && !isNull(name)) optDouble(name, default) else default

    private fun pointFromCurrent(c: JSONObject): Point {
        val main = c.optJSONObject("main") ?: JSONObject()
        val wind = c.optJSONObject("wind") ?: JSONObject()
        val w0 = c.optJSONArray("weather")?.optJSONObject(0)
        val rain = c.optJSONObject("rain")?.num("1h", 0.0) ?: 0.0
        val snow = c.optJSONObject("snow")?.num("1h", 0.0) ?: 0.0
        val speed = wind.num("speed", 0.0) * 3.6
        return Point(
            t = c.optLong("dt") * 1000L,
            temp = main.num("temp", 0.0),
            feels = main.num("feels_like", main.num("temp", 0.0)),
            pressure = main.num("grnd_level", main.num("pressure", 1013.0)),
            humidity = main.num("humidity", 0.0),
            windKmh = speed,
            gustKmh = max(wind.num("gust", 0.0) * 3.6, speed),
            windDeg = wind.num("deg", 0.0),
            clouds = c.optJSONObject("clouds")?.num("all", 0.0) ?: 0.0,
            visibility = c.num("visibility", 10000.0),
            code = wmoCode(w0?.optInt("id", 800) ?: 800),
            pop = 0.0,
            precip3h = (rain + snow) * 3.0   // Stundenwert hochgerechnet, nur als Platzhalter
        )
    }

    private fun pointFromForecast(e: JSONObject): Point {
        val main = e.optJSONObject("main") ?: JSONObject()
        val wind = e.optJSONObject("wind") ?: JSONObject()
        val w0 = e.optJSONArray("weather")?.optJSONObject(0)
        val rain = e.optJSONObject("rain")?.num("3h", 0.0) ?: 0.0
        val snow = e.optJSONObject("snow")?.num("3h", 0.0) ?: 0.0
        val speed = wind.num("speed", 0.0) * 3.6
        return Point(
            t = e.optLong("dt") * 1000L,
            temp = main.num("temp", 0.0),
            feels = main.num("feels_like", main.num("temp", 0.0)),
            pressure = main.num("grnd_level", main.num("pressure", 1013.0)),
            humidity = main.num("humidity", 0.0),
            windKmh = speed,
            gustKmh = max(wind.num("gust", 0.0) * 3.6, speed),
            windDeg = wind.num("deg", 0.0),
            clouds = e.optJSONObject("clouds")?.num("all", 0.0) ?: 0.0,
            visibility = e.num("visibility", 10000.0),
            code = wmoCode(w0?.optInt("id", 800) ?: 800),
            pop = e.num("pop", 0.0),
            precip3h = rain + snow
        )
    }

    /** Formatiert einen Zeitpunkt als Ortszeit-Text („yyyy-MM-dd'T'HH:mm“) des Wetterorts. */
    private fun local(pattern: String, utcMs: Long, offsetSec: Int): String {
        val f = SimpleDateFormat(pattern, Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(java.util.Date(utcMs + offsetSec * 1000L))
    }

    private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f

    /**
     * Baut die Open-Meteo-förmige Antwort.
     * @param current Antwort von `/data/2.5/weather`
     * @param forecast Antwort von `/data/2.5/forecast`
     * @param withYesterday true: `daily` beginnt mit einem Platzhalter für gestern (wie bei Open-Meteo
     * mit `past_days=1`); false: `daily` beginnt mit heute
     */
    fun buildForecast(current: JSONObject, forecast: JSONObject, withYesterday: Boolean): JSONObject {
        val offsetSec = current.optInt("timezone", forecast.optJSONObject("city")?.optInt("timezone", 0) ?: 0)
        val coord = current.optJSONObject("coord")
        val lat = coord?.num("lat", 0.0) ?: 0.0
        val lon = coord?.num("lon", 0.0) ?: 0.0

        val now = pointFromCurrent(current)
        val list = forecast.optJSONArray("list") ?: JSONArray()
        val future = ArrayList<Point>()
        for (i in 0 until list.length()) {
            val p = pointFromForecast(list.getJSONObject(i))
            if (p.t > now.t) future.add(p)
        }
        // Stützpunkte: aktuelle Messung + Vorhersage (aufsteigend)
        val anchors = ArrayList<Point>().apply { add(now); addAll(future) }

        // ── Sonnenzeiten (heute laut OWM, sonst berechnet) ──────────────────────
        val sys = current.optJSONObject("sys")
        val owmRise = sys?.optLong("sunrise", 0L)?.times(1000L) ?: 0L
        val owmSet = sys?.optLong("sunset", 0L)?.times(1000L) ?: 0L
        val isDay = if (owmRise > 0 && owmSet > 0) now.t in owmRise until owmSet else true

        // ── current ─────────────────────────────────────────────────────────────
        val cur = JSONObject()
        cur.put("time", local(TIME_PATTERN, now.t, offsetSec))
        cur.put("temperature_2m", round1(now.temp))
        cur.put("apparent_temperature", round1(now.feels))
        cur.put("relative_humidity_2m", now.humidity.roundToInt())
        cur.put("precipitation", round1(now.precip3h / 3.0))
        cur.put("weather_code", now.code)
        cur.put("surface_pressure", round1(now.pressure))
        cur.put("wind_speed_10m", round1(now.windKmh))
        cur.put("wind_gusts_10m", round1(now.gustKmh))
        cur.put("wind_direction_10m", now.windDeg.roundToInt())
        cur.put("cloud_cover", now.clouds.roundToInt())
        cur.put("is_day", if (isDay) 1 else 0)

        // ── hourly (3-Stunden-Werte stündlich interpoliert) ─────────────────────
        val hTime = JSONArray(); val hTemp = JSONArray(); val hFeels = JSONArray()
        val hPrecip = JSONArray(); val hPop = JSONArray(); val hCode = JSONArray()
        val hWind = JSONArray(); val hGust = JSONArray(); val hDeg = JSONArray()
        val hCloud = JSONArray(); val hVis = JSONArray(); val hPress = JSONArray(); val hHum = JSONArray()

        val firstHour = floor(now.t.toDouble() / HOUR_MS).toLong() * HOUR_MS
        val lastT = anchors.last().t
        var t = firstHour
        while (t <= lastT) {
            // Intervall [a, b] mit a.t <= t <= b.t suchen (vor dem ersten Punkt: erster Punkt)
            var idx = 0
            while (idx + 1 < anchors.size && anchors[idx + 1].t <= t) idx++
            val a = anchors[idx]
            val b = if (idx + 1 < anchors.size) anchors[idx + 1] else a
            val f = if (t <= a.t || b.t == a.t) 0.0 else (t - a.t).toDouble() / (b.t - a.t)
            val nearest = if (f < 0.5) a else b
            // Niederschlag: Summe des 3-Stunden-Fensters, das auf diese Stunde fällt
            val next = anchors.firstOrNull { it.t >= t && it !== now } ?: b
            hTime.put(local(TIME_PATTERN, t, offsetSec))
            hTemp.put(round1(lerp(a.temp, b.temp, f)))
            hFeels.put(round1(lerp(a.feels, b.feels, f)))
            hPrecip.put(round1(next.precip3h / 3.0))
            hPop.put((next.pop * 100).roundToInt())
            hCode.put(nearest.code)
            hWind.put(round1(lerp(a.windKmh, b.windKmh, f)))
            hGust.put(round1(lerp(a.gustKmh, b.gustKmh, f)))
            hDeg.put(nearest.windDeg.roundToInt())
            hCloud.put(lerp(a.clouds, b.clouds, f).roundToInt())
            hVis.put(lerp(a.visibility, b.visibility, f).roundToInt())
            hPress.put(round1(lerp(a.pressure, b.pressure, f)))
            hHum.put(lerp(a.humidity, b.humidity, f).roundToInt())
            t += HOUR_MS
        }
        val hourly = JSONObject()
        hourly.put("time", hTime)
        hourly.put("temperature_2m", hTemp)
        hourly.put("apparent_temperature", hFeels)
        hourly.put("precipitation", hPrecip)
        hourly.put("precipitation_probability", hPop)
        hourly.put("weather_code", hCode)
        hourly.put("wind_speed_10m", hWind)
        hourly.put("wind_gusts_10m", hGust)
        hourly.put("wind_direction_10m", hDeg)
        hourly.put("cloud_cover", hCloud)
        hourly.put("visibility", hVis)
        hourly.put("surface_pressure", hPress)
        hourly.put("relative_humidity_2m", hHum)

        // ── daily ───────────────────────────────────────────────────────────────
        // Nach Ortsdatum gruppieren (Messpunkt + Vorhersage)
        val byDay = java.util.LinkedHashMap<String, MutableList<Point>>()
        for (p in anchors) byDay.getOrPut(local(DAY_PATTERN, p.t, offsetSec)) { ArrayList() }.add(p)
        val todayKey = local(DAY_PATTERN, now.t, offsetSec)

        val dTime = JSONArray(); val dMin = JSONArray(); val dMax = JSONArray()
        val dCode = JSONArray(); val dPop = JSONArray(); val dWind = JSONArray(); val dGust = JSONArray()
        val dRise = JSONArray(); val dSet = JSONArray()

        val dayKeys = byDay.keys.filter { k ->
            k == todayKey || (byDay[k]?.size ?: 0) >= 4    // angebrochene letzte Tage weglassen
        }
        // Ortsdatum → Mitternacht (UTC-Millisekunden des Ortstages)
        fun dayStartUtc(key: String): Long {
            val f = SimpleDateFormat(DAY_PATTERN, Locale.US); f.timeZone = TimeZone.getTimeZone("UTC")
            return f.parse(key)!!.time
        }

        fun sunTimes(key: String): Pair<String, String> {
            val dayStart = dayStartUtc(key)
            val calc = SunCalc.sunriseSunset(lat, lon, dayStart)
            return if (calc != null) {
                local(TIME_PATTERN, calc.first, offsetSec) to local(TIME_PATTERN, calc.second, offsetSec)
            } else if (owmRise > 0 && owmSet > 0) {
                // Polartag/-nacht: Ersatzwert aus der Messung von heute
                local(TIME_PATTERN, owmRise, offsetSec) to local(TIME_PATTERN, owmSet, offsetSec)
            } else {
                "${key}T06:00" to "${key}T18:00"
            }
        }

        fun addDay(key: String, pts: List<Point>) {
            val temps = pts.map { it.temp }
            val noon = dayStartUtc(key) + 12 * HOUR_MS - offsetSec * 1000L
            val severe = pts.map { it.code }.filter { it >= 51 }.maxOrNull()
            val code = severe ?: pts.minByOrNull { abs(it.t - noon) }!!.code
            dTime.put(key)
            dMin.put(round1(temps.minOrNull() ?: 0.0))
            dMax.put(round1(temps.maxOrNull() ?: 0.0))
            dCode.put(code)
            dPop.put(((pts.maxOfOrNull { it.pop } ?: 0.0) * 100).roundToInt())
            dWind.put(round1(pts.maxOfOrNull { it.windKmh } ?: 0.0))
            dGust.put(round1(pts.maxOfOrNull { it.gustKmh } ?: 0.0))
            val (r, s) = sunTimes(key)
            dRise.put(r); dSet.put(s)
        }

        // Mit withYesterday: Index 0 = „gestern“ (Platzhalter, weil es keine Vergangenheitsdaten
        // gibt; die App überspringt ihn). Nur die Sonnenzeiten sind echt berechnet.
        val firstKey = dayKeys.firstOrNull() ?: todayKey
        val yesterdayKey = local(DAY_PATTERN, dayStartUtc(firstKey) - DAY_MS, 0)
        if (withYesterday) addDay(yesterdayKey, byDay[firstKey] ?: listOf(now))
        for (k in dayKeys) addDay(k, byDay[k]!!)

        val daily = JSONObject()
        daily.put("time", dTime)
        daily.put("temperature_2m_min", dMin)
        daily.put("temperature_2m_max", dMax)
        daily.put("weather_code", dCode)
        daily.put("precipitation_probability_max", dPop)
        daily.put("wind_speed_10m_max", dWind)
        daily.put("wind_gusts_10m_max", dGust)
        daily.put("sunrise", dRise)
        daily.put("sunset", dSet)

        return JSONObject().apply {
            put("latitude", lat); put("longitude", lon)
            put("utc_offset_seconds", offsetSec)
            put("current", cur); put("hourly", hourly); put("daily", daily)
        }
    }

    /**
     * @param air Antwort von `/data/2.5/air_pollution/forecast` (stündlich, ca. 4 Tage)
     * @param offsetSec Zeitzonenversatz des Orts in Sekunden (aus der Wetterantwort)
     */
    fun buildAirQuality(air: JSONObject, offsetSec: Int): JSONObject {
        val list = air.optJSONArray("list") ?: JSONArray()
        val time = JSONArray(); val pm10 = JSONArray(); val pm25 = JSONArray()
        val no2 = JSONArray(); val o3 = JSONArray(); val co = JSONArray()
        for (i in 0 until list.length()) {
            val e = list.getJSONObject(i)
            val c = e.optJSONObject("components") ?: continue
            time.put(local(TIME_PATTERN, e.optLong("dt") * 1000L, offsetSec))
            pm10.put(c.num("pm10", 0.0)); pm25.put(c.num("pm2_5", 0.0))
            no2.put(c.num("no2", 0.0)); o3.put(c.num("o3", 0.0)); co.put(c.num("co", 0.0))
        }
        val hourly = JSONObject()
        hourly.put("time", time); hourly.put("pm10", pm10); hourly.put("pm2_5", pm25)
        hourly.put("nitrogen_dioxide", no2); hourly.put("ozone", o3); hourly.put("carbon_monoxide", co)
        return JSONObject().put("hourly", hourly)
    }

    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
}

/** Sonnenauf- und -untergang nach der NOAA-Näherung („Sunrise equation“). */
object SunCalc {
    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI

    /**
     * @param dayStartUtcMs 00:00 UTC des gewünschten Kalendertags (Ortsdatum, als UTC gelesen)
     * @return Paar (Aufgang, Untergang) in Unix-Millisekunden, oder null bei Polartag/-nacht
     */
    fun sunriseSunset(latDeg: Double, lonDeg: Double, dayStartUtcMs: Long): Pair<Long, Long>? {
        // Julianischer Tag (ganzzahlig) des Mittags dieses Kalendertags, gezählt ab J2000
        val n = Math.round(dayStartUtcMs / 86_400_000.0 + 2440587.5 + 0.5 - 2451545.0).toDouble()
        val jStar = n - lonDeg / 360.0
        val m = (357.5291 + 0.98560028 * jStar).mod(360.0)
        val c = 1.9148 * sin(rad(m)) + 0.02 * sin(rad(2 * m)) + 0.0003 * sin(rad(3 * m))
        val lambda = (m + c + 180.0 + 102.9372).mod(360.0)
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(rad(m)) - 0.0069 * sin(rad(2 * lambda))
        val sinDelta = sin(rad(lambda)) * sin(rad(23.4397))
        val cosDelta = cos(asinSafe(sinDelta))
        val cosOmega = (sin(rad(-0.833)) - sin(rad(latDeg)) * sinDelta) / (cos(rad(latDeg)) * cosDelta)
        if (cosOmega > 1.0 || cosOmega < -1.0) return null
        val omega = deg(acos(cosOmega))
        val jRise = jTransit - omega / 360.0
        val jSet = jTransit + omega / 360.0
        fun toMs(j: Double) = ((j - 2440587.5) * 86_400_000.0).toLong()
        return toMs(jRise) to toMs(jSet)
    }

    private fun asinSafe(x: Double) = kotlin.math.asin(min(1.0, max(-1.0, x)))
}
