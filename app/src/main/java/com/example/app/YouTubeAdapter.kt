package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.*

class YouTubeAdapter(
    private val onVideoClick: (YouTubeVideo) -> Unit
) : ListAdapter<YouTubeVideo, YouTubeAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_youtube_video, parent, false)
        return VideoViewHolder(view, onVideoClick)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class VideoViewHolder(
        itemView: View,
        private val onVideoClick: (YouTubeVideo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val thumbnailView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val titleView: TextView = itemView.findViewById(R.id.videoTitle)
        private val descriptionView: TextView = itemView.findViewById(R.id.videoDescription)
        private val dateView: TextView = itemView.findViewById(R.id.videoDate)

        fun bind(video: YouTubeVideo) {
            titleView.text = video.title
            descriptionView.text = video.description.take(150)
            dateView.text = formatDate(video.publishedAt)

            // Lade Thumbnail mit Coil
            thumbnailView.load(video.thumbnailUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_menu_report_image)
            }

            itemView.setOnClickListener {
                onVideoClick(video)
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(dateString)

                val now = Date()
                val diff = now.time - (date?.time ?: 0)

                val seconds = diff / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24

                when {
                    days > 30 -> "vor ${days / 30} Monat(en)"
                    days > 0 -> "vor $days Tag(en)"
                    hours > 0 -> "vor $hours Stunde(n)"
                    minutes > 0 -> "vor $minutes Minute(n)"
                    else -> "gerade eben"
                }
            } catch (e: Exception) {
                dateString
            }
        }
    }

    private class VideoDiffCallback : DiffUtil.ItemCallback<YouTubeVideo>() {
        override fun areItemsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem.videoId == newItem.videoId
        }

        override fun areContentsTheSame(oldItem: YouTubeVideo, newItem: YouTubeVideo): Boolean {
            return oldItem == newItem
        }
    }
}