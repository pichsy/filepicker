package com.pichs.filepicker.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat

object FilePickerTimeFormatUtils {

    @SuppressLint("SimpleDateFormat")
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    fun formatTime(time: Long): String {
        return timeFormat.format(time)
    }

    /**
     * 格式化
     * @param seconds 秒，多少秒
     * eg : 11:59:59 // 十一点
     * or   10:10 // 10分钟10秒
     */
    fun formatTimeMillSeconds(seconds: Long): String {
        var second = seconds / 1000L
        var min = 0L
        var hour = 0L
        if (second >= 60) {
            min = second / 60
            second %= 60
        }
        if (min >= 60) {
            hour = min / 60
            min %= 60
        }
        val timeBuilder = StringBuffer()
        if (hour >= 10) {
            timeBuilder.append(hour).append(":")
        } else if (hour > 0) {
            timeBuilder.append("0").append(hour).append(":")
        }

        if (min >= 10) {
            timeBuilder.append(min).append(":")
        } else if (min > 0) {
            timeBuilder.append("0").append(min).append(":")
        } else {
            timeBuilder.append("00:")
        }
        if (second >= 10) {
            timeBuilder.append(second)
        } else {
            timeBuilder.append("0").append(second)
        }
        return timeBuilder.toString()
    }


    @SuppressLint("DefaultLocale")
    fun formatFileSize(byteSize: Long): String {
        if (byteSize < 1024) {
            return "$byteSize B"
        } else if (byteSize < 1024 * 1024) {
            return String.format("%.2f KB", byteSize / 1024.0)
        } else if (byteSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", byteSize / (1024.0 * 1024.0))
        } else {
            return String.format("%.2f GB", byteSize / (1024.0 * 1024.0 * 1024.0))
        }
    }


}