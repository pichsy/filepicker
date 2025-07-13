package com.pichs.filepicker.utils

import java.io.File

object FilePickerFileUtils {

    fun getFileName(filePath: String): String {
        if (filePath.isEmpty()) return ""
        return filePath.substringAfterLast('/')
    }

    fun getFolderName(filePath: String): String {
        // 根据文件路劲获取文件夹名称
        if (filePath.isEmpty()) return ""
        return filePath.substringBeforeLast('/').substringAfterLast('/')
    }

    fun getFolderPath(filePath: String): String {
        // 根据文件路劲获取文件夹路径
        if (filePath.isEmpty()) return ""
        return filePath.substringBeforeLast('/')
    }

    fun getFileExtension(filePath: String): String {
        if (filePath.isEmpty()) return ""
        return filePath.substringAfterLast('.', "")
    }

    fun isFile(file: File): Boolean {
        return try {
            file.isFile
        } catch (e: Exception) {
            false
        }
    }

    fun getFileSize(file: File): Long {
        return try {
            file.length()
        } catch (e: Exception) {
            0L
        }
    }

    fun isFileExists(file: File): Boolean {
        return try {
            file.exists()
        } catch (e: Exception) {
            false
        }
    }


    fun isFileInHiddenDir(path: String): Boolean {
        // 判断路径中是否有某个路径以.开头的，如果有，那么就是隐藏目录
        return path.contains("/.")
    }

}