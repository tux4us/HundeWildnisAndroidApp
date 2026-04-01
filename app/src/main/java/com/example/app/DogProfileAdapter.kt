package com.hundewildnis.app

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.hundewildnis.app.databinding.ItemDogProfileBinding

class DogProfileAdapter(
    private val profiles: List<DogProfile>,
    private val onView: (DogProfile) -> Unit,
    private val onEdit: (DogProfile) -> Unit,
    private val onDelete: (DogProfile) -> Unit,
    private val onShare: (DogProfile, Set<String>) -> Unit,
    private val onQR: (DogProfile, Set<String>) -> Unit,
    private val onPDF: (DogProfile, Set<String>) -> Unit,
    private val onEmergency: (DogProfile) -> Unit // ✅ NEU
) : RecyclerView.Adapter<DogProfileAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemDogProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: DogProfile) {
            val isDarkTheme = (binding.root.context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            val card = binding.root as MaterialCardView
            if (isDarkTheme) {
                card.setCardBackgroundColor(Color.parseColor("#2D2D2D"))
                card.strokeColor = Color.parseColor("#404040")
                card.strokeWidth = 2
            } else {
                card.setCardBackgroundColor(Color.WHITE)
                card.strokeWidth = 0
            }

            binding.tvName.text = profile.name
            binding.tvBreed.text = profile.breed
            binding.tvInfo.text = "${profile.gender} • ${profile.birthDate}"

            if (profile.photoUri.isNotEmpty()) {
                try {
                    binding.ivPhoto.setImageURI(Uri.parse(profile.photoUri))
                } catch (e: Exception) {
                    binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
                }
            } else {
                binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            }

            val buttonTextColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")
            val buttonStrokeColor = if (isDarkTheme) Color.WHITE else Color.parseColor("#2d5016")

            listOf(
                binding.btnView,
                binding.btnEdit,
                binding.btnQR,
                binding.btnPDF,
                binding.btnShare
            ).forEach { button ->
                button.setTextColor(buttonTextColor)
                button.strokeColor = android.content.res.ColorStateList.valueOf(buttonStrokeColor)
            }

            binding.btnDelete.setTextColor(Color.WHITE)
            
            // Notfall Button Styling
            binding.btnEmergency.setOnClickListener { onEmergency(profile) }

            binding.btnView.setOnClickListener { onView(profile) }
            binding.btnEdit.setOnClickListener { onEdit(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
            binding.btnShare.setOnClickListener {
                PrivacyDialogHelper.showFieldSelectionDialog(
                    context = binding.root.context,
                    profile = profile,
                    title = "📤 Teilen Datenauswahl"
                ) { selectedFields ->
                    onShare(profile, selectedFields)
                }
            }
            binding.btnQR.setOnClickListener {
                PrivacyDialogHelper.showFieldSelectionDialog(
                    context = binding.root.context,
                    profile = profile,
                    title = "📱 QR-Code Datenauswahl"
                ) { selectedFields ->
                    onQR(profile, selectedFields)
                }
            }
            binding.btnPDF.setOnClickListener {
                PrivacyDialogHelper.showFieldSelectionDialog(
                    context = binding.root.context,
                    profile = profile,
                    title = "📄 PDF Datenauswahl"
                ) { selectedFields ->
                    onPDF(profile, selectedFields)
                }
            }
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