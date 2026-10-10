package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import coil.request.CachePolicy
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
            val paddingStart = (item.level * 32).coerceAtMost(64)  // ← Reduziert von 128 auf 64
            binding.root.setPadding(paddingStart + 16, 16, 16, 16)

            // Grüntöne (für dunklen Hintergrund)
            val darkGreen = "#6b8e4e"
            val mediumGreen = "#8fad6f"
            val lightGreen = "#a3c187"

            // Icon und Styling basierend auf Typ und Level
            when (item.type) {
                "blog" -> {
                    binding.titleText.textSize = 16f
                    binding.titleText.setTypeface(null, android.graphics.Typeface.NORMAL)
                    binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                    binding.titleText.text = "💬 ${item.title}"
                }
                "page" -> {
                    val (icon, textSize, typeface) = when (item.level) {
                        0 -> Triple("🐶🦉", 16f, android.graphics.Typeface.BOLD)
                        1 -> Triple("📁", 16f, android.graphics.Typeface.BOLD)
                        2 -> Triple("   📄", 14f, android.graphics.Typeface.NORMAL)
                        else -> Triple("      📄", 13f, android.graphics.Typeface.NORMAL)
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
                    // Vereinfachte Kurs-Hierarchie: Nur noch 3 Ebenen (0, 1, 2)
                    when (item.level) {
                        0 -> {
                            // Ebene 0: Hauptseite "Onlinekurse Hundeschule"
                            binding.titleText.textSize = 18f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                            binding.titleText.text = "📚 ${item.title}"
                        }
                        1 -> {
                            // Ebene 1: "Kursverzeichnis Hundeschule"
                            binding.titleText.textSize = 16f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(darkGreen))
                            binding.titleText.text = "📂 ${item.title}"
                        }
                        else -> {
                            // Ebene 2: Einzelkurse (alle Kurse aus Kursverzeichnis)
                            binding.titleText.textSize = 15f
                            binding.titleText.setTypeface(null, android.graphics.Typeface.NORMAL)
                            binding.titleText.setTextColor(android.graphics.Color.parseColor(mediumGreen))
                            binding.titleText.text = "   📖 ${item.title}"
                        }
                    }
                }
            }

            // Datum (nur für Blog-Beiträge einblenden)
            if (item.type == "blog" && item.date.isNotEmpty()) {
                binding.dateText.text = item.date
                binding.dateText.visibility = android.view.View.VISIBLE
            } else {
                binding.dateText.visibility = android.view.View.GONE
            }

            // Excerpt
            binding.excerptText.text = item.excerpt
            binding.excerptText.visibility = if (item.excerpt.isEmpty())
                android.view.View.GONE else android.view.View.VISIBLE

            // ⚡ OPTIMIERT: Bild-Loading mit Coil
            if (item.imageUrl.isNotEmpty()) {
                binding.itemImage.visibility = View.VISIBLE
                binding.itemImage.load(item.imageUrl) {
                    // Thumbnail-Größe (300px breit)
                    size(300)
                    scale(Scale.FIT)

                    // Aggressive Caching
                    diskCachePolicy(CachePolicy.ENABLED)
                    memoryCachePolicy(CachePolicy.ENABLED)

                    // Placeholder während Laden
                    placeholder(R.drawable.ic_placeholder_image)

                    // Fehler-Bild
                    error(R.drawable.ic_error_image)

                    // Crossfade Animation
                    crossfade(200)
                }
            } else {
                binding.itemImage.visibility = View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ContentItem>() {
        override fun areItemsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: ContentItem, newItem: ContentItem): Boolean {
            return oldItem == newItem
        }
    }
}