package com.hundewildnis.app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemVitalRecordBinding

class VitalListAdapter(
    private val context: Context,
    private val records: MutableList<VitalRecord>,
    private val shoulderHeightCm: Int?,
    private val onEdit: (VitalRecord) -> Unit,
    private val onShare: (VitalRecord) -> Unit,
    private val onDelete: (VitalRecord) -> Unit
) : RecyclerView.Adapter<VitalListAdapter.VitalViewHolder>() {

    inner class VitalViewHolder(val binding: ItemVitalRecordBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VitalViewHolder {
        val binding = ItemVitalRecordBinding.inflate(
            LayoutInflater.from(context), parent, false
        )
        return VitalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VitalViewHolder, position: Int) {
        val record = records[position]
        val b = holder.binding

        // Datum
        b.tvDate.text = record.formattedDate()

        // Belastungsszenario
        b.tvScenario.text = context.getString(
            R.string.vital_item_scenario_format,
            LoadScenarioCode.displayName(context, record.loadScenario)
        )

        // Vitalwerte-Zusammenfassung
        b.tvSummary.text = record.summaryLine(context)

        // Notizen (nur wenn vorhanden)
        if (record.notes.isNotBlank()) {
            b.tvNotes.text = context.getString(R.string.vital_item_notes_format, record.notes)
            b.tvNotes.visibility = View.VISIBLE
        } else {
            b.tvNotes.visibility = View.GONE
        }

        // Puls-Status-Badge
        val status = record.pulseStatus(shoulderHeightCm)
        when (status) {
            PulseStatus.NORMAL -> {
                b.tvPulseStatus.text = context.getString(R.string.vital_item_status_normal)
                b.tvPulseStatus.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.gl_success_fill))
                b.tvPulseStatus.visibility = View.VISIBLE
            }
            PulseStatus.HIGH -> {
                b.tvPulseStatus.text = context.getString(R.string.vital_item_status_high)
                b.tvPulseStatus.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.gl_danger_fill))
                b.tvPulseStatus.visibility = View.VISIBLE
            }
            PulseStatus.LOW -> {
                b.tvPulseStatus.text = context.getString(R.string.vital_item_status_low)
                b.tvPulseStatus.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.gl_warning_fill))
                b.tvPulseStatus.visibility = View.VISIBLE
            }
            PulseStatus.UNKNOWN -> {
                b.tvPulseStatus.visibility = View.GONE
            }
        }

        // Click auf Karte → Detail/Edit
        holder.itemView.setOnClickListener { onEdit(record) }
        b.btnEdit.setOnClickListener { onEdit(record) }
        b.btnShare.setOnClickListener { onShare(record) }
        b.btnDelete.setOnClickListener { onDelete(record) }
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<VitalRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
    }

    fun removeRecord(record: VitalRecord) {
        val idx = records.indexOfFirst { it.id == record.id }
        if (idx >= 0) {
            records.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }
}
