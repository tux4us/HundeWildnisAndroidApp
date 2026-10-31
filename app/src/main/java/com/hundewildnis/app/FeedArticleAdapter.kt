package com.hundewildnis.app

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemFeedArticleBinding
import com.hundewildnis.app.feed.FeedArticle
import com.hundewildnis.app.feed.FeedText
import java.text.DateFormat
import java.util.Date

/** Artikelliste des Feeds; ungelesene Artikel sind fett. */
class FeedArticleAdapter(
    private val onClick: (FeedArticle) -> Unit,
) : RecyclerView.Adapter<FeedArticleAdapter.Holder>() {

    class Holder(val binding: ItemFeedArticleBinding) : RecyclerView.ViewHolder(binding.root)

    private var items: List<FeedArticle> = emptyList()
    private var readIds: Set<String> = emptySet()
    private val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)

    fun submit(newItems: List<FeedArticle>, read: Set<String>) {
        items = newItems
        readIds = read
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFeedArticleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val article = items[position]
        val unread = article.id !in readIds
        val b = holder.binding
        b.title.text = article.title
        b.title.setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
        b.title.alpha = if (unread) 1f else 0.7f
        val date = dateFormat.format(Date(article.publishedSeconds * 1000))
        b.meta.text = listOfNotNull(article.feedTitle.ifBlank { null }, article.author?.ifBlank { null }, date).joinToString(" · ")
        val excerpt = FeedText.excerpt(FeedText.head(article.body, 3000), 200)
        b.excerpt.text = excerpt
        b.excerpt.visibility = if (excerpt.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        b.root.setOnClickListener { onClick(article) }
    }
}
