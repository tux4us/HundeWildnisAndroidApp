package com.hundewildnis.app

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// ════════════════════════════════════════════════════════════════
// GEFAHRENPFLANZEN-SCOUT – RecyclerView Adapter
// Datei: PlantListAdapter.kt
// ════════════════════════════════════════════════════════════════

class PlantListAdapter(
    private val items: MutableList<ToxicPlant>,
    private val onClick: (ToxicPlant) -> Unit
) : RecyclerView.Adapter<PlantListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName:      TextView       = view.findViewById(R.id.tvPlantName)
        val tvLatin:     TextView       = view.findViewById(R.id.tvPlantLatin)
        val tvToxicity:  TextView       = view.findViewById(R.id.tvPlantToxicity)
        val tvSeasonal:  TextView       = view.findViewById(R.id.tvPlantSeasonal)
        val tvSystems:   TextView       = view.findViewById(R.id.tvPlantSystems)
        val cardStripe:  View           = view.findViewById(R.id.viewToxicityStripe)
        val card:        MaterialCardView = view.findViewById(R.id.cardPlantItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plant, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val plant = items[position]

        val ctx = holder.itemView.context
        holder.tvName.text    = plant.name(ctx)
        holder.tvLatin.text   = plant.latinName
        holder.tvToxicity.text = "${plant.toxicityLevel.icon} ${plant.toxicityLevel.displayName(ctx)}"
        holder.tvSystems.text = plant.affectedSystems.take(2)
            .joinToString(", ") { it.displayName(ctx) } +
                if (plant.affectedSystems.size > 2) " +" else ""

        // Saisonaler Indikator
        if (plant.isCurrentlySeasonal()) {
            holder.tvSeasonal.visibility = View.VISIBLE
            holder.tvSeasonal.text = ctx.getString(R.string.plant_list_currently_seasonal)
        } else {
            holder.tvSeasonal.visibility = View.GONE
        }

        // Farbstreifen links nach Giftigkeitsstufe
        holder.cardStripe.setBackgroundColor(
            Color.parseColor(plant.toxicityLevel.color)
        )

        holder.tvToxicity.setTextColor(Color.parseColor(plant.toxicityLevel.color))
        holder.card.strokeColor = Color.parseColor(plant.toxicityLevel.color)
        holder.card.strokeWidth = (1.5f * ctx.resources.displayMetrics.density).toInt()

        holder.card.setOnClickListener { onClick(plant) }
    }

    fun updateList(newList: List<ToxicPlant>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
