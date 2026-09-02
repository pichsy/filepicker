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

object FilePickerMimeType {

    /**
     * 根据文件后缀获得mimeType
     */
    fun of(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> IMAGE
            "png" -> IMAGE
            "gif" -> GIF
            "mp4" -> VIDEO
            "mp3" -> AUDIO_MP3
            "wav" -> AUDIO_WAV
            "flac" -> AUDIO_FLAC
            "m4a" -> AUDIO_M4A
            "amr" -> AUDIO_AMR
            "pdf" -> PDF
            "doc" -> DOC
            "docx" -> DOCX
            "ppt" -> PPT
            "pptx" -> PPTX
            "xls" -> EXCEL
            "xlsx" -> EXCELX
            "txt" -> TXT
            "apk" -> APK
            "zip" -> ZIP
            "rar" -> RAR
            "7z" -> SEVEN_Z
            "tar" -> TAR
            "gz" -> GZ
            "bz2" -> BZ2
            "iso" -> ISO
            else -> ""
        }
    }

    const val IMAGE = "image/*"
    const val VIDEO = "video/*"
    const val GIF = "image/gif"

    const val AUDIO = "audio/*"
    const val AUDIO_MP3 = "audio/mpeg"
    const val AUDIO_WAV = "audio/wav"
    const val AUDIO_FLAC = "audio/flac"
    const val AUDIO_M4A = "audio/mp4"
    const val AUDIO_AMR = "audio/amr"


    const val PDF = "application/pdf"
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val DOC = "application/msword"
    const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val PPT = "application/vnd.ms-powerpoint"
    const val EXCEL = "application/vnd.ms-excel"
    const val EXCELX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val TXT = "text/plain"

    const val IMAGE_JPEG = "image/jpeg"
    const val IMAGE_PNG = "image/png"
    const val IMAGE_WEBP = "image/webp"
    const val VIDEO_MP4 = "video/mp4"

    const val APK = "application/vnd.android.package-archive"

    const val ZIP = "application/zip"
    const val RAR = "application/x-rar-compressed"

    /** rar 的 IANA 标准类型，部分设备/来源会报这个，需与 RAR 同时兼容 */
    const val RAR_VND = "application/vnd.rar"
    const val SEVEN_Z = "application/x-7z-compressed"
    const val TAR = "application/x-tar"
    const val GZ = "application/gzip"
    const val BZ2 = "application/x-bzip2"
    const val ISO = "application/x-iso9660-image"

}

object SelectTypeUtil {

    fun getAllTypes(): List<String> {
        return listOf(
            IMAGE_VIDEO, IMAGE_VIDEO_GIF, IMAGE, VIDEO, GIF, AUDIO, DOCUMENT, PDF, DOC, PPT, EXCEL, TXT, APK, ZIP_ALL, ZIP, RAR, SEVEN_Z, TAR, GZ, BZ2, ISO
        )
    }

    fun isValidType(type: String): Boolean {
        return getAllTypes().contains(type)
    }

    fun isCanPreview(type: String): Boolean {
        return when (type) {
            IMAGE_VIDEO, IMAGE_VIDEO_GIF, IMAGE, VIDEO, GIF -> true
            else -> false
        }
    }

    fun isCanOriginal(type: String): Boolean {
        return when (type) {
            IMAGE_VIDEO, IMAGE_VIDEO_GIF, IMAGE, VIDEO -> true
            else -> false
        }
    }
}