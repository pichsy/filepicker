package com.pichs.filepicker

import com.pichs.filepicker.FilePickerSelectType.APK
import com.pichs.filepicker.FilePickerSelectType.AUDIO
import com.pichs.filepicker.FilePickerSelectType.BZ2
import com.pichs.filepicker.FilePickerSelectType.DOC
import com.pichs.filepicker.FilePickerSelectType.DOCUMENT
import com.pichs.filepicker.FilePickerSelectType.EXCEL
import com.pichs.filepicker.FilePickerSelectType.GIF
import com.pichs.filepicker.FilePickerSelectType.GZ
import com.pichs.filepicker.FilePickerSelectType.IMAGE
import com.pichs.filepicker.FilePickerSelectType.IMAGE_VIDEO
import com.pichs.filepicker.FilePickerSelectType.IMAGE_VIDEO_GIF
import com.pichs.filepicker.FilePickerSelectType.ISO
import com.pichs.filepicker.FilePickerSelectType.PDF
import com.pichs.filepicker.FilePickerSelectType.PPT
import com.pichs.filepicker.FilePickerSelectType.RAR
import com.pichs.filepicker.FilePickerSelectType.SEVEN_Z
import com.pichs.filepicker.FilePickerSelectType.TAR
import com.pichs.filepicker.FilePickerSelectType.TXT
import com.pichs.filepicker.FilePickerSelectType.VIDEO
import com.pichs.filepicker.FilePickerSelectType.ZIP
import com.pichs.filepicker.FilePickerSelectType.ZIP_ALL

object FilePickerSelectType {
    // all 是指 image+video，网格
    const val IMAGE_VIDEO = "image,video"
    const val IMAGE_VIDEO_GIF = "image,video,gif"
    const val IMAGE = "image"
    const val VIDEO = "video"
    const val GIF = "gif"

    // 列表
    // 音频类型
    const val AUDIO = "audio"

    // 文档类型
    const val DOCUMENT = "document"
    const val PDF = "pdf"
    const val DOC = "doc"
    const val PPT = "ppt"
    const val EXCEL = "excel"
    const val TXT = "txt"

    // 应用类型，安装包
    const val APK = "apk"

    // 压缩包类型
    const val ZIP_ALL = "zip,rar,7z,tar,gz,bz2,iso"
    const val ZIP = "zip"
    const val RAR = "rar"
    const val SEVEN_Z = "7z"
    const val TAR = "tar"
    const val GZ = "gz"
    const val BZ2 = "bz2"
    const val ISO = "iso"

}

object SelectTypeUtil {

    fun getAllTypes(): List<String> {
        return listOf(
            IMAGE_VIDEO, IMAGE_VIDEO_GIF, IMAGE, VIDEO, GIF,
            AUDIO, DOCUMENT, PDF, DOC, PPT, EXCEL, TXT,
            APK, ZIP_ALL, ZIP, RAR, SEVEN_Z, TAR, GZ, BZ2, ISO
        )
    }

    fun isValidType(type: String): Boolean {
        return getAllTypes().contains(type)
    }
}