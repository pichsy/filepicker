package com.pichs.filepicker.crop

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 裁剪几何计算（纯 Kotlin，无 Android 依赖，便于单测）。
 *
 * 约定：
 * - 坐标单位一律是**像素**（View 坐标或图片坐标），比例换算在 [frameToImageRatio] 里做；
 * - 裁剪框（frame）不能超出图片显示区域（imageRect），图片显示区域不能小于裁剪框；
 * - 旋转按 90° 步进，用归一化坐标（0~1）做变换，避免和具体像素尺寸耦合。
 */
object CropGeometry {

    data class RectF2(val left: Float, val top: Float, val right: Float, val bottom: Float) {

        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f

        fun isEmpty(): Boolean = width <= 0.5f || height <= 0.5f

        fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

        fun contains(other: RectF2): Boolean =
            other.left >= left - 0.5f && other.top >= top - 0.5f &&
                other.right <= right + 0.5f && other.bottom <= bottom + 0.5f

        fun offset(dx: Float, dy: Float): RectF2 =
            RectF2(left + dx, top + dy, right + dx, bottom + dy)

        fun toIntArray(): IntArray = intArrayOf(
            left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt()
        )

        companion object {
            val ZERO = RectF2(0f, 0f, 0f, 0f)
        }
    }

    /** 裁剪框的把手（8 个 + 框内） */
    enum class Handle {
        NONE, INSIDE,
        TOP_LEFT, TOP, TOP_RIGHT,
        END, START,
        BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT
    }

