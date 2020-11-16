package lv.chi.photopicker

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lv.chi.photopicker.MediaPickerViewModel.Companion.SELECTION_UNDEFINED
import lv.chi.photopicker.adapter.MediaPickerAdapter
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.ext.*
import lv.chi.photopicker.ext.Intents
import lv.chi.photopicker.ext.isPermissionGranted
import lv.chi.photopicker.ext.parentAs
import lv.chi.photopicker.utils.CameraActivity
import lv.chi.photopicker.utils.NonDismissibleBehavior
import lv.chi.photopicker.utils.SpacingItemDecoration

class MediaPickerFragment : DialogFragment() {

    companion object {
        private const val KEY_MULTIPLE = "KEY_MULTIPLE"
        private const val KEY_ALLOW_CAMERA = "KEY_ALLOW_CAMERA"
        private const val KEY_THEME = "KEY_THEME"
        private const val KEY_MAX_SELECTION = "KEY_MAX_SELECTION"
        private const val KEY_PICKER_MODE = "KEY_PICKER_MODE"

        fun newInstance(
            multiple: Boolean = false,
            allowCamera: Boolean = false,
            maxSelection: Int = SELECTION_UNDEFINED,
            pickerMode: PickerMode = PickerMode.ANY,
            @StyleRes theme: Int = R.style.MediaPicker_Light
        ) = MediaPickerFragment().apply {
            arguments = Bundle().apply {
                putBoolean(KEY_MULTIPLE, multiple)
                putBoolean(KEY_ALLOW_CAMERA, allowCamera)
                putInt(KEY_MAX_SELECTION, maxSelection)
                putSerializable(KEY_PICKER_MODE, pickerMode)
                putInt(KEY_THEME, theme)
            }
        }

        private fun getTheme(args: Bundle) = args.getInt(KEY_THEME)
        private fun getAllowCamera(args: Bundle) = args.getBoolean(KEY_ALLOW_CAMERA)
        private fun getAllowMultiple(args: Bundle) = args.getBoolean(KEY_MULTIPLE)
        private fun getMaxSelection(args: Bundle) = args.getInt(KEY_MAX_SELECTION)
        private fun getPickerMode(args: Bundle) = args.getSerializable(KEY_PICKER_MODE) as PickerMode
    }

    enum class PickerMode {
        IMAGE,
        VIDEO,
        ANY
    }

    private object Request {
        const val MEDIA_ACCESS_PERMISSION = 1
        const val ADD_IMAGE_CAMERA = 2
        const val ADD_IMAGE_GALLERY = 3
    }

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var bottomSheetDialog: FrameLayout
    private lateinit var galleryButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var emptyText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var permissionTextView: TextView
    private lateinit var grantTextView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var mediaPickerAdapter: MediaPickerAdapter

    private lateinit var viewModel: MediaPickerViewModel

    private var behavior: BottomSheetBehavior<FrameLayout>? = null

    private var snackBar: Snackbar? = null

    private val cornerRadiusOutValue = TypedValue()

    private lateinit var contextWrapper: ContextThemeWrapper

    private lateinit var pickerMode: PickerMode

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        error.printStackTrace()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(MediaPickerViewModel::class.java)
        viewModel.setMaxSelectionCount(getMaxSelection(requireArguments()))

        contextWrapper = ContextThemeWrapper(context, getTheme(requireArguments()))

        pickerMode = getPickerMode(requireArguments())

