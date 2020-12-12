package lv.chi.photopicker.model

import android.net.Uri

data class SelectedImage constructor(
    override val id: Long,
    override val fileUri: Uri,
    override val displayName: String,
    override val title: String,
    override val mimeType: String,
    override val width: Int,
    override val height: Int,
    override val dateAdded: Long,
    override val dateModified: Long,
    override val dateCreated: Long,
    override val size: Long,

    override var isSelected: Boolean = false
) : SelectedMedia