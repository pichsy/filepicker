package com.pichs.filepicker.utils

import android.app.Activity
import com.pichs.xwidget.utils.XDeviceHelper
import com.pichs.xwidget.utils.XDisplayHelper

object NavigationBarUtils {

    fun hasNavigationBar(activity: Activity?): Boolean {
        val calH =
            (XDisplayHelper.getRealScreenHeight(activity) - XDisplayHelper.getScreenHeight(activity) - XDisplayHelper.getStatusBarHeight(activity))
        val realH = XDisplayHelper.getNavigationBarHeight(activity)
        if (activity != null) {
            if (activity.isInMultiWindowMode || activity.isInPictureInPictureMode) {
                return false
            }
        }
        if (calH == realH) {
            return true
        }
        val isTable = XDeviceHelper.isTablet(activity)
        if (isTable && calH > 10) {
            return true
        }

        if ((calH - realH) > 0) {
            return true
        }
        return calH > 0 && realH > 0
    }

    fun getNavigationBarHeight(activity: Activity): Int {
        return XDisplayHelper.getNavigationBarHeight(activity)
    }


}