package lv.chi.photopicker

import android.content.ContentUris
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lv.chi.photopicker.model.*
import lv.chi.photopicker.utils.MediaFile
import lv.chi.photopicker.utils.SingleLiveEvent
import java.util.concurrent.TimeUnit

internal class MediaPickerViewModel : ViewModel() {

    companion object {
        private val TAG = MediaPickerViewModel::class.java.simpleName

        const val SELECTION_UNDEFINED = -1
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        error.printStackTrace()
    }

    private val hasContentData = MutableLiveData(false)
    private val inProgressData = MutableLiveData(true)
    private val hasPermissionData = MutableLiveData(false)
    private val selectedData = MutableLiveData<ArrayList<SelectedMedia>>(arrayListOf())
    private val mediaData = MutableLiveData<ArrayList<SelectedMedia>>()
    private val maxSelectionReachedData = SingleLiveEvent<Unit>()

    private var maxSelectionCount = SELECTION_UNDEFINED

    fun getHasContent(): LiveData<Boolean> = Transformations.distinctUntilChanged(hasContentData)
    fun getInProgress(): LiveData<Boolean> = inProgressData
    fun getHasPermission(): LiveData<Boolean> = hasPermissionData
    fun getSelected(): LiveData<ArrayList<SelectedMedia>> = selectedData
    fun getMedia(): LiveData<ArrayList<SelectedMedia>> = mediaData
    fun getMaxSelectionReached(): LiveData<Unit> = maxSelectionReachedData

    fun setHasPermission(hasPermission: Boolean) {
        hasPermissionData.postValue(hasPermission)
    }

    fun setMaxSelectionCount(count: Int) {
        maxSelectionCount = count
    }

    fun clearSelected() {
//        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
//            val media = requireNotNull(mediaData.value).map { it.copy(selected = false) }
//            val array = arrayListOf<SelectableMedia>()
//            array.addAll(media)
//            mediaData.postValue(array)
//            selectedData.postValue(arrayListOf())
//        }
    }

    suspend fun setMedia(pickerMode: MediaPickerFragment.PickerMode, cursor: Cursor?) {
        withContext(Dispatchers.IO + exceptionHandler){
            if (cursor == null) return@withContext
            val array = arrayListOf<SelectedMedia>()
            array.addAll(
                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .map {
                        when (pickerMode) {
                            MediaPickerFragment.PickerMode.IMAGE ->
                                readImageAtCursor(cursor)
                            MediaPickerFragment.PickerMode.VIDEO ->
                                readVideoAtCursor(cursor)
                            MediaPickerFragment.PickerMode.IMAGE_AND_VIDEO ->
                                readFileAtCursor(cursor) ?: EmptySelectedMedia()
                        }
                    }
                    .toList()
            )
            hasContentData.postValue(array.isNotEmpty())
            mediaData.postValue(array)
        }
    }

    fun setInProgress(progress: Boolean) {
        inProgressData.postValue(progress)
    }

    fun toggleSelected(selectedMedia: SelectedMedia) {
//        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
//            val selected = requireNotNull(selectedData.value)
//
//            when {
//                selectableMedia.selected -> selected.removeAll { it.id == selectableMedia.id }
//                canSelectMore(selected.size) -> selected.add(selectableMedia)
//                else -> {
//                    maxSelectionReachedData.postValue(Unit)
//                    return@launch
//                }
//            }
//
//            val media = requireNotNull(mediaData.value)
//            media.indexOfFirst { item -> item.id == selectableMedia.id }
//                .takeIf { position -> position != -1 }
//                ?.let { position ->
//                    media[position] = selectableMedia.copy(selected = !selectableMedia.selected)
//                }
//
//            selectedData.postValue(selected)
//            mediaData.postValue(media)
//        }
    }

    private fun canSelectMore(size: Int): Boolean =
            maxSelectionCount == SELECTION_UNDEFINED || maxSelectionCount > size

    private fun readImageAtCursor(cursor: Cursor): SelectedImage {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID))
        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.TITLE))
        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.WIDTH))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.HEIGHT))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.MIME_TYPE))

        var dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_ADDED))
        dateAdded = TimeUnit.SECONDS.toMillis(dateAdded)

        var dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_MODIFIED))
        dateModified = TimeUnit.SECONDS.toMillis(dateModified)

        val dateTaken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN))
        } else {
            0L
        }

        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.SIZE))

        return SelectedImage(
            id = id,
            fileUri = contentUri,
            displayName = displayName,
            title = title,
            mimeType = mimeType,
            width = width,
            height = height,
            dateAdded = dateAdded,
            dateModified = dateModified,
            dateCreated = dateTaken,
            size = size
        )
    }

    private fun readVideoAtCursor(cursor: Cursor): SelectedVideo {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns._ID))
        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DISPLAY_NAME))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.TITLE))
        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.WIDTH))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.HEIGHT))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.MIME_TYPE))

        var dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_ADDED))
        dateAdded = TimeUnit.SECONDS.toMillis(dateAdded)

        var dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_MODIFIED))
        dateModified = TimeUnit.SECONDS.toMillis(dateModified)

        val dateTaken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_TAKEN))
        } else {
            0L
        }

        val duration = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION))
        } else {
            val retriever = MediaMetadataRetriever()
//            retriever.setDataSource(contentUri.toFile().absolutePath)
            retriever.setDataSource(contentUri.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            retriever.close()
            duration
        }

        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.SIZE))

        return SelectedVideo(
            id = id,
            fileUri = contentUri,
            displayName = displayName,
            title = title,
            mimeType = mimeType,
            width = width,
            height = height,
            dateAdded = dateAdded,
            dateModified = dateModified,
            dateCreated = dateTaken,
            duration = duration,
            size = size
        )
    }

    private fun readFileAtCursor(cursor: Cursor): SelectedFile? {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))

        val fileType = MediaFile.getFileTypeForMimeType(mimeType)
        val type: SelectedFile.Type?
        val contentUri: Uri?

        when {
            MediaFile.isImageFileType(fileType) -> {
                type = SelectedFile.Type.IMAGE
                contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
            MediaFile.isVideoFileType(fileType) -> {
                type = SelectedFile.Type.VIDEO
                contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
            MediaFile.isAudioFileType(fileType) -> {
                type = SelectedFile.Type.AUDIO
                contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
            else -> {
                type = null
                contentUri = null
            }
        }

        if (type == null || contentUri == null) {
            return null
        }

        val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.TITLE))
        val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH))
        val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT))

        var dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
        dateAdded = TimeUnit.SECONDS.toMillis(dateAdded)

        var dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED))
        dateModified = TimeUnit.SECONDS.toMillis(dateModified)

        val dateTaken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN))
        } else {
            0L
        }

        val duration = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION))
        } else {
            val retriever = MediaMetadataRetriever()
//            retriever.setDataSource(contentUri.toFile().absolutePath)
            retriever.setDataSource(contentUri.path)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
            retriever.close()
            duration
        }

        val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE))

        return SelectedFile(
            id = id,
            type = type,
            fileUri = contentUri,
            displayName = displayName,
            title = title,
            mimeType = mimeType,
            width = width,
            height = height,
            dateAdded = dateAdded,
            dateModified = dateModified,
            dateCreated = dateTaken,
            duration = duration,
            size = size
        )
    }

}