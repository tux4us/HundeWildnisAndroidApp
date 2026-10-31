package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemSubRowBinding

/** Zeile der Abo-Verwaltung (Feed oder Podcast). [key] ist die Feed-Adresse. */
data class SubRow(val key: String, val name: String, val detail: String)

/** Liste der Abos mit „Entfernen“-Knopf; gemeinsam für Feed und Podcasts. */
class SubRowAdapter(
    private var rows: List<SubRow>,
    private val onRemove: (SubRow) -> Unit,
) : RecyclerView.Adapter<SubRowAdapter.Holder>() {

    class Holder(val binding: ItemSubRowBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newRows: List<SubRow>) {
        rows = newRows
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSubRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        holder.binding.name.text = row.name
        holder.binding.detail.text = row.detail
        holder.binding.btnRemove.setOnClickListener { onRemove(row) }
    }
}
