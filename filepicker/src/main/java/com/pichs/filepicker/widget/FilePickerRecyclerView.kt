package com.pichs.filepicker.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.core.view.isNotEmpty
import androidx.recyclerview.widget.RecyclerView

class FilePickerRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var isSelectionMode = false
    private var downX = 0f
    private var downY = 0f
    private var startPosition = NO_POSITION
    private var lastTouchPosition = NO_POSITION
    private var targetSelectionState = false

    private var autoScrollRunnable: Runnable? = null
    private var autoScrollVelocity = 0
    private var edgeThreshold = 0f // 80dp边界区域

    var maxSelectNumber: Int = 0  // 0表示无限制
    var currentSelectedCountProvider: (() -> Int)? = null

    private var onItemSelectionChanged: OnItemSelectionChangedListener? = null

    fun setOnItemSelectionChangedListener(listener: OnItemSelectionChangedListener) {
        onItemSelectionChanged = listener
    }

    private var isEnterSelectMode = false

    init {
        addOnItemTouchListener(object : OnItemTouchListener {

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        isSelectionMode = false
                        startPosition = NO_POSITION
                        lastTouchPosition = NO_POSITION
                        stopAutoScroll()
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = e.x - downX
                        val deltaY = e.y - downY

                        if (!isSelectionMode && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 20) {
                            if (!isEnterSelectMode) {
                                isSelectionMode = true
                                onItemSelectionChanged?.onToucheSelectStart()
                            }
                            isSelectionMode = true
                            parent.requestDisallowInterceptTouchEvent(true)
                            return true
                        }

//                        if (isSelectionMode) {
//                            handleSelection(e)
//                            checkAutoScroll(e)
//                            return true
//                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isSelectionMode = false
                        lastTouchPosition = NO_POSITION
                        startPosition = NO_POSITION
                        isEnterSelectMode = false
                        stopAutoScroll()
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (isSelectionMode) {
                    if (e.actionMasked == MotionEvent.ACTION_MOVE && isSelectionMode) {
                        handleSelection(e)
                        checkAutoScroll(e)
                    }
                    if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                        isSelectionMode = false
                        isEnterSelectMode = false
                        lastTouchPosition = NO_POSITION
                        startPosition = NO_POSITION
                        stopAutoScroll()
                        onItemSelectionChanged?.onTouchSelectEnd()
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }


    private fun handleSelection(e: MotionEvent) {
        val childView = findChildViewUnder(e.x, e.y)
        childView?.let {
            val position = getChildAdapterPosition(it)
            if (position != NO_POSITION && position != lastTouchPosition) {
                lastTouchPosition = position

                if (startPosition == NO_POSITION) {
                    startPosition = position
                    targetSelectionState = !it.isSelected  // 首次状态决定本次滑动的统一状态
                }

                // 限制选中数量
                if (targetSelectionState && maxSelectNumber > 0) {
                    val selectedCount = currentSelectedCountProvider?.invoke() ?: 0
                    if (selectedCount >= maxSelectNumber) {
                        isSelectionMode = false
                        lastTouchPosition = NO_POSITION
                        startPosition = NO_POSITION
                        stopAutoScroll()
                        Log.d("FilePickerFragment", "数据列表：选择已达最大值，: $maxSelectNumber ， 停止滚动选择。")
                        onItemSelectionChanged?.onSelectionMaxStopped(maxSelectNumber)
                        onItemSelectionChanged?.onTouchSelectEnd()
                        return
                    }
                }

                onItemSelectionChanged?.onItemSelectionChanged(startPosition, position, targetSelectionState)
            }
        }
    }

    private fun checkAutoScroll(e: MotionEvent) {
        val y = e.y
        val fastMultiplier = 1.5f
        val thresholdHalf = edgeThreshold / 2f

        autoScrollVelocity = when {
            y < edgeThreshold -> {  // 顶部区域
                val distanceToTop = y
                val baseSpeed = (-(10) * (1 - distanceToTop / edgeThreshold)).toInt().coerceAtMost(-2)

                if (distanceToTop < thresholdHalf) {
                    (baseSpeed * fastMultiplier).toInt().coerceAtMost(-2)
                } else {
                    baseSpeed
                }
            }

            y > height - edgeThreshold -> {  // 底部区域
                val distanceToBottom = height - y
                val baseSpeed = (10 * (1 - distanceToBottom / edgeThreshold)).toInt().coerceAtLeast(2)

                if (distanceToBottom < thresholdHalf) {
                    (baseSpeed * fastMultiplier).toInt().coerceAtLeast(2)
                } else {
                    baseSpeed
                }
            }

            else -> 0
        }

        if (autoScrollVelocity != 0) {
            startAutoScroll()
        } else {
            stopAutoScroll()
        }
    }

    private fun startAutoScroll() {
        if (!isSelectionMode) return
        if (autoScrollRunnable != null) return

        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (!isSelectionMode) {
                    stopAutoScroll()
                    return
                }
                if (autoScrollVelocity != 0) {
                    val canScrollUp = canScrollVertically(-1)
                    val canScrollDown = canScrollVertically(1)

                    if ((autoScrollVelocity < 0 && canScrollUp) || (autoScrollVelocity > 0 && canScrollDown)) {
                        scrollBy(0, autoScrollVelocity)
                        postDelayed(autoScrollRunnable, 16)
                    } else {
                        stopAutoScroll()
                    }
                } else {
                    stopAutoScroll()
                }
            }
        }
        postDelayed(autoScrollRunnable, 16)
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let { removeCallbacks(it) }
        autoScrollRunnable = null
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (edgeThreshold <= 0 && isNotEmpty()) {
            val firstChild = getChildAt(0)
            edgeThreshold = firstChild?.measuredHeight?.toFloat() ?: (80 * resources.displayMetrics.density)
            Log.d("FilePickerFragment", "RecyclerView 获取item的高度：Edge threshold set to: $edgeThreshold")
        }
    }
}

