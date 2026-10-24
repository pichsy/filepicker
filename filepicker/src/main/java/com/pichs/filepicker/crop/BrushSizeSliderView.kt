package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * 画笔粗细滑条（锥形）：左边细、右边粗，拖动调整画笔大小。
 *
 * 视觉上不是一根直条，而是从左到右逐渐变粗的笔触（一堆半径递增的圆），
 * 拖动时白色圆环指示当前位置。
 */
class BrushSizeSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0 = 最细，1 = 最粗 */
    private var value = 0.35f

    /** 拖动回调（0~1） */
    var onValueChanged: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 纯色（不透明），避免半透明叠在图片上出现虚影
        color = 0xFFD0D0D0.toInt()
        style = Paint.Style.FILL
    }
    private val trackPath = android.graphics.Path()
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    fun setValue(v: Float) {
        value = v.coerceIn(0f, 1f)
        invalidate()
    }

    fun currentValue(): Float = value

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = (36 * density).toInt()
        setMeasuredDimension(
            resolveSize((200 * density).toInt(), widthMeasureSpec),
            resolveSize(h, heightMeasureSpec),
        )
    }

    private val padH = 10f * density
    private val thinR = 1.0f * density
    private val thickR = 7.5f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val usable = max(1f, width - padH * 2)
        // 用 Path 画一条连续的锥形笔触（梯形 + 两端圆头），不再是圈圈
        val x0 = padH
        val x1 = padH + usable
        trackPath.reset()
        trackPath.moveTo(x0, cy - thinR)
        trackPath.lineTo(x1, cy - thickR)
        trackPath.lineTo(x1, cy + thickR)
        trackPath.lineTo(x0, cy + thinR)
        trackPath.close()
        canvas.drawPath(trackPath, trackPaint)
        canvas.drawCircle(x0, cy, thinR, trackPaint)
        canvas.drawCircle(x1, cy, thickR, trackPaint)
        // 当前粗细位置：实心白色圆点
        val thumbR = thinR + (thickR - thinR) * value + 2.5f * density
        canvas.drawCircle(padH + usable * value, cy, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val usable = max(1f, width - padH * 2)
                value = ((event.x - padH) / usable).coerceIn(0f, 1f)
                invalidate()
                onValueChanged?.invoke(value)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
