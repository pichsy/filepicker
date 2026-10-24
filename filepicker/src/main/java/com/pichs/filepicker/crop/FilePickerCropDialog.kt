package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.FilePickerLog
import razerdp.basepopup.BasePopupWindow
import kotlin.math.max

/**
 * 预览页「编辑」用的**全屏弹窗**（和预览界面一样是 BasePopupWindow，不是 Activity）。
 *
 * 头像裁剪走独立的 [FilePickerCropActivity]；预览里的编辑必须是弹窗，
 * 这样从预览页打开、关闭后回到预览页，不涉及 Activity 跳转和返回栈。
 *
 * 交互与裁剪页一致：取消 / 旋转 / 镜像 / 还原 …… 右下绿色「确定」。
 */
class FilePickerCropDialog(
    val context: Context,
    private val entity: MediaEntity,
    private val aspect: Float = 0f,
    private val outputMaxSize: Int = 2048,
    private val quality: Int = 90,
    private val onResult: (String?) -> Unit,
) : BasePopupWindow(context) {

    companion object {
        private const val TAG = "FilePickerCropDialog"
    }

    private lateinit var cropView: CropView
    private lateinit var progressBar: View
    private lateinit var btnDone: TextView
    private lateinit var btnReset: TextView

    private var bitmap: Bitmap? = null
    private var exporting = false
    private var finished = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        setContentView(R.layout.dialog_filepicker_crop)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        setBackgroundColor(Color.TRANSPARENT)
        setOutSideDismiss(false)
        setBackPressEnable(true)

        cropView = contentView.findViewById(R.id.crop_view)
        progressBar = contentView.findViewById(R.id.progress)
        btnDone = contentView.findViewById(R.id.btn_done)
        btnReset = contentView.findViewById(R.id.btn_reset)

        contentView.findViewById<View>(R.id.btn_cancel).setOnClickListener { finishWithResult(null) }
        btnDone.setOnClickListener { export() }
        contentView.findViewById<View>(R.id.btn_rotate).setOnClickListener {
            cropView.rotate90()
            updateResetState()
        }
        contentView.findViewById<View>(R.id.btn_mirror).setOnClickListener {
            cropView.mirror()
            updateResetState()
        }
        btnReset.setOnClickListener {
            cropView.resetCrop()
            updateResetState()
        }
        cropView.onFrameChanged = { updateResetState() }

        progressBar.visibility = View.VISIBLE
        loadBitmapAsync()
    }

    private fun loadBitmapAsync() {
        val uri = entity.uri
        val path = entity.path
        val maxSize = max(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
        )
        Thread {
            val decoded = CropEngine.openDisplayBitmap(context, uri, path, maxSize)
            handler.post {
                if (!isShowing) {
                    decoded?.recycle()
                    return@post
                }
                if (decoded == null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                    finishWithResult(null)
                    return@post
                }
                bitmap = decoded
                cropView.setBitmap(decoded, aspect)
                progressBar.visibility = View.GONE
                updateResetState()
            }
        }.start()
    }

    private fun updateResetState() {
        btnReset.isEnabled = !::cropView.isInitialized || !cropView.isDefault()
        btnReset.alpha = if (btnReset.isEnabled) 1f else 0.4f
    }

    private fun export() {
        if (exporting || bitmap == null) return
        exporting = true
        progressBar.visibility = View.VISIBLE
        btnDone.isEnabled = false

        val ratio = cropView.cropRatio()
        val transform = cropView.exportTransform()
        val uri = entity.uri
        val path = entity.path
        Thread {
            val file = CropEngine.export(context, uri, path, ratio, transform, outputMaxSize, quality)
            handler.post {
                exporting = false
                progressBar.visibility = View.GONE
                btnDone.isEnabled = true
                if (file == null) {
                    Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                } else {
                    FilePickerLog.d(TAG, "编辑导出成功: ${file.absolutePath}")
                    finishWithResult(file.absolutePath)
                }
            }
        }.start()
    }

    private fun finishWithResult(path: String?) {
        if (finished) return
        finished = true
        try {
            dismiss()
        } catch (ignored: Throwable) {
        }
        onResult(path)
    }

    override fun onDismiss() {
        super.onDismiss()
        if (::cropView.isInitialized) cropView.release()
        bitmap = null
    }
}
