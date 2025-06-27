package com.pichs.filepicker.utils

object FilePickerTimeFormatUtils {

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


}