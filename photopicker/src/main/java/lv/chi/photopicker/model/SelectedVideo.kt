package lv.chi.photopicker.model

import android.net.Uri
import java.util.concurrent.TimeUnit

data class SelectedVideo constructor(
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
    val duration: Long,
    override val size: Long,

    override var isSelected: Boolean = false
) : SelectedMedia {

    fun getDisplayDuration(): String {
        return try {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
            val seconds = duration % minutes.toInt()

            "${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
        } catch (exception: ArithmeticException) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(duration)

            String.format("00:%02d", seconds)
        }
    }

}