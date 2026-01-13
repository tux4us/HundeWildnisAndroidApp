package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemContentBinding

class ContentAdapter(
    private val onItemClick: (ContentItem) -> Unit
) : ListAdapter<ContentItem, ContentAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemContentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(item: ContentItem) {
            // Einrückung basierend auf Level
            val paddingStart = (item.level * 32).coerceAtMost(128)
            binding.root.setPadding(paddingStart + 16, 16, 16, 16)

            // Icon und Styling basierend auf Typ und Level
            when (item.type) {
                "blog" -> {
                    // Blog-Einträge
                    binding.titleText.textSize = 16f
                    binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                    binding.titleText.setTextColor(android.graphics.Color.parseColor("#355d1a"))
                    binding.titleText.text = "💬 ${item.title}"
                }
                "page" -> {
                    // Webseiten
                    val icon = if (item.level == 0) "🐶🦉" else "   \uD83D\uDCDC"
                    binding.titleText.textSize = if (item.level == 0) 16f else 14f
                    binding.titleText.setTypeface(null, if (item.level == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.BOLD)
                    binding.titleText.setTextColor(android.graphics.Color.parseColor("#355d1a"))
                    binding.titleText.text = "$icon ${item.title}"
                }
                "course" -> {
                    // Onlinekurse - wie bisher
                    when (item.level) {
                        0 -> {
                            binding.titleText.textSize = 18f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor("#355d1a"))
                            binding.titleText.text = "📚 ${item.title}"
                        }
                        1 -> {
                            binding.titleText.textSize = 16f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor("#4a6135"))
                            binding.titleText.text = "📂 ${item.title}"
                        }
                        2 -> {
                            binding.titleText.textSize = 15f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor("#5a7145"))
                            binding.titleText.text = "   \uD83D\uDCD7 ${item.title}"
                        }
                        else -> {
                            binding.titleText.textSize = 14f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor("#666666"))
                            binding.titleText.text = "      \uD83D\uDCD6 ${item.title}"
                        }
                    }
                }
            }

            // Excerpt
            binding.excerptText.text = if (item.excerpt.isNotEmpty()) item.excerpt
            else if (item.date.isNotEmpty()) item.date
            else ""

            binding.excerptText.visibility = if (binding.excerptText.text.isEmpty())
                android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    // DiffCallback HIER - auf gleicher Ebene wie ViewHolder, NICHT darin!
    class DiffCallback : DiffUtil.ItemCallback<ContentItem>() {
        override fun areItemsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
            return oldItem == newItem
        }
    }
}