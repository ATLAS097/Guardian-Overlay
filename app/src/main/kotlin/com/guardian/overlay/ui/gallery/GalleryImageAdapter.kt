package com.guardian.overlay.ui.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.guardian.overlay.databinding.ItemGalleryImageBinding

data class GalleryImageItem(
    val id: Long,
    val uri: Uri
)

class GalleryImageAdapter(
    private val contentResolver: ContentResolver,
    private val onImageClicked: (GalleryImageItem) -> Unit
) : RecyclerView.Adapter<GalleryImageAdapter.GalleryViewHolder>() {
    private val items = mutableListOf<GalleryImageItem>()

    fun submitList(newItems: List<GalleryImageItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class GalleryViewHolder(
        private val binding: ItemGalleryImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GalleryImageItem) {
            binding.imageThumb.setImageURI(null)
            binding.imageThumb.setImageBitmap(loadThumb(item.id, item.uri))
            binding.root.setOnClickListener { onImageClicked(item) }
        }
    }

    private fun loadThumb(imageId: Long, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(uri, Size(280, 280), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Thumbnails.getThumbnail(
                    contentResolver,
                    imageId,
                    MediaStore.Images.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (_: Throwable) {
            null
        }
    }
}
