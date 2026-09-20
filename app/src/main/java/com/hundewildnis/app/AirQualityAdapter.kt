package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AirQualityAdapter(
    private val items: List<WeatherActivity.AirQualityHourItem>
) : RecyclerView.Adapter<AirQualityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText:  TextView = view.findViewById(R.id.aqiTimeText)
        val aqiText:   TextView = view.findViewById(R.id.aqiValueText)
        val labelText: TextView = view.findViewById(R.id.aqiLabelText)
        val pm25Text:  TextView = view.findViewById(R.id.aqiPm25Text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_air_quality, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.timeText.text  = item.time
        holder.aqiText.text   = "${item.aqi}"
        holder.labelText.text = item.aqiLabel
        holder.pm25Text.text  = context.getString(R.string.weather_pm25_label, item.pm25)

        holder.aqiText.setTextColor(item.aqiColor)
        holder.labelText.setTextColor(item.aqiColor)
    }

    override fun getItemCount() = items.size
}
