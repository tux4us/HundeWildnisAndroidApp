package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class POIAdapter(
    private val pois: List<POI>,
    private val onPOIClick: (POI) -> Unit
) : RecyclerView.Adapter<POIAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.poiNameText)
        val categoryText: TextView = view.findViewById(R.id.poiCategoryText)
        val distanceText: TextView = view.findViewById(R.id.poiDistanceText)
        val iconText: TextView = view.findViewById(R.id.poiIconText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poi, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val poi = pois[position]

        holder.iconText.text = poi.categoryIcon
        holder.nameText.text = poi.name
        holder.categoryText.text = poi.category
        holder.distanceText.text = formatDistance(poi.distance)

        // Klick-Handler
        holder.itemView.setOnClickListener {
            onPOIClick(poi)
        }

        // Spezielle Kennzeichnung für hundefreundliche Orte
        if (poi.tags["dog"] == "yes" || poi.tags["dog"] == "unleashed") {
            holder.nameText.text = "🐕 ${poi.name}"
        }

        // Warnung bei Leinenpflicht
        if (poi.tags["dog"] == "leashed" || poi.tags["leash"] == "yes") {
            holder.categoryText.text = "${poi.category} • 🦮 Leinenpflicht"
        }

        // Warnung bei Hundeverbot
        if (poi.tags["dog"] == "no") {
            holder.categoryText.text = "${poi.category} • ⛔ Keine Hunde"
            holder.itemView.alpha = 0.5f
        }
    }

    override fun getItemCount() = pois.size

    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            else -> "${String.format("%.1f", meters / 1000)} km"
        }
    }
}