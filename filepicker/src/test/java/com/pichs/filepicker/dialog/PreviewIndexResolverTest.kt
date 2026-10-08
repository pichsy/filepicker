package com.pichs.filepicker.dialog

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B1 回归测试：预览弹窗的下标规整。
 *
 * 背景证据：ViewPager2 源码 `setCurrentItemInternal` 里有 `item = Math.max(item, 0)`，
 * 传 -1 不报错、静默显示第 0 页。而 `FilePickerPreviewDialog` 的列表会被
 * fast→full 两次扫描整体替换，点击时捕获的 item 可能已不在新列表里（`indexOf` = -1），
 * 旧代码直接把 -1 交给 setCurrentItem → "点的是这张、显示的是第一张"。
 */
class PreviewIndexResolverTest {

    /** 核心回归：找不到时回退到"当前页"，而不是 0 */
    @Test
    fun notFound_fallsBackToCurrentPage_notZero() {
        assertEquals(2, PreviewIndexResolver.resolve(foundIndex = -1, listSize = 3, fallbackIndex = 2))
    }

    @Test
    fun notFound_fallbackBeyondRange_isClampedIntoList() {
        assertEquals(2, PreviewIndexResolver.resolve(-1, 3, 99))
        assertEquals(0, PreviewIndexResolver.resolve(-1, 3, -5))
    }

    @Test
    fun found_returnsItsOwnIndex() {
        assertEquals(0, PreviewIndexResolver.resolve(0, 3, 2))
        assertEquals(2, PreviewIndexResolver.resolve(2, 3, 0))
    }

    /** 下标等于 listSize 也算越界（ViewPager2 允许的最大值是 listSize-1） */
    @Test
    fun foundIndexOutOfRange_isTreatedAsNotFound() {
        assertEquals(0, PreviewIndexResolver.resolve(3, 3, 0))
        assertEquals(0, PreviewIndexResolver.resolve(5, 3, 0))
    }

    /** 列表为空：返回 0，对空 adapter 的 setCurrentItem(0) 是 no-op，不会抛异常 */
    @Test
    fun emptyList_returnsZero() {
        assertEquals(0, PreviewIndexResolver.resolve(-1, 0, 2))
        assertEquals(0, PreviewIndexResolver.resolve(0, 0, 2))
    }

    @Test
    fun isFound_matchesResolve() {
        assertEquals(true, PreviewIndexResolver.isFound(0, 1))
        assertEquals(false, PreviewIndexResolver.isFound(-1, 1))
        assertEquals(false, PreviewIndexResolver.isFound(1, 1))
        assertEquals(false, PreviewIndexResolver.isFound(0, 0))
    }
}
