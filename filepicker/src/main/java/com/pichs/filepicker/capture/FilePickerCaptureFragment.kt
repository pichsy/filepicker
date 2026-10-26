package com.pichs.filepicker.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.FilePickerUIConfig
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.camera.FilePickerCaptureHelper
import com.pichs.filepicker.crop.FilePickerCropActivity
import com.pichs.filepicker.crop.FilePickerEditDialog
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.utils.FilePickerLog
import kotlinx.coroutines.launch
import java.io.File

/**
 * 「拍摄优先」流程的无界面 Fragment：**完全不经过图片列表**。
 *
 * - attach 到调用方 Activity / Fragment 后，立即申请 CAMERA（宿主声明了才申请）
 * - 直接拉起系统相机（拍照 / 录像）
 *   - 相机取消/返回：整个流程取消，回调 onCancel
 *   - 视频：无编辑，直接回调视频
 *   - 图片 + 头像裁剪：进 1:1 裁剪页
 *   - 图片 + 点击即编辑：直接显示编辑弹窗
 *   - 图片（无编辑）：直接回调原图
 *
 * 编辑弹窗「取消」/ 裁剪页「取消」按"取消整个流程"处理（回到用户界面）。
 */
class FilePickerCaptureFragment : Fragment() {

    companion object {
        private const val TAG = "FilePickerCaptureFragment"
        private const val FRAGMENT_TAG = "FilePickerCaptureFragment"

        private const val ARG_SELECT_TYPE = "arg_select_type"
        private const val ARG_EDIT_ON_CLICK = "arg_edit_on_click"
        private const val ARG_AVATAR_CROP = "arg_avatar_crop"
        private const val ARG_ASPECT = "arg_aspect"
        private const val ARG_OUT_MAX = "arg_out_max"
        private const val ARG_QUALITY = "arg_quality"
        private const val ARG_UI_CONFIG = "arg_ui_config"

        private const val STATE_CAPTURE_URI = "state_capture_uri"
        private const val STATE_CAPTURE_IS_VIDEO = "state_capture_is_video"
        private const val STATE_CAPTURE_PRE_Q_FILE = "state_capture_pre_q_file"
        private const val STATE_PHASE = "state_phase"
        private const val STATE_ENTITY = "state_entity"

        private const val PHASE_IDLE = "idle"
        private const val PHASE_CAPTURING = "capturing"
        private const val PHASE_EDITING = "editing"

        /** 回调（由 FilePicker 在 attach 前设置；重建时从静态兜底取） */
        private var staticOnSelected: ((MutableList<MediaEntity>) -> Unit)? = null
        private var staticOnCancel: (() -> Unit)? = null

        fun newInstance(
            selectType: String,
            editOnClick: Boolean,
            avatarCrop: Boolean,
            aspect: Float,
            outputMaxSize: Int,
            quality: Int,
            uiConfig: FilePickerUIConfig?,
        ): FilePickerCaptureFragment = FilePickerCaptureFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_SELECT_TYPE, selectType)
                putBoolean(ARG_EDIT_ON_CLICK, editOnClick)
                putBoolean(ARG_AVATAR_CROP, avatarCrop)
                putFloat(ARG_ASPECT, aspect)
                putInt(ARG_OUT_MAX, outputMaxSize)
                putInt(ARG_QUALITY, quality)
                uiConfig?.let { putBundle(ARG_UI_CONFIG, it.toTransportBundle()) }
            }
        }

        fun tagName(): String = FRAGMENT_TAG
    }

    private var onSelected: ((MutableList<MediaEntity>) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    fun setCallbacks(
        onSelected: (MutableList<MediaEntity>) -> Unit,
        onCancel: () -> Unit,
    ) {
        this.onSelected = onSelected
        this.onCancel = onCancel
        staticOnSelected = onSelected
        staticOnCancel = onCancel
    }

    private lateinit var selectType: String
    private var editOnClick = false
    private var avatarCrop = false
    private var aspect = 0f
    private var outputMaxSize = 2048
    private var quality = 90
    private var uiConfig: FilePickerUIConfig? = null

    private lateinit var captureLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private var pendingCapture: FilePickerCaptureHelper.PendingCapture? = null
    private var phase = PHASE_IDLE
    private var currentEntity: MediaEntity? = null
    private var editorDialog: FilePickerEditDialog? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        selectType = args?.getString(ARG_SELECT_TYPE) ?: FilePickerSelectType.IMAGE
        editOnClick = args?.getBoolean(ARG_EDIT_ON_CLICK) ?: false
        avatarCrop = args?.getBoolean(ARG_AVATAR_CROP) ?: false
        aspect = args?.getFloat(ARG_ASPECT) ?: 0f
        outputMaxSize = args?.getInt(ARG_OUT_MAX) ?: 2048
        quality = args?.getInt(ARG_QUALITY) ?: 90
        uiConfig = FilePickerUIConfig.fromTransportBundle(args?.getBundle(ARG_UI_CONFIG))

        captureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result -> handleCaptureResult(result.resultCode, result.data) }
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) launchCamera() else {
                FilePickerLog.e("$TAG 相机权限被拒绝，结束流程")
                finishCanceled()
            }
        }

        if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_CAPTURE_URI)?.let { uri ->
                pendingCapture = FilePickerCaptureHelper.PendingCapture(
                    outputUri = Uri.parse(uri),
                    isVideo = savedInstanceState.getBoolean(STATE_CAPTURE_IS_VIDEO, false),
                    preQFile = savedInstanceState.getString(STATE_CAPTURE_PRE_Q_FILE)?.let { File(it) },
                )
            }
            phase = savedInstanceState.getString(STATE_PHASE) ?: PHASE_IDLE
            @Suppress("DEPRECATION")
            currentEntity = savedInstanceState.getParcelable(STATE_ENTITY)
        }
        if (onSelected == null) onSelected = staticOnSelected
        if (onCancel == null) onCancel = staticOnCancel
        FilePickerLog.d("$TAG onCreate phase=$phase pending=${pendingCapture != null}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = null

    override fun onResume() {
        super.onResume()
        if (finished) return
        when (phase) {
            PHASE_IDLE -> {
                phase = PHASE_CAPTURING
                beginFlow()
            }
            PHASE_EDITING -> {
                currentEntity?.let { if (editorDialog?.isShowing != true) openEditor(it) }
                    ?: finishCanceled()
            }
            else -> Unit
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PHASE, phase)
        pendingCapture?.let { capture ->
            outState.putString(STATE_CAPTURE_URI, capture.outputUri.toString())
            outState.putBoolean(STATE_CAPTURE_IS_VIDEO, capture.isVideo)
            outState.putString(STATE_CAPTURE_PRE_Q_FILE, capture.preQFile?.absolutePath)
        }
        currentEntity?.let { outState.putParcelable(STATE_ENTITY, it) }
    }

    private fun beginFlow() {
        val isVideo = selectType == FilePickerSelectType.VIDEO
        FilePickerLog.d("$TAG beginFlow isVideo=$isVideo editOnClick=$editOnClick avatarCrop=$avatarCrop")
        if (needRequestCameraPermission()) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            launchCamera()
        }
    }

    private fun needRequestCameraPermission(): Boolean {
        val ctx = context ?: return false
        if (!isPermissionDeclared(ctx, Manifest.permission.CAMERA)) return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun isPermissionDeclared(context: Context, permission: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            ).requestedPermissions?.contains(permission) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCamera() {
        val ctx = context ?: return
        val isVideo = selectType == FilePickerSelectType.VIDEO
        val capture = if (isVideo) {
            FilePickerCaptureHelper.createVideoOutput(ctx)
        } else {
            FilePickerCaptureHelper.createImageOutput(ctx)
        }
        if (capture == null) {
            FilePickerLog.e("$TAG 创建拍摄输出失败")
            Toast.makeText(ctx, "无法启动相机", Toast.LENGTH_SHORT).show()
            finishCanceled()
            return
        }
        pendingCapture = capture
        phase = PHASE_CAPTURING
        try {
            captureLauncher.launch(FilePickerCaptureHelper.buildCaptureIntent(capture))
        } catch (e: Exception) {
            FilePickerLog.e("$TAG 启动系统相机失败: ${e.message}")
            FilePickerCaptureHelper.discard(ctx, capture)
            pendingCapture = null
            Toast.makeText(ctx, "无法启动相机", Toast.LENGTH_SHORT).show()
            finishCanceled()
        }
    }

    private fun handleCaptureResult(resultCode: Int, data: Intent?) {
        val ctx = context ?: return
        val capture = pendingCapture ?: return
        pendingCapture = null
        if (resultCode != android.app.Activity.RESULT_OK) {
            FilePickerLog.d("$TAG 相机已取消")
            FilePickerCaptureHelper.discard(ctx, capture)
            finishCanceled()
            return
        }
        lifecycleScope.launch {
            val uri = FilePickerCaptureHelper.finalizeCapture(ctx, capture, data)
            val entity = FileQueryHelper.queryEntityByUri(ctx, uri)
            val usable = entity != null &&
                    (entity.size > 0L || FilePickerCaptureHelper.hasContent(ctx, uri))
            if (!usable || entity == null) {
                FilePickerLog.e("$TAG 拍摄无有效内容: uri=$uri")
                FilePickerCaptureHelper.discard(ctx, capture)
                finishCanceled()
                return@launch
            }
            currentEntity = entity
            FilePickerLog.d("$TAG 拍摄成功: ${entity.path}")
            routeCaptured(entity)
        }
    }

    private fun routeCaptured(entity: MediaEntity) {
        when {
            entity.isVideo() -> deliver(listOf(entity))
            avatarCrop -> startCrop(entity)
            editOnClick -> openEditor(entity)
            else -> deliver(listOf(entity))
        }
    }

    private fun startCrop(entity: MediaEntity) {
        val act = activity ?: return
        FilePickerCropActivity.start(
            context = act,
            entity = entity,
            aspect = if (aspect > 0f) aspect else 1f,
            outputMaxSize = outputMaxSize,
            quality = quality,
            title = "裁剪",
        ) { editedPath ->
            if (finished) return@start
            if (editedPath.isNullOrEmpty()) {
                FilePickerLog.d("$TAG 裁剪已取消")
                finishCanceled()
            } else {
                deliver(listOf(entity.copy(path = editedPath, uri = null, editedPath = null)))
            }
        }
    }

    private fun openEditor(entity: MediaEntity) {
        val ctx = context ?: return
        phase = PHASE_EDITING
        currentEntity = entity
        val dialog = FilePickerEditDialog(
            context = ctx,
            entity = entity,
            outputMaxSize = outputMaxSize,
            quality = quality,
            uiConfig = uiConfig,
        ) { editedPath ->
            if (finished) return@FilePickerEditDialog
            if (editedPath.isNullOrEmpty()) {
                FilePickerLog.d("$TAG 编辑已取消，结束流程")
                finishCanceled()
            } else {
                deliver(listOf(entity.copy(path = editedPath, uri = null, editedPath = null)))
            }
        }
        editorDialog = dialog
        dialog.showPopupWindow()
    }

    private fun deliver(list: List<MediaEntity>) {
        if (finished) return
        finished = true
        val result = ArrayList(list)
        FilePickerViewModel.finalSelectedDataList.clear()
        FilePickerViewModel.finalSelectedDataList.addAll(result)
        FilePickerLog.d("$TAG 回调结果: ${result.size} 个")
        (onSelected ?: staticOnSelected)?.invoke(result)
        clearCallbacks()
        removeSelf()
    }

    private fun finishCanceled() {
        if (finished) return
        finished = true
        FilePickerLog.d("$TAG 回调取消")
        (onCancel ?: staticOnCancel)?.invoke()
        clearCallbacks()
        removeSelf()
    }

    private fun clearCallbacks() {
        onSelected = null
        onCancel = null
        staticOnSelected = null
        staticOnCancel = null
    }

    private fun removeSelf() {
        editorDialog?.dismiss()
        editorDialog = null
        runCatching {
            parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!finished && activity?.isChangingConfigurations != true) {
            FilePickerLog.d("$TAG onDestroy，流程未完成")
        }
        editorDialog?.dismiss()
        editorDialog = null
    }
}
