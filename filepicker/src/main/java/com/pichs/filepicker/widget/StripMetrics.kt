package com.pichs.filepicker.widget

import kotlin.math.abs
import kotlin.math.floor

/**
 * 底部缩略图横条的间距算法（纯 Kotlin，无 Android 依赖，便于单测）。
 *
 * 目标：**格子尺寸和删除按钮都不动**，只调格子之间的间距，让一屏正好露出 5 个半格子。
 *
 * 一格的真实宽度 = 图片 + 右上角删除按钮占的位置（本机 191px）。
 * 191 × 5.5 = 1050px > 屏幕 1020px，所以"正好 5.5 格"在保持格子尺寸时会略微不足；
 * 这里只做**非负间距**（把空隙压到最小），实测 1020 / 191 ≈ 5.34 格，最后露出约 1/3 格，
 * 足以提示"后面还有图"。绝不使用负间距——那会让相邻缩略图压住删除按钮。
 */
object StripMetrics {

    /** 目标：一屏露出 [1 + VISIBLE_FRACTION] 格（5 个整格 + 半格） */
    const val TARGET_FRACTION = 0.5f

    /** 露出比例落在 0.4 ~ 0.6 之间就算"半格" */
    private const val TOLERANCE = 0.1f

    /** 算出来的横条布局：间距（非负，只压缩空隙，不让格子相贴） */
    data class Layout(val itemSizePx: Int, val spacingPx: Int)

    /**
     * 求一个间距，使 `可用宽度 / (格子宽度 + 间距)` 的小数部分最接近 0.5。
     *
     * 搜索顺序：0、1、2 …（间距只增不减），命中"露出半格"就返回。
     *
     * @param availableWidth 横条可视宽度（已扣 padding）
     * @param itemWidthPx 格子本身宽度（含删除按钮区域，不含外边距）
     * @param maxSpacingPx 允许的最大间距
     */
    fun resolveLayout(availableWidth: Int, itemWidthPx: Int, maxSpacingPx: Int): Layout {
        if (availableWidth <= 0 || itemWidthPx <= 0) return Layout(itemWidthPx, 0)
        val limit = maxOf(0, maxSpacingPx)

        val candidates = ArrayList<Int>(limit + 1)
        for (spacing in 0..limit) {
            candidates.add(spacing)
        }
        for (spacing in candidates) {
            val slot = itemWidthPx + spacing
            if (slot <= 0) continue
            if (abs(fraction(availableWidth, slot) - TARGET_FRACTION) <= TOLERANCE) {
                return Layout(itemWidthPx, spacing)
            }
        }
        // 实在凑不到（宽度正好是整数倍且间距调不动）：挑小数部分最接近 0.5 的那个
        var bestSpacing = 0
        var bestDiff = Float.MAX_VALUE
        for (spacing in candidates) {
            val slot = itemWidthPx + spacing
            if (slot <= 0) continue
            val diff = abs(fraction(availableWidth, slot) - TARGET_FRACTION)
            if (diff < bestDiff) {
                bestDiff = diff
                bestSpacing = spacing
            }
        }
        return Layout(itemWidthPx, bestSpacing)
    }

    /** 最右边那一格露出的比例 = 宽度 / 每格占用 的小数部分 */
    fun fraction(width: Int, slotPx: Int): Float {
        if (slotPx <= 0) return 0f
        val slots = width.toFloat() / slotPx
        return slots - floor(slots)
    }
}
