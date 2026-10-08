package com.pichs.filepicker.dialog

/**
 * 预览弹窗里"要显示第几页"的下标规整逻辑。
 *
 * 为什么要单独抽出来：`ViewPager2.setCurrentItemInternal` 里有
 * `item = Math.max(item, 0)`，传 -1 不会报错，而是**静默显示第 0 页**。
 * 预览弹窗的数据源在后台会被 fast→full 两次扫描整体替换，点击时捕获的 item
 * 可能已不在新列表里，`indexOf` 就会返回 -1 —— 表现就是"点的是这张、显示的是第一张"。
 * 所以任何交给 `setCurrentItem` 的下标都必须先经过这里。
 *
 * 刻意抽成不依赖 Android 的纯函数：下标规整是这套逻辑里最容易写错、也最难在真机上
 * 复现的一环，保持无副作用纯函数，任何地方都能直接把它拎出来单独验证。
 */
internal object PreviewIndexResolver {

    /**
     * @param foundIndex 目标项在列表中的下标，找不到时传负数（如 `indexOf` 的 -1）
     * @param listSize 当前列表长度
     * @param fallbackIndex 找不到时的期望回退页（一般是 ViewPager 当前页）
     * @return 一定落在 `[0, max(listSize-1, 0)]` 内的下标；列表为空时返回 0
     *         （此时 `setCurrentItem(0)` 对空 adapter 是 no-op，不会出错）
     */
    fun resolve(foundIndex: Int, listSize: Int, fallbackIndex: Int): Int {
        if (isFound(foundIndex, listSize)) return foundIndex
        if (listSize <= 0) return 0
        return fallbackIndex.coerceIn(0, listSize - 1)
    }

    /** 目标项是否真的在列表里（决定要不要把 mCurrentItem 替换成回退项） */
    fun isFound(foundIndex: Int, listSize: Int): Boolean = foundIndex in 0 until listSize
}
