package com.example.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.POI
import com.hundewildnis.app.R
import com.hundewildnis.app.databinding.ItemPoiBinding

class POIAdapter(
    private val items: List<POI>,
    private val onItemClick: (POI) -> Unit
) : RecyclerView.Adapter<POIAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemPoiBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPoiBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val poi = items[position]

        with(holder.binding) {
            poiIcon.text = poi.categoryIcon
            poiName.text = poi.name
            poiCategory.text = poi.category

            // Zeige Entfernung mit Richtung
            val distanceStr = when {
                poi.distance < 1000 -> "${poi.distance.toInt()} m"
                else -> "${String.format("%.1f", poi.distance / 1000)} km"
            }
            poiDistance.text = "$distanceStr ${poi.direction} ${poi.directionArrow}"

            // Hunde-Tags
            val dogTag = when {
                poi.tags["dog"] == "yes" -> "🐕 Hundefreundlich"
                poi.tags["dog"] == "unleashed" -> "🐕 Freilauf erlaubt"
                poi.tags["dog"] == "leashed" -> "🦮 Leinenpflicht"
                poi.tags["dog"] == "no" -> "⛔ Keine Hunde"
                else -> null
            }

            if (dogTag != null) {
                poiCategory.text = "${poi.category} • $dogTag"
                if (dogTag.contains("Keine Hunde")) {
                    root.alpha = 0.5f
                } else {
                    root.alpha = 1.0f
                }
            }

            root.setOnClickListener {
                onItemClick(poi)
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
