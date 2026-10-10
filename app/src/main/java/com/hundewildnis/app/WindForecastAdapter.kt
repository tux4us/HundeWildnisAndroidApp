package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class WindForecastAdapter(
    private val items: List<WeatherActivity.WindForecastItem>
) : RecyclerView.Adapter<WindForecastAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText:      TextView = view.findViewById(R.id.windTimeText)
        val arrowText:     TextView = view.findViewById(R.id.windArrowText)
        val speedText:     TextView = view.findViewById(R.id.windSpeedText)
        val gustText:      TextView = view.findViewById(R.id.windGustText)
        val beaufortText:  TextView = view.findViewById(R.id.windBeaufortText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wind_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.timeText.text     = item.time
        holder.arrowText.text    = degreeToArrow(item.windDegree)
        val context = holder.itemView.context
        holder.speedText.text    = context.getString(R.string.weather_wind_speed_unit, item.windSpeed)
        holder.gustText.text     = context.getString(R.string.weather_wind_gusts_label, item.windGusts)
        holder.beaufortText.text = context.getString(R.string.weather_beaufort_label, item.beaufort)

        // Beaufort-Farbkodierung
        val color = beaufortColor(item.beaufort)
        holder.speedText.setTextColor(color)
        holder.beaufortText.setTextColor(color)
    }

    override fun getItemCount() = items.size

    private fun degreeToArrow(degree: Double): String = when {
        degree >= 337.5 || degree < 22.5  -> "↓"   // Wind kommt von Norden → zeigt nach Süden
        degree >= 22.5  && degree < 67.5  -> "↙"
        degree >= 67.5  && degree < 112.5 -> "←"
        degree >= 112.5 && degree < 157.5 -> "↖"
        degree >= 157.5 && degree < 202.5 -> "↑"
        degree >= 202.5 && degree < 247.5 -> "↗"
        degree >= 247.5 && degree < 292.5 -> "→"
        degree >= 292.5 && degree < 337.5 -> "↘"
        else -> "•"
    }

    private fun beaufortColor(bft: Int): Int = when {
        bft <= 3 -> android.graphics.Color.parseColor("#4CAF50")   // grün
        bft <= 5 -> android.graphics.Color.parseColor("#FFC107")   // gelb
        bft <= 7 -> android.graphics.Color.parseColor("#FF9800")   // orange
        else     -> android.graphics.Color.parseColor("#FF6F60")   // rot
    }
}
