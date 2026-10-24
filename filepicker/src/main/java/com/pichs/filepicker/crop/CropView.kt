package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.pichs.filepicker.utils.FilePickerLog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 裁剪画布：图片可拖动/双指缩放，裁剪框画在图片之上。
 *
 * 两种模式：
 * - **固定比例**（[aspectRatio] > 0，头像 1:1）：**框大小固定**（取屏幕短边的 86% 居中），
 *   图片缩放覆盖框；横图竖图都用同一套规则，框永远一样大；
 * - **自由裁剪**（[aspectRatio] <= 0）：初始框 = 整张图，8 个把手可改框。
 *
 * 手势（和微信一致）：
 * - 单指拖动 = 平移图片；
 * - 双指缩放 = 以焦点为中心缩放，同时跟随焦点位移平移（双指拖动）；
 * - 松手不跳：多指抬起时重置基准点（[MotionEvent.ACTION_POINTER_UP]）。
 *
 * 坐标系：View 坐标里画 [frame]，导出时用 [cropRatio] 换算成图片内的 0~1 坐标。
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bitmap: Bitmap? = null

    /** 初始显示位图（EXIF 摆正后、未做用户旋转/镜像），"还原"时恢复它 */
    private var sourceBitmap: Bitmap? = null

    /** 编辑页加的文字图层（锚点/字号都是相对当前显示图片的比例） */
    private val textLayers = mutableListOf<TextLayer>()

    /** 文字默认字号：图片短边的 7% */
    private val defaultTextSizeFrac = 0.07f

    /** 马赛克/画笔的笔画 */
    private val strokes = mutableListOf<BrushStroke>()
    private var activeStroke: BrushStroke? = null
    /** 是否处于"涂抹"模式（马赛克工具） */
    private var paintEnabled = false
    /** 0 = 像素马赛克，1 = 毛刷模糊，2 = 彩色画笔 */
    private var paintStyle = 0
    /** 画笔颜色 */
    private var brushColor = Color.WHITE
    /** 彩色涂鸦层（画笔） */
    private var doodleLayer: Bitmap? = null
    private var doodleDirty = true
    /** 是否橡皮（擦掉涂抹效果） */
    private var paintEraser = false
    /** 笔刷半径 = 图片短边 * 该比例 */
    private var brushRadiusFrac = 0.05f
    /** 涂抹遮罩/效果图缓存 */
    private var maskBitmap: Bitmap? = null
    private var maskDirty = true
    private var mosaicBitmap: Bitmap? = null
    private var smudgeBitmap: Bitmap? = null
    private var effectsDirty = true
    private val paintStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }
    private val effectPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }

    /** 用户旋转/镜像的累计变换（只含 90° 旋转和水平镜像，导出时作用在解码后的原图上） */
    private val bakeMatrix = Matrix()

    private var aspectRatio: Float = 0f
    private var rotation: Int = 0

    /** 裁剪框（View 坐标） */
    private val frame = RectF()

    /** 图片显示矩形（View 坐标），由 [scale]/[centerX]/[centerY] 推导 */
    private val imageRect = RectF()

    /** 每 1 个图片像素占多少 View 像素 */
    private var scale = 1f
    private var centerX = 0f
    private var centerY = 0f
    private var minScale = 1f
    private var maxScale = 4f
    private var initialized = false

    /** 是否处于"裁剪工具"模式：false 时隐藏裁剪框、忽略手势（画笔/文字/马赛克用画布时切换） */
    private var cropMode = true

    /** 裁剪框停手后自动放大到"留边合适大小"（和微信一致） */
    private val idleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var idleAutoFit: Runnable? = null
    private var autoFitAnimator: android.animation.ValueAnimator? = null

    /** 进入裁剪时图片留白比例（图片占屏幕的 90%，四周留出可拖拽空间） */
    private val cropEnterInset = 0.90f

    /** 已确认的裁剪区域（相对当前显示图片的归一化坐标；null = 还没裁剪） */
    private var pendingCrop: CropGeometry.RectF2? = null

    /** 裁剪可用区域的上下内边距（由编辑页把顶栏/工具栏高度传进来，避免框角被挡） */
    private var cropContentTop = 0
    private var cropContentBottom = 0

    /** 编辑页告诉裁剪画布：顶部栏/底部栏占掉多少高度 */
    fun setCropContentInsets(top: Int, bottom: Int) {
        cropContentTop = top.coerceAtLeast(0)
        cropContentBottom = bottom.coerceAtLeast(0)
        if (cropMode && initialized) {
            setCropMode(false)
            setCropMode(true)
        }
    }

    /** 当前选中的文字图层下标，-1 = 没选中 */
    private var selectedTextIndex = -1
    /** 是否正在拖动文字 */
    private var textDragging = false
    /** 本次按下时文字已经是选中态（松手没拖动 → 进入编辑） */
    private var textTapCandidate = false
    /** 本次手势是否发生了拖动（超过 touchSlop） */
    private var textMoved = false
    private var textDownX = 0f
    private var textDownY = 0f
    /** 文字手势锁：文字输入浮层打开时锁住，避免误拖 */
    private var textGesturesLocked = false
    /** 双指缩放文字时上一帧两指间距 */
    private var lastSpan = 0f

    private var draggingHandle = CropGeometry.Handle.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val density = resources.displayMetrics.density
    private val touchRadius = 22f * density

    private val dimPaint = Paint().apply { color = 0x80000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.WHITE
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f * density
        color = 0x66FFFFFF
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.SQUARE
        color = Color.WHITE
    }
    private val textMeasurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textSelectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xCCFFFFFF.toInt()
        pathEffect = android.graphics.DashPathEffect(
            floatArrayOf(5f * density, 4f * density), 0f
        )
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        setShadowLayer(2f * density, 0f, 1f * density, 0x66000000)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    /** 框变化回调（"还原"按钮可用状态） */
    var onFrameChanged: (() -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomAbout(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        },
    )

    fun setBitmap(bmp: Bitmap, aspect: Float) {
        sourceBitmap = bmp
        bitmap = bmp
        aspectRatio = aspect
        rotation = 0
        bakeMatrix.reset()
        textLayers.clear()
        strokes.clear()
        activeStroke = null
        maskDirty = true
        effectsDirty = true
        initialized = false
        initLayout()
        invalidate()
    }

    fun currentRotation(): Int = rotation

    /**
     * 在当前裁剪框中心加一段文字（坐标按显示图片归一化保存）。
     *
     * @param textColor 文字颜色
     * @param backgroundColor 圆角背景色；null = 无背景（字体颜色模式）
     */
    fun addText(
        text: String,
        textColor: Int = android.graphics.Color.WHITE,
        backgroundColor: Int? = null,
    ): Int {
        val bmp = bitmap ?: return -1
        if (text.isBlank() || imageRect.width() <= 0f || imageRect.height() <= 0f) return -1
        val centerXInBmp = (frame.centerX() - imageRect.left) / imageRect.width()
        val centerYInBmp = (frame.centerY() - imageRect.top) / imageRect.height()
        textLayers.add(
            TextLayer(
                text = text,
                fx = centerXInBmp.coerceIn(0f, 1f),
                fy = centerYInBmp.coerceIn(0f, 1f),
                sizeFrac = defaultTextSizeFrac,
                textColor = textColor,
                backgroundColor = backgroundColor,
            )
        )
        // 新加的文字直接选中，方便马上拖动/缩放
        selectedTextIndex = textLayers.size - 1
        return selectedTextIndex
        android.util.Log.d(
            "FilePickerCropView",
            "文字图层 +1: \"$text\" 共 ${textLayers.size} 个, 选中=$selectedTextIndex"
        )
        invalidate()
    }

    /** 当前文字图层快照（导出时用） */
    fun textLayersSnapshot(): List<TextLayer> = textLayers.toList()

    /** 笔画快照（导出时用） */
    fun strokesSnapshot(): List<BrushStroke> =
        strokes.filter { it.points.isNotEmpty() }.map { stroke ->
            BrushStroke(
                points = stroke.points.map { it.copyOf() }.toMutableList(),
                radiusFrac = stroke.radiusFrac,
                mode = stroke.mode,
                erase = stroke.erase,
                color = stroke.color,
            )
        }

    /** 是否处于涂抹模式（马赛克工具） */
    fun setPaintEnabled(enabled: Boolean) {
        if (paintEnabled == enabled) return
        paintEnabled = enabled
        activeStroke = null
        invalidate()
    }

    /** 涂抹样式：0 = 像素马赛克，1 = 毛刷模糊，2 = 彩色画笔 */
    fun setPaintStyle(style: Int) {
        paintStyle = style
    }

    /** 画笔颜色（彩色画笔用） */
    fun setBrushColor(color: Int) {
        brushColor = color
    }

    /** 橡皮：擦掉涂抹效果 */
    fun setPaintEraser(erb: Boolean) {
        paintEraser = erb
    }

    fun paintStyleSelected(): Int = paintStyle

    /** 已确认的裁剪区域（相对显示图片归一化坐标） */
    fun confirmedCrop(): CropGeometry.RectF2? = pendingCrop

    /**
     * 确认裁剪：把当前裁剪框合成到已确认区域里，并把视图缩放到"裁剪后的画面填满可用区域"。
     * 返回合成后的裁剪区域。
     */
    fun confirmCrop(): CropGeometry.RectF2? {
        val bmp = bitmap ?: return pendingCrop
        val bar = cropRatio().let {
            CropGeometry.RectF2(
                it.left.coerceIn(0f, 1f),
                it.top.coerceIn(0f, 1f),
                it.right.coerceIn(0f, 1f),
                it.bottom.coerceIn(0f, 1f),
            )
        }
        val old = pendingCrop
        val composed = if (old == null) bar else CropGeometry.RectF2(
            old.left + bar.left * old.width,
            old.top + bar.top * old.height,
            old.left + bar.right * old.width,
            old.top + bar.bottom * old.height,
        )
        pendingCrop = composed
        // 视图上把"裁剪后的画面"铺满可用区域
        val availTop = cropContentTop.toFloat()
        val availBottom = (height - cropContentBottom).toFloat()
        val availW = width.toFloat()
        val availH = (availBottom - availTop).coerceAtLeast(1f)
        val regionW = composed.width * bmp.width
        val regionH = composed.height * bmp.height
        if (regionW > 0f && regionH > 0f) {
            scale = min(availW / regionW, availH / regionH)
            val regionCx = (composed.left + composed.width / 2f) * bmp.width
            val regionCy = (composed.top + composed.height / 2f) * bmp.height
            centerX = availW / 2f - (regionCx - bmp.width / 2f) * scale
            centerY = availTop + availH / 2f - (regionCy - bmp.height / 2f) * scale
            minScale = min(scale, computeMinScale())
            maxScale = minScale * MAX_ZOOM
            updateImageRect()
            constrain()
            notifyFrameChanged()
            invalidate()
        }
        android.util.Log.d(
            "FilePickerCropView",
            "确认裁剪: region=[${composed.left},${composed.top},${composed.right},${composed.bottom}] scale=$scale",
        )
        return composed
    }

    /** 取消裁剪：不动已确认区域，框回到图片区域 */
    fun cancelCrop() {
        val bmp = bitmap ?: return
        val fit = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        scale = fit
        centerX = width / 2f
        centerY = height / 2f
        updateImageRect()
        frame.set(imageRect)
        constrain()
        notifyFrameChanged()
        invalidate()
    }

    /** 是否处于裁剪工具状态（只有这时导出才按裁剪框，否则导出整张图） */
    fun isCropMode(): Boolean = cropMode

    /** 导出色块：已确认裁剪优先 → 当前裁剪框 → 整张图 */
    fun exportRatio(): CropGeometry.RectF2 {
        pendingCrop?.let { return it }
        return if (cropMode) cropRatio() else CropGeometry.RectF2(0f, 0f, 1f, 1f)
    }

    fun isPaintEraser(): Boolean = paintEraser

    /** 点"已选中的文字"时回调（进入该文字的编辑状态） */
    var onTextEditRequested: ((Int) -> Unit)? = null

    /** 是否选中了某段文字（编辑页用来显示提示/状态） */
    fun hasSelectedText(): Boolean = selectedTextIndex >= 0

    fun selectedTextIndex(): Int = selectedTextIndex

    fun textLayerOrNull(index: Int): TextLayer? = textLayers.getOrNull(index)

    /** 更新某段文字的内容/样式（编辑文字时用） */
    fun updateText(index: Int, text: String, textColor: Int, backgroundColor: Int?) {
        val layer = textLayers.getOrNull(index) ?: return
        layer.text = text
        layer.textColor = textColor
        layer.backgroundColor = backgroundColor
        invalidate()
    }

    /** 临时隐藏/恢复某段文字（输入框编辑时隐藏画布上的那份） */
    fun setTextHidden(index: Int, hidden: Boolean) {
        val layer = textLayers.getOrNull(index) ?: return
        layer.hidden = hidden
        invalidate()
    }

    /** 删除某段文字 */
    fun removeText(index: Int) {
        if (index !in textLayers.indices) return
        textLayers.removeAt(index)
        if (selectedTextIndex == index) selectedTextIndex = -1
        if (selectedTextIndex > index) selectedTextIndex--
        invalidate()
    }

    /** 取消文字选中（切工具/点空白时调用） */
    fun clearTextSelection() {
        if (selectedTextIndex < 0) return
        selectedTextIndex = -1
        textDragging = false
        invalidate()
    }

    /**
     * 锁住文字手势（文字输入浮层打开时用，避免拖动底下的文字）。
     * 解锁后：点文字=选中，按住拖动=移动，双指=缩放该文字。
     */
    fun setTextGesturesLocked(locked: Boolean) {
        if (textGesturesLocked == locked) return
        textGesturesLocked = locked
        if (locked) clearTextSelection()
    }

    fun currentRotationDegrees(): Int = rotation

    /** 切换裁剪工具模式（编辑页里选其他工具时隐藏裁剪框） */
    fun setCropMode(enabled: Boolean) {
        if (cropMode == enabled) return
        cropMode = enabled
        draggingHandle = CropGeometry.Handle.NONE
        cancelIdleAutoFit()
        autoFitAnimator?.cancel()
        autoFitAnimator = null
        val bmp = bitmap
        if (bmp != null) {
            if (enabled) {
                // 进入裁剪：先把图片缩一点，四周留出可拖拽的边距（微信的初始状态）
                val availTop = cropContentTop.toFloat()
                val availBottom = (height - cropContentBottom).toFloat()
                val availW = width.toFloat()
                val availH = (availBottom - availTop).coerceAtLeast(1f)
                val fit = min(availW / bmp.width, availH / bmp.height)
                scale = (fit * cropEnterInset).coerceAtLeast(0.05f)
                centerX = availW / 2f
                centerY = availTop + availH / 2f
                updateImageRect()
                // 网格框 = 已确认裁剪区域在当前视图里的可见部分；没有裁剪过就用图片区域
                val pending = pendingCrop
                if (pending != null) {
                    val left = imageRect.left + pending.left * imageRect.width()
                    val top = imageRect.top + pending.top * imageRect.height()
                    val right = imageRect.left + pending.right * imageRect.width()
                    val bottom = imageRect.top + pending.bottom * imageRect.height()
                    frame.set(
                        max(left, imageRect.left),
                        max(top, imageRect.top),
                        min(right, imageRect.right),
                        min(bottom, imageRect.bottom),
                    )
                } else {
                    frame.set(imageRect)
                }
                minScale = min(fit, scale)
                maxScale = minScale * MAX_ZOOM
                android.util.Log.d(
                    "FilePickerCropView",
                    "进入裁剪: imgScale=$scale frame=${frame.width().toInt()}x${frame.height().toInt()} " +
                        "view=${width}x$height",
                )
                scheduleIdleAutoFit()
            }
            minScale = if (enabled) minScale else computeMinScale()
            maxScale = minScale * MAX_ZOOM
            scale = scale.coerceIn(minScale, maxScale)
            updateImageRect()
            constrain()
            notifyFrameChanged()
        }
        invalidate()
    }

    /** 裁剪框在图片中的归一化位置（0~1） */
    fun cropRatio(): CropGeometry.RectF2 =
        CropGeometry.frameToImageRatio(frame.toGeometry(), imageRect.toGeometry())

    /** 框是否初始状态（用于"还原"置灰） */
    fun isDefault(): Boolean {
        if (rotation != 0 || !bakeMatrix.isIdentity || !initialized) return false
        val bmp = bitmap ?: return false
        val target = initialFrameFor(bmp)
        return abs(target.left - frame.left) < 1f &&
            abs(target.top - frame.top) < 1f &&
            abs(target.right - frame.right) < 1f &&
            abs(target.bottom - frame.bottom) < 1f
    }

    /** 还原：重置缩放/平移/框/旋转 */
    fun resetCrop() {
        rotation = 0
        bakeMatrix.reset()
        textLayers.clear()
        strokes.clear()
        activeStroke = null
        maskDirty = true
        effectsDirty = true
        bitmap = sourceBitmap
        initLayout()
        notifyFrameChanged()
        invalidate()
    }

    /** 水平镜像：同样**以裁剪框内的内容中心为原点**原地翻转，画面不跳 */
    fun mirror() {
        val src = bitmap ?: return
        val cropInBitmap = currentCropRectInBitmap()
        if (cropInBitmap.isEmpty()) return
        val flipped = try {
            Bitmap.createBitmap(
                src, 0, 0, src.width, src.height,
                Matrix().apply { postScale(-1f, 1f) }, true
            )
        } catch (e: Throwable) {
            null
        } ?: return
        // 水平镜像：bitmap 坐标 (x,y) → (W-x, y)
        val w = src.width.toFloat()
        val contentInNewBitmap = CropGeometry.RectF2(
            w - cropInBitmap.right,
            cropInBitmap.top,
            w - cropInBitmap.left,
            cropInBitmap.bottom,
        )
        replaceBitmap(flipped)
        bakeMatrix.postConcat(Matrix().apply { postScale(-1f, 1f) })
        applyInPlace(contentInNewBitmap)
        // 文字/笔画跟着图一起镜像
        textLayers.forEach { it.followMirrorHorizontal() }
        strokes.forEach { it.followMirrorHorizontal() }
        pendingCrop = pendingCrop?.let {
            CropGeometry.RectF2(1f - it.right, it.top, 1f - it.left, it.bottom)
        }
        maskDirty = true
        doodleDirty = true
        effectsDirty = true
        invalidate()
    }

    /** 当前裁剪框在图片像素坐标里的位置 */
    private fun currentCropRectInBitmap(): CropGeometry.RectF2 {
        if (bitmap == null || scale <= 0f) return CropGeometry.RectF2.ZERO
        val left = (frame.left - imageRect.left) / scale
        val top = (frame.top - imageRect.top) / scale
        val right = (frame.right - imageRect.left) / scale
        val bottom = (frame.bottom - imageRect.top) / scale
        val bmp = bitmap ?: return CropGeometry.RectF2.ZERO
        return CropGeometry.RectF2(
            left.coerceIn(0f, bmp.width.toFloat()),
            top.coerceIn(0f, bmp.height.toFloat()),
            right.coerceIn(0f, bmp.width.toFloat()),
            bottom.coerceIn(0f, bmp.height.toFloat()),
        )
    }

    /** 替换当前显示位图（保留原始位图不回收） */
    private fun replaceBitmap(newBitmap: Bitmap) {
        val old = bitmap
        if (old != null && old != newBitmap && old != sourceBitmap && !old.isRecycled) {
            old.recycle()
        }
        bitmap = newBitmap
    }

    /**
     * 变换后让"框内那块内容"重新覆盖到框里，内容中心对齐框中心。
     * 这样旋转/镜像看起来是"框里的画面原地转了/翻了"，而不是整张图跳位置。
     */
    private fun applyInPlace(contentInBitmap: CropGeometry.RectF2) {
        val bmp = bitmap ?: return
        if (contentInBitmap.width <= 0f || contentInBitmap.height <= 0f) return
        val newScale = maxOf(
            frame.width() / contentInBitmap.width,
            frame.height() / contentInBitmap.height,
        )
        minScale = computeMinScale()
        maxScale = minScale * MAX_ZOOM
        scale = newScale.coerceIn(minScale, maxScale)
        val contentCx = (contentInBitmap.left + contentInBitmap.right) / 2f
        val contentCy = (contentInBitmap.top + contentInBitmap.bottom) / 2f
        centerX = frame.centerX() - (contentCx - bmp.width / 2f) * scale
        centerY = frame.centerY() - (contentCy - bmp.height / 2f) * scale
        updateImageRect()
        constrain()
        notifyFrameChanged()
        invalidate()
    }

    /** 导出用的累计变换（旋转 + 镜像），交给 CropEngine 作用在解码后的原图上 */
    fun exportTransform(): Matrix = Matrix(bakeMatrix)

    /** 释放位图资源（Activity 销毁时调用） */
    fun release() {
        val current = bitmap
        val source = sourceBitmap
        if (current != null && current != source && !current.isRecycled) current.recycle()
        if (source != null && !source.isRecycled) source.recycle()
        bitmap = null
        sourceBitmap = null
    }

    /**
     * 顺时针旋转 90°：**以裁剪框内的内容中心为原点原地旋转**。
     *
     * 不是"整张图重新 fitCenter"——那样用户缩放/平移到某处后一点旋转，画面会整个跳走。
     * 这里保持框不动：算出框内那块内容 → 旋转 → 重新覆盖到框里并让内容中心仍对齐框中心。
     */
    fun rotate90() {
        val src = bitmap ?: return
        val cropInBitmap = currentCropRectInBitmap()
        if (cropInBitmap.isEmpty()) return
        val rotated = try {
            Bitmap.createBitmap(
                src, 0, 0, src.width, src.height,
                Matrix().apply { postRotate(90f) }, true
            )
        } catch (e: Throwable) {
            null
        } ?: return
        // 顺时针 90°：bitmap 坐标 (x,y) → (H-y, x)
        val h = src.height.toFloat()
        val contentInNewBitmap = CropGeometry.RectF2(
            h - cropInBitmap.bottom,
            cropInBitmap.left,
            h - cropInBitmap.top,
            cropInBitmap.right,
        )
        replaceBitmap(rotated)
        rotation = (rotation + 90) % 360
        bakeMatrix.postConcat(Matrix().apply { postRotate(90f) })
        applyInPlace(contentInNewBitmap)
        // 文字/笔画跟着图一起转
        textLayers.forEach { it.followRotate90Cw() }
        strokes.forEach { it.followRotate90Cw() }
        pendingCrop = pendingCrop?.let {
            CropGeometry.RectF2(1f - it.bottom, it.left, 1f - it.top, it.right)
        }
        maskDirty = true
        doodleDirty = true
        effectsDirty = true
        invalidate()
    }

    /** 把文字图层画到 View 上（坐标随图片缩放/平移） */
    private fun drawTextLayers(canvas: Canvas, bmp: Bitmap) {
        if (textLayers.isEmpty()) return
        val shortSide = min(bmp.width, bmp.height).toFloat()
        textLayers.forEachIndexed { index, layer ->
            if (layer.hidden) return@forEachIndexed
            textPaint.textSize = max(8f * density, layer.sizeFrac * shortSide * scale)
            val x = imageRect.left + layer.fx * imageRect.width()
            val y = imageRect.top + layer.fy * imageRect.height()
            val box = textBox(layer, textPaint, x, y)
            // 圆角背景（微信那种"文字带底色"）
            layer.backgroundColor?.let { bg ->
                textBgPaint.color = bg
                canvas.drawRoundRect(box, box.height() * 0.28f, box.height() * 0.28f, textBgPaint)
            }
            textPaint.color = layer.textColor
            val fm = textPaint.fontMetrics
            canvas.drawText(layer.text, x, y - (fm.ascent + fm.descent) / 2f, textPaint)

            // 选中的文字：画虚线框，提示可以拖动/双指缩放
            if (index == selectedTextIndex) {
                canvas.drawRoundRect(box, 4f * density, 4f * density, textSelectionPaint)
            }
        }
    }

    /** 文字（含背景内边距）在 View 上的矩形 */
    private fun textBox(layer: TextLayer, paint: Paint, cx: Float, cy: Float): RectF {
        val w = paint.measureText(layer.text)
        val fm = paint.fontMetrics
        val h = fm.descent - fm.ascent
        return if (layer.backgroundColor != null) {
            val padH = h * 0.38f
            val padV = h * 0.20f
            RectF(cx - w / 2f - padH, cy - h / 2f - padV, cx + w / 2f + padH, cy + h / 2f + padV)
        } else {
            val pad = 8f * density
            RectF(cx - w / 2f - pad, cy - h / 2f - pad, cx + w / 2f + pad, cy + h / 2f + pad)
        }
    }

    // ------------------------------------------------------------------
    // 裁剪框空闲自动放大（停手 3 秒 → 框放大到留边合适大小，动画过渡）
    // ------------------------------------------------------------------

    private fun scheduleIdleAutoFit() {
        android.util.Log.d("FilePickerCropView", "安排空闲自动放大 3s")
        cancelIdleAutoFit()
        val runnable = Runnable { autoFitCropFrame() }
        idleAutoFit = runnable
        idleHandler.postDelayed(runnable, 3000)
    }

    private fun cancelIdleAutoFit() {
        idleAutoFit?.let { idleHandler.removeCallbacks(it) }
        idleAutoFit = null
    }

    /**
     * 把当前裁剪框放大到"屏幕内留边最大"（保持宽高比），
     * 同时让框里的内容保持在框中心，动画过渡；和微信停手后的自动回弹一致。
     */
    private fun autoFitCropFrame() {
        if (!cropMode || !initialized) return
        val bmp = bitmap ?: return
        if (frame.width() <= 1f || frame.height() <= 1f) return
        val margin = 24f * density
        val availTop = cropContentTop.toFloat()
        val availBottom = (height - cropContentBottom).toFloat()
        val maxW = width - margin * 2
        val maxH = (availBottom - availTop) - margin * 2
        val factor = min(maxW / frame.width(), maxH / frame.height())
        if (factor <= 1.02f) return

        // 框中心在图片里的归一化位置（放大时保持这个中心）
        val frameCxInBmp = (frame.centerX() - imageRect.left) / imageRect.width()
        val frameCyInBmp = (frame.centerY() - imageRect.top) / imageRect.height()

        val startFrame = RectF(frame)
        val startScale = scale
        val startCx = centerX
        val startCy = centerY

        // 目标框：保持宽高比、居中到屏幕中心
        val targetW = frame.width() * factor
        val targetH = frame.height() * factor
        val availCx = width / 2f
        val availCy = availTop + (availBottom - availTop) / 2f
        val targetFrame = RectF(
            availCx - targetW / 2f,
            availCy - targetH / 2f,
            availCx + targetW / 2f,
            availCy + targetH / 2f,
        )
        val targetScale = (startScale * factor).coerceIn(minScale, maxScale)
        // 让框里的内容中心仍然对齐框中心
        val bmpCx = frameCxInBmp * bmp.width
        val bmpCy = frameCyInBmp * bmp.height
        val targetCx = targetFrame.centerX() - (bmpCx - bmp.width / 2f) * targetScale
        val targetCy = targetFrame.centerY() - (bmpCy - bmp.height / 2f) * targetScale

        autoFitAnimator?.cancel()
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 260
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { va ->
            val f = va.animatedFraction
            frame.set(
                startFrame.left + (targetFrame.left - startFrame.left) * f,
                startFrame.top + (targetFrame.top - startFrame.top) * f,
                startFrame.right + (targetFrame.right - startFrame.right) * f,
                startFrame.bottom + (targetFrame.bottom - startFrame.bottom) * f,
            )
            scale = startScale + (targetScale - startScale) * f
            centerX = startCx + (targetCx - startCx) * f
            centerY = startCy + (targetCy - startCy) * f
            updateImageRect()
            invalidate()
        }
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                constrain()
                notifyFrameChanged()
                invalidate()
            }
        })
        autoFitAnimator = animator
        animator.start()
        android.util.Log.d(
            "FilePickerCropView",
            "裁剪框空闲自动放大: factor=$factor frame=${frame.width().toInt()}x${frame.height().toInt()}",
        )
    }

    // ------------------------------------------------------------------
    // 初始化 / 布局
    // ------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) initLayout() else {
            // 尺寸变化（旋转屏幕等）：重新初始化，避免沿用旧坐标
            initLayout()
        }
    }

    private fun initLayout() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        centerX = width / 2f
        centerY = height / 2f
        val fitScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        if (aspectRatio > 0f) {
            // 固定框：边长 = 屏幕短边 * 0.86，居中；横图竖图框一样大
            val side = min(width, height) * 0.86f
            val frameW: Float
            val frameH: Float
            if (aspectRatio >= 1f) {
                frameH = side
                frameW = side * aspectRatio
            } else {
                frameW = side
                frameH = side / aspectRatio
            }
            frame.set(
                centerX - frameW / 2f,
                centerY - frameH / 2f,
                centerX + frameW / 2f,
                centerY + frameH / 2f,
            )
            // 图片必须覆盖框：缩放到"短边 ≥ 框边"（横图按高、竖图按宽）
            minScale = max(frameW / bmp.width, frameH / bmp.height)
            scale = minScale
        } else {
            // 自由裁剪：初始框 = 整张图
            scale = fitScale
            updateImageRect()
            frame.set(imageRect)
            minScale = fitScale
        }
        maxScale = minScale * MAX_ZOOM
        updateImageRect()
        constrain()
        initialized = true
        FilePickerLog.d(
            "FilePickerCropView",
            "init: view=${width}x$height bmp=${bmp.width}x${bmp.height} aspect=$aspectRatio " +
                "frame=${frame.width().toInt()}x${frame.height().toInt()} scale=$scale min=$minScale max=$maxScale"
        )
        notifyFrameChanged()
    }

    private fun initialFrameFor(bmp: Bitmap): RectF {
        if (aspectRatio > 0f) {
            val side = min(width, height) * 0.86f
            val fw = if (aspectRatio >= 1f) side * aspectRatio else side
            val fh = if (aspectRatio >= 1f) side else side / aspectRatio
            return RectF(
                width / 2f - fw / 2f, height / 2f - fh / 2f,
                width / 2f + fw / 2f, height / 2f + fh / 2f,
            )
        }
        return CropGeometry.fitCenter(bmp.width, bmp.height, width, height).toRectF()
    }

    private fun updateImageRect() {
        val bmp = bitmap ?: return
        val w = bmp.width * scale
        val h = bmp.height * scale
        imageRect.set(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
    }

    private fun computeMinScale(): Float {
        val bmp = bitmap ?: return 1f
        if (bmp.width <= 0 || bmp.height <= 0) return 1f
        // 非裁剪状态：允许缩到"整张图刚好显示在屏幕里"
        if (!cropMode) return min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        return max(frame.width() / bmp.width, frame.height() / bmp.height)
    }

    // ------------------------------------------------------------------
    // 手势
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized) return false
        // 文字手势优先：点文字选中/拖动/双指缩放
        if (handleTextTouch(event)) return true
        // 涂抹模式：单指画，双指缩放图片
        if (handlePaintTouch(event)) return true
        // 注意：不管是否处于裁剪状态，图片都允许拖动/双指缩放
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelIdleAutoFit()
                lastTouchX = event.x
                lastTouchY = event.y
                lastFocusX = event.x
                lastFocusY = event.y
                draggingHandle = if (cropMode && aspectRatio <= 0f) {
                    val hit = CropGeometry.hitTest(frame.toGeometry(), event.x, event.y, touchRadius)
                    if (hit == CropGeometry.Handle.INSIDE) CropGeometry.Handle.NONE else hit
                } else {
                    CropGeometry.Handle.NONE
                }
                android.util.Log.d(
                    "FilePickerCropView",
                    "DOWN at (${event.x.toInt()},${event.y.toInt()}) hit=$draggingHandle frame=" +
                        "${frame.left.toInt()},${frame.top.toInt()},${frame.right.toInt()},${frame.bottom.toInt()}",
                )
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 变为双指：记录焦点基准
                lastFocusX = scaleDetector.focusX
                lastFocusY = scaleDetector.focusY
                draggingHandle = CropGeometry.Handle.NONE
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    // 双指：缩放由 scaleDetector 处理；这里按焦点位移做平移（双指拖动）
                    val fx = scaleDetector.focusX
                    val fy = scaleDetector.focusY
                    panBy(fx - lastFocusX, fy - lastFocusY)
                    lastFocusX = fx
                    lastFocusY = fy
                } else if (scaleDetector.isInProgress) {
                    // 缩放结束的最后一帧，不做单指平移，避免跳
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (cropMode && draggingHandle != CropGeometry.Handle.NONE) {
                        resizeFrame(dx, dy)
                    } else {
                        panBy(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 关键：多指抬起后重置基准点到剩下那根手指，否则松手瞬间会"窜"
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = event.getX(remainingIndex)
                    lastTouchY = event.getY(remainingIndex)
                    lastFocusX = lastTouchX
                    lastFocusY = lastTouchY
                }
                draggingHandle = CropGeometry.Handle.NONE
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingHandle = CropGeometry.Handle.NONE
                if (cropMode) scheduleIdleAutoFit()
                return true
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // 文字手势：单指拖、双指缩放
    // ------------------------------------------------------------------

    private fun handleTextTouch(event: MotionEvent): Boolean {
        if (textGesturesLocked || textLayers.isEmpty()) return false
        val bmp = bitmap ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTestText(event.x, event.y, bmp)
                if (hit >= 0) {
                    // 本次按下前是否已是选中态：是的话，松手不拖动 = 再点一下 → 进入编辑
                    textTapCandidate = hit == selectedTextIndex
                    selectedTextIndex = hit
                    textDragging = true
                    textMoved = false
                    textDownX = event.x
                    textDownY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
                // 点空白：取消选中，把手势让回图片
                if (selectedTextIndex >= 0) {
                    selectedTextIndex = -1
                    invalidate()
                }
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!textDragging || selectedTextIndex < 0) return false
                lastSpan = spanOf(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!textDragging || selectedTextIndex < 0) return false
                if (event.pointerCount >= 2) {
                    val span = spanOf(event)
                    if (lastSpan > 1f && span > 1f) {
                        resizeSelectedText(span / lastSpan)
                        textMoved = true
                    }
                    lastSpan = span
                } else {
                    if (!textMoved) {
                        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                        if (abs(event.x - textDownX) > slop || abs(event.y - textDownY) > slop) {
                            textMoved = true
                        }
                    }
                    moveSelectedText(event.x - lastTouchX, event.y - lastTouchY)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (!textDragging) return false
                // 手指抬起后重置单指拖动基准，避免松手瞬间跳
                val remain = if (event.actionIndex == 0) 1 else 0
                if (remain < event.pointerCount) {
                    lastTouchX = event.getX(remain)
                    lastTouchY = event.getY(remain)
                }
                lastSpan = 0f
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!textDragging) return false
                textDragging = false
                if (!textMoved && textTapCandidate && selectedTextIndex >= 0) {
                    // 再点一下已选中的文字：进入这段文字的编辑状态
                    onTextEditRequested?.invoke(selectedTextIndex)
                }
                textTapCandidate = false
                textMoved = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (!textDragging) return false
                textDragging = false
                textTapCandidate = false
                textMoved = false
                return true
            }
        }
        return false
    }

    private fun spanOf(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** 命中测试：返回被点中的文字图层下标（后加的在上层） */
    private fun hitTestText(x: Float, y: Float, bmp: Bitmap): Int {
        val shortSide = min(bmp.width, bmp.height).toFloat()
        for (i in textLayers.indices.reversed()) {
            val layer = textLayers[i]
            if (layer.hidden) continue
            textMeasurePaint.textSize = max(8f * density, layer.sizeFrac * shortSide * scale)
            val cx = imageRect.left + layer.fx * imageRect.width()
            val cy = imageRect.top + layer.fy * imageRect.height()
            val box = textBox(layer, textMeasurePaint, cx, cy)
            if (x in box.left..box.right && y in box.top..box.bottom) return i
        }
        return -1
    }

    /** 拖动选中的文字（dx/dy 是 View 像素，换算成图片归一化坐标） */
    private fun moveSelectedText(dx: Float, dy: Float) {
        val layer = textLayers.getOrNull(selectedTextIndex) ?: return
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return
        layer.fx = (layer.fx + dx / imageRect.width()).coerceIn(0f, 1f)
        layer.fy = (layer.fy + dy / imageRect.height()).coerceIn(0f, 1f)
        invalidate()
    }

    /** 双指缩放选中的文字：只改字号比例，锚点不动 */
    private fun resizeSelectedText(factor: Float) {
        val layer = textLayers.getOrNull(selectedTextIndex) ?: return
        layer.sizeFrac = (layer.sizeFrac * factor).coerceIn(0.02f, 0.4f)
        invalidate()
    }

    // ------------------------------------------------------------------
    // 涂抹（马赛克/毛刷）
    // ------------------------------------------------------------------

    private fun handlePaintTouch(event: MotionEvent): Boolean {
        if (!paintEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount > 1) return false
                startStroke(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 双指：结束当前笔画，交给图片缩放
                activeStroke = null
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val stroke = activeStroke ?: return false
                if (event.pointerCount > 1) return false
                for (i in 0 until event.pointerCount) {
                    addPointToStroke(stroke, event.getX(i), event.getY(i))
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                activeStroke = null
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val had = activeStroke != null
                activeStroke = null
                return had
            }
        }
        return false
    }

    private fun startStroke(x: Float, y: Float) {
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return
        android.util.Log.d(
            "FilePickerCropView",
            "开始涂抹: style=$paintStyle eraser=$paintEraser color=${Integer.toHexString(brushColor)}",
        )
        val stroke = BrushStroke(
            radiusFrac = brushRadiusFrac,
            mode = paintStyle,
            erase = paintEraser,
            color = brushColor,
        )
        strokes.add(stroke)
        activeStroke = stroke
        addPointToStroke(stroke, x, y)
    }

    private fun addPointToStroke(stroke: BrushStroke, x: Float, y: Float) {
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return
        val fx = ((x - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
        val fy = ((y - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        val points = stroke.points
        if (points.isNotEmpty()) {
            val last = points[points.size - 1]
            val dx = (fx - last[0]) * imageRect.width()
            val dy = (fy - last[1]) * imageRect.height()
            if (dx * dx + dy * dy < (2.5f * density) * (2.5f * density)) return
        }
        stroke.add(fx, fy)
        maskDirty = true
        doodleDirty = true
        invalidate()
    }

    /** 把涂抹效果画到画布上：效果图 + 遮罩 */
    private fun drawPaintedEffects(canvas: Canvas, bmp: Bitmap) {
        if (strokes.isEmpty()) return
        ensureEffects(bmp)
        ensureMasks(bmp)
        val mosaicMask = pixelMask ?: return
        val smudgeMask = smudgeMask ?: return
        val save = canvas.saveLayer(imageRect, null)
        // 像素马赛克
        val mosaic = mosaicBitmap
        if (mosaic != null) {
            canvas.drawBitmap(mosaic, null, imageRect, effectPaint)
            canvas.drawBitmap(mosaicMask, null, imageRect, maskPaint)
            // DST_IN 后原本区域变成马赛克；需要再恢复遮罩外的原图：
        }
        canvas.restoreToCount(save)
        // 上面的 saveLayer 只处理马赛克；毛刷单独一层
        val save2 = canvas.saveLayer(imageRect, null)
        val smudge = smudgeBitmap
        if (smudge != null) {
            canvas.drawBitmap(smudge, null, imageRect, effectPaint)
            canvas.drawBitmap(smudgeMask, null, imageRect, maskPaint)
        }
        canvas.restoreToCount(save2)
        // 彩色画笔涂鸦层
        val doodle = doodleLayer
        if (doodle != null) {
            canvas.drawBitmap(doodle, null, imageRect, effectPaint)
        }
    }

    private var pixelMask: Bitmap? = null
    private var smudgeMask: Bitmap? = null

    private fun ensureEffects(bmp: Bitmap) {
        if (!effectsDirty && mosaicBitmap != null && smudgeBitmap != null) return
        effectsDirty = false
        mosaicBitmap?.recycle()
        smudgeBitmap?.recycle()
        val shortSide = min(bmp.width, bmp.height).coerceAtLeast(8)
        val mosaicBlock = (shortSide * 0.035f).toInt().coerceAtLeast(4)
        val mosaicW = max(1, bmp.width / mosaicBlock)
        val mosaicH = max(1, bmp.height / mosaicBlock)
        val down = Bitmap.createScaledBitmap(bmp, mosaicW, mosaicH, true)
        mosaicBitmap = Bitmap.createScaledBitmap(down, bmp.width, bmp.height, false)
        if (down != mosaicBitmap && down != bmp) down.recycle()

        val blurW = max(1, bmp.width / 14)
        val blurH = max(1, bmp.height / 14)
        val blurDown = Bitmap.createScaledBitmap(bmp, blurW, blurH, true)
        smudgeBitmap = Bitmap.createScaledBitmap(blurDown, bmp.width, bmp.height, true)
        if (blurDown != smudgeBitmap && blurDown != bmp) blurDown.recycle()
    }

    private fun ensureMasks(bmp: Bitmap) {
        if (!maskDirty && pixelMask != null && smudgeMask != null) return
        maskDirty = false
        pixelMask?.recycle()
        smudgeMask?.recycle()
        pixelMask = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        smudgeMask = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val shortSide = min(bmp.width, bmp.height).toFloat()
        val pixelCanvas = Canvas(pixelMask!!)
        val smudgeCanvas = Canvas(smudgeMask!!)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        // 彩色画笔涂鸦层
        if (doodleDirty || doodleLayer == null) {
            doodleDirty = false
            doodleLayer?.recycle()
            doodleLayer = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val doodleCanvas = Canvas(doodleLayer!!)
            val doodlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val doodleClear = Paint(doodlePaint).apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            }
            for (stroke in strokes) {
                if (stroke.points.isEmpty() || stroke.mode != 2) continue
                doodlePaint.color = stroke.color
                doodlePaint.strokeWidth = stroke.radiusFrac * shortSide * 2f
                doodleClear.strokeWidth = doodlePaint.strokeWidth
                drawStrokePath(
                    doodleCanvas, stroke, bmp,
                    if (stroke.erase) doodleClear else doodlePaint,
                )
            }
        }
        for (stroke in strokes) {
            if (stroke.points.isEmpty() || stroke.mode == 2) continue
            paintStrokePaint.strokeWidth = stroke.radiusFrac * shortSide * 2f
            clearPaint.strokeWidth = paintStrokePaint.strokeWidth
            if (stroke.erase) {
                // 橡皮：两张遮罩都要擦掉
                drawStrokePath(pixelCanvas, stroke, bmp, clearPaint)
                drawStrokePath(smudgeCanvas, stroke, bmp, clearPaint)
            } else {
                val target = if (stroke.mode == 1) smudgeCanvas else pixelCanvas
                drawStrokePath(target, stroke, bmp, paintStrokePaint)
            }
        }
    }

    private fun drawStrokePath(canvas: Canvas, stroke: BrushStroke, bmp: Bitmap, paint: Paint) {
        val points = stroke.points
        if (points.size == 1) {
            canvas.drawPoint(points[0][0] * bmp.width, points[0][1] * bmp.height, paint)
            return
        }
        val path = android.graphics.Path()
        path.moveTo(points[0][0] * bmp.width, points[0][1] * bmp.height)
        for (i in 1 until points.size) {
            path.lineTo(points[i][0] * bmp.width, points[i][1] * bmp.height)
        }
        canvas.drawPath(path, paint)
    }

    private fun panBy(dx: Float, dy: Float) {
        centerX += dx
        centerY += dy
        updateImageRect()
        constrain()
        invalidate()
    }

    private fun zoomAbout(factor: Float, focusX: Float, focusY: Float) {
        val oldScale = scale
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        if (abs(newScale - oldScale) < 0.0001f) return
        // 让 focus 下的图片点保持不动
        centerX = focusX - (focusX - centerX) * (newScale / oldScale)
        centerY = focusY - (focusY - centerY) * (newScale / oldScale)
        scale = newScale
        updateImageRect()
        constrain()
        invalidate()
    }

    private fun resizeFrame(dx: Float, dy: Float) {
        val bounds = imageRect.toGeometry()
        val minSize = 48f * density
        val resized = CropGeometry.resizeFrame(
            frame = frame.toGeometry(),
            handle = draggingHandle,
            dx = dx,
            dy = dy,
            bounds = bounds,
            minSize = minSize,
            aspect = if (aspectRatio > 0f) aspectRatio else null,
        )
        frame.set(resized.left, resized.top, resized.right, resized.bottom)
        minScale = computeMinScale()
        if (scale < minScale) {
            scale = minScale
            updateImageRect()
        }
        maxScale = minScale * MAX_ZOOM
        constrain()
        notifyFrameChanged()
        invalidate()
    }

    /** 图片必须盖住裁剪框：不满足时把图片推回去 */
    private fun constrain() {
        val bmp = bitmap ?: return
        if (!cropMode) {
            // 非裁剪：图片可以自由拖动/缩放，只要求别整张拖出屏幕
            val visible = 72f * density
            val w = bmp.width * scale
            val h = bmp.height * scale
            val minCx = visible - w / 2f
            val maxCx = width - visible + w / 2f
            val minCy = visible - h / 2f
            val maxCy = height - visible + h / 2f
            val nx = centerX.coerceIn(minCx, maxCx)
            val ny = centerY.coerceIn(minCy, maxCy)
            if (nx != centerX || ny != centerY) {
                centerX = nx
                centerY = ny
                updateImageRect()
            }
            return
        }
        if (frame.isEmpty) return
        var dx = 0f
        if (imageRect.width() >= frame.width()) {
            if (imageRect.left > frame.left) dx = frame.left - imageRect.left
            else if (imageRect.right < frame.right) dx = frame.right - imageRect.right
        }
        var dy = 0f
        if (imageRect.height() >= frame.height()) {
            if (imageRect.top > frame.top) dy = frame.top - imageRect.top
            else if (imageRect.bottom < frame.bottom) dy = frame.bottom - imageRect.bottom
        }
        if (dx != 0f || dy != 0f) {
            centerX += dx
            centerY += dy
            updateImageRect()
        }
    }

    private fun notifyFrameChanged() {
        onFrameChanged?.invoke()
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (!initialized) return
        canvas.drawBitmap(bmp, null, imageRect, null)
        drawPaintedEffects(canvas, bmp)
        drawTextLayers(canvas, bmp)
        if (!cropMode) return

        val w = width.toFloat()
        val h = height.toFloat()
        // 框外压暗
        canvas.drawRect(0f, 0f, w, frame.top, dimPaint)
        canvas.drawRect(0f, frame.bottom, w, h, dimPaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint)
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, dimPaint)

        // 框线
        canvas.drawRect(frame, borderPaint)

        // 三分线：自由裁剪才画（和微信编辑器一致；头像裁剪不画）
        if (aspectRatio <= 0f) {
            val thirdW = frame.width() / 3f
            val thirdH = frame.height() / 3f
            for (i in 1..2) {
                val x = frame.left + thirdW * i
                canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint)
                val y = frame.top + thirdH * i
                canvas.drawLine(frame.left, y, frame.right, y, gridPaint)
            }
            drawCorners(canvas)
            drawEdgeHandles(canvas)
        } else {
            // 固定框：只画四角括号，简洁
            drawCorners(canvas)
        }
    }

    /** 四角 L 型括号 */
    private fun drawCorners(canvas: Canvas) {
        val len = 16f * density
        val l = frame.left
        val t = frame.top
        val r = frame.right
        val b = frame.bottom
        canvas.drawLine(l, t, l + len, t, cornerPaint)
        canvas.drawLine(l, t, l, t + len, cornerPaint)
        canvas.drawLine(r, t, r - len, t, cornerPaint)
        canvas.drawLine(r, t, r, t + len, cornerPaint)
        canvas.drawLine(r, b, r - len, b, cornerPaint)
        canvas.drawLine(r, b, r, b - len, cornerPaint)
        canvas.drawLine(l, b, l + len, b, cornerPaint)
        canvas.drawLine(l, b, l, b - len, cornerPaint)
    }

    /** 四边中点把手（自由裁剪） */
    private fun drawEdgeHandles(canvas: Canvas) {
        val len = 14f * density
        val half = 1.5f * density
        canvas.drawLine(frame.centerX(), frame.top - half, frame.centerX(), frame.top + len, handlePaint)
        canvas.drawLine(frame.centerX(), frame.bottom - len, frame.centerX(), frame.bottom + half, handlePaint)
        canvas.drawLine(frame.left - half, frame.centerY(), frame.left + len, frame.centerY(), handlePaint)
        canvas.drawLine(frame.right - len, frame.centerY(), frame.right + half, frame.centerY(), handlePaint)
    }

    // ------------------------------------------------------------------
    // 桥接
    // ------------------------------------------------------------------

    private fun RectF.toGeometry(): CropGeometry.RectF2 =
        CropGeometry.RectF2(left, top, right, bottom)

    private fun CropGeometry.RectF2.toRectF(): RectF = RectF(left, top, right, bottom)

    private fun RectF.set(rect: CropGeometry.RectF2) {
        set(rect.left, rect.top, rect.right, rect.bottom)
    }

    companion object {
        /** 最大放大倍数（相对"刚好覆盖框"的倍数）：常用机型 3~4 倍 */
        private const val MAX_ZOOM = 4f
    }
}
