package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
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
    private lateinit var toolDoodle: ImageView
    private lateinit var toolText: ImageView
    private lateinit var toolCrop: ImageView
    private lateinit var toolMosaic: ImageView
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
        // 选中色点会原地放大 1.2 倍，允许子 View 超出自身槽位绘制，避免被裁
        secondaryPanel.clipChildren = false
        secondaryPanel.clipToPadding = false
        toolDoodle = contentView.findViewById(R.id.tool_doodle)
        toolText = contentView.findViewById(R.id.tool_text)
        toolCrop = contentView.findViewById(R.id.tool_crop)
        toolMosaic = contentView.findViewById(R.id.tool_mosaic)
        btnDone = contentView.findViewById(R.id.btn_done)
        progressBar = contentView.findViewById(R.id.progress)

        // 底部工具条按导航栏让位（弹窗里 insets 分发不可靠，直接取系统资源高度）
        val toolRow = contentView.findViewById<View>(R.id.tool_row)
        val navBarHeight = run {
            val res = context.resources
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) res.getDimensionPixelSize(id) else dp(48)
        }
        // 行高 = 内容 64dp + 导航栏高度，避免 padding 把 48dp 图标挤扁
        toolRow.layoutParams = toolRow.layoutParams.apply { height = dp(64) + navBarHeight }
        toolRow.setPadding(
            toolRow.paddingLeft, toolRow.paddingTop, toolRow.paddingRight, navBarHeight
        )
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (bars.bottom > navBarHeight) {
                toolRow.setPadding(
                    toolRow.paddingLeft, toolRow.paddingTop, toolRow.paddingRight, bars.bottom
                )
            }
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
        toolDoodle.setColorFilter(if (currentTool == Tool.DOODLE) accent else normal)
        toolText.setColorFilter(if (currentTool == Tool.TEXT) accent else normal)
        toolCrop.setColorFilter(if (currentTool == Tool.CROP) accent else normal)
        toolMosaic.setColorFilter(if (currentTool == Tool.MOSAIC) accent else normal)
    }

    private fun buildSecondaryPanel() {
        if (!::secondaryPanel.isInitialized) return
        secondaryPanel.removeAllViews()
        when (currentTool) {
            Tool.CROP -> {
                addIconButton(R.drawable.filepicker_ic_crop_rotate) { cropView.rotate90() }
                addIconButton(R.drawable.filepicker_ic_crop_mirror) { cropView.mirror() }
                addIconButton(R.drawable.filepicker_ic_edit_reset) { cropView.resetCrop() }
            }

            Tool.DOODLE -> {
                addIconButton(R.drawable.filepicker_ic_edit_eraser) {
                    Toast.makeText(context, "橡皮下一步接入", Toast.LENGTH_SHORT).show()
                }
                addDivider()
                for (color in DOODLE_COLORS) {
                    addColorDot(color)
                }
            }

            Tool.TEXT -> {
                addIconButton(R.drawable.filepicker_ic_edit_text, selected = true) {
                    Toast.makeText(context, "文字编辑下一步接入", Toast.LENGTH_SHORT).show()
                }
            }

            Tool.MOSAIC -> {
                addIconButton(R.drawable.filepicker_ic_edit_eraser) {
                    Toast.makeText(context, "橡皮下一步接入", Toast.LENGTH_SHORT).show()
                }
                addDivider()
                addIconButton(R.drawable.filepicker_ic_edit_mosaic, selected = mosaicStyle == 0) {
                    mosaicStyle = 0
                    buildSecondaryPanel()
                }
                addIconButton(R.drawable.filepicker_ic_edit_blur, selected = mosaicStyle == 1) {
                    mosaicStyle = 1
                    buildSecondaryPanel()
                }
            }
        }
    }

    /** 图标按钮（36dp 热区，选中用绿色 tint） */
    private fun addIconButton(iconRes: Int, selected: Boolean = false, onClick: () -> Unit) {
        val size = dp(36)
        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
            setColorFilter(if (selected) 0xFF06B10D.toInt() else Color.WHITE)
            setOnClickListener { onClick() }
        }
        secondaryPanel.addView(image)
    }

    private fun addDivider() {
        val divider = View(context).apply {
            setBackgroundColor(0x40FFFFFF)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
        }
        secondaryPanel.addView(divider)
    }

    /**
     * 颜色小圆点（比例按微信实测）：
     * - 视觉直径 14dp，**槽位 34dp 且整槽可点**（好点，和微信一致）；
     * - 未选中 14dp，选中原地放大 1.4 倍（≈20dp），槽位不变、不挤动其他点。
     */
    private fun addColorDot(color: Int) {
        val selected = color == selectedColor
        val slotSize = dp(34)
        val dotSize = dp(14)
        val slot = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(slotSize, dp(48))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedColor = color
                buildSecondaryPanel()
            }
        }
        val dot = View(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = android.view.Gravity.CENTER
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (selected) {
                    setStroke(dp(2), Color.WHITE)
                } else {
                    // 给每个点一圈淡描边：黑色点在深色图片上也看得见
                    setStroke(dp(1), 0x99FFFFFF.toInt())
                }
            }
            if (selected) {
                scaleX = 1.4f
                scaleY = 1.4f
            }
        }
        slot.addView(dot)
        secondaryPanel.addView(slot)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
