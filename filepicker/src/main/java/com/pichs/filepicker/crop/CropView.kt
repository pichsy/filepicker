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
        initialized = false
        initLayout()
        invalidate()
    }

    fun currentRotation(): Int = rotation

    /** 在当前裁剪框中心加一段文字（坐标按显示图片归一化保存） */
    fun addText(text: String, color: Int = android.graphics.Color.WHITE) {
        val bmp = bitmap ?: return
        if (text.isBlank() || imageRect.width() <= 0f || imageRect.height() <= 0f) return
        val centerXInBmp = (frame.centerX() - imageRect.left) / imageRect.width()
        val centerYInBmp = (frame.centerY() - imageRect.top) / imageRect.height()
        textLayers.add(
            TextLayer(
                text = text,
                fx = centerXInBmp.coerceIn(0f, 1f),
                fy = centerYInBmp.coerceIn(0f, 1f),
                sizeFrac = defaultTextSizeFrac,
                color = color,
            )
        )
        android.util.Log.d("FilePickerCropView", "文字图层 +1: \"$text\" 共 ${textLayers.size} 个")
        invalidate()
    }

    /** 当前文字图层快照（导出时用） */
    fun textLayersSnapshot(): List<TextLayer> = textLayers.toList()

    fun currentRotationDegrees(): Int = rotation

    /** 切换裁剪工具模式（编辑页里选其他工具时隐藏裁剪框） */
    fun setCropMode(enabled: Boolean) {
        if (cropMode == enabled) return
        cropMode = enabled
        draggingHandle = CropGeometry.Handle.NONE
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
        // 文字锚点跟着图一起镜像
        textLayers.forEach { it.followMirrorHorizontal() }
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
        // 文字锚点跟着图一起转
        textLayers.forEach { it.followRotate90Cw() }
        invalidate()
    }

    /** 把文字图层画到 View 上（坐标随图片缩放/平移） */
    private fun drawTextLayers(canvas: Canvas, bmp: Bitmap) {
        if (textLayers.isEmpty()) return
        val shortSide = min(bmp.width, bmp.height).toFloat()
        for (layer in textLayers) {
            textPaint.color = layer.color
            textPaint.textSize = max(8f * density, layer.sizeFrac * shortSide * scale)
            val x = imageRect.left + layer.fx * imageRect.width()
            val y = imageRect.top + layer.fy * imageRect.height()
            val fm = textPaint.fontMetrics
            canvas.drawText(layer.text, x, y - (fm.ascent + fm.descent) / 2f, textPaint)
        }
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
        return max(frame.width() / bmp.width, frame.height() / bmp.height)
    }

    // ------------------------------------------------------------------
    // 手势
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized) return false
        if (!cropMode) return true
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                lastFocusX = event.x
                lastFocusY = event.y
                draggingHandle = if (aspectRatio > 0f) {
                    CropGeometry.Handle.NONE
                } else {
                    val hit = CropGeometry.hitTest(frame.toGeometry(), event.x, event.y, touchRadius)
                    if (hit == CropGeometry.Handle.INSIDE) CropGeometry.Handle.NONE else hit
                }
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
                    if (draggingHandle == CropGeometry.Handle.NONE) {
                        panBy(dx, dy)
                    } else {
                        resizeFrame(dx, dy)
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
                return true
            }
        }
        return true
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
        if (frame.isEmpty || bitmap == null) return
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
