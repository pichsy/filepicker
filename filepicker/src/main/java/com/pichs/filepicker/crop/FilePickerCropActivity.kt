package com.pichs.filepicker.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.FilePickerLog
import java.io.File
import kotlin.math.max

/**
 * 裁剪页（全屏）。
 *
 * 头像走「固定比例」模式（aspect=1:1，框不可改，只能拖动/缩放图片）；
 * 预览页的「编辑」走「自由裁剪」模式（8 个把手可改框 + 旋转 + 还原）。
 *
 * 结果通过 [start] 传入的回调返回（弹窗不是 Fragment，避免到处转发 onActivityResult），
 * 同时也会 setResult 一份，方便宿主自己用 startActivityForResult 接。
 */
class FilePickerCropActivity : Activity() {

    companion object {
        private const val TAG = "FilePickerCropActivity"

        private const val EXTRA_URI = "crop_source_uri"
        private const val EXTRA_PATH = "crop_source_path"
        private const val EXTRA_ASPECT = "crop_aspect"
        private const val EXTRA_OUTPUT_MAX = "crop_output_max"
        private const val EXTRA_QUALITY = "crop_quality"
        private const val EXTRA_RESIZABLE = "crop_resizable"
        private const val EXTRA_RATIO_LOCKED = "crop_ratio_locked"
        private const val EXTRA_FRAME_SCALE = "crop_frame_scale"
        private const val EXTRA_TITLE = "crop_title"

        const val RESULT_PATH = "crop_result_path"

        private var resultCallback: ((String?) -> Unit)? = null

        /** 防止同一次点击被两个监听器各触发一次，导致叠了两个裁剪页 */
        @Volatile
        private var running = false

        /**
         * 启动裁剪页。
         *
         * @param aspect 固定比例（1f = 1:1 头像）；<= 0 表示自由裁剪
         * @param resizable 裁剪框是否允许拖动改大小；false = 固定大小框，只能平移/缩放图片
         * @param ratioLocked 可拖拽时是否锁定比例；false = 可随意拉宽拉高（变形）
         * @param frameScale 初始框尺寸占屏幕短边的比例（0.05~1）
         * @param onResult 裁剪结果文件绝对路径；取消/失败为 null
         */
        fun start(
            context: Context,
            entity: MediaEntity,
            aspect: Float,
            outputMaxSize: Int,
            quality: Int,
            resizable: Boolean = true,
            ratioLocked: Boolean = true,
            frameScale: Float = 0.75f,
            title: String = "裁剪",
            onResult: (String?) -> Unit,
        ) {
            if (running) {
                FilePickerLog.d(TAG, "裁剪页已在运行，忽略重复启动")
                return
            }
            running = true
            resultCallback = onResult
            val intent = Intent(context, FilePickerCropActivity::class.java).apply {
                putExtra(EXTRA_URI, entity.uri?.toString())
                putExtra(EXTRA_PATH, entity.path)
                putExtra(EXTRA_ASPECT, aspect)
                putExtra(EXTRA_OUTPUT_MAX, outputMaxSize)
                putExtra(EXTRA_QUALITY, quality)
                putExtra(EXTRA_RESIZABLE, resizable)
                putExtra(EXTRA_RATIO_LOCKED, ratioLocked)
                putExtra(EXTRA_FRAME_SCALE, frameScale)
                putExtra(EXTRA_TITLE, title)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Throwable) {
                FilePickerLog.e(TAG, "启动裁剪页失败: ${e.message}")
                resultCallback = null
                running = false
                Toast.makeText(context, "无法打开裁剪页", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var cropView: CropView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDone: TextView
    private lateinit var btnReset: TextView
    private lateinit var bottomBar: View

    private var sourceUri: Uri? = null
    private var sourcePath: String? = null
    private var aspect = 0f
    private var cropResizable = true
    private var cropRatioLocked = true
    private var cropFrameScale = 0.75f
    private var outputMaxSize = 2048
    private var quality = 90
    private var title = "裁剪"

    private var bitmap: Bitmap? = null
    private var exporting = false
    private var finished = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_filepicker_crop)
        applySystemBarInsets()

        readIntent()

        cropView = findViewById(R.id.crop_view)
        progressBar = findViewById(R.id.progress)
        btnDone = findViewById(R.id.btn_done)
        btnReset = findViewById(R.id.btn_reset)
        bottomBar = findViewById(R.id.bottom_bar)
        bottomBar.visibility = View.VISIBLE

        findViewById<View>(R.id.btn_cancel).setOnClickListener { cancel() }
        btnDone.setOnClickListener { export() }
        findViewById<View>(R.id.btn_rotate).setOnClickListener {
            cropView.rotate90()
            updateResetState()
        }
        findViewById<View>(R.id.btn_mirror).setOnClickListener {
            cropView.mirror()
            updateResetState()
        }
        btnReset.setOnClickListener {
            cropView.resetCrop()
            updateResetState()
        }

        cropView.onFrameChanged = { updateResetState() }
        // 裁剪框活动区域：顶部不能压到状态栏，底部不能压到「确定」操作栏
        cropView.post { applyCropContentInsets() }
        progressBar.visibility = View.VISIBLE
        loadBitmapAsync()
    }

    /** 把状态栏 / 底部操作栏的高度告诉 CropView，限定裁剪框的上下范围 */
    private fun applyCropContentInsets() {
        val res = resources
        val statusBarRes = res.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarRes > 0) res.getDimensionPixelSize(statusBarRes) else dp(24)
        val bottomBarTop = bottomBar.top
        val bottomInset = (cropView.height - bottomBarTop).coerceAtLeast(0) + dp(8)
        cropView.setCropContentInsets(statusBar + dp(8), bottomInset)
        FilePickerLog.d(TAG, "裁剪区域限定: top=${statusBar + dp(8)} bottom=$bottomInset view=${cropView.width}x${cropView.height} barTop=$bottomBarTop")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 内容铺满全屏，底部操作栏按导航栏高度让位（否则"确定"会被导航条压住） */
    private fun applySystemBarInsets() {
        val bottomBarView = findViewById<View>(R.id.bottom_bar)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
            window.decorView
        ) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            bottomBarView.setPadding(
                bottomBarView.paddingLeft, bottomBarView.paddingTop,
                bottomBarView.paddingRight, bars.bottom
            )
            insets
        }
    }