        mediaPickerAdapter = MediaPickerAdapter(
            lifecycleScope = lifecycleScope,
            onMediaClick = ::onMediaClicked,
            multiple = getAllowMultiple(requireArguments()),
            imageLoader = PickerConfiguration.getImageLoader()
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return PickerDialog(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        LayoutInflater.from(contextWrapper).inflate(
            R.layout.fragment_media_picker,
            container,
            false
        ).apply {
            contextWrapper.theme.resolveAttribute(R.attr.pickerCornerRadius, cornerRadiusOutValue, true)

            coordinatorLayout = findViewById(R.id.coordinator_layout)
            bottomSheetDialog = findViewById(R.id.bottom_sheet)
            galleryButton = findViewById(R.id.gallery_button)
            cameraButton = findViewById(R.id.camera_button)
            emptyText = findViewById(R.id.empty_text)
            recyclerView = findViewById(R.id.recycler_view)
            permissionTextView = findViewById(R.id.permission_text_view)
            grantTextView = findViewById(R.id.grant_text_view)
            progressBar = findViewById(R.id.progress_bar)

            recyclerView.apply {
                adapter = mediaPickerAdapter
                val margin = context.resources.getDimensionPixelSize(R.dimen.margin_2dp)
                addItemDecoration(SpacingItemDecoration(margin, margin, margin, margin))
                layoutManager = GridLayoutManager(
                    requireContext(),
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) 5 else 3,
                    RecyclerView.VERTICAL,
                    false
                )
            }

            cameraButton.isVisible = getAllowCamera(requireArguments())
            galleryButton.setOnClickListener {
                when (pickerMode) {
                    PickerMode.IMAGE -> pickImageGallery()
                    PickerMode.VIDEO -> pickVideoGallery()
                    PickerMode.ANY -> pickImageAndVideoGallery()
                }
            }
            cameraButton.setOnClickListener { pickImageCamera() }
            grantTextView.setOnClickListener { grantPermissions() }

            pickerBottomSheetCallback.setMargin(requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId))
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinatorLayout.doOnLayout {
            behavior = BottomSheetBehavior.from(bottomSheetDialog).apply {
                addBottomSheetCallback(pickerBottomSheetCallback)
                isHideable = true
                skipCollapsed = false
                peekHeight =
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) it.measuredHeight / 2
                    else BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
        }

        if (savedInstanceState == null) updateState()

        viewModel.hasPermission.observe(viewLifecycleOwner, { handlePermission(it) })
        viewModel.selected.observe(viewLifecycleOwner, { handleSelected(it) })
        viewModel.media.observe(viewLifecycleOwner, { handleMedia(it) })
        viewModel.inProgress.observe(viewLifecycleOwner, {
            recyclerView.visibility = if (it) View.INVISIBLE else View.VISIBLE
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        })
        viewModel.hasContent.observe(viewLifecycleOwner, {
            pickerBottomSheetCallback.setNeedTransformation(it)
            if (it) remeasureContentDialog()
        })
        viewModel.maxSelectionReached.observe(viewLifecycleOwner, {
            val max = getMaxSelection(requireArguments())
            Toast.makeText(
                requireContext(),
                resources.getQuantityString((R.plurals.picker_max_selection_reached), max, max),
                Toast.LENGTH_SHORT
            ).show()
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request.MEDIA_ACCESS_PERMISSION && isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
            updateState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Request.ADD_IMAGE_GALLERY, Request.ADD_IMAGE_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    Intents.getUriResult(data)?.let {
                        parentAs<Callback>()?.onMediaPicked(it)
                        dismiss()
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onMediaClicked(state: SelectableMedia) {
        if (getAllowMultiple(requireArguments())) {
            viewModel.toggleSelected(state)
        } else {
            parentAs<Callback>()?.onMediaPicked(arrayListOf(state.uri))
            dismiss()
        }
    }

    private fun remeasureContentDialog() {
        coordinatorLayout.doOnLayout {
            val heightLp = bottomSheetDialog.layoutParams
            heightLp.height = coordinatorLayout.measuredHeight + requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId)
            bottomSheetDialog.layoutParams = heightLp
        }
    }

    private fun handlePermission(hasPermission: Boolean) {
        permissionTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE
        grantTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE

        recyclerView.visibility = if (hasPermission) View.VISIBLE else View.INVISIBLE
    }

    private fun handleSelected(selected: List<Uri>) {
        if (selected.isEmpty()) {
            snackBar?.dismiss()
            snackBar = null
        } else {
            val count = selected.count()
            if (snackBar == null) {
                val view = LayoutInflater.from(contextWrapper).inflate(R.layout.view_snackbar, null)
                snackBar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_INDEFINITE)
                    .setBehavior(NonDismissibleBehavior())
                (snackBar?.view as? ViewGroup)?.apply {
                    setPadding(0, 10, 0, 10)
                    removeAllViews()
                    addView(view)
                    findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { viewModel.clearSelected() }
                    findViewById<MaterialButton>(R.id.selectButton).setOnClickListener { uploadSelected() }
                }
                snackBar?.show()
            }
            snackBar?.view?.findViewById<TextView>(R.id.countView)?.text =
                resources.getQuantityString(R.plurals.picker_selected_count, count, count)
        }
    }

    private fun handleMedia(media: List<SelectableMedia>) {
        viewModel.setInProgress(false)
        mediaPickerAdapter.submitList(media.toMutableList())
        emptyText.visibility =
            if (media.isEmpty() && viewModel.hasPermission.value == true) View.VISIBLE
            else View.GONE
    }

    private fun loadImages() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )
            }

            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            requireContext().contentResolver.query(
                uri,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            ).use { viewModel.setMedia(it) }
        }
    }

    private fun loadVideos() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DATE_ADDED
                )
            } else {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DATE_ADDED
                )
            }

            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            requireContext().contentResolver.query(
                uri,
                projection,
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use { viewModel.setMedia(it) }
        }
    }

    private fun loadImagesAndVideos() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DATE_ADDED
                )
            } else {
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.DATE_ADDED
                )
            }

            val uri = MediaStore.Files.getContentUri("external")

            val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

            requireContext().contentResolver.query(
                uri,
                projection,
                selection,
                null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            )?.use { viewModel.setMedia(it) }
        }
    }

    private fun grantPermissions() {
        if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                Request.MEDIA_ACCESS_PERMISSION
            )
    }

    private fun updateState() {
        if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            viewModel.setHasPermission(true)
            when (pickerMode) {
                PickerMode.IMAGE ->
                    loadImages()
                PickerMode.VIDEO ->
                    loadVideos()
                PickerMode.ANY -> {
                    loadImagesAndVideos()
                }
            }
        }
    }

    private val pickerBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        private var margin = 0
        private var needTransformation = false

        override fun onStateChanged(bottomSheet: View, @BottomSheetBehavior.State newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (!needTransformation) return
            val calculatedSpacing = calculateSpacing(slideOffset)
            bottomSheetDialog.translationY = -calculatedSpacing
            bottomSheetDialog.setPadding(0, calculatedSpacing.toInt(), 0, 0)
        }

        fun setMargin(margin: Int) {
            this.margin = margin
        }

        fun setNeedTransformation(need: Boolean) {
            needTransformation = need
        }

        private fun calculateSpacing(progress: Float) = margin * progress
    }

    private fun pickImageCamera() {
        val captureMode = when (pickerMode) {
            PickerMode.IMAGE -> CameraActivity.CaptureMode.IMAGE
            PickerMode.VIDEO -> CameraActivity.CaptureMode.VIDEO
            PickerMode.ANY -> CameraActivity.CaptureMode.IMAGE
        }

        startActivityForResult(
            CameraActivity.newIntent(requireContext(), captureMode),
            Request.ADD_IMAGE_CAMERA
        )
    }

    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_photo)),
            Request.ADD_IMAGE_GALLERY
        )
    }

    private fun pickVideoGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_video)),
            Request.ADD_IMAGE_GALLERY
        )
    }

    private fun pickImageAndVideoGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = "image/* video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_media)),
            Request.ADD_IMAGE_GALLERY
        )
    }

    private fun uploadSelected() {
        val selected = ArrayList(viewModel.selected.value ?: emptyList())

        parentAs<Callback>()?.onMediaPicked(selected)
        dismiss()
    }

    private fun orientation() = requireContext().resources.configuration.orientation

    interface Callback {
        fun onMediaPicked(media: List<Uri>)
    }

}