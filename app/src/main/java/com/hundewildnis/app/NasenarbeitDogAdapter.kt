package com.hundewildnis.app

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemDogCardBinding
import java.text.SimpleDateFormat
import java.util.*

class NasenarbeitDogAdapter(
    private val context: Context,
    private val profiles: List<DogProfile>,
    private val onDogClick: (DogProfile) -> Unit,
    private val onExportPdf: (DogProfile) -> Unit,
    private val onExportCsv: (DogProfile) -> Unit,
    private val onDeleteData: (DogProfile) -> Unit
) : RecyclerView.Adapter<NasenarbeitDogAdapter.DogViewHolder>() {

    inner class DogViewHolder(val binding: ItemDogCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DogViewHolder {
        val binding = ItemDogCardBinding.inflate(LayoutInflater.from(context), parent, false)
        return DogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DogViewHolder, position: Int) {
        val profile = profiles[position]
        val binding = holder.binding

        binding.tvDogName.text = profile.name
        binding.tvDogBreed.text = "${profile.breed}, ${GenderCode.displayName(context, profile.gender)}"

        if (profile.photoUri.isNotEmpty()) {
            binding.ivDogPhoto.setImageURI(Uri.parse(profile.photoUri))
        } else {
            binding.ivDogPhoto.setImageResource(R.drawable.ic_dog_placeholder)
        }

        // Nasenarbeit-Statistik laden
        val fortschritt = NasenarbeitManager.loadFortschrittForDog(context, profile.id)
        val lastDate = if (fortschritt.letzteSession > 0) {
            SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(fortschritt.letzteSession))
        } else context.getString(R.string.nasenarbeit_dog_adapter_no_training_yet)

        binding.tvDogInfo.text = buildString {
            append(context.getString(R.string.nasenarbeit_dog_adapter_header)).append("\n")
            append("  🐾 ${context.getString(R.string.nasenarbeit_disziplin_verlorensuche)}: ")
            append(context.getString(R.string.nasenarbeit_dog_adapter_sessions_count, fortschritt.sessionsVerlorensuche))
            append(" (${fortschritt.levelVerlorensuche.displayName(context)})\n")
            append("  🧑 ${context.getString(R.string.nasenarbeit_disziplin_personensuche)}: ")
            append(context.getString(R.string.nasenarbeit_dog_adapter_sessions_count, fortschritt.sessionsPersonensuche))
            append(" (${fortschritt.levelPersonensuche.displayName(context)})\n")
            append("  🔍 ${context.getString(R.string.nasenarbeit_disziplin_freisuche)}: ")
            append(context.getString(R.string.nasenarbeit_dog_adapter_sessions_count, fortschritt.sessionsFreisuche))
            append(" (${fortschritt.levelFreisuche.displayName(context)})\n")
            append(context.getString(R.string.nasenarbeit_dog_adapter_total_line, fortschritt.gesamtSessions, lastDate))
        }

        // Click-Listeners
        binding.layoutDogInfo.setOnClickListener { onDogClick(profile) }
        binding.btnExportPdf.setOnClickListener { onExportPdf(profile) }
        binding.btnExportCsv.setOnClickListener { onExportCsv(profile) }
        binding.btnDeleteTrainingData.setOnClickListener { onDeleteData(profile) }
    }

    override fun getItemCount(): Int = profiles.size
}