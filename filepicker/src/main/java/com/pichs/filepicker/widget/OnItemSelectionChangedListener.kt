package com.pichs.filepicker.widget

fun interface OnItemSelectionChangedListener {
    fun onItemSelectionChanged(startPosition: Int, currentPosition: Int, isSelected: Boolean)
    fun onSelectionMaxStopped(maxCount: Int) {
        // 默认实现，子类可以选择覆盖
    }

    fun onTouchSelectEnd() {
        // 当触摸结束
    }
    fun onToucheSelectStart() {
        // 当触摸结束
    }
}