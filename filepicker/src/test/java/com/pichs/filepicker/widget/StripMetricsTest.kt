package com.pichs.filepicker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 底部缩略图横条间距算法单测。
 *
 * 前提：**格子尺寸和删除按钮都不动**，只调间距，让一屏露出 5 个半格子。
 */
class StripMetricsTest {

    /**
     * 本机实测：可视宽 1020px，一格 191px（50dp 图片 + 右上角删除按钮区域）。
     * 191 × 5.5 = 1050 > 1020，所以间距压到 0 时约 5.34 格（最后露出约 1/3 格）。
     */
    @Test
    fun resolve_compressesSpacingWithoutTouchingItemSize() {
        val layout = StripMetrics.resolveLayout(availableWidth = 1020, itemWidthPx = 191, maxSpacingPx = 17)
        assertEquals("格子尺寸不能变", 191, layout.itemSizePx)
        assertTrue("间距不能为负（否则压住删除按钮），实际 ${layout.spacingPx}", layout.spacingPx >= 0)
        val slots = 1020f / (layout.itemSizePx + layout.spacingPx)
        assertTrue("可见格数应在 5.0~5.6 之间，实际 $slots", slots in 5.0f..5.6f)
    }

    @Test
    fun spacing_neverNegative() {
        // 任意宽度下都不允许出现负间距
        for (width in listOf(720, 1020, 1080, 1440)) {
            val layout = StripMetrics.resolveLayout(width, 191, 17)
            assertTrue("width=$width 出现负间距 ${layout.spacingPx}", layout.spacingPx >= 0)
        }
    }

    @Test
    fun fraction_basics() {
        assertEquals(0f, StripMetrics.fraction(1000, 100), 0.001f)
        assertEquals(0.5f, StripMetrics.fraction(1050, 100), 0.001f)
    }

    @Test
    fun invalidInput_keepsItemSize() {
        assertEquals(StripMetrics.Layout(100, 0), StripMetrics.resolveLayout(0, 100, 10))
        assertEquals(0f, StripMetrics.fraction(1020, 0), 0.001f)
    }
}
