package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

data class UvForecastItem(
    val time: String,
    val uvIndex: Double,
    val label: String,
    val color: Int
)

class UvForecastAdapter(
    private val items: List<UvForecastItem>,
    private val onItemClick: (UvForecastItem) -> Unit
) : RecyclerView.Adapter<UvForecastAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText:  TextView = view.findViewById(R.id.uvTimeText)
        val valueText: TextView = view.findViewById(R.id.uvValueText)
        val labelText: TextView = view.findViewById(R.id.uvLabelText)
        val colorBar:  View     = view.findViewById(R.id.uvColorBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_uv_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.timeText.text  = item.time
        holder.valueText.text = String.format(Locale.GERMAN, "%.1f", item.uvIndex)
        holder.labelText.text = item.label
        holder.valueText.setTextColor(item.color)
        holder.labelText.setTextColor(item.color)
        holder.colorBar.setBackgroundColor(item.color)

        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
