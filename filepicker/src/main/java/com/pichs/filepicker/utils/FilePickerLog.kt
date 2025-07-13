package com.pichs.filepicker.utils

import android.util.Log
import com.pichs.filepicker.BuildConfig

object FilePickerLog {

    private const val TAG = "FilePickerLog"

    private var isDebug = BuildConfig.DEBUG

    fun setDebugMode(debug: Boolean) {
        isDebug = debug
    }

    fun d(tag: String, msg: String) {
        if (isDebug) {
            Log.d(TAG, "$tag==>$msg")
        }
    }

    fun d(message: () -> String) {
        if (isDebug) {
            Log.d(TAG, message())
        }
    }

    fun e(tag: String, msg: String) {
        if (isDebug) {
            Log.e(TAG, "$tag==>$msg")
        }
    }

    fun e(message: () -> String) {
        if (isDebug) {
            Log.e(TAG, message())
        }
    }

}