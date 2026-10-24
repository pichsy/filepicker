package com.pichs.filepicker.widget

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.drake.brv.utils.bindingAdapter
import com.pichs.filepicker.R

/**
 * 底部"已选缩略图"横条（相册页 / 预览页）的公共操作。
 *
 * 三条约定：
 * 1. **数据**：内容没变就不重新赋值 models（BRV 的赋值是 notifyDataSetChanged，会整条重绑 + 缩略图闪一下）；
 * 2. **间距**：格子尺寸保持布局原样，只按 [StripMetrics] 调格子间距，让最右边露出半格；
 * 3. **高亮**：当前页对应的那一格用绿框，靠 [paintCurrent] 按 **adapter position** 整条重绘 ——
 *    不再用"记住上一项再清它的边框"那种增量做法，避免 ViewHolder 复用后绿框残留在别的格子上。
 *
 * 滚动统一用 RecyclerView 自带的 `smoothScrollToPosition`，不再自己算居中坐标。
 */
object SelectedThumbnailStrip {

    /** 允许的最大间距：就是 item 布局里原有的左右各 3dp 边距 */
    const val MAX_SPACING_DP = 6

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
        adapter.models = models
        return true
    }

    /**
     * 按当前可视宽度和格子宽度，算出"一屏 5 个半"的间距。
     *
     * @param itemWidthPx 格子宽度（含右上角删除按钮区域，不含外边距）；<= 0 表示还没测量出来
     * @return 布局（含间距，可能为负表示轻微相贴）；null 表示还没测量出来
     */
    fun resolveLayout(recyclerView: RecyclerView, itemWidthPx: Int): StripMetrics.Layout? {
        if (itemWidthPx <= 0) return null
        val available = recyclerView.width - recyclerView.paddingLeft - recyclerView.paddingRight
        val maxSpacing = (MAX_SPACING_DP * recyclerView.resources.displayMetrics.density).toInt()
        return StripMetrics.resolveLayout(available, itemWidthPx, maxSpacing)
    }

    /**
     * 把算出来的间距写到格子上：**只改左右边距**，图片尺寸、右上角删除按钮都不动。
     */
    fun applyLayout(itemView: View, layout: StripMetrics.Layout?) {
        if (layout == null) return
        val params = itemView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.marginStart == 0 && params.marginEnd == layout.spacingPx) return
        params.marginStart = 0
        params.marginEnd = layout.spacingPx
        itemView.layoutParams = params
    }

    /**
     * 整条重绘"当前页"高亮：只有 adapter position == [position] 的那格显示绿框。
     *
     * 按 position 而不是按数据对象比对，且**每次都把可见格子全刷一遍** ——
     * ViewHolder 被复用、数据被移动时都不会留下旧绿框。
     *
     * @param position 当前页在**已选列表**里的下标；< 0 表示当前页不在已选里，全部取消高亮
     */
    fun paintCurrent(recyclerView: RecyclerView, position: Int) {
        for (i in 0 until recyclerView.childCount) {
            val child: ViewGroup = recyclerView.getChildAt(i) as? ViewGroup ?: continue
            val childPosition = recyclerView.getChildAdapterPosition(child)
            child.findViewById<View>(R.id.iv_cover_image)?.isSelected = childPosition == position
        }
    }
}
