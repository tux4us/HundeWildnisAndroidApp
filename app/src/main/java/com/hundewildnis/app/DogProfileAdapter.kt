package com.hundewildnis.app

import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
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
            val context = binding.root.context
            val isDarkTheme = (context.resources.configuration.uiMode and
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
            binding.tvInfo.text = context.getString(
                R.string.dog_info_gender_birthdate_format,
                GenderCode.displayName(context, profile.gender),
                profile.birthDate
            )

            if (profile.photoUri.isNotEmpty()) {
                try {
                    binding.ivPhoto.setImageURI(Uri.parse(profile.photoUri))
                } catch (e: Exception) {
                    binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
                }
            } else {
                binding.ivPhoto.setImageResource(R.drawable.ic_dog_placeholder)
            }

            setupActionGrids(profile)
        }

        private fun setupActionGrids(profile: DogProfile) {
            val context = binding.root.context

            // Standard Aktionen
            val actionTiles = listOf(
                StartTile("dog_view", R.drawable.ic_info, context.getString(R.string.dog_tile_view)),
                StartTile("dog_edit", R.drawable.ic_notes, context.getString(R.string.dog_tile_edit)),
                StartTile("dog_qr", R.drawable.ic_signal, context.getString(R.string.dog_tile_qr)),
                StartTile("dog_pdf", R.drawable.ic_notes, context.getString(R.string.dog_tile_pdf)),
                StartTile("dog_share", R.drawable.ic_map_pin_share, context.getString(R.string.dog_tile_share)),
                StartTile("dog_delete", R.drawable.ic_remove, context.getString(R.string.dog_tile_delete))
            )

            val colorsActions = StartGroupColors.forGroup(context, 1) // Ocker/Gelb
            binding.gridDogActions.layoutManager = GridLayoutManager(context, 3)
            binding.gridDogActions.adapter = StartTileAdapter(
                tiles = actionTiles,
                chipColorRes = colorsActions.chipColorRes,
                accentColorRes = colorsActions.accentColorRes,
                onTileClick = { id ->
                    when(id) {
                        "dog_view" -> onView(profile)
                        "dog_edit" -> onEdit(profile)
                        "dog_qr" -> PrivacyDialogHelper.showFieldSelectionDialog(context, profile, context.getString(R.string.dog_qr_field_selection_title)) { onQR(profile, it) }
                        "dog_pdf" -> PrivacyDialogHelper.showFieldSelectionDialog(context, profile, context.getString(R.string.dog_pdf_field_selection_title)) { onPDF(profile, it) }
                        "dog_share" -> PrivacyDialogHelper.showFieldSelectionDialog(context, profile, context.getString(R.string.dog_share_field_selection_title)) { onShare(profile, it) }
                        "dog_delete" -> onDelete(profile)
                    }
                }
            )

            // Notfall
            val emergencyTiles = listOf(
                StartTile("dog_emergency", R.drawable.ic_emergency, context.getString(R.string.dog_tile_emergency), isDanger = true)
            )

            val colorsEmergency = StartGroupColors.forGroup(context, 4) // Blau (wird vom Adapter bei isDanger ignoriert)
            binding.gridDogEmergency.layoutManager = GridLayoutManager(context, 1)
            binding.gridDogEmergency.adapter = StartTileAdapter(
                tiles = emergencyTiles,
                chipColorRes = colorsEmergency.chipColorRes,
                accentColorRes = colorsEmergency.accentColorRes,
                onTileClick = { onEmergency(profile) }
            )
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