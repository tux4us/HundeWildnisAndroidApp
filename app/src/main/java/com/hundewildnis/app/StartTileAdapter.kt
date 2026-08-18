package com.hundewildnis.app

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Adapter für das Icon-Grid EINER Gruppe auf der Startseite.
 * Pro Gruppe wird ein eigener Adapter + eigene RecyclerView im Layout instanziiert,
 * damit jede Gruppe ihre eigene Akzentfarbe und ihren eigenen spanCount erhält.
 *
 * Die Gruppenfarben (chipColorRes/accentColorRes) werden von StartActivity bereits
 * passend zum aktuellen Theme (Hell/Dunkel) ausgewählt und übergeben — der Adapter
 * selbst muss also nicht zwischen den Modi unterscheiden, außer bei der NOTFALL-Kachel.
 */
class StartTileAdapter(
    private val tiles: List<StartTile>,
    private val chipColorRes: Int,
    private val accentColorRes: Int,
    private val onTileClick: (String) -> Unit
) : RecyclerView.Adapter<StartTileAdapter.TileViewHolder>() {

    inner class TileViewHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_start, parent, false) as MaterialCardView
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = tiles[position]
        val context = holder.card.context
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        val icon = holder.card.findViewById<ImageView>(R.id.tileIcon)
        val emoji = holder.card.findViewById<TextView>(R.id.tileEmoji)
        val label = holder.card.findViewById<TextView>(R.id.tileLabel)

        if (tile.iconRes != 0) {
            icon.visibility = View.VISIBLE
            emoji.visibility = View.GONE
            icon.setImageResource(tile.iconRes)
        } else if (tile.emoji != null) {
            icon.visibility = View.GONE
            emoji.visibility = View.VISIBLE
            emoji.text = tile.emoji
        } else {
            icon.visibility = View.INVISIBLE
            emoji.visibility = View.GONE
        }

        label.text = tile.label

        if (tile.isDanger) {
            // NOTFALL-Kachel: bleibt unabhängig von der Gruppenfarbe immer rot
            val dangerChipRes = if (isDark) R.color.rd_danger_chip_dark else R.color.rd_danger_chip_light
            val dangerChip = ContextCompat.getColor(context, dangerChipRes)
            val dangerText = ContextCompat.getColor(context, R.color.rd_danger_text)
            holder.card.setCardBackgroundColor(dangerChip)
            icon.setColorFilter(dangerText)
            label.setTextColor(dangerText)
        } else {
            // chipColorRes/accentColorRes kommen bereits themenkorrekt aufgelöst
            // von StartGroupColors.forGroup() — hier nur noch anwenden
            val textPrimaryRes = if (isDark) R.color.rd_text_primary_dark else R.color.rd_text_primary_light
            val chipColor = ContextCompat.getColor(context, chipColorRes)
            val accentColor = ContextCompat.getColor(context, accentColorRes)
            val textColor = ContextCompat.getColor(context, textPrimaryRes)
            holder.card.setCardBackgroundColor(chipColor)
            icon.setColorFilter(accentColor)
            label.setTextColor(textColor)
        }

        holder.card.setOnClickListener { onTileClick(tile.id) }
    }

    override fun getItemCount() = tiles.size
}
