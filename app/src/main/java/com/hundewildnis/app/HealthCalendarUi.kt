package com.hundewildnis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Gemeinsame Zeilen-Bausteine für den Gesundheitskalender
 * (Abschnitt in DogViewActivity und HealthCalendarActivity).
 */
object HealthCalendarUi {

    fun buildRow(
        context: Context,
        item: HealthCalendarItem,
        onObservationClick: (HealthObservationEntry) -> Unit,
        onEmergencyClick: (EmergencyReport) -> Unit
    ): View = when (item) {
        is HealthCalendarItem.Observation -> buildObservationRow(context, item.entry) { onObservationClick(item.entry) }
        is HealthCalendarItem.Emergency -> buildEmergencyRow(context, item.report) { onEmergencyClick(item.report) }
    }

    // ════════════════════════════════════════════════════════════════
    // BEOBACHTUNG
    // ════════════════════════════════════════════════════════════════

    private fun buildObservationRow(
        context: Context,
        entry: HealthObservationEntry,
        onClick: () -> Unit
    ): View {
        val category = HealthCategory.fromCode(entry.category)
        val severity = HealthSeverity.fromCode(entry.severity)
        val status = HealthStatus.fromCode(entry.status)

        val container = rowContainer(context, onClick)

        container.addView(
            text(
                context,
                context.getString(
                    R.string.health_row_header_format,
                    category.emoji,
                    context.getString(category.labelRes),
                    context.getString(severity.labelRes)
                ),
                bold = true, sp = 14f
            )
        )

        val period = when {
            entry.endDate.isNotEmpty() -> {
                val days = entry.durationDays
                if (days != null) {
                    context.getString(R.string.health_row_period_closed_format, entry.startDate, entry.endDate, days)
                } else {
                    context.getString(R.string.health_row_period_closed_nodays_format, entry.startDate, entry.endDate)
                }
            }
            else -> context.getString(
                R.string.health_row_period_open_format,
                entry.startDate,
                context.getString(status.labelRes)
            )
        }
        container.addView(text(context, period, sp = 13f))

        val symptomLabels = entry.symptoms
            .mapNotNull { HealthSymptom.fromCode(it) }
            .joinToString(", ") { context.getString(it.labelRes) }
        if (symptomLabels.isNotEmpty()) {
            container.addView(text(context, symptomLabels, sp = 12f, alpha = 0.75f))
        }

        if (entry.notes.isNotEmpty()) {
            container.addView(text(context, entry.notes, sp = 12f, alpha = 0.75f, maxLines = 2))
        }

        if (entry.vetVisited) {
            val vet = if (entry.vetNotes.isNotEmpty()) {
                context.getString(R.string.health_row_vet_with_notes_format, entry.vetNotes)
            } else {
                context.getString(R.string.health_row_vet)
            }
            container.addView(text(context, vet, sp = 12f, alpha = 0.75f, maxLines = 2))
        }

        if (entry.photoUris.isNotEmpty()) {
            container.addView(
                text(context, context.getString(R.string.health_row_photos_format, entry.photoUris.size), sp = 12f, alpha = 0.75f)
            )
        }

        container.addView(divider(context))
        return container
    }

    // ════════════════════════════════════════════════════════════════
    // NOTFALLMELDUNG (nur Anzeige, Daten bleiben in EmergencyActivity)
    // ════════════════════════════════════════════════════════════════

    private fun buildEmergencyRow(
        context: Context,
        report: EmergencyReport,
        onClick: () -> Unit
    ): View {
        val container = rowContainer(context, onClick)

        container.addView(
            text(
                context,
                context.getString(R.string.health_row_emergency_header_format, HealthDates.format(report.timestamp)),
                bold = true, sp = 14f
            )
        )
        if (report.symptom.isNotEmpty()) {
            container.addView(text(context, report.symptom, sp = 13f, maxLines = 2))
        }
        if (report.notes.isNotEmpty()) {
            container.addView(text(context, report.notes, sp = 12f, alpha = 0.75f, maxLines = 2))
        }
        container.addView(text(context, context.getString(R.string.health_row_emergency_hint), sp = 11f, alpha = 0.55f))

        container.addView(divider(context))
        return container
    }

    /** Öffnet das PDF einer Notfallmeldung (gleiche Logik wie EmergencyActivity.openPdf). */
    fun openEmergencyPdf(context: Context, report: EmergencyReport) {
        try {
            val uri: Uri? = when {
                !report.contentUri.isNullOrEmpty() -> Uri.parse(report.contentUri)
                report.pdfPath.isNotEmpty() && File(report.pdfPath).exists() ->
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(report.pdfPath))
                else -> null
            }
            if (uri == null) {
                Toast.makeText(context, context.getString(R.string.emergency_toast_pdf_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
                context.grantUriPermission(it.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.emergency_pdf_open_chooser_title))
            )
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.emergency_toast_generic_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    // ════════════════════════════════════════════════════════════════
    // BAUSTEINE
    // ════════════════════════════════════════════════════════════════

    private fun rowContainer(context: Context, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 8, 0, 8)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null)
        setOnClickListener { onClick() }
    }

    private fun text(
        context: Context,
        value: String,
        bold: Boolean = false,
        sp: Float = 14f,
        alpha: Float = 1f,
        maxLines: Int = Int.MAX_VALUE
    ) = TextView(context).apply {
        text = value
        textSize = sp
        this.alpha = alpha
        this.maxLines = maxLines
        if (maxLines != Int.MAX_VALUE) ellipsize = android.text.TextUtils.TruncateAt.END
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 2, 0, 2) }
    }

    private fun divider(context: Context) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .apply { setMargins(0, 8, 0, 0) }
        setBackgroundColor(0x33FFFFFF)
    }

    fun infoText(context: Context, value: String) = TextView(context).apply {
        text = value
        textSize = 13f
        alpha = 0.6f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 4, 0, 8) }
    }
}
