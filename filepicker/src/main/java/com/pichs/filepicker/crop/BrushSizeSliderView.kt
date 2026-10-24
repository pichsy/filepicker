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
        color = 0xCCFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
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
    private val thinR = 1.2f * density
    private val thickR = 9.5f * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val usable = max(1f, width - padH * 2)
        val steps = 64
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val r = thinR + (thickR - thinR) * t
            val x = padH + usable * t
            canvas.drawCircle(x, cy, r, trackPaint)
        }
        // 当前粗细位置：白色圆环
        val thumbR = thinR + (thickR - thinR) * value + 3f * density
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
