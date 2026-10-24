package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.FilePickerLog
import razerdp.basepopup.BasePopupWindow
import kotlin.math.max

/**
 * 预览页「编辑」的全屏弹窗（和预览界面一样是 BasePopupWindow）。
 *
 * 结构（对齐微信编辑器）：
 * ```
 * 取消                        ↶ ↷
 *         [ 画布：图片 + 当前工具的交互 ]
 *   … 二级面板（随工具切换）…
 * ✏画笔  T文字  ⧉裁剪  ▦马赛克            [完成]
 * ```
 *
 * 当前版本先搭"编辑页外壳 + 裁剪能力"：画笔/文字/马赛克的二级面板能切换，
 * 笔迹绘制能力后续接入；裁剪和以前一样可用（旋转/镜像/还原 + 框内拖动/缩放）。
 */
class FilePickerEditDialog(
    val context: Context,
    private val entity: MediaEntity,
    private val outputMaxSize: Int = 2048,
    private val quality: Int = 90,
    private val aspect: Float = 0f,
    private val onResult: (String?) -> Unit,
) : BasePopupWindow(context) {

    companion object {
        private const val TAG = "FilePickerEditDialog"

        /** 画笔颜色（微信那套：白黑红橙黄浅绿绿蓝） */
        private val DOODLE_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFF000000.toInt(),
            0xFFFF3B30.toInt(),
            0xFFFF9500.toInt(),
            0xFFFFCC00.toInt(),
            0xFF8BC34A.toInt(),
            0xFF06B10D.toInt(),
            0xFF2F7DFF.toInt(),
        )
    }

    private enum class Tool { CROP, DOODLE, TEXT, MOSAIC }

    private lateinit var cropView: CropView
    private lateinit var secondaryPanel: LinearLayout
    private lateinit var toolDoodle: TextView
    private lateinit var toolText: TextView
    private lateinit var toolCrop: TextView
    private lateinit var toolMosaic: TextView
    private lateinit var btnDone: TextView
    private lateinit var progressBar: View

    private var currentTool = Tool.CROP
    private var selectedColor = Color.WHITE
    private var mosaicStyle = 0 // 0 = 像素马赛克，1 = 模糊
    private var bitmapLoaded = false
    private var exporting = false
    private var finished = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        setContentView(R.layout.dialog_filepicker_edit)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        setBackgroundColor(Color.TRANSPARENT)
        setOutSideDismiss(false)
        setBackPressEnable(true)

        cropView = contentView.findViewById(R.id.crop_view)
        secondaryPanel = contentView.findViewById(R.id.secondary_panel)
        toolDoodle = contentView.findViewById(R.id.tool_doodle)
        toolText = contentView.findViewById(R.id.tool_text)
        toolCrop = contentView.findViewById(R.id.tool_crop)
        toolMosaic = contentView.findViewById(R.id.tool_mosaic)
        btnDone = contentView.findViewById(R.id.btn_done)
        progressBar = contentView.findViewById(R.id.progress)

        // 底部工具条按导航栏让位
        val toolRow = contentView.findViewById<View>(R.id.tool_row)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            toolRow.setPadding(
                toolRow.paddingLeft, toolRow.paddingTop, toolRow.paddingRight, bars.bottom
            )
            insets
        }

        contentView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            finishWithResult(null)
        }
        toolDoodle.setOnClickListener { switchTool(Tool.DOODLE) }
        toolText.setOnClickListener { switchTool(Tool.TEXT) }
        toolCrop.setOnClickListener { switchTool(Tool.CROP) }
        toolMosaic.setOnClickListener { switchTool(Tool.MOSAIC) }
        btnDone.setOnClickListener { export() }

        cropView.onFrameChanged = { /* 还原按钮可用状态在二级面板里刷新 */ }

        progressBar.visibility = View.VISIBLE
        loadBitmapAsync()
        switchTool(Tool.CROP)
    }

    // ------------------------------------------------------------------
    // 图片加载 / 导出
    // ------------------------------------------------------------------

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
                bitmapLoaded = true
                cropView.setBitmap(decoded, aspect)
                progressBar.visibility = View.GONE
                buildSecondaryPanel()
            }
        }.start()
    }

    private fun export() {
        if (exporting || !bitmapLoaded) return
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
        FilePickerLog.d(TAG, "finishWithResult: path=$path isShowing=$isShowing")
        try {
            dismiss()
            FilePickerLog.d(TAG, "dismiss() 调用后 isShowing=$isShowing")
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "dismiss 失败: ${e.message}")
        }
        onResult(path)
    }

    override fun onDismiss() {
        super.onDismiss()
        FilePickerLog.d(TAG, "onDismiss")
        if (::cropView.isInitialized) cropView.release()
    }

    // ------------------------------------------------------------------
    // 工具切换 + 二级面板
    // ------------------------------------------------------------------

    private fun switchTool(tool: Tool) {
        currentTool = tool
        // 只有裁剪工具显示裁剪框并允许手势，其他工具先保留画布（笔迹能力后续接入）
        cropView.setCropMode(tool == Tool.CROP)
        updateToolColors()
        buildSecondaryPanel()
    }

    private fun updateToolColors() {
        val accent = 0xFF06B10D.toInt()
        val normal = Color.WHITE
        toolDoodle.setTextColor(if (currentTool == Tool.DOODLE) accent else normal)
        toolText.setTextColor(if (currentTool == Tool.TEXT) accent else normal)
        toolCrop.setTextColor(if (currentTool == Tool.CROP) accent else normal)
        toolMosaic.setTextColor(if (currentTool == Tool.MOSAIC) accent else normal)
    }

    private fun buildSecondaryPanel() {
        if (!::secondaryPanel.isInitialized) return
        secondaryPanel.removeAllViews()
        when (currentTool) {
            Tool.CROP -> {
                addTextButton("↺ 旋转") { cropView.rotate90() }
                addTextButton("⇋ 镜像") { cropView.mirror() }
                addTextButton("还原") { cropView.resetCrop() }
            }

            Tool.DOODLE -> {
                addEraserButton()
                addDivider()
                for (color in DOODLE_COLORS) {
                    addColorDot(color)
                }
                addHint("画笔绘制下一步接入")
            }

            Tool.TEXT -> {
                addTextButton("+ 添加文字") {
                    Toast.makeText(context, "文字编辑下一步接入", Toast.LENGTH_SHORT).show()
                }
                addHint("样式/拖动/缩放在下一步")
            }

            Tool.MOSAIC -> {
                addEraserButton()
                addDivider()
                addTextButton(if (mosaicStyle == 0) "▦ 像素 ✓" else "▦ 像素") {
                    mosaicStyle = 0
                    buildSecondaryPanel()
                }
                addTextButton(if (mosaicStyle == 1) "◉ 模糊 ✓" else "◉ 模糊") {
                    mosaicStyle = 1
                    buildSecondaryPanel()
                }
                addHint("涂抹下一步接入")
            }
        }
    }

    private fun addTextButton(text: String, onClick: () -> Unit) {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            val pad = dp(10)
            setPadding(pad, pad, pad, pad)
            setOnClickListener { onClick() }
        }
        secondaryPanel.addView(tv)
    }

    private fun addEraserButton() {
        addTextButton("◇ 橡皮") {
            Toast.makeText(context, "橡皮下一步接入", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addHint(text: String) {
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(0x66FFFFFF)
            textSize = 12f
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
        }
        secondaryPanel.addView(tv)
    }

    private fun addDivider() {
        val divider = View(context).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                setMargins(dp(6), 0, dp(6), 0)
            }
        }
        secondaryPanel.addView(divider)
    }

    private fun addColorDot(color: Int) {
        val size = dp(30)
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(5), 0, dp(5), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == selectedColor) {
                    setStroke(dp(2), Color.WHITE)
                }
            }
            setOnClickListener {
                selectedColor = color
                buildSecondaryPanel()
            }
        }
        secondaryPanel.addView(dot)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
