package com.hundewildnis.app

import android.content.Context
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
        val context = holder.itemView.context

        holder.timeText.text = item.time
        holder.tempText.text = context.getString(R.string.weather_degree_celsius, item.temperature)

        val (_, icon) = getWeatherDescription(context, item.weatherCode)
        holder.iconText.text = icon

        holder.rainText.text = context.getString(R.string.weather_rain_percent, item.precipitation.toInt())

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

    private fun getWeatherDescription(context: Context, code: Int): Pair<String, String> {
        val resId = when (code) {
            0            -> R.string.weather_desc_clear
            1, 2, 3      -> R.string.weather_desc_cloudy
            45, 48       -> R.string.weather_desc_fog
            51, 53, 55   -> R.string.weather_desc_light_drizzle
            61, 63, 65   -> R.string.weather_desc_light_rain
            71, 73, 75   -> R.string.weather_desc_light_snow
            77           -> R.string.weather_desc_snow_grains
            80, 81, 82   -> R.string.weather_desc_light_showers
            85, 86       -> R.string.weather_desc_light_snow_showers
            95           -> R.string.weather_desc_thunderstorm
            96, 99       -> R.string.weather_desc_thunderstorm_light_hail
            else         -> R.string.weather_unknown
        }
        val icon = when (code) {
            0            -> "☀️"
            1, 2, 3      -> "⛅"
            45, 48       -> "🌫️"
            51, 53, 55   -> "🌦️"
            61, 63, 65   -> "🌧️"
            71, 73, 75   -> "🌨️"
            77           -> "🌨️"
            80, 81, 82   -> "🌦️"
            85, 86       -> "🌨️"
            95           -> "⛈️"
            96, 99       -> "⛈️"
            else         -> "🌡️"
        }
        return context.getString(resId) to icon
    }
}
