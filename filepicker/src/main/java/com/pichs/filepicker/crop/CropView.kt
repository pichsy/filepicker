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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 裁剪画布：图片可拖动/捏合缩放，裁剪框固定显示在图片之上。
 *
 * 两种模式：
 * - **自由裁剪**（[aspectRatio] <= 0）：8 个把手可拖拽改框（4 角 + 4 边），框内/框外拖动都是平移图片；
 * - **固定比例**（如头像 1:1）：框大小固定不可改，只能拖动/缩放图片。
 *
 * 所有绘制都在 View 坐标里做；导出时用 [cropRatio] 把框换算成图片内的 0~1 归一化坐标，
 * 再交给 [CropEngine] 按原图分辨率解码裁剪。
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** fitCenter 之后、zoom=1 时的图片显示矩形 */
    private val baseRect = RectF()

    /** 当前图片显示矩形（baseRect * zoom + offset） */
    private val imageRect = RectF()

    /** 裁剪框（View 坐标） */
    private val frame = RectF()

    private var bitmap: Bitmap? = null
    private var aspectRatio: Float = 0f
    private var rotation: Int = 0
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var minZoom = 1f
    private var maxZoom = 4f
    private var initialized = false

    private var activeHandle = CropGeometry.Handle.NONE
    private var lastX = 0f
    private var lastY = 0f

    private val density = resources.displayMetrics.density
    private val touchRadius = 22f * density

    private val dimPaint = Paint().apply { color = 0x66000000 }
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
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    /** 框变化回调（用于"还原"按钮的可用状态） */
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
        bitmap = bmp
        aspectRatio = aspect
        rotation = 0
        initialized = false
        tryInit()
        invalidate()
    }

    /** 用户旋转角度（0/90/180/270，顺时针），导出时作用在图片上 */
    fun currentRotation(): Int = rotation

    /** 裁剪框在图片中的归一化位置（0~1） */
    fun cropRatio(): CropGeometry.RectF2 =
        CropGeometry.frameToImageRatio(frame.toGeometry(), imageRect.toGeometry())

    /** 框是否处于初始状态（用于"还原"按钮置灰） */
    fun isDefault(): Boolean {
        if (rotation != 0) return false
        val initial = CropGeometry.initialFrame(baseRect.toGeometry(), aspectRatio)
        return abs(initial.left - frame.left) < 1f &&
            abs(initial.top - frame.top) < 1f &&
            abs(initial.right - frame.right) < 1f &&
            abs(initial.bottom - frame.bottom) < 1f
    }

    /** 还原：重置缩放/平移/框/旋转 */
    fun resetCrop() {
        rotation = 0
        rebuildBaseRect()
        notifyFrameChanged()
        invalidate()
    }

    /** 顺时针旋转 90°（重新 fitCenter + 重置框） */
    fun rotate90() {
        val src = bitmap ?: return
        rotation = (rotation + 90) % 360
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } catch (e: Throwable) {
            null
        }
        if (rotated != null) {
            if (rotated != src) {
                bitmap = rotated
                src.recycle()
            }
            rebuildBaseRect()
            notifyFrameChanged()
            invalidate()
        }
    }

    // ------------------------------------------------------------------
    // 初始化 / 布局
    // ------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!initialized) tryInit() else rebuildBaseRect()
    }

    private fun tryInit() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        val fit = CropGeometry.fitCenter(bmp.width, bmp.height, width, height)
        if (fit.isEmpty()) return
        baseRect.set(fit.left, fit.top, fit.right, fit.bottom)
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        updateImageRect()
        val initial = CropGeometry.initialFrame(imageRect.toGeometry(), aspectRatio)
        frame.set(initial.left, initial.top, initial.right, initial.bottom)
        minZoom = computeMinZoom()
        initialized = true
        notifyFrameChanged()
    }

    private fun rebuildBaseRect() {
        val bmp = bitmap ?: return
        if (width <= 0 || height <= 0) return
        val fit = CropGeometry.fitCenter(bmp.width, bmp.height, width, height)
        baseRect.set(fit.left, fit.top, fit.right, fit.bottom)
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        updateImageRect()
        val initial = CropGeometry.initialFrame(imageRect.toGeometry(), aspectRatio)
        frame.set(initial.left, initial.top, initial.right, initial.bottom)
        minZoom = computeMinZoom()
        initialized = true
    }

    private fun updateImageRect() {
        val w = baseRect.width() * zoom
        val h = baseRect.height() * zoom
        val cx = baseRect.centerX() + offsetX
        val cy = baseRect.centerY() + offsetY
        imageRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    private fun computeMinZoom(): Float {
        if (baseRect.width() <= 0f || baseRect.height() <= 0f) return 1f
        val need = max(frame.width() / baseRect.width(), frame.height() / baseRect.height())
        return need.coerceIn(0.2f, maxZoom)
    }

    // ------------------------------------------------------------------
    // 手势
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!initialized) return false
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                activeHandle = if (aspectRatio > 0f) {
                    CropGeometry.Handle.NONE
                } else {
                    CropGeometry.hitTest(frame.toGeometry(), event.x, event.y, touchRadius)
                }
                if (activeHandle == CropGeometry.Handle.INSIDE ||
                    activeHandle == CropGeometry.Handle.NONE
                ) {
                    activeHandle = CropGeometry.Handle.NONE
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) return true
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                if (activeHandle == CropGeometry.Handle.NONE) {
                    panBy(dx, dy)
                } else {
                    resizeFrame(dx, dy)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeHandle = CropGeometry.Handle.NONE
                return true
            }
        }
        return true
    }

    private fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        offsetY += dy
        updateImageRect()
        constrainImage()
        invalidate()
    }

    private fun zoomAbout(factor: Float, focusX: Float, focusY: Float) {
        val oldZoom = zoom
        val newZoom = (zoom * factor).coerceIn(minZoom, maxZoom)
        if (abs(newZoom - oldZoom) < 0.0001f) return
        // 保持 focus 下的图片点不动
        val centerX = baseRect.centerX() + offsetX
        val centerY = baseRect.centerY() + offsetY
        val newCenterX = focusX - (focusX - centerX) * (newZoom / oldZoom)
        val newCenterY = focusY - (focusY - centerY) * (newZoom / oldZoom)
        zoom = newZoom
        offsetX = newCenterX - baseRect.centerX()
        offsetY = newCenterY - baseRect.centerY()
        updateImageRect()
        constrainImage()
        invalidate()
    }

    private fun resizeFrame(dx: Float, dy: Float) {
        val bounds = imageRect.toGeometry()
        val minSize = 48f * density
        val resized = CropGeometry.resizeFrame(
            frame = frame.toGeometry(),
            handle = activeHandle,
            dx = dx,
            dy = dy,
            bounds = bounds,
            minSize = minSize,
            aspect = if (aspectRatio > 0f) aspectRatio else null,
        )
        frame.set(resized.left, resized.top, resized.right, resized.bottom)
        minZoom = computeMinZoom()
        constrainImage()
        notifyFrameChanged()
        invalidate()
    }

    /** 图片必须盖住裁剪框：不满足时把图片推回去 */
    private fun constrainImage() {
        if (frame.isEmpty) return
        var dx = 0f
        if (imageRect.width() >= frame.width()) {
            if (imageRect.left > frame.left) dx = frame.left - imageRect.left
            else if (imageRect.right < frame.right) dx = frame.right - imageRect.right
        } else {
            dx = frame.centerX() - imageRect.centerX()
        }
        var dy = 0f
        if (imageRect.height() >= frame.height()) {
            if (imageRect.top > frame.top) dy = frame.top - imageRect.top
            else if (imageRect.bottom < frame.bottom) dy = frame.bottom - imageRect.bottom
        } else {
            dy = frame.centerY() - imageRect.centerY()
        }
        offsetX += dx
        offsetY += dy
        if (dx != 0f || dy != 0f) updateImageRect()
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

        val w = width.toFloat()
        val h = height.toFloat()
        // 框外压暗（微信是轻微压暗，图片仍可见）
        canvas.drawRect(0f, 0f, w, frame.top, dimPaint)
        canvas.drawRect(0f, frame.bottom, w, h, dimPaint)
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint)
        canvas.drawRect(frame.right, frame.top, w, frame.bottom, dimPaint)

        // 框线
        canvas.drawRect(frame, borderPaint)

        // 三分构图线
        val thirdW = frame.width() / 3f
        val thirdH = frame.height() / 3f
        for (i in 1..2) {
            val x = frame.left + thirdW * i
            canvas.drawLine(x, frame.top, x, frame.bottom, gridPaint)
            val y = frame.top + thirdH * i
            canvas.drawLine(frame.left, y, frame.right, y, gridPaint)
        }

        if (aspectRatio <= 0f) {
            drawCorners(canvas)
            drawEdgeHandles(canvas)
        }
    }

    /** 四角 L 型括号 */
    private fun drawCorners(canvas: Canvas) {
        val len = 18f * density
        val l = frame.left
        val t = frame.top
        val r = frame.right
        val b = frame.bottom
        // 左上
        canvas.drawLine(l, t, l + len, t, cornerPaint)
        canvas.drawLine(l, t, l, t + len, cornerPaint)
        // 右上
        canvas.drawLine(r, t, r - len, t, cornerPaint)
        canvas.drawLine(r, t, r, t + len, cornerPaint)
        // 右下
        canvas.drawLine(r, b, r - len, b, cornerPaint)
        canvas.drawLine(r, b, r, b - len, cornerPaint)
        // 左下
        canvas.drawLine(l, b, l + len, b, cornerPaint)
        canvas.drawLine(l, b, l, b - len, cornerPaint)
    }

    /** 四边中点把手 */
    private fun drawEdgeHandles(canvas: Canvas) {
        val len = 14f * density
        val half = 1.5f * density
        // 上下
        canvas.drawLine(frame.centerX(), frame.top - half, frame.centerX(), frame.top + len, handlePaint)
        canvas.drawLine(frame.centerX(), frame.bottom - len, frame.centerX(), frame.bottom + half, handlePaint)
        // 左右
        canvas.drawLine(frame.left - half, frame.centerY(), frame.left + len, frame.centerY(), handlePaint)
        canvas.drawLine(frame.right - len, frame.centerY(), frame.right + half, frame.centerY(), handlePaint)
    }

    // ------------------------------------------------------------------
    // 与纯数学对象的桥接
    // ------------------------------------------------------------------

    private fun RectF.toGeometry(): CropGeometry.RectF2 =
        CropGeometry.RectF2(left, top, right, bottom)

    private fun RectF.set(rect: CropGeometry.RectF2) {
        set(rect.left, rect.top, rect.right, rect.bottom)
    }
}
