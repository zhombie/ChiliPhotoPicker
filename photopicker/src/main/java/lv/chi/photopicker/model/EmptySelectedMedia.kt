package lv.chi.photopicker.model

import android.net.Uri

data class EmptySelectedMedia constructor(
    override val id: Long = -1L,
    override val fileUri: Uri = Uri.EMPTY,
    override val displayName: String = "",
    override val title: String = "",
    override val mimeType: String = "",
    override val width: Int = 0,
    override val height: Int = 0,
    override val dateAdded: Long = 0L,
    override val dateModified: Long = 0L,
    override val dateCreated: Long = 0L,
    override val size: Long = 0L,

    override var isSelected: Boolean = false
) : SelectedMedia