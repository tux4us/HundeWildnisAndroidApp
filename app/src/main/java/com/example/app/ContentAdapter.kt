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

            // Erkenne Dark Mode
            val nightMode = binding.root.context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES

            // Grüntöne je nach Theme
            val darkGreen = if (isDarkMode) "#6b8e4e" else "#2d5016"
            val mediumGreen = if (isDarkMode) "#8fad6f" else "#4a6135"
            val lightGreen = if (isDarkMode) "#a3c187" else "#5a7145"

            // Icon und Styling basierend auf Typ und Level
            when (item.type) {
                "blog" -> {
                    // Blog-Einträge
                    binding.titleText.textSize = 16f
                    binding.titleText.setTypeface(null, android.graphics.Typeface.NORMAL)
                    binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                    binding.titleText.text = "💬 ${item.title}"
                }
                "page" -> {
                    // Webseiten mit verschiedenen Icons je nach Level
                    val (icon, textSize, typeface) = when (item.level) {
                        0 -> Triple("🐶🦉", 16f, android.graphics.Typeface.BOLD)
                        1 -> Triple("📁", 16f, android.graphics.Typeface.BOLD)
                        2 -> Triple("   \uD83D\uDCDC", 14f, android.graphics.Typeface.NORMAL)
                        else -> Triple("      \uD83D\uDCDC", 13f, android.graphics.Typeface.NORMAL)
                    }

                    binding.titleText.textSize = textSize
                    binding.titleText.setTypeface(null, typeface)
                    binding.titleText.setTextColor(android.graphics.Color.parseColor(
                        when (item.level) {
                            0, 1 -> darkGreen
                            2 -> mediumGreen
                            else -> lightGreen
                        }
                    ))
                    binding.titleText.text = "$icon ${item.title}"
                }
                "course" -> {
                    // Onlinekurse - mit angepassten Farben
                    when (item.level) {
                        0 -> {
                            binding.titleText.textSize = 18f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                            binding.titleText.text = "📚 ${item.title}"
                        }
                        1 -> {
                            binding.titleText.textSize = 16f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                            binding.titleText.text = "📂 ${item.title}"
                        }
                        2 -> {
                            binding.titleText.textSize = 15f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(mediumGreen))
                            binding.titleText.text = "   📖 ${item.title}"
                        }
                        else -> {
                            binding.titleText.textSize = 14f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.NORMAL)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(lightGreen))
                            binding.titleText.text = "      • ${item.title}"
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