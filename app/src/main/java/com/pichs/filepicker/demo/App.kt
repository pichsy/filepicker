package com.pichs.filepicker.demo

import android.app.Application
import com.pichs.xbase.cache.BaseMMKVHelper
import com.pichs.xbase.utils.UiKit
import com.pichs.xbase.xlog.XLog

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        XLog.init(this, "")
        XLog.setGlobalTAG("FilePickerDemo")
        UiKit.init(this)
        BaseMMKVHelper.init(this, "FilePickerDemo")

    }
}