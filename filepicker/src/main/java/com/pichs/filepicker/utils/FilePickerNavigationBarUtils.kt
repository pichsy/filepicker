package com.pichs.filepicker.utils

import android.app.Activity
import android.os.Build
import android.provider.Settings
import android.view.WindowInsets
import com.pichs.xwidget.utils.XDisplayHelper

object FilePickerNavigationBarUtils {

    fun getNavigationBarHeight(activity: Activity): Int {
        val hei = getBottomNavBarHeight(activity)
        if (hei > 0) {
            return hei
        }
        return XDisplayHelper.getNavigationBarHeight(activity)
    }

    /**
     * 获取手势导航条高度
     * 这个判断必须放在 UI加载完毕以后。
     *  建议：ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
     *    isGestureBarVisible(this@XXXActivity)
     *  }
     */
    fun isGestureBarVisible(activity: Activity?): Boolean {
        if (activity == null) {
            return false
        }

        val bn = getBottomNavBarHeight(activity)

        if (bn > 0) {
            // 说明是 屏幕内三键 导航
            return true
        }

        val gestureInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window?.decorView?.rootWindowInsets?.getInsets(WindowInsets.Type.systemGestures())?.bottom ?: 0
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window?.decorView?.rootWindowInsets?.systemGestureInsets?.bottom ?: 0
        } else {
            0
        }

        if (gestureInsets > 0) {
            return true
        }

        // 小米特殊判断
        if (Build.BRAND.equals("xiaomi", ignoreCase = true)) {
            return try {
                val value = Settings.Global.getInt(activity.contentResolver, "force_fsg_nav_bar", 0)
                value == 1
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    private fun getBottomNavBarHeight(activity: Activity): Int {
        val rootInsets = activity.window?.decorView?.rootWindowInsets ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rootInsets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            rootInsets.stableInsetBottom
        }
    }

}