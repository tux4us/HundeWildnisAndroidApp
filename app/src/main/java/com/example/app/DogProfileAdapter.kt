package com.hundewildnis.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hundewildnis.app.databinding.ItemDogProfileBinding

class DogProfileAdapter(
    private val profiles: List<DogProfile>,
    private val onView: (DogProfile) -> Unit,
    private val onEdit: (DogProfile) -> Unit,
    private val onDelete: (DogProfile) -> Unit,
    private val onShare: (DogProfile) -> Unit,
    private val onQR: (DogProfile) -> Unit,
    private val onPDF: (DogProfile) -> Unit
) : RecyclerView.Adapter<DogProfileAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemDogProfileBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(profile: DogProfile) {
            // Name, Rasse, Info
            binding.tvName.text = profile.name
            binding.tvBreed.text = profile.breed
            binding.tvInfo.text = "${profile.gender} • ${profile.birthDate}"
            
            // Foto
            if (profile.photoUri.isNotEmpty()) {
                try {
                    binding.ivPhoto.setImageURI(Uri.parse(profile.photoUri))
                } catch (e: Exception) {
                    binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
                }
            } else {
                binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            }

            // Button Callbacks
            binding.btnView.setOnClickListener { onView(profile) }
            binding.btnEdit.setOnClickListener { onEdit(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
            binding.btnShare.setOnClickListener { onShare(profile) }
            binding.btnQR.setOnClickListener { onQR(profile) }
            binding.btnPDF.setOnClickListener { onPDF(profile) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDogProfileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    override fun getItemCount(): Int = profiles.size
}
