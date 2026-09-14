package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemSoundLevelSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class SoundLevelSessionAdapter(
    private val sessions: List<SoundLevelSession>,
    private val onItemClick: (SoundLevelSession) -> Unit,
    private val onDeleteClick: (SoundLevelSession) -> Unit
) : RecyclerView.Adapter<SoundLevelSessionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemSoundLevelSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSoundLevelSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))

        holder.binding.tvSessionDate.text = date

        val duration = session.totalDuration / 1000
        val min = duration / 60
        val sec = duration % 60
        val dominant = session.getDominantLevel()

        holder.binding.tvSessionSummary.text = holder.itemView.context.getString(
            R.string.sound_level_session_summary,
            session.avgEstimatedDb.toInt(), dominant.label(holder.itemView.context), min, sec
        )

        holder.binding.sessionClickArea.setOnClickListener { onItemClick(session) }
        holder.binding.btnDeleteSession.setOnClickListener { onDeleteClick(session) }
    }

    override fun getItemCount() = sessions.size
}
