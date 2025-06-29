package com.pichs.filepicker.utils

import android.content.Context
import android.util.DisplayMetrics
import kotlin.math.sqrt

object PadUtils {
    /**
     * 是否是平板
     */
    fun isTablet(context: Context): Boolean {
        // 获取 WindowManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
        if (windowManager == null) {
            return false // 无法获取 WindowManager，默认返回 false
        }
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        val widthInches = displayMetrics.widthPixels / displayMetrics.xdpi
        val heightInches = displayMetrics.heightPixels / displayMetrics.ydpi
        val diagonalInches = sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())

        return diagonalInches >= 7.0
    }
}