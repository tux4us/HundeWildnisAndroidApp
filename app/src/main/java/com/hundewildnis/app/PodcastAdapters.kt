package com.hundewildnis.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemEpisodeBinding
import com.hundewildnis.app.databinding.ItemPodcastBinding
import com.hundewildnis.app.podcast.Episode
import com.hundewildnis.app.podcast.Podcast
import com.hundewildnis.app.podcast.PodcastData
import com.hundewildnis.app.podcast.PodcastLogic
import java.text.DateFormat
import java.util.Date

/** Liste der abonnierten Podcasts. */
class PodcastRowAdapter(
    private val onClick: (Podcast) -> Unit,
) : RecyclerView.Adapter<PodcastRowAdapter.Holder>() {

    class Holder(val binding: ItemPodcastBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<Podcast> = emptyList()
    private var counts: Map<String, Int> = emptyMap()

    fun submit(podcasts: List<Podcast>, data: PodcastData) {
        items = podcasts
        counts = data.episodes.mapValues { it.value.size }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPodcastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val podcast = items[position]
        val b = holder.binding
        b.title.text = podcast.title.ifBlank { podcast.feedUrl.substringAfter("://").substringBefore('/') }
        val count = counts[podcast.feedUrl] ?: 0
        val context = b.root.context
        b.meta.text = listOfNotNull(
            podcast.author.ifBlank { null },
            if (count > 0) context.getString(R.string.pod_podcast_meta, count) else null,
        ).joinToString(" · ")
        b.meta.visibility = if (b.meta.text.isNullOrBlank()) View.GONE else View.VISIBLE
        b.root.setOnClickListener { onClick(podcast) }
    }
}

/** Liste von Folgen. Gehörte Folgen sind abgeblendet; die laufende Folge ist mit ▶ markiert. */
class EpisodeAdapter(
    private val onPlay: (Episode) -> Unit,
    private val onInfo: (Episode) -> Unit,
    private val onLongClick: (Episode) -> Unit,
) : RecyclerView.Adapter<EpisodeAdapter.Holder>() {

    class Holder(val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<Episode> = emptyList()
    private var data = PodcastData()
    private var showPodcast = false
    private var currentId: String? = null
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    fun submit(episodes: List<Episode>, podcastData: PodcastData, withPodcastName: Boolean, playingId: String?) {
        items = episodes
        data = podcastData
        showPodcast = withPodcastName
        currentId = playingId
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val episode = items[position]
        val b = holder.binding
        val context = b.root.context
        val played = episode.id in data.played
        val current = episode.id == currentId
        b.title.text = if (current) "▶ ${episode.title}" else episode.title
        b.title.setTypeface(null, if (played) Typeface.NORMAL else Typeface.BOLD)
        b.title.alpha = if (played && !current) 0.65f else 1f

        val parts = ArrayList<String>()
        if (showPodcast) parts.add(episode.podcastTitle)
        if (episode.publishedSeconds > 0) parts.add(dateFormat.format(Date(episode.publishedSeconds * 1000)))
        if (episode.durationSeconds > 0) parts.add(PodcastLogic.formatClock(episode.durationSeconds.toLong()))
        b.meta.text = parts.joinToString(" · ")

        val resume = PodcastLogic.resumePosition(data, episode.id)
        val status = when {
            played -> context.getString(R.string.pod_played)
            resume > 0 -> context.getString(R.string.pod_resume_at, PodcastLogic.formatClock(resume / 1000))
            else -> ""
        }
        b.status.text = status
        b.status.visibility = if (status.isEmpty()) View.GONE else View.VISIBLE

        b.root.setOnClickListener { onPlay(episode) }
        b.root.setOnLongClickListener {
            onLongClick(episode)
            true
        }
        b.btnInfo.setOnClickListener { onInfo(episode) }
    }
}
