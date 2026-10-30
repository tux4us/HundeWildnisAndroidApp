package com.hundewildnis.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hundewildnis.app.databinding.ActivityCourseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Zeigt einen Kurs oder eine Lektion aus den lokalen Kursdaten im Glas-Design.
 *
 * Extras: EXTRA_COURSE_ID (Kurs-ID) und EXTRA_PATH (Indexpfad durch die verschachtelten Lektionen).
 *
 * Kursseite:   Einleitung als Glaskarte, darunter die Lektionen als Kacheln.
 * Lektionsseite: jede Zwischenüberschrift ("h") beginnt eine Glaskarte; Absätze und Listen stehen darin.
 *                Unten Schaltflächen für die vorherige und die nächste Lektion.
 */
class CourseActivity : SecureBaseActivity() {

    companion object {
        const val EXTRA_COURSE_ID = "course_id"
        const val EXTRA_PATH = "path"

        /** Rand- und Titelfarben der Karten, reihum (Gruppenfarben des Glas-Designs). */
        private val ACCENTS = intArrayOf(
            R.color.rd_group1_accent,
            R.color.rd_group5_accent,
            R.color.rd_group3_accent,
            R.color.rd_group4_accent,
            R.color.rd_group2_accent
        )
    }

    private lateinit var binding: ActivityCourseBinding

    private class Loaded(val lesson: CourseLesson, val parent: CourseLesson?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        val courseId = intent.getStringExtra(EXTRA_COURSE_ID) ?: run { finish(); return }
        val path = intent.getIntArrayExtra(EXTRA_PATH) ?: IntArray(0)

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    var parent: CourseLesson? = null
                    var current = CourseRepository.loadCourseById(this@CourseActivity, courseId)
                    for (index in path) {
                        parent = current
                        current = current?.lessons?.getOrNull(index)
                    }
                    current?.let { Loaded(it, parent) }
                } catch (e: Exception) {
                    null
                }
            }
            if (loaded == null) {
                Toast.makeText(this@CourseActivity, getString(R.string.courses_load_error), Toast.LENGTH_LONG).show()
                finish()
            } else {
                render(loaded, courseId, path)
            }
        }
    }

    // ── Aufbau ───────────────────────────────────────────────────────────────

    private fun render(loaded: Loaded, courseId: String, path: IntArray) {
        val lesson = loaded.lesson
        supportActionBar?.title = lesson.title
        val container = binding.courseContent
        container.removeAllViews()

        val isCourse = path.isEmpty()
        val sections = splitSections(lesson.blocks)

        if (isCourse) {
            // Kursseite: Einleitung (Untertitel) und Lektionsliste
            sections.forEachIndexed { i, section -> container.addView(sectionCard(section, i, lead = true)) }
        } else {
            sections.forEachIndexed { i, section -> container.addView(sectionCard(section, i, lead = false)) }
        }

        if (lesson.lessons.isNotEmpty()) {
            container.addView(label(getString(R.string.course_lessons_heading)))
            lesson.lessons.forEachIndexed { index, sub ->
                container.addView(lessonTile(index, sub, courseId, path))
            }
        }

        if (!isCourse) {
            container.addView(navigationRow(loaded.parent, courseId, path))
        }
    }

    private class Section(val title: String?, val blocks: MutableList<CourseBlock> = mutableListOf())

    /** Zerlegt die Blöcke: jede Überschrift ("h") beginnt einen neuen Abschnitt. */
    private fun splitSections(blocks: List<CourseBlock>): List<Section> {
        val result = mutableListOf<Section>()
        var current: Section? = null
        for (block in blocks) {
            if (block.type == "h") {
                current = Section(block.text).also { result.add(it) }
            } else {
                if (current == null) current = Section(null).also { result.add(it) }
                current.blocks.add(block)
            }
        }
        return result.filter { it.title != null || it.blocks.isNotEmpty() }
    }

    private fun sectionCard(section: Section, index: Int, lead: Boolean): View {
        val accent = ContextCompat.getColor(this, ACCENTS[index % ACCENTS.size])
        val card = glassCard(accent)
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
        }
        section.title?.let {
            inner.addView(text(it, 17f, color = accent, bold = true))
        }
        section.blocks.forEachIndexed { i, block ->
            val top = if (i == 0 && section.title == null) 0 else 10
            when (block.type) {
                "list" -> block.items.forEachIndexed { j, item ->
                    inner.addView(bulletRow(item, accent, if (j == 0) top else 6))
                }
                else -> inner.addView(
                    text(block.text, 16f, italic = lead, topMarginDp = top)
                )
            }
        }
        card.addView(inner)
        return card
    }

    private fun lessonTile(index: Int, sub: CourseLesson, courseId: String, path: IntArray): View {
        val accent = ContextCompat.getColor(this, ACCENTS[index % ACCENTS.size])
        val card = glassCard(accent).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@CourseActivity, CourseActivity::class.java).apply {
                    putExtra(EXTRA_COURSE_ID, courseId)
                    putExtra(EXTRA_PATH, path + index)
                })
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        row.addView(text("${index + 1}", 18f, color = accent, bold = true, wrapWidth = true).apply {
            minWidth = dp(28)
        })
        row.addView(text(sub.title, 16f).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(text("›", 22f, color = accent, bold = true, wrapWidth = true))
        card.addView(row)
        return card
    }

    private fun navigationRow(parent: CourseLesson?, courseId: String, path: IntArray): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20); bottomMargin = dp(16) }
        }
        val last = path.last()
        val siblings = parent?.lessons?.size ?: 0

        fun button(label: String, target: Int, first: Boolean) = GlassButton(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { if (!first) marginStart = dp(12) }
            setOnClickListener {
                startActivity(Intent(this@CourseActivity, CourseActivity::class.java).apply {
                    putExtra(EXTRA_COURSE_ID, courseId)
                    putExtra(EXTRA_PATH, path.copyOf().also { it[it.size - 1] = target })
                })
                finish()
            }
        }

        if (last > 0) row.addView(button("‹ " + getString(R.string.course_previous), last - 1, true))
        if (last + 1 < siblings) row.addView(button(getString(R.string.course_next) + " ›", last + 1, last == 0))
        return row
    }

    // ── Bausteine ────────────────────────────────────────────────────────────

    private fun glassCard(accent: Int): GlassCardView = GlassCardView(this).apply {
        radius = dp(16).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(ContextCompat.getColor(this@CourseActivity, R.color.gl_fill))
        setStrokeColor(accent)
        setStrokeWidth(dp(1) + dp(1) / 2)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
    }

    private fun bulletRow(item: String, accent: Int, topMarginDp: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(topMarginDp) }
        }
        row.addView(text("•", 16f, color = accent, bold = true, wrapWidth = true).apply { minWidth = dp(20) })
        row.addView(text(item, 16f).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        return row
    }

    private fun label(value: String): TextView =
        text(value.uppercase(), 12f, color = ContextCompat.getColor(this, R.color.rd_text_secondary), bold = true, topMarginDp = 24)
            .apply { letterSpacing = 0.08f }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int = ContextCompat.getColor(this, R.color.rd_text_primary),
        bold: Boolean = false,
        italic: Boolean = false,
        topMarginDp: Int = 0,
        wrapWidth: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        setLineSpacing(dp(3).toFloat(), 1f)
        setTextIsSelectable(false)
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        setTypeface(typeface, style)
        layoutParams = LinearLayout.LayoutParams(
            if (wrapWidth) LinearLayout.LayoutParams.WRAP_CONTENT else LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(topMarginDp) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
