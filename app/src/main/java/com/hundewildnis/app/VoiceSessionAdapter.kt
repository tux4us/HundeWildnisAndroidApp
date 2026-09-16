package com.hundewildnis.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemVoiceSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class VoiceSessionAdapter(
    private val sessions: List<VoiceSession>,
    private val onItemClick: (VoiceSession) -> Unit,
    private val onDeleteClick: (VoiceSession) -> Unit
) : RecyclerView.Adapter<VoiceSessionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemVoiceSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemVoiceSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(Date(session.startTime))
        
        holder.binding.tvSessionDate.text = date
        
        val efficiency = session.getEfficiency().toInt()
        val duration = session.totalDuration / 1000
        val min = duration / 60
        val sec = duration % 60
        val durationStr = String.format("%d:%02d", min, sec)
        
        holder.binding.tvSessionSummary.text = 
            holder.itemView.context.getString(R.string.voice_session_efficiency, efficiency, durationStr)
        
        holder.binding.sessionClickArea.setOnClickListener { onItemClick(session) }
        holder.binding.btnDeleteSession.setOnClickListener { onDeleteClick(session) }
    }

    override fun getItemCount() = sessions.size
}
