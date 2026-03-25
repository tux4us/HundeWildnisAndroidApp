package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemPoiExtendedBinding

class POIAdapter(
    private val pois: List<POI>,
    private val onPOIClick: (POI) -> Unit
) : RecyclerView.Adapter<POIAdapter.POIViewHolder>() {

    inner class POIViewHolder(private val binding: ItemPoiExtendedBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(poi: POI) {
            // Basis-Informationen
            binding.poiIcon.text = poi.categoryIcon
            binding.poiCategory.text = poi.category
            binding.poiName.text = poi.name
            binding.poiDistance.text = formatDistance(poi.distance)
            binding.poiDirection.text = "${poi.directionArrow} ${poi.direction}"

            // ✅ Erweiterte Details - nur anzeigen wenn vorhanden
            var hasDetails = false

            // Adresse
            if (!poi.address.isNullOrEmpty()) {
                binding.addressLayout.visibility = View.VISIBLE
                binding.poiAddress.text = poi.address
                hasDetails = true
            } else {
                binding.addressLayout.visibility = View.GONE
            }

            // Telefon
            if (!poi.phone.isNullOrEmpty()) {
                binding.phoneLayout.visibility = View.VISIBLE
                binding.poiPhone.text = poi.phone
                hasDetails = true
            } else {
                binding.phoneLayout.visibility = View.GONE
            }

            // Website
            if (!poi.website.isNullOrEmpty()) {
                binding.websiteLayout.visibility = View.VISIBLE
                binding.poiWebsite.text = poi.website
                hasDetails = true
            } else {
                binding.websiteLayout.visibility = View.GONE
            }

            // Öffnungszeiten
            if (!poi.openingHours.isNullOrEmpty()) {
                binding.hoursLayout.visibility = View.VISIBLE
                binding.poiHours.text = poi.openingHours
                hasDetails = true
            } else {
                binding.hoursLayout.visibility = View.GONE
            }

            // Beschreibung
            if (!poi.description.isNullOrEmpty()) {
                binding.descriptionLayout.visibility = View.VISIBLE
                binding.poiDescription.text = poi.description
                hasDetails = true
            } else {
                binding.descriptionLayout.visibility = View.GONE
            }

            // Betreiber
            if (!poi.operator.isNullOrEmpty()) {
                binding.operatorLayout.visibility = View.VISIBLE
                binding.poiOperator.text = poi.operator
                hasDetails = true
            } else {
                binding.operatorLayout.visibility = View.GONE
            }

            // Zugang
            if (!poi.access.isNullOrEmpty()) {
                binding.accessLayout.visibility = View.VISIBLE
                binding.poiAccess.text = when (poi.access) {
                    "yes" -> "Frei zugänglich"
                    "no" -> "Kein Zugang"
                    "private" -> "Privat"
                    "permissive" -> "Bedingt zugänglich"
                    "leashed" -> "🐕 Nur an der Leine"
                    "no" -> "🐕 Hunde nicht erlaubt"
                    else -> poi.access
                }
                hasDetails = true
            } else {
                binding.accessLayout.visibility = View.GONE
            }

            // Details-Container nur anzeigen wenn Details vorhanden
            binding.poiDetailsContainer.visibility = if (hasDetails) View.VISIBLE else View.GONE

            // Click-Listener
            binding.poiContainer.setOnClickListener {
                onPOIClick(poi)
            }
        }

        private fun formatDistance(meters: Double): String {
            return when {
                meters < 1000 -> "${meters.toInt()} m"
                else -> "${String.format("%.1f", meters / 1000)} km"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): POIViewHolder {
        val binding = ItemPoiExtendedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return POIViewHolder(binding)
    }

    override fun onBindViewHolder(holder: POIViewHolder, position: Int) {
        holder.bind(pois[position])
    }

    override fun getItemCount() = pois.size
}
