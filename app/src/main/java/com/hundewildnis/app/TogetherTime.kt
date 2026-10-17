package com.hundewildnis.app

import android.content.Context
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

// ════════════════════════════════════════════════════════════════
// GEMEINSAME ZEIT
//
// Berechnet aus DogProfile.togetherSince (bzw. birthDate als Fallback),
// wie lange Mensch und Hund zusammenleben. Es wird nichts gespeichert;
// die Anzeige ist immer die aktuelle Sicht auf die Profildaten.
//
// Reine Datumsarithmetik ohne java.time (minSdk 24, kein Desugaring)
// und ohne Zeitzonenabhängigkeit.
// ════════════════════════════════════════════════════════════════

/** Stabile interne Codes für die Art des gemeinsamen Starts. Leer = Geburt. */
object TogetherSinceType {
    const val BIRTH = "birth"
    const val PURCHASE = "purchase"
    const val ADOPTION = "adoption"
    const val FOSTER = "foster"

    /** Reihenfolge entspricht R.array.together_since_type_options. */
    private val ORDER = listOf(BIRTH, PURCHASE, ADOPTION, FOSTER)

    fun normalize(code: String): String = if (code in ORDER) code else BIRTH

    fun toIndex(code: String): Int = ORDER.indexOf(normalize(code))

    fun fromIndex(index: Int): String = ORDER.getOrElse(index) { BIRTH }
}

enum class DatePrecision { DAY, MONTH, YEAR }

/**
 * Datum mit Genauigkeit. Bei MONTH ist [day] ohne Bedeutung (1),
 * bei YEAR sind [month] und [day] ohne Bedeutung (1).
 */
data class PartialDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val precision: DatePrecision
) {
    fun toEpochDay(): Long = TogetherMath.epochDay(year, month, day)

    /** Monatsindex (Jahr*12 + Monat-1) für Monatsdifferenzen. */
    fun monthIndex(): Int = year * 12 + (month - 1)
}

/** Ergebnis einer Zeitspanne. [totalDays] nur bei DAY-Genauigkeit. */
data class Elapsed(
    val precision: DatePrecision,
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long?
)

enum class TogetherMilestoneKind { ANNIVERSARY, DAY_COUNT }

data class TogetherMilestone(
    val kind: TogetherMilestoneKind,
    /** Bei ANNIVERSARY: Jahreszahl; bei DAY_COUNT: Tagesanzahl. */
    val value: Long,
    /** 0 = heute. */
    val daysRemaining: Long
)

data class TogetherInfo(
    /** Effektiver Typ: BIRTH, wenn kein eigenes Startdatum verwendet wurde. */
    val effectiveType: String,
    val start: PartialDate,
    val elapsed: Elapsed,
    /** Alter des Hundes, nur wenn der Hund bei Start der gemeinsamen Zeit bereits älter als 0 war. */
    val dogAge: Elapsed?,
    val dogAgePrecision: DatePrecision?,
    val milestone: TogetherMilestone?
)

object TogetherMath {

    private val FULL = Regex("""(\d{2})\.(\d{2})\.(\d{4})""")
    private val MONTH_YEAR = Regex("""(\d{2})\.(\d{4})""")
    private val YEAR = Regex("""\d{4}""")

    private val DAY_MILESTONES: List<Long> =
        listOf(100L, 250L, 500L, 750L) + (1000L..20000L step 500L).toList()

    fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    fun daysInMonth(y: Int, m: Int): Int = when (m) {
        2 -> if (isLeap(y)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Tage seit 1970-01-01 (proleptisch gregorianisch, Algorithmus nach H. Hinnant). */
    fun epochDay(y: Int, m: Int, d: Int): Long {
        val yy = (if (m <= 2) y - 1 else y).toLong()
        val era = Math.floorDiv(yy, 400L)
        val yoe = yy - era * 400
        val mp = (m + 9) % 12
        val doy = (153L * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** Addiert Monate und kürzt den Tag auf die Monatslänge (31.01. + 1 Monat = 28./29.02.). */
    fun addMonths(y: Int, m: Int, d: Int, months: Int): Triple<Int, Int, Int> {
        val idx = y * 12 + (m - 1) + months
        val ny = Math.floorDiv(idx, 12)
        val nm = Math.floorMod(idx, 12) + 1
        return Triple(ny, nm, minOf(d, daysInMonth(ny, nm)))
    }

    /**
     * Parst dd.MM.yyyy, MM.yyyy oder yyyy. Alles andere (leer, "Unbekannt", ungültiges Datum)
     * ergibt null.
     */
    fun parse(text: String): PartialDate? {
        val s = text.trim()
        if (s.isEmpty()) return null

        FULL.matchEntire(s)?.let {
            val d = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val y = it.groupValues[3].toInt()
            if (y < 1900 || m !in 1..12 || d !in 1..daysInMonth(y, m)) return null
            return PartialDate(y, m, d, DatePrecision.DAY)
        }
        MONTH_YEAR.matchEntire(s)?.let {
            val m = it.groupValues[1].toInt()
            val y = it.groupValues[2].toInt()
            if (y < 1900 || m !in 1..12) return null
            return PartialDate(y, m, 1, DatePrecision.MONTH)
        }
        if (YEAR.matches(s)) {
            val y = s.toInt()
            if (y < 1900) return null
            return PartialDate(y, 1, 1, DatePrecision.YEAR)
        }
        return null
    }

    fun today(): PartialDate {
        val c = Calendar.getInstance()
        return PartialDate(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            DatePrecision.DAY
        )
    }

    /** Zeitspanne von [start] bis [today]; null, wenn [start] in der Zukunft liegt. */
    fun elapsed(start: PartialDate, today: PartialDate): Elapsed? {
        return when (start.precision) {
            DatePrecision.DAY -> {
                val totalDays = today.toEpochDay() - start.toEpochDay()
                if (totalDays < 0) return null
                var totalMonths = today.monthIndex() - start.monthIndex()
                var days = today.day - start.day
                if (totalMonths > 0 && days < 0) {
                    totalMonths--
                    val (cy, cm, cd) = addMonths(start.year, start.month, start.day, totalMonths)
                    days = (today.toEpochDay() - epochDay(cy, cm, cd)).toInt()
                }
                Elapsed(DatePrecision.DAY, totalMonths / 12, totalMonths % 12, days, totalDays)
            }
            DatePrecision.MONTH -> {
                val totalMonths = today.monthIndex() - start.monthIndex()
                if (totalMonths < 0) return null
                Elapsed(DatePrecision.MONTH, totalMonths / 12, totalMonths % 12, 0, null)
            }
            DatePrecision.YEAR -> {
                val years = today.year - start.year
                if (years < 0) return null
                Elapsed(DatePrecision.YEAR, years, 0, 0, null)
            }
        }
    }

    /** Nächster Jahrestag oder runder Tageswert (der nähere zählt, bei Gleichstand der Jahrestag). */
    fun nextMilestone(start: PartialDate, elapsed: Elapsed, today: PartialDate): TogetherMilestone? {
        val totalDays = elapsed.totalDays ?: return null
        val todayEpoch = today.toEpochDay()

        val annivYear = if (elapsed.years >= 1 && elapsed.months == 0 && elapsed.days == 0) {
            elapsed.years
        } else {
            elapsed.years + 1
        }
        val (ay, am, ad) = addMonths(start.year, start.month, start.day, annivYear * 12)
        val anniversary = TogetherMilestone(
            TogetherMilestoneKind.ANNIVERSARY, annivYear.toLong(), epochDay(ay, am, ad) - todayEpoch
        )

        val dayTarget = DAY_MILESTONES.firstOrNull { it >= totalDays && it > 0 }
        val dayMilestone = dayTarget?.let {
            TogetherMilestone(TogetherMilestoneKind.DAY_COUNT, it, it - totalDays)
        }

        return if (dayMilestone != null && dayMilestone.daysRemaining < anniversary.daysRemaining) {
            dayMilestone
        } else {
            anniversary
        }
    }

    /**
     * Ermittelt die gemeinsame Zeit eines Hundes.
     * - Eigenes Startdatum (Kauf/Übernahme/Pflege), wenn gesetzt und gültig.
     * - Sonst Geburtsdatum.
     * - null, wenn weder noch bekannt ist oder das Startdatum in der Zukunft liegt.
     */
    fun compute(profile: DogProfile, today: PartialDate = today()): TogetherInfo? {
        val type = TogetherSinceType.normalize(profile.togetherSinceType)
        val birth = parse(profile.birthDate)

        val customStart = if (type != TogetherSinceType.BIRTH) parse(profile.togetherSince) else null
        val start = customStart ?: birth ?: return null
        val effectiveType = if (customStart != null) type else TogetherSinceType.BIRTH

        val elapsed = elapsed(start, today) ?: return null

        var dogAge: Elapsed? = null
        var dogAgePrecision: DatePrecision? = null
        if (customStart != null && birth != null && birth.toEpochDay() < customStart.toEpochDay()) {
            dogAge = elapsed(birth, today)
            dogAgePrecision = birth.precision
        }

        return TogetherInfo(
            effectiveType = effectiveType,
            start = start,
            elapsed = elapsed,
            dogAge = dogAge,
            dogAgePrecision = dogAgePrecision,
            milestone = nextMilestone(start, elapsed, today)
        )
    }
}

// ════════════════════════════════════════════════════════════════
// TEXTAUFBEREITUNG (DE/EN über strings.xml und plurals)
// ════════════════════════════════════════════════════════════════

data class TogetherDisplay(
    val sinceLine: String,
    val headline: String,
    val daysLine: String?,
    val dogAgeLine: String?,
    val milestoneLine: String?
)

object TogetherFormatter {

    fun format(context: Context, info: TogetherInfo): TogetherDisplay {
        val locale = currentLocale(context)
        val nf = NumberFormat.getIntegerInstance(locale)

        val sinceFormatRes = when (info.effectiveType) {
            TogetherSinceType.PURCHASE -> R.string.together_since_purchase_format
            TogetherSinceType.ADOPTION -> R.string.together_since_adoption_format
            TogetherSinceType.FOSTER -> R.string.together_since_foster_format
            else -> R.string.together_since_birth_format
        }
        val sinceLine = context.getString(sinceFormatRes, startText(info.start))

        val headline = span(context, info.elapsed)

        // Gesamttage und Wochen nur bei vollständigem Datum
        val daysLine = info.elapsed.totalDays?.let { total ->
            val weeks = (total / 7).toInt()
            val restDays = (total % 7).toInt()
            val totalText = context.resources.getQuantityString(
                R.plurals.together_days, clampInt(total), nf.format(total)
            )
            if (total < 7) {
                totalText
            } else {
                context.getString(
                    R.string.together_days_weeks_format,
                    totalText,
                    context.resources.getQuantityString(R.plurals.together_weeks, weeks, nf.format(weeks)),
                    context.resources.getQuantityString(R.plurals.together_days, restDays, nf.format(restDays))
                )
            }
        }

        val dogAgeLine = info.dogAge?.let {
            context.getString(R.string.together_dog_age_format, span(context, it))
        }

        val milestoneLine = info.milestone?.let { m ->
            when (m.kind) {
                TogetherMilestoneKind.ANNIVERSARY -> if (m.daysRemaining == 0L) {
                    context.getString(R.string.together_milestone_today_years_format, m.value.toInt())
                } else {
                    context.getString(
                        R.string.together_milestone_years_format,
                        context.resources.getQuantityString(
                            R.plurals.together_days, clampInt(m.daysRemaining), nf.format(m.daysRemaining)
                        ),
                        m.value.toInt()
                    )
                }
                TogetherMilestoneKind.DAY_COUNT -> if (m.daysRemaining == 0L) {
                    context.getString(R.string.together_milestone_today_days_format, nf.format(m.value))
                } else {
                    context.getString(
                        R.string.together_milestone_days_format,
                        context.resources.getQuantityString(
                            R.plurals.together_days, clampInt(m.daysRemaining), nf.format(m.daysRemaining)
                        ),
                        nf.format(m.value)
                    )
                }
            }
        }

        return TogetherDisplay(sinceLine, headline, daysLine, dogAgeLine, milestoneLine)
    }

    /** Startdatum so, wie es eingegeben wurde (Tag.Monat.Jahr, Monat.Jahr oder Jahr). */
    fun startText(d: PartialDate): String = when (d.precision) {
        DatePrecision.DAY -> String.format(Locale.ROOT, "%02d.%02d.%04d", d.day, d.month, d.year)
        DatePrecision.MONTH -> String.format(Locale.ROOT, "%02d.%04d", d.month, d.year)
        DatePrecision.YEAR -> d.year.toString()
    }

    /**
     * Zeitspanne als Text.
     * DAY: "2 Jahre, 3 Monate, 5 Tage". MONTH/YEAR mit Präfix "ca." und ohne Tage.
     */
    private fun span(context: Context, e: Elapsed): String {
        val res = context.resources
        val parts = mutableListOf<String>()
        if (e.years > 0) parts.add(res.getQuantityString(R.plurals.together_years, e.years, e.years))
        if (e.months > 0) parts.add(res.getQuantityString(R.plurals.together_months, e.months, e.months))
        if (e.precision == DatePrecision.DAY && e.days > 0) {
            parts.add(res.getQuantityString(R.plurals.together_days, e.days, e.days))
        }

        val text = when {
            parts.isNotEmpty() -> parts.joinToString(", ")
            e.precision == DatePrecision.DAY -> context.getString(R.string.together_span_today)
            e.precision == DatePrecision.MONTH -> context.getString(R.string.together_span_less_than_month)
            else -> context.getString(R.string.together_span_less_than_year)
        }
        return if (e.precision != DatePrecision.DAY && parts.isNotEmpty()) {
            context.getString(R.string.together_approx_format, text)
        } else {
            text
        }
    }

    /** Plural-Menge: Werte über Int.MAX_VALUE kommen praktisch nicht vor, werden aber abgesichert. */
    private fun clampInt(v: Long): Int = v.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun currentLocale(context: Context): Locale =
        context.resources.configuration.locales.get(0)
}
