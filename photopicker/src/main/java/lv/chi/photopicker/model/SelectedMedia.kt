package lv.chi.photopicker.model

import android.net.Uri

interface SelectedMedia : Selectable {
    val id: Long
    val fileUri: Uri
    val displayName: String?
    val title: String?
    val mimeType: String?
    val width: Int?
    val height: Int?
    val dateAdded: Long  // milliseconds
    val dateModified: Long  // milliseconds
    val dateCreated: Long?  // milliseconds
    val size: Long

    override fun equals(other: Any?): Boolean
}