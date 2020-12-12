package lv.chi.photopicker.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader
import lv.chi.photopicker.model.SelectedFile
import lv.chi.photopicker.model.SelectedImage
import lv.chi.photopicker.model.SelectedMedia
import lv.chi.photopicker.model.SelectedVideo

internal class MediaPickerAdapter(
    private val onMediaClick: (SelectedMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : RecyclerView.Adapter<MediaPickerAdapter.MediaPickerViewHolder>() {

    companion object {
        private val TAG = MediaPickerAdapter::class.java.simpleName

        private val diffCallback = object : DiffUtil.ItemCallback<SelectedMedia>() {
            override fun areItemsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SelectedMedia, newItem: SelectedMedia): Boolean =
                oldItem == newItem

            override fun getChangePayload(
                oldItem: SelectedMedia,
                newItem: SelectedMedia
            ): Any? = when {
                oldItem.isSelected != newItem.isSelected -> Payload.SELECTED
                else -> null
            }
        }
    }

    private object ViewType {
        const val UNKNOWN = 99
        const val IMAGE = 100
        const val VIDEO = 101
    }

    private object Payload {
        const val SELECTED = "selected"
    }

    private val asyncListDiffer: AsyncListDiffer<SelectedMedia> by lazy {
        AsyncListDiffer(this, diffCallback)
    }

    fun submitList(list: List<SelectedMedia>) {
        asyncListDiffer.submitList(list)
    }

    override fun getItemCount(): Int = asyncListDiffer.currentList.size

    private fun getItem(position: Int): SelectedMedia = asyncListDiffer.currentList[position]

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        Log.d(TAG, "item: $item")
        return when (item) {
            is SelectedImage -> ViewType.IMAGE
            is SelectedVideo -> ViewType.VIDEO
            is SelectedFile -> {
                when (item.type) {
                    SelectedFile.Type.IMAGE -> ViewType.IMAGE
                    SelectedFile.Type.VIDEO -> ViewType.VIDEO
                    else -> ViewType.UNKNOWN
                }
            }
            else -> ViewType.UNKNOWN
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaPickerViewHolder {
        val holder = when (type) {
            ViewType.IMAGE, ViewType.UNKNOWN -> {
                ImagePickerViewHolder(
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.view_selectable_image, parent, false)
                )
            }
            ViewType.VIDEO -> {
                VideoPickerViewHolder(
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.view_selectable_video, parent, false)
                )
            }
            else ->
                throw IllegalStateException("Something wrong happened. There is no ViewHolder for this viewType.")
        }
        holder.checkBox.visibility = if (multiple) View.VISIBLE else View.GONE
        return holder
    }

    override fun onBindViewHolder(
        holder: MediaPickerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        payloads.firstOrNull()?.let {
            if (it is String && it == Payload.SELECTED) {
                holder.checkBox.isChecked = getItem(position).isSelected
            }
        } ?: super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: MediaPickerViewHolder, position: Int) = with(holder) {
        val item = getItem(position)

        imageLoader.loadImage(itemView.context, holder.imageView, item.fileUri)

        holder.checkBox.isChecked = item.isSelected

        if (item is SelectedVideo && holder is VideoPickerViewHolder) {
            holder.durationView.text = item.getDisplayDuration()
        }

        itemView.setOnClickListener { onMediaClick(getItem(position)) }
    }

    open class MediaPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ShapeableImageView = itemView.findViewById(R.id.imageView)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
    }

    class ImagePickerViewHolder(view: View) : MediaPickerViewHolder(view)

    class VideoPickerViewHolder(view: View) : MediaPickerViewHolder(view) {
        val durationView: MaterialTextView = itemView.findViewById(R.id.durationView)
    }

}