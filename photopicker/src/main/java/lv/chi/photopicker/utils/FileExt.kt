package lv.chi.photopicker.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import lv.chi.photopicker.PickerConfiguration
import java.io.File

/**
 * Creates a Uri from the given file.
 *
 * @see Uri.fromFile
 */
fun File.toUri(): Uri = Uri.fromFile(this)


/** Creates a [File] from the given [Uri]. */
fun Uri.toFile(): File {
    require(scheme == "file") { "Uri lacks 'file' scheme: $this" }
    return File(requireNotNull(path) { "Uri path is null: $this" })
}


internal fun File.providerUri(context: Context) =
    FileProvider.getUriForFile(context, PickerConfiguration.getAuthority(), this)