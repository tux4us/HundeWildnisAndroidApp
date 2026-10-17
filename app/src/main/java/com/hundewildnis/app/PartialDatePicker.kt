package com.hundewildnis.app

import android.app.Activity
import android.app.DatePickerDialog
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import java.util.Calendar
import java.util.Locale

/**
 * Datumsauswahl mit wählbarer Genauigkeit. Liefert Text im Format
 * dd.MM.yyyy, MM.yyyy oder yyyy (wie DogProfile.birthDate).
 * Zukünftige Zeitpunkte sind nicht wählbar.
 */
object PartialDatePicker {

    private const val YEARS_BACK = 40

    fun showFull(activity: Activity, onPicked: (String) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(
            activity,
            { _, year, month, day ->
                onPicked(String.format(Locale.ROOT, "%02d.%02d.%04d", day, month + 1, year))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    fun showMonthYear(activity: Activity, onPicked: (String) -> Unit) {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val view = activity.layoutInflater.inflate(R.layout.dialog_month_year_picker, null)
        val monthPicker = view.findViewById<NumberPicker>(R.id.monthPicker)
        val yearPicker = view.findViewById<NumberPicker>(R.id.yearPicker)

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = now.get(Calendar.MONTH) + 1
        yearPicker.minValue = currentYear - YEARS_BACK
        yearPicker.maxValue = currentYear
        yearPicker.value = currentYear

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dog_edit_month_year_dialog_title))
            .setView(view)
            .setPositiveButton(activity.getString(R.string.dog_edit_ok_button)) { _, _ ->
                var month = monthPicker.value
                if (yearPicker.value == currentYear) {
                    month = minOf(month, now.get(Calendar.MONTH) + 1)
                }
                onPicked(String.format(Locale.ROOT, "%02d.%04d", month, yearPicker.value))
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    fun showYear(activity: Activity, onPicked: (String) -> Unit) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearPicker = NumberPicker(activity).apply {
            minValue = currentYear - YEARS_BACK
            maxValue = currentYear
            value = currentYear
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.dog_edit_year_dialog_title))
            .setView(yearPicker)
            .setPositiveButton(activity.getString(R.string.dog_edit_ok_button)) { _, _ ->
                onPicked(yearPicker.value.toString())
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }
}