    private fun readIntent() {
        sourceUri = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
        sourcePath = intent.getStringExtra(EXTRA_PATH)
        aspect = intent.getFloatExtra(EXTRA_ASPECT, 0f)
        cropResizable = intent.getBooleanExtra(EXTRA_RESIZABLE, true)
        cropRatioLocked = intent.getBooleanExtra(EXTRA_RATIO_LOCKED, true)
        cropFrameScale = intent.getFloatExtra(EXTRA_FRAME_SCALE, 0.75f)
        outputMaxSize = intent.getIntExtra(EXTRA_OUTPUT_MAX, 2048)
        quality = intent.getIntExtra(EXTRA_QUALITY, 90)
        title = intent.getStringExtra(EXTRA_TITLE) ?: "裁剪"
        if (sourceUri == null && sourcePath.isNullOrEmpty()) {
            FilePickerLog.e(TAG, "缺少图片来源")
            finishWithResult(null)
        }
    }

    private fun loadBitmapAsync() {
        val uri = sourceUri
        val path = sourcePath
        val maxSize = max(
            resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels,
        )
        Thread {
            val decoded = CropEngine.openDisplayBitmap(this, uri, path, maxSize)
            handler.post {
                if (isFinishing || isDestroyed) {
                    decoded?.recycle()
                    return@post
                }
                if (decoded == null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show()
                    finishWithResult(null)
                    return@post
                }
                bitmap = decoded
                cropView.setCropResizable(cropResizable)
                cropView.setCropRatioLocked(cropRatioLocked)
                cropView.setCropFrameScale(cropFrameScale)
                cropView.setBitmap(decoded, aspect)
                progressBar.visibility = View.GONE
                updateResetState()
            }
        }.start()
    }

    private fun updateResetState() {
        // 固定比例的框不可改，"还原"只对自由裁剪有意义
        btnReset.isEnabled = !cropView.isDefault()
        btnReset.alpha = if (btnReset.isEnabled) 1f else 0.4f
    }

    private fun export() {
        if (exporting || bitmap == null) return
        exporting = true
        progressBar.visibility = View.VISIBLE
        btnDone.isEnabled = false

        val ratio = cropView.cropRatio()
        val transform = cropView.exportTransform()
        val uri = sourceUri
        val path = sourcePath
        Thread {
            val file = CropEngine.export(this, uri, path, ratio, transform, outputMaxSize, quality)
            // 头像成品也发布到系统相册 Pictures/filepicker（和编辑弹窗一致），
            // 返回给调用方的路径必须是稳定可用的成品，而不是临时缓存文件
            val published = if (file != null) CropEngine.publishToGallery(this, file) else null
            handler.post {
                exporting = false
                progressBar.visibility = View.GONE
                btnDone.isEnabled = true
                if (file == null) {
                    Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                } else {
                    FilePickerLog.d("FilePickerCrop", "头像裁剪成品: ${published ?: file.absolutePath}")
                    finishWithResult(published ?: file.absolutePath)
                }
            }
        }.start()
    }

    private fun cancel() {
        finishWithResult(null)
    }

    private fun finishWithResult(path: String?) {
        if (finished) return
        finished = true
        try {
            setResult(
                if (path != null) RESULT_OK else RESULT_CANCELED,
                Intent().apply { putExtra(RESULT_PATH, path) },
            )
        } catch (ignored: Throwable) {
        }
        resultCallback?.invoke(path)
        resultCallback = null
        running = false
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (exporting) return
        finishWithResult(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        // 位图统一由 CropView 释放（它同时持有原始位图与旋转/镜像产生的中间位图）
        if (::cropView.isInitialized) cropView.release()
        bitmap = null
    }

    /** 供外部测试/调试：当前导出任务是否进行中 */
    fun isExporting(): Boolean = exporting

    /** 供外部测试/调试：当前裁剪结果文件（未导出前为 null） */
    fun lastOutputFile(): File? = null
}
