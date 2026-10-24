package com.pichs.filepicker.widget

import android.content.Context
import android.view.View
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.utils.bindingAdapter

/**
 * 底部已选缩略图横条（相册页 / 预览页）的公共操作：数据同步 + 平滑跟随。
 *
 * 旧实现的两个问题：
 * 1. 每次翻页都 `notifyItemRangeChanged(0, itemCount)`。全量刷新会让所有可见缩略图重新走一遍
 *    Glide 请求，DefaultItemAnimator 还会对新旧 View 做一次交叉淡入淡出 —— 表现就是缩略图"闪一下"。
 * 2. 居中用 `scrollToPositionWithOffset(position, (rvWidth - itemWidth) / 2)` 瞬移；
 *    而目标在屏幕外时 `findViewByPosition` 返回 null，itemWidth 退化成 0，
 *    落点变成"item 左边贴着屏幕中线"，看起来就是列表毫无规律地乱跳。
 *
 * 现在的策略：
 * - 数据没变就不重新赋值 models（BRV 的赋值是 notifyDataSetChanged，会整条重绑）；
 *   真变了也先记住第一个可见项的偏移，赋值后恢复，列表不会跳回最左边；
 * - 当前缩略图已完整可见时不动（相邻翻页列表保持静止）；需要滚动时用居中对齐的
 *   [LinearSmoothScroller] 平滑滑过去，不再瞬移。
 */
object SelectedThumbnailStrip {

    /**
     * 同步缩略图数据。
     *
     * @return true 表示数据确实变化了（列表会重新布局）
     */
    fun syncModels(recyclerView: RecyclerView, models: List<Any?>?): Boolean {
        val adapter = recyclerView.bindingAdapter
        val current = adapter.models
        val changed = when {
            current == null -> !models.isNullOrEmpty()
            models == null -> current.isNotEmpty()
            current.size != models.size -> true
            else -> current.indices.any { current[it] != models[it] }
        }
        if (!changed) return false

        // 记录第一个可见项及其偏移：models 重新赋值后数据一般只是增删，
        // 恢复这个锚点比"滚回 0"自然得多。
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val anchorPosition = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
        val anchorItem = current?.getOrNull(anchorPosition)
        val anchorView = if (anchorPosition != RecyclerView.NO_POSITION) {
            layoutManager?.findViewByPosition(anchorPosition)
        } else {
            null
        }
        val anchorOffset = anchorView?.let { it.left - recyclerView.paddingLeft } ?: 0

        adapter.models = models

        if (layoutManager != null && anchorItem != null && models != null) {
            val newPosition = models.indexOf(anchorItem)
            if (newPosition >= 0) {
                layoutManager.scrollToPositionWithOffset(newPosition, anchorOffset)
            }
        }
        return true
    }

    /**
     * 让 [position] 处的缩略图进入可视区。
     *
     * - 已完整可见：什么都不做（连续翻页时这条缩略图条不会跟着晃）；
     * - 只露出一部分 / 完全离屏：平滑滚动到中间；
     * - 还没布局完：等下一次 layout 再算，避免用旧坐标算出错误落点。
     */
    fun scrollToCurrent(recyclerView: RecyclerView, position: Int) {
        if (position < 0) return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        if (recyclerView.width <= 0 || recyclerView.isLayoutRequested) {
            recyclerView.doOnNextLayout { scrollToCurrent(recyclerView, position) }
            return
        }

        val view = layoutManager.findViewByPosition(position)
        if (view != null) {
            val startPadding = recyclerView.paddingLeft
            val endPadding = recyclerView.paddingRight
            val fullyVisible = view.left >= startPadding &&
                view.right <= recyclerView.width - endPadding
            if (fullyVisible) return

            val viewCenter = (view.left + view.right) / 2
            val containerCenter = (recyclerView.width - startPadding - endPadding) / 2 + startPadding
            recyclerView.stopScroll()
            recyclerView.smoothScrollBy(viewCenter - containerCenter, 0)
            return
        }

        // 目标离屏：交给 LinearSmoothScroller 自己找人，不用我们估 itemWidth / offset。
        val scroller = CenterSmoothScroller(recyclerView.context)
        scroller.targetPosition = position
        recyclerView.stopScroll()
        layoutManager.startSmoothScroll(scroller)
    }

    /**
     * 居中对齐的平滑滚动器。
     *
     * LinearSmoothScroller 默认按"贴边"对齐，这里改写到屏幕中线；
     * 列表两端滚不过去时 RecyclerView 会自然停在边界，不会出现空白。
     */
    private class CenterSmoothScroller(context: Context) : LinearSmoothScroller(context) {
        override fun calculateDxToMakeVisible(view: View, snapPreference: Int): Int {
            val layoutManager = layoutManager ?: return 0
            val viewCenter = (layoutManager.getDecoratedLeft(view) + layoutManager.getDecoratedRight(view)) / 2
            val containerCenter = (layoutManager.width - layoutManager.paddingLeft - layoutManager.paddingRight) / 2 +
                layoutManager.paddingLeft
            return viewCenter - containerCenter
        }
    }
}
