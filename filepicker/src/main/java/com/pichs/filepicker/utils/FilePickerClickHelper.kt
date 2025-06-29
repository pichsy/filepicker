package com.pichs.filepicker.utils

import android.view.View

/**
 * 点击事件处理工具，
 * 单个按钮的防重点击事件
 * 可设置 点击音效
 * 使用点击音效 需要先初始化 [ClickPlayer] 点击音频播放器
 * 详见 [ClickPlayer]
 */
object FilePickerClickHelper {

    /**
     * 默认点击间隔 500 ms
     */
    const val CLICK_INTERVAL_DEFAULT_VALUE = 250L

    /**
     * 点击事件防重，单击，非全局
     */
    fun clicks(
        vararg views: View,
        listener: View.OnClickListener?
    ) {
        if (views.isEmpty()) return
        val lis: OnFilePickerDebouncingClickListener =
            object : OnFilePickerDebouncingClickListener(CLICK_INTERVAL_DEFAULT_VALUE) {
                override fun onViewClicked(v: View?) {
                    listener?.onClick(v)
                }
            }
        for (view in views) {
            if (listener == null) {
                view.setOnClickListener(null)
            } else {
                view.setOnClickListener(lis)
            }
        }
    }


    /**
     * 点击事件防重，单击，非全局
     */
    fun clicks(
        vararg views: View,
        interval: Long = CLICK_INTERVAL_DEFAULT_VALUE,
        listener: View.OnClickListener?
    ) {
        if (views.isEmpty()) return
        val lis: OnFilePickerDebouncingClickListener =
            object : OnFilePickerDebouncingClickListener(interval) {
                override fun onViewClicked(v: View?) {
                    listener?.onClick(v)
                }
            }
        for (view in views) {
            if (listener == null) {
                view.setOnClickListener(null)
            } else {
                view.setOnClickListener(lis)
            }
        }
    }
}

abstract class OnFilePickerDebouncingClickListener(var mDuration: Long) : View.OnClickListener {

    @Volatile
    private var lastClickTime = 0L
    abstract fun onViewClicked(v: View?)

    override fun onClick(v: View) {
        if (isFastClick()) return
        onViewClicked(v)
    }

    @Synchronized
    private fun isFastClick(): Boolean {
        val curMills = System.currentTimeMillis()
        if ((curMills - lastClickTime >= mDuration) || (curMills - lastClickTime < 0)) {
            lastClickTime = curMills
            return false
        }
        return true
    }
}
