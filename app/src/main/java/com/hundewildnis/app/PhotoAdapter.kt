package com.hundewildnis.app

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale

/**
 * PhotoAdapter - Zeigt Foto-Vorschau mit Löschen-Button
 */
class PhotoAdapter(
    private val photos: MutableList<Uri>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivPhoto)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoUri = photos[position]
        
        // Foto laden mit Coil (Zuschnitt wie bisher: centerCrop)
        try {
            holder.imageView.load(photoUri) {
                placeholder(R.drawable.ic_placeholder_image)
                error(R.drawable.ic_error_image)
                scale(Scale.FILL)
            }
        } catch (e: Exception) {
            // Fallback: Direkt setzen
            holder.imageView.setImageResource(R.drawable.ic_error_image)
        }
        
        // Löschen-Button
        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount() = photos.size
    
    fun addPhoto(uri: Uri) {
        photos.add(uri)
        notifyItemInserted(photos.size - 1)
    }
    
    fun removePhoto(position: Int) {
        photos.removeAt(position)
        notifyItemRemoved(position)
    }
}