    /** 图片在 View 内 fitCenter 的显示矩形 */
    fun fitCenter(srcWidth: Int, srcHeight: Int, viewWidth: Int, viewHeight: Int): RectF2 {
        if (srcWidth <= 0 || srcHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return RectF2.ZERO
        val scale = min(viewWidth.toFloat() / srcWidth, viewHeight.toFloat() / srcHeight)
        val w = srcWidth * scale
        val h = srcHeight * scale
        val l = (viewWidth - w) / 2f
        val t = (viewHeight - h) / 2f
        return RectF2(l, t, l + w, t + h)
    }

    /**
     * 初始裁剪框：
     * - [aspect] 为空/<=0（自由裁剪）→ 就是图片显示区域（微信默认整图）；
     * - [aspect] > 0（固定比例，如头像 1:1）→ 在图片区域内按比例居中取最大框。
     */
    fun initialFrame(imageRect: RectF2, aspect: Float?): RectF2 {
        if (imageRect.isEmpty()) return RectF2.ZERO
        if (aspect == null || aspect <= 0f) return imageRect
        val w: Float
        val h: Float
        if (imageRect.width / imageRect.height > aspect) {
            h = imageRect.height
            w = h * aspect
        } else {
            w = imageRect.width
            h = w / aspect
        }
        val l = imageRect.left + (imageRect.width - w) / 2f
        val t = imageRect.top + (imageRect.height - h) / 2f
        return RectF2(l, t, l + w, t + h)
    }

    /** 把框钳制进 bounds，并保证不小于 minSize；比例锁定时按比例收缩 */
    fun clampFrame(frame: RectF2, bounds: RectF2, minSize: Float, aspect: Float? = null): RectF2 {
        if (bounds.isEmpty()) return RectF2.ZERO
        var w = max(minSize, min(frame.width, bounds.width))
        var h = max(minSize, min(frame.height, bounds.height))
        if (aspect != null && aspect > 0f) {
            if (w / h > aspect) w = h * aspect else h = w / aspect
            if (w > bounds.width) {
                w = bounds.width
                h = w / aspect
            }
            if (h > bounds.height) {
                h = bounds.height
                w = h * aspect
            }
        }
        var l = frame.centerX - w / 2f
        var t = frame.centerY - h / 2f
        l = l.coerceIn(bounds.left, bounds.right - w)
        t = t.coerceIn(bounds.top, bounds.bottom - h)
        return RectF2(l, t, l + w, t + h)
    }

    /** 拖动整框（框内拖动/拖边框） */
    fun moveFrame(frame: RectF2, dx: Float, dy: Float, bounds: RectF2): RectF2 {
        if (frame.isEmpty() || bounds.isEmpty()) return frame
        val moved = frame.offset(dx, dy)
        val l = moved.left.coerceIn(bounds.left, max(bounds.left, bounds.right - frame.width))
        val t = moved.top.coerceIn(bounds.top, max(bounds.top, bounds.bottom - frame.height))
        return RectF2(l, t, l + frame.width, t + frame.height)
    }

    /**
     * 拖把手改大小。
     *
     * @param anchor 另一个对角（角把手）或对边（边把手）固定不动
     * @param aspect 固定比例；null/<=0 表示自由拉伸
     */
    fun resizeFrame(
        frame: RectF2,
        handle: Handle,
        dx: Float,
        dy: Float,
        bounds: RectF2,
        minSize: Float,
        aspect: Float? = null,
    ): RectF2 {
        if (frame.isEmpty()) return frame
        var left = frame.left
        var top = frame.top
        var right = frame.right
        var bottom = frame.bottom

        when (handle) {
            Handle.TOP_LEFT -> { left += dx; top += dy }
            Handle.TOP -> { top += dy }
            Handle.TOP_RIGHT -> { right += dx; top += dy }
            Handle.END -> { right += dx }
            Handle.BOTTOM_RIGHT -> { right += dx; bottom += dy }
            Handle.BOTTOM -> { bottom += dy }
            Handle.BOTTOM_LEFT -> { left += dx; bottom += dy }
            Handle.START -> { left += dx }
            else -> return frame
        }

        // 先保证最小尺寸，再按 bounds 钳制
        if (right - left < minSize) {
            when (handle) {
                Handle.TOP_LEFT, Handle.BOTTOM_LEFT, Handle.START -> left = right - minSize
                else -> right = left + minSize
            }
        }
        if (bottom - top < minSize) {
            when (handle) {
                Handle.TOP_LEFT, Handle.TOP, Handle.TOP_RIGHT -> top = bottom - minSize
                else -> bottom = top + minSize
            }
        }
        left = left.coerceIn(bounds.left, bounds.right)
        right = right.coerceIn(bounds.left, bounds.right)
        top = top.coerceIn(bounds.top, bounds.bottom)
        bottom = bottom.coerceIn(bounds.top, bounds.bottom)

        if (aspect != null && aspect > 0f) {
            // 锁比：以变化更大的一边为准
            var w = right - left
            var h = bottom - top
            if (w / h > aspect) h = w / aspect else w = h * aspect
            when (handle) {
                Handle.TOP_LEFT -> { left = right - w; top = bottom - h }
                Handle.TOP -> { w = right - left; h = w / aspect; top = bottom - h }
                Handle.TOP_RIGHT -> { right = left + w; top = bottom - h }
                Handle.END -> { h = bottom - top; w = h * aspect; right = left + w }
                Handle.BOTTOM_RIGHT -> { right = left + w; bottom = top + h }
                Handle.BOTTOM -> { w = right - left; h = w / aspect; bottom = top + h }
                Handle.BOTTOM_LEFT -> { left = right - w; bottom = top + h }
                Handle.START -> { h = bottom - top; w = h * aspect; left = right - w }
                else -> Unit
            }
            // 锁比后可能又越界，二次钳制
            left = left.coerceIn(bounds.left, bounds.right)
            right = right.coerceIn(bounds.left, bounds.right)
            top = top.coerceIn(bounds.top, bounds.bottom)
            bottom = bottom.coerceIn(bounds.top, bounds.bottom)
            if (right - left < minSize || bottom - top < minSize) return frame
        }

        val result = RectF2(left, top, right, bottom)
        if (result.width < minSize || result.height < minSize) return frame
        return result
    }

    /** 命中测试：先把手，再框内，最后 NONE */
    fun hitTest(frame: RectF2, x: Float, y: Float, touchRadius: Float): Handle {
        if (frame.isEmpty()) return Handle.NONE
        val r = touchRadius
        // 角
        if (abs(x - frame.left) <= r && abs(y - frame.top) <= r) return Handle.TOP_LEFT
        if (abs(x - frame.right) <= r && abs(y - frame.top) <= r) return Handle.TOP_RIGHT
        if (abs(x - frame.right) <= r && abs(y - frame.bottom) <= r) return Handle.BOTTOM_RIGHT
        if (abs(x - frame.left) <= r && abs(y - frame.bottom) <= r) return Handle.BOTTOM_LEFT
        // 边
        val withinX = x >= frame.left - r && x <= frame.right + r
        val withinY = y >= frame.top - r && y <= frame.bottom + r
        if (withinY && abs(x - frame.left) <= r) return Handle.START
        if (withinY && abs(x - frame.right) <= r) return Handle.END
        if (withinX && abs(y - frame.top) <= r) return Handle.TOP
        if (withinX && abs(y - frame.bottom) <= r) return Handle.BOTTOM
        if (frame.contains(x, y)) return Handle.INSIDE
        return Handle.NONE
    }

    /** 图片至少要放大到能盖住裁剪框的倍数（以当前显示尺寸为基准） */
    fun minScale(imageRect: RectF2, frame: RectF2): Float {
        if (imageRect.width <= 0f || imageRect.height <= 0f) return 1f
        return max(frame.width / imageRect.width, frame.height / imageRect.height)
    }

    /** 裁剪框在图片里的归一化位置（0~1） */
    fun frameToImageRatio(frame: RectF2, imageRect: RectF2): RectF2 {
        if (imageRect.width <= 0f || imageRect.height <= 0f) return RectF2.ZERO
        val l = ((frame.left - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
        val t = ((frame.top - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
        val r = ((frame.right - imageRect.left) / imageRect.width).coerceIn(l, 1f)
        val b = ((frame.bottom - imageRect.top) / imageRect.height).coerceIn(t, 1f)
        return RectF2(l, t, r, b)
    }

    /** 图片旋转后尺寸（90/270 交换宽高） */
    fun rotatedSize(width: Int, height: Int, degrees: Int): IntArray {
        return if (normalizeRotation(degrees) % 180 == 0) {
            intArrayOf(width, height)
        } else {
            intArrayOf(height, width)
        }
    }

    /** 归一化角度到 [0, 360) */
    fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360

    /**
     * 归一化坐标的裁剪框随图片旋转后的新坐标（顺时针）。
     *
     * 推导：顺时针 90° 时，图片尺寸从 (w,h) 变成 (h,w)，
     * 点 (x,y) → (h - y, x)，归一化后 X' = 1 - Y、Y' = X。
     */
    fun rotateRect(rect: RectF2, degrees: Int): RectF2 {
        return when (normalizeRotation(degrees)) {
            90 -> RectF2(1f - rect.bottom, rect.left, 1f - rect.top, rect.right)
            180 -> RectF2(1f - rect.right, 1f - rect.bottom, 1f - rect.left, 1f - rect.top)
            270 -> RectF2(rect.top, 1f - rect.right, rect.bottom, 1f - rect.left)
            else -> rect
        }
    }
}
