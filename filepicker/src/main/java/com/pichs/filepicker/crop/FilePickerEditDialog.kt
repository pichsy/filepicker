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
import kotlin.math.pow

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
    /** 裁剪模式右侧"还原"文案（多语言，来自 uiConfig.cropResetText） */
    private val cropResetText: String = "还原",
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
    /** 裁剪模式专属操作条（旋转/镜像/还原 + ✕ / ✓） */
    private lateinit var cropControls: android.widget.FrameLayout
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

        // 底部工具条：和预览页底部栏完全一致（48dp、贴底），切换时位置不跳
        val toolRow = contentView.findViewById<View>(R.id.tool_row)
        toolRow.layoutParams = toolRow.layoutParams.apply { height = dp(48) }
        toolRow.setPadding(toolRow.paddingLeft, toolRow.paddingTop, toolRow.paddingRight, 0)

        setupCropControls(contentView)


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
        // 裁剪模式：先退出裁剪，回到编辑初始状态；再按一次才关弹窗
        if (currentTool == Tool.CROP) {
            cropView.cancelCrop()
            switchTool(Tool.NONE)
            return true
        }
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
    // 裁剪模式：隐藏所有其他菜单，只留「旋转/镜像/还原 + ✕ / ✓」
    // ------------------------------------------------------------------

    private fun setupCropControls(contentView: View) {
        val margin = dp(24)
        val bottomGap = dp(8)
        val host = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isClickable = false
            visibility = View.GONE
        }
        // 上排：靠左 旋转 / 镜像；右侧"还原"文字（多语言）
        val toolsRow = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52),
                android.view.Gravity.BOTTOM or android.view.Gravity.START,
            ).apply {
                bottomMargin = bottomGap + dp(56)
            }
        }
        val tools = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(52),
                android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL,
            ).apply {
                leftMargin = margin
            }
        }
        fun addCropIcon(iconRes: Int, desc: String, onClick: () -> Unit) {
            tools.addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                    setMargins(dp(10), 0, dp(10), 0)
                }
                setPadding(dp(14), dp(14), dp(14), dp(14))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(iconRes)
                contentDescription = desc
                setOnClickListener { onClick() }
            })
        }
        addCropIcon(R.drawable.filepicker_ic_crop_rotate, "旋转") { cropView.rotate90() }
        addCropIcon(R.drawable.filepicker_ic_crop_mirror, "镜像") { cropView.mirror() }
        toolsRow.addView(tools)

        // 右侧：还原（文字，文案来自 config，默认"还原"）
        val resetText = TextView(context).apply {
            text = cropResetText
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            contentDescription = "还原"
            setOnClickListener { cropView.resetCrop() }
        }
        toolsRow.addView(resetText, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            rightMargin = margin
        })
        host.addView(toolsRow)

        // 下排：左边 ✕（取消裁剪），右边 ✓（确认裁剪），贴近底部
        val minRow = dp(56)
        val cancelCrop = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            contentDescription = "取消裁剪"
            setOnClickListener {
                cropView.cancelCrop()
                switchTool(Tool.NONE)
            }
        }
        host.addView(cancelCrop, android.widget.FrameLayout.LayoutParams(minRow, minRow).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            bottomMargin = bottomGap
            leftMargin = dp(16)
        })

        // ✓ 换成需求方给的图标（视觉小一点，点击热区不变）
        val confirmCrop = ImageView(context).apply {
            setImageResource(R.drawable.filepicker_ic_crop_confirm)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "确认裁剪"
            setOnClickListener {
                cropView.confirmCrop()
                switchTool(Tool.NONE)
            }
        }
        host.addView(confirmCrop, android.widget.FrameLayout.LayoutParams(minRow, minRow).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            bottomMargin = bottomGap
            rightMargin = dp(16)
        })

        // 裁剪可用区域：顶=状态栏下 8dp（裁剪时顶栏隐藏），底=✕/✓ + 旋转排 + 间距
        val statusBar = run {
            val res = context.resources
            val id = res.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) res.getDimensionPixelSize(id) else dp(24)
        }
        val cropBottomInset = bottomGap + minRow + dp(8) + dp(52) + dp(8)
        cropView.setCropContentInsets(statusBar + dp(8), cropBottomInset)

        (contentRoot as? android.view.ViewGroup)?.addView(host)
        cropControls = host
        FilePickerLog.d(TAG, "setupCropControls: 裁剪专属操作条已创建")
    }

    private fun showCropControls(show: Boolean) {
        if (!::cropControls.isInitialized) return
        cropControls.visibility = if (show) View.VISIBLE else View.GONE
        val others = if (show) View.GONE else View.VISIBLE
        topBar.visibility = others
        toolRow.visibility = others
        secondaryPanel.visibility = others
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
        // 强制整窗重绘：确保文字立刻出现在图片上（不等下一次触摸）
        cropView.postInvalidateOnAnimation()
        contentRoot.postInvalidateOnAnimation()
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
            showCropControls(false)
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
        showCropControls(tool == Tool.CROP)
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
        panelRow = null
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
                // 第一行：画笔图标 + 竖线 + 锥形粗细滑条（紧贴下面颜色行）
                newRow(44)
                row().addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                        setMargins(dp(4), 0, dp(6), 0)
                    }
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageResource(R.drawable.filepicker_ic_brush_size)
                })
                row().addView(View(context).apply {
                    setBackgroundColor(0x40FFFFFF)
                    layoutParams = LinearLayout.LayoutParams(dp(1), dp(20)).apply {
                        setMargins(dp(6), 0, dp(10), 0)
                    }
                })
                val slider = BrushSizeSliderView(context).apply {
                    // 右侧留白：不要冲到屏幕边，止于蓝色选色圈以内
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                        rightMargin = dp(34)
                    }
                    contentDescription = "画笔粗细"
                    setValue(sliderFromBrushFrac(cropView.brushRadiusFracValue()))
                    onValueChanged = { v ->
                        cropView.setBrushRadiusFrac(brushFracFromSlider(v))
                    }
                }
                row().addView(slider)
                // 第二行：橡皮 + 颜色
                newRow()
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

    /** 当前面板行（面板是竖向的，每个工具可以有 1~2 行） */
    private var panelRow: LinearLayout? = null

    private fun newRow(rowHeight: Int = 52): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(rowHeight),
            )
        }
        secondaryPanel.addView(row)
        panelRow = row
        return row
    }

    private fun row(): LinearLayout = panelRow ?: newRow()

    /**
     * 滑条进度 → 笔刷粗细（占图片短边比例）。
     * 0~25% 保持原来的线性比例；25% 之后用 1.9 次幂加速，越往右变粗越快。
     */
    private fun brushFracFromSlider(v0: Float): Float {
        val v = v0.coerceIn(0f, 1f)
        val minF = 0.0015f
        val maxF = 0.020f
        val pivot = 0.25f
        val atPivot = minF + pivot * (maxF - minF)
        return if (v <= pivot) {
            minF + v * (maxF - minF)
        } else {
            atPivot + (maxF - atPivot) * ((v - pivot) / (1f - pivot)).pow(1.9f)
        }
    }

    /** 笔刷粗细 → 滑条进度（上面映射的逆函数） */
    private fun sliderFromBrushFrac(frac: Float): Float {
        val minF = 0.0015f
        val maxF = 0.020f
        val pivot = 0.25f
        val atPivot = minF + pivot * (maxF - minF)
        val f = frac.coerceIn(minF, maxF)
        return if (f <= atPivot) {
            (f - minF) / (maxF - minF)
        } else {
            pivot + (1f - pivot) * ((f - atPivot) / (maxF - atPivot)).pow(1f / 1.9f)
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
        row().addView(image)
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
        row().addView(image)
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
        row().addView(slot)
    }

    private fun addDivider() {
        val divider = View(context).apply {
            setBackgroundColor(0x40FFFFFF)
            layoutParams = LinearLayout.LayoutParams(dp(1), dp(22)).apply {
                setMargins(dp(8), 0, dp(8), 0)
            }
        }
        row().addView(divider)
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
        row().addView(slot)
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()
}
