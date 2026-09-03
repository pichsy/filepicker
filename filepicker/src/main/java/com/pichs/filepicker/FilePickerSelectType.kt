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
    const val ZIP_ALL = "zip,rar,7z,tar,gz,bz2,iso,br,lz4,zstd,xz"
    const val ZIP = "zip"
    const val RAR = "rar"
    const val SEVEN_Z = "7z"
    const val TAR = "tar"
    const val GZ = "gz"
    const val BZ2 = "bz2"
    const val ISO = "iso"
    const val BR = "br"
    const val LZ4 = "lz4"
    const val ZSTD = "zstd"
    const val XZ = "xz"

    /** 自定义后缀类型的前缀，由 [ofExtensions] 生成，如 "ext:xz,tar,bak" */
    const val CUSTOM_EXT_PREFIX = "ext:"

    /**
     * 按任意文件后缀组合自定义过滤类型，如 ofExtensions("xz", "tar", "bak") -> "ext:xz,tar,bak"。
     * 结果直接传给 FilePicker.setSelectType() 即可。
     * 后缀会自动去掉前导点、转小写、去重，并过滤空串和 SQL 通配符（% _），防止 LIKE 误匹配。
     */
    fun ofExtensions(vararg extensions: String): String {
        val exts = extensions.map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() && it.none { c -> c == '%' || c == '_' } }
            .distinct()
        require(exts.isNotEmpty()) {
            "ofExtensions 至少需要一个合法后缀，如 ofExtensions(\"xz\", \"tar\")"
        }
        return CUSTOM_EXT_PREFIX + exts.joinToString(",")
    }

    /** 是否是自定义后缀类型（[ofExtensions] 生成的） */
    fun isCustomExtType(type: String): Boolean = type.startsWith(CUSTOM_EXT_PREFIX)

    /** 解析自定义后缀类型里的后缀列表，非自定义类型返回空列表 */
    fun parseCustomExts(type: String): List<String> {
        return if (isCustomExtType(type)) {
            type.removePrefix(CUSTOM_EXT_PREFIX).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

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
            "gz", "tgz" -> GZ
            "bz2" -> BZ2
            "iso" -> ISO
            "br" -> BR
            "lz4" -> LZ4
            "zst", "zstd" -> ZSTD
            "xz" -> XZ
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

    /** 通用压缩格式。 */
    const val ZIP = "application/zip"
    const val RAR = "application/x-rar-compressed"
    /** rar 的 IANA 标准类型，部分设备/来源会报这个，需与 RAR 同时兼容 */
    const val RAR_VND = "application/vnd.rar"
    /** 部分设备的 MimeTypeMap 会把 .rar 映射成这个非标准值，三种都要兼容 */
    const val RAR_PLAIN = "application/rar"
    const val SEVEN_Z = "application/x-7z-compressed"
    const val TAR = "application/x-tar"
    const val GZ = "application/gzip"
    const val BZ2 = "application/x-bzip2"
    const val ISO = "application/x-iso9660-image"
    const val BR = "application/br"
    const val LZ4 = "application/x-lz4"
    const val ZSTD = "application/x-zstd"
    const val XZ = "application/x-xz"
    const val gzip = "application/x-gzip"

}

object SelectTypeUtil {

    fun getAllTypes(): List<String> {
        return listOf(
            IMAGE_VIDEO, IMAGE_VIDEO_GIF, IMAGE, VIDEO, GIF, AUDIO, DOCUMENT, PDF, DOC, PPT, EXCEL, TXT, APK, ZIP_ALL, ZIP, RAR, SEVEN_Z, TAR, GZ, BZ2, ISO,
            FilePickerSelectType.BR, FilePickerSelectType.LZ4, FilePickerSelectType.ZSTD, FilePickerSelectType.XZ
        )
    }

    fun isValidType(type: String): Boolean {
        if (FilePickerSelectType.isCustomExtType(type)) {
            return FilePickerSelectType.parseCustomExts(type).isNotEmpty()
        }
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