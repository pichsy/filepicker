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

    private enum class Tool { NONE, CROP, DOODLE, TEXT, MOSAIC }

    private lateinit var cropView: CropView
    private lateinit var secondaryPanel: LinearLayout
    private lateinit var toolDoodle: ImageView
    private lateinit var toolText: ImageView
    private lateinit var toolCrop: ImageView
    private lateinit var toolMosaic: ImageView
    private lateinit var btnDone: TextView
    private lateinit var topBar: View
    private lateinit var toolRow: View
    private lateinit var progressBar: View

    private var currentTool = Tool.CROP
    /** 选中的画笔颜色；null 表示处于"橡皮模式"（所有颜色点都不选中） */
    private var selectedColor: Int? = DOODLE_COLORS.first()
    /** 画笔面板：橡皮是否被选中（选中时色点全部取消） */
    private var doodleEraserActive = false
    /** 马赛克面板：橡皮是否被选中 */
    private var mosaicEraserActive = false
    private var mosaicStyle = 0 // 0 = 像素马赛克，1 = 模糊
    /** 弹窗内容根视图（文字输入浮层挂在这上面） */
    private lateinit var contentRoot: android.view.ViewGroup
    /** 当前正在显示的文字输入浮层 */
    private var textInputOverlay: android.widget.FrameLayout? = null
    private var bitmapLoaded = false
    private var exporting = false
    private var finished = false

    private val handler = Handler(Looper.getMainLooper())

    init {
        setContentView(R.layout.dialog_filepicker_edit)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        (contentView as? android.view.ViewGroup)?.let { contentRoot = it }
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
        topBar = contentView.findViewById(R.id.top_bar)
        toolRow = contentView.findViewById(R.id.tool_row)
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

        // 把顶栏/工具栏占用的高度告诉裁剪画布：裁剪框不会钻到它们下面，四角都能拖
        contentRoot.post {
            val top = topBar.bottom
            val bottom = contentRoot.height - toolRow.top
            cropView.setCropContentInsets(top, bottom)
        }

        contentView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            finishWithResult(null)
        }
        toolDoodle.setOnClickListener { switchTool(Tool.DOODLE) }
        // 文字：点一下就在屏幕正中弹透明输入框，不出现二级面板
        toolText.setOnClickListener {
            currentTool = Tool.TEXT
            cropView.setCropMode(false)
            updateToolColors()
            buildSecondaryPanel()
            showTextInput()
        }
        // 点画布上已选中的文字：进入这段文字的编辑状态
        cropView.onTextEditRequested = { index -> showTextInput(index) }
        toolCrop.setOnClickListener { switchTool(Tool.CROP) }
        toolMosaic.setOnClickListener { switchTool(Tool.MOSAIC) }
        btnDone.setOnClickListener { export() }

        cropView.onFrameChanged = { /* 还原按钮可用状态在二级面板里刷新 */ }

        progressBar.visibility = View.VISIBLE
        loadBitmapAsync()
        // 进入编辑页默认什么都不选：没有裁剪框、没有二级面板
        switchTool(Tool.NONE)
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

        val ratio = cropView.exportRatio()
        val transform = cropView.exportTransform()
        val textLayers = cropView.textLayersSnapshot()
        val strokes = cropView.strokesSnapshot()
        FilePickerLog.d(TAG, "导出: 文字=${textLayers.size} 笔画=${strokes.size}")
        val uri = entity.uri
        val path = entity.path
        Thread {
            val file = CropEngine.export(
                context, uri, path, ratio, transform, outputMaxSize, quality, textLayers, strokes
            )
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

    /** 返回键：文字输入时先收键盘（颜色条带动画落回底部），再按一次才退出输入 */
    override fun onBackPressed(): Boolean {
        val overlay = textInputOverlay
        if (overlay != null) {
            if (textKeyboardVisible) {
                // 键盘还开着：先收键盘（颜色条会动画落回底部）
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(overlay.windowToken, 0)
            } else {
                // 键盘已收起：再按一次退出文字输入
                cancelTextInput()
            }
            return true
        }
        return super.onBackPressed()
    }

    override fun onDismiss() {
        super.onDismiss()
        FilePickerLog.d(TAG, "onDismiss")
        if (::cropView.isInitialized) cropView.release()
    }

    // ------------------------------------------------------------------
    // 文字输入（透明浮层：屏幕中央输入 + 右上角绿色完成 + 底部颜色条）
    // ------------------------------------------------------------------

    /** 文字样式模式：true = 圆角背景模式（颜色条=背景色，文字白色）；false = 字体颜色模式 */
    private var textBgMode = false
    /** 颜色条选中的颜色（背景模式=背景色，字体模式=字色） */
    private var textStyleColor = DOODLE_COLORS.first()
    /** 正在编辑的文字下标；-1 = 新增文字 */
    private var editingTextIndex = -1
    /** 文字输入时键盘是否可见（返回键先收键盘，再退出输入） */
    private var textKeyboardVisible = false

    private fun showTextInput(editIndex: Int = -1) {
        if (textInputOverlay != null || !::contentRoot.isInitialized) return
        editingTextIndex = editIndex
        // 编辑已有文字：回填内容和样式，并把画布上那份临时隐藏
        val editingLayer = cropView.textLayerOrNull(editIndex)
        if (editingLayer != null) {
            textBgMode = editingLayer.backgroundColor != null
            textStyleColor = editingLayer.backgroundColor ?: editingLayer.textColor
            cropView.setTextHidden(editIndex, true)
        } else {
            textBgMode = false
            textStyleColor = DOODLE_COLORS.first()
        }

        val statusBar = run {
            val res = context.resources
            val id = res.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) res.getDimensionPixelSize(id) else dp(24)
        }
        val host = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        host.addView(View(context).apply {
            setBackgroundColor(0x33000000)
        }, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        // ---- 输入框（支持样式实时预览）----
        val editText = android.widget.EditText(context).apply {
            hint = "输入文字"
            setHintTextColor(0x80FFFFFF.toInt())
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            background = null
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            maxLines = 4
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        host.addView(editText, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER,
        ))
        if (editingLayer != null) {
            editText.setText(editingLayer.text)
            editText.setSelection(editingLayer.text.length)
        }

        // ---- 顶部：取消 / 绿色圆角完成 ----
        val cancel = TextView(context).apply {
            text = "取消"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { cancelTextInput() }
        }
        host.addView(cancel, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            topMargin = statusBar + dp(2)
            leftMargin = dp(2)
        })

        val done = TextView(context).apply {
            text = "完成"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(0xFF04AD0C.toInt())
            }
            setOnClickListener { commitTextInput(editText.text.toString()) }
        }
        host.addView(done, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = statusBar + dp(6)
            rightMargin = dp(12)
        })

        // ---- 底部颜色条：T（背景开关） + 8 个颜色 ----
        val accessory = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        // 默认离底部 24dp；键盘弹出时整体上移（带平滑动画）
        val accessoryLp = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            dp(52),
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL,
        ).apply {
            bottomMargin = dp(24)
        }
        host.addView(accessory, accessoryLp)

        fun applyPreviewStyle() {
            if (textBgMode) {
                editText.setTextColor(contrastTextColor(textStyleColor))
                editText.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(textStyleColor)
                }
            } else {
                editText.setTextColor(textStyleColor)
                editText.background = null
            }
            editText.setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        fun rebuildAccessory() {
            accessory.removeAllViews()
            // T 按钮：默认不选中 = 无背景；选中 = 圆形浅白底 + 背景模式
            val tHit = dp(40)
            val tCircle = dp(32)
            val tSlot = android.widget.FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(tHit, dp(48)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                isClickable = true
                contentDescription = if (textBgMode) "背景模式（已选中）" else "字体颜色模式（未选中）"
                setOnClickListener {
                    textBgMode = !textBgMode
                    applyPreviewStyle()
                    rebuildAccessory()
                }
            }
            val tButton = ImageView(context).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(tCircle, tCircle).apply {
                    gravity = android.view.Gravity.CENTER
                }
                setPadding(dp(7), dp(7), dp(7), dp(7))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.filepicker_ic_edit_text)
                setColorFilter(if (textBgMode) 0xFF06B10D.toInt() else Color.WHITE)
                background = if (textBgMode) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x40FFFFFF)
                    }
                } else {
                    null
                }
            }
            tSlot.addView(tButton)
            accessory.addView(tSlot)
            accessory.addView(View(context).apply {
                setBackgroundColor(0x40FFFFFF)
                layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).apply {
                    setMargins(dp(8), 0, dp(8), 0)
                }
            })
            for (color in DOODLE_COLORS) {
                val selected = color == textStyleColor
                val slot = android.widget.FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(48))
                    isClickable = true
                    contentDescription = "颜色#%06X".format(0xFFFFFF and color)
                    setOnClickListener {
                        textStyleColor = color
                        applyPreviewStyle()
                        rebuildAccessory()
                    }
                }
                val dot = View(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                        if (selected) {
                            setStroke(dp(2.5f), Color.WHITE)
                        } else {
                            setStroke(dp(1.5f), Color.WHITE)
                        }
                    }
                    if (selected) {
                        scaleX = 1.4f
                        scaleY = 1.4f
                    }
                }
                slot.addView(dot)
                accessory.addView(slot)
            }
        }

        applyPreviewStyle()
        rebuildAccessory()

        // 键盘(IME)高度变化：颜色条平滑升降（收起时落回底部上方 24dp）
        var lastIme = -1
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            textKeyboardVisible = ime > 0
            if (ime != lastIme) {
                lastIme = ime
                accessory.animate()
                    .translationY(-ime.toFloat())
                    .setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            insets
        }

        topBar.visibility = View.GONE
        toolRow.visibility = View.GONE
        secondaryPanel.visibility = View.GONE
        cropView.setCropMode(false)
        cropView.setTextGesturesLocked(true)

        contentRoot.addView(host)
        textInputOverlay = host
        FilePickerLog.d(TAG, "showTextInput: 编辑下标=$editIndex 背景模式=$textBgMode")

        editText.requestFocus()
        editText.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            host.requestApplyInsets()
        }
    }

    /** 背景模式下文字颜色：**只有纯白背景用黑字**，其他背景一律白字 */
    private fun contrastTextColor(background: Int): Int {
        val rgb = background and 0x00FFFFFF
        return if (rgb == 0x00FFFFFF) Color.BLACK else Color.WHITE
    }

    private fun commitTextInput(text: String) {
        val content = text.trim()
        val index = editingTextIndex
        if (index >= 0) {
            if (content.isEmpty()) {
                cropView.removeText(index)
            } else if (textBgMode) {
                cropView.updateText(index, content, contrastTextColor(textStyleColor), textStyleColor)
            } else {
                cropView.updateText(index, content, textStyleColor, null)
            }
            cropView.setTextHidden(index, false)
        } else if (content.isNotEmpty()) {
            if (textBgMode) {
                cropView.addText(content, contrastTextColor(textStyleColor), textStyleColor)
            } else {
                cropView.addText(content, textStyleColor, null)
            }
        }
        editingTextIndex = -1
        hideTextInput()
        exitTextEditingState()
    }

    private fun cancelTextInput() {
        val index = editingTextIndex
        if (index >= 0) cropView.setTextHidden(index, false)
        editingTextIndex = -1
        hideTextInput()
        exitTextEditingState()
    }

    /** 退出文字输入后：主菜单回到"什么都没选"的状态，文字仍可拖动/点选 */
    private fun exitTextEditingState() {
        currentTool = Tool.NONE
        updateToolColors()
        buildSecondaryPanel()
        cropView.setCropMode(false)
        cropView.setTextGesturesLocked(false)
    }

    private fun hideTextInput() {
        val overlay = textInputOverlay ?: return
        textInputOverlay = null
        textKeyboardVisible = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(overlay.windowToken, 0)
        (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
        if (::topBar.isInitialized) topBar.visibility = View.VISIBLE
        if (::toolRow.isInitialized) toolRow.visibility = View.VISIBLE
        if (::secondaryPanel.isInitialized) secondaryPanel.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------
    // 工具切换 + 二级面板
    // ------------------------------------------------------------------

    private fun switchTool(tool: Tool) {
        currentTool = tool
        if (tool == Tool.NONE) {
            cropView.setCropMode(false)
            cropView.setTextGesturesLocked(false)
            cropView.setPaintEnabled(false)
            updateToolColors()
            buildSecondaryPanel()
            return
        }
        // 只有裁剪工具显示裁剪框并允许手势，其他工具先保留画布（笔迹能力后续接入）
        cropView.setCropMode(tool == Tool.CROP)
        // 文字手势保持可用（点文字可选中/拖动），输入浮层打开时才锁住
        cropView.setTextGesturesLocked(false)
        // 画笔 / 马赛克 工具都会开启"涂抹"
        cropView.setPaintEnabled(tool == Tool.MOSAIC || tool == Tool.DOODLE)
        if (tool == Tool.MOSAIC) {
            cropView.setPaintEraser(false)
            cropView.setPaintStyle(mosaicStyle)
        } else if (tool == Tool.DOODLE) {
            cropView.setPaintEraser(false)
            cropView.setPaintStyle(2)
            cropView.setBrushColor(selectedColor ?: Color.WHITE)
        }
        updateToolColors()
        buildSecondaryPanel()
    }

    private fun updateToolColors() {
        val accent = 0xFF06B10D.toInt()
        val normal = Color.WHITE
        if (currentTool == Tool.NONE) {
            toolDoodle.setColorFilter(normal)
            toolText.setColorFilter(normal)
            toolCrop.setColorFilter(normal)
            toolMosaic.setColorFilter(normal)
            return
        }
        toolDoodle.setColorFilter(if (currentTool == Tool.DOODLE) accent else normal)
        toolText.setColorFilter(if (currentTool == Tool.TEXT) accent else normal)
        toolCrop.setColorFilter(if (currentTool == Tool.CROP) accent else normal)
        toolMosaic.setColorFilter(if (currentTool == Tool.MOSAIC) accent else normal)
    }

    private fun buildSecondaryPanel() {
        if (!::secondaryPanel.isInitialized) return
        secondaryPanel.removeAllViews()
        when (currentTool) {
            Tool.NONE -> {
                // 主菜单什么都没选：没有二级面板
            }

            Tool.CROP -> {
                addIconButton(R.drawable.filepicker_ic_crop_rotate) { cropView.rotate90() }
                addIconButton(R.drawable.filepicker_ic_crop_mirror) { cropView.mirror() }
                addIconButton(R.drawable.filepicker_ic_edit_reset) { cropView.resetCrop() }
            }

            Tool.DOODLE -> {
                // 橡皮是"模式"：选中时浅白圆角底 + 所有颜色点取消选中
                addEraserButton(active = cropView.isPaintEraser()) {
                    cropView.setPaintEraser(true)
                    doodleEraserActive = true
                    selectedColor = null
                    buildSecondaryPanel()
                }
                addDivider()
                for (color in DOODLE_COLORS) {
                    addColorDot(color)
                }
            }

            Tool.TEXT -> {
                // 文字直接弹输入框，二级面板留空
            }

            Tool.MOSAIC -> {
                val eraser = cropView.isPaintEraser()
                addEraserButton(active = eraser) {
                    cropView.setPaintEraser(true)
                    mosaicEraserActive = true
                    buildSecondaryPanel()
                }
                addDivider()
                // 两种马赛克：像素马赛克 / 毛刷模糊（图标放大到 22dp）
                addBigIconButton(
                    R.drawable.filepicker_ic_edit_mosaic,
                    selected = !eraser && mosaicStyle == 0,
                ) {
                    mosaicStyle = 0
                    mosaicEraserActive = false
                    cropView.setPaintEraser(false)
                    cropView.setPaintStyle(0)
                    buildSecondaryPanel()
                }
                addBigIconButton(
                    R.drawable.filepicker_ic_edit_blur,
                    selected = !eraser && mosaicStyle == 1,
                ) {
                    mosaicStyle = 1
                    mosaicEraserActive = false
                    cropView.setPaintEraser(false)
                    cropView.setPaintStyle(1)
                    buildSecondaryPanel()
                }
            }
        }
    }

    /** 大号图标按钮（44dp 热区 / 22dp 视觉，马赛克的两种效果用） */
    private fun addBigIconButton(iconRes: Int, selected: Boolean = false, onClick: () -> Unit) {
        val size = dp(44)
        val image = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setPadding(dp(11), dp(11), dp(11), dp(11))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
            setColorFilter(if (selected) 0xFF06B10D.toInt() else Color.WHITE)
            setOnClickListener { onClick() }
        }
        secondaryPanel.addView(image)
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

    /**
     * 橡皮按钮：未选中透明背景；选中时**浅白圆形底**（不要太大，32dp 圆形）。
     */
    private fun addEraserButton(active: Boolean, onClick: () -> Unit) {
        val hitSize = dp(40)
        val bgSize = dp(32)
        val slot = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(hitSize, dp(48)).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val image = ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(bgSize, bgSize).apply {
                gravity = android.view.Gravity.CENTER
            }
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.filepicker_ic_edit_eraser)
            setColorFilter(if (active) 0xFF06B10D.toInt() else Color.WHITE)
            background = if (active) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x40FFFFFF)
                }
            } else {
                null
            }
        }
        slot.addView(image)
        secondaryPanel.addView(slot)
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
        val selected = selectedColor == color
        val slotSize = dp(34)
        val dotSize = dp(16)
        val slot = android.widget.FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(slotSize, dp(48))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedColor = color
                doodleEraserActive = false
                cropView.setPaintEraser(false)
                cropView.setPaintStyle(2)
                cropView.setBrushColor(color)
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
                    setStroke(dp(2.5f), Color.WHITE)
                } else {
                    // 每个圆点都有白色描边（1.5dp），黑色点在深色图片上也清楚
                    setStroke(dp(1.5f), Color.WHITE)
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

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()
}
