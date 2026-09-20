package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class DailyForecastAdapter(
    private val items: List<WeatherActivity.DailyForecastItem>,
    private val onItemClick: (WeatherActivity.DailyForecastItem) -> Unit
) : RecyclerView.Adapter<DailyForecastAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dayText: TextView      = view.findViewById(R.id.dailyDayText)
        val dateText: TextView     = view.findViewById(R.id.dailyDateText)
        val iconText: TextView     = view.findViewById(R.id.dailyIconText)
        val maxTempText: TextView  = view.findViewById(R.id.dailyMaxTempText)
        val minTempText: TextView  = view.findViewById(R.id.dailyMinTempText)
        val rainText: TextView     = view.findViewById(R.id.dailyRainText)
        val windText: TextView     = view.findViewById(R.id.dailyWindText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.dayText.text  = item.dayLabel
        holder.dateText.text = item.dateLabel
        holder.iconText.text = getWeatherIcon(item.weatherCode)
        val context = holder.itemView.context
        holder.maxTempText.text = context.getString(R.string.weather_temp_degree, item.tempMax)
        holder.minTempText.text = context.getString(R.string.weather_temp_degree, item.tempMin)
        holder.rainText.text = context.getString(R.string.weather_rain_percent, item.precipProbability)
        val beaufort = getBeaufortScale(item.windSpeedMax)
        holder.windText.text = context.getString(R.string.weather_wind_bft, beaufort)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
    }

    override fun getItemCount() = items.size

    private fun getWeatherIcon(code: Int): String = when (code) {
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

    private fun getBeaufortScale(windKmh: Double): Int = when {
        windKmh < 1   -> 0
        windKmh < 6   -> 1
        windKmh < 12  -> 2
        windKmh < 20  -> 3
        windKmh < 29  -> 4
        windKmh < 39  -> 5
        windKmh < 50  -> 6
        windKmh < 62  -> 7
        windKmh < 75  -> 8
        windKmh < 89  -> 9
        windKmh < 103 -> 10
        windKmh < 118 -> 11
        else          -> 12
    }
}
