package lv.chi.photopicker.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.*

internal object FileUtils {

    fun getFile(context: Context, uri: Uri): File? {
        return when {
            uri.scheme?.startsWith("content", ignoreCase = true) == true ->
                fileFromContentUri(context, uri)
            uri.scheme?.startsWith("file", ignoreCase = true) == true ->
                uri.toFile()
            else ->
                return null
        }
    }

    fun fileFromContentUri(context: Context, contentUri: Uri): File {
        // Preparing temp file name
        val fileExtension = getFileExtension(context, contentUri)
        val fileName = "temp_file" + if (fileExtension != null) ".$fileExtension" else ""

        // Creating temp file
        val tempFile = File(context.cacheDir, fileName)
        tempFile.createNewFile()

        try {
            val outputStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)

            inputStream?.use {
                copy(inputStream, outputStream)
            }

            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tempFile
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        val fileType: String? = context.contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType)
    }

    @Throws(IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8 * 1024)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }

    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

}