package com.hundewildnis.app

import android.content.res.Configuration
import android.graphics.Color
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
            val context = binding.root.context
            val isDarkTheme = (context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // ✅ Haupt-Daten setzen
            binding.poiName.text = poi.name
            binding.poiIcon.text = poi.categoryIcon
            // Bei vorhandenem Subtyp (z.B. "Defibrillator", "Hydrant") diesen statt der
            // Oberkategorie zeigen – wichtig v.a. bei "Notfall", wo sonst jede Card nur
            // "Notfall" anzeigt und im Ernstfall nicht unterscheidbar wäre.
            binding.poiCategory.text = poi.subtype ?: poi.category
            binding.poiDistance.text = formatDistance(poi.distance)
            binding.poiDirection.text = "${poi.directionArrow} ${poi.direction}"

            // ✅ Detail-Farbe: Weiß im Dark Mode, Dunkelgrün im Light Mode
            val detailColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            // Alle Detail-TextViews:
            binding.poiAddress.setTextColor(detailColor)
            binding.poiPhone.setTextColor(Color.WHITE)
            binding.poiPhone.setLinkTextColor(Color.WHITE)
            binding.poiHours.setTextColor(detailColor)
            binding.poiDescription.setTextColor(detailColor)
            binding.poiOperator.setTextColor(detailColor)
            binding.poiAccess.setTextColor(detailColor)

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
                binding.poiWebsite.setTextColor(Color.WHITE)
                binding.poiWebsite.setLinkTextColor(Color.WHITE)
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