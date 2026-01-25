package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// ERWEITERT: Mit feelsLike (gefühlte Temperatur)
data class ForecastItem(
    val time: String,
    val temperature: Double,
    val weatherCode: Int,
    val precipitation: Double,
    val feelsLike: Double = temperature  // NEU: Gefühlte Temperatur
)

// ERWEITERT: Mit Klick-Callback
class ForecastAdapter(
    private val items: List<ForecastItem>,
    private val onItemClick: (ForecastItem) -> Unit  // NEU: Klick-Handler
) : RecyclerView.Adapter<ForecastAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.timeText)
        val iconText: TextView = view.findViewById(R.id.iconText)
        val tempText: TextView = view.findViewById(R.id.tempText)
        val rainText: TextView = view.findViewById(R.id.rainText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.timeText.text = item.time
        holder.tempText.text = String.format("%.0f°C", item.temperature)

        val (_, icon) = getWeatherDescription(item.weatherCode)
        holder.iconText.text = icon

        holder.rainText.text = "💧 ${item.precipitation.toInt()}%"

        // NEU: Klickbar machen
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // NEU: Visuelles Feedback bei Klick
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true

        // NEU: Erhöhe Kontrast für bessere Lesbarkeit
        holder.itemView.setBackgroundResource(
            android.R.drawable.list_selector_background
        )
    }

    override fun getItemCount() = items.size

    private fun getWeatherDescription(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "Klar" to "☀️"
            1, 2, 3 -> "Bewölkt" to "⛅"
            45, 48 -> "Nebel" to "🌫️"
            51, 53, 55 -> "Nieselregen" to "🌦️"
            61, 63, 65 -> "Regen" to "🌧️"
            71, 73, 75 -> "Schneefall" to "🌨️"
            77 -> "Graupel" to "🌨️"
            80, 81, 82 -> "Regenschauer" to "🌦️"
            85, 86 -> "Schneeschauer" to "🌨️"
            95 -> "Gewitter" to "⛈️"
            96, 99 -> "Gewitter mit Hagel" to "⛈️"
            else -> "Unbekannt" to "🌡️"
        }
    }
}
