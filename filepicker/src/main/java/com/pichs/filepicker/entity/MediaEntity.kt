package com.pichs.filepicker.entity

import android.net.Uri
import android.os.Parcelable
import com.pichs.filepicker.FilePickerMimeType
import kotlinx.parcelize.Parcelize
import java.io.Serializable

@Parcelize
data class MediaEntity(
    var uri: Uri? = null,
    var name: String? = null,
    var path: String? = null,
    var mimeType: String? = null,
    var width: Int = 0,
    var height: Int = 0,
    var orientation: Int? = null,
    var size: Long = 0,
    var duration: Long = 0,
    var addTime: Long? = null,
    // 是否为选中状态， -1 表示未选中，0标识选中列表中的位置， 默认值为 -1
    internal var selectedIndex: Int = -1,
) : Parcelable {

    companion object {
        /** MediaStore 对识别不出类型的文件统一存这个通用 mime，并非真实文件类型，此时按文件后缀判断 */
        private const val GENERIC_MIME = "application/octet-stream"

        fun fromPath(path: String): MediaEntity {
            return MediaEntity(path = path).apply {
                mimeType = when {
                    path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> FilePickerMimeType.IMAGE_JPEG
                    path.endsWith(".png", true) -> FilePickerMimeType.IMAGE_PNG
                    path.endsWith(".gif", true) -> FilePickerMimeType.GIF
                    path.endsWith(".mp4", true) -> FilePickerMimeType.VIDEO_MP4
                    path.endsWith(".mp3", true) -> FilePickerMimeType.AUDIO_MP3
                    path.endsWith(".wav", true) -> FilePickerMimeType.AUDIO_WAV
                    path.endsWith(".flac", true) -> FilePickerMimeType.AUDIO_FLAC
                    path.endsWith(".m4a", true) -> FilePickerMimeType.AUDIO_M4A
                    path.endsWith(".amr", true) -> FilePickerMimeType.AUDIO_AMR
                    path.endsWith(".txt", true) -> FilePickerMimeType.TXT
                    path.endsWith(".pdf", true) -> FilePickerMimeType.PDF
                    path.endsWith(".doc", true) || path.endsWith(".docx", true) -> FilePickerMimeType.DOC
                    path.endsWith(".ppt", true) || path.endsWith(".pptx", true) -> FilePickerMimeType.PPT
                    path.endsWith(".xls", true) || path.endsWith(".xlsx", true) -> FilePickerMimeType.EXCEL
                    path.endsWith(".zip", true) -> FilePickerMimeType.ZIP
                    path.endsWith(".rar", true) -> FilePickerMimeType.RAR
                    path.endsWith(".tar", true) -> FilePickerMimeType.TAR
                    path.endsWith(".gz", true) -> FilePickerMimeType.GZ
                    path.endsWith(".iso", true) -> FilePickerMimeType.ISO
                    path.endsWith(".7z", true) -> FilePickerMimeType.SEVEN_Z
                    path.endsWith(".bz2", true) -> FilePickerMimeType.BZ2
                    path.endsWith(".br", true) -> FilePickerMimeType.BR
                    path.endsWith(".lz4", true) -> FilePickerMimeType.LZ4
                    path.endsWith(".zst", true) || path.endsWith(".zstd", true) -> FilePickerMimeType.ZSTD
                    path.endsWith(".xz", true) -> FilePickerMimeType.XZ
                    path.endsWith(".apk", true) -> FilePickerMimeType.APK
                    else -> null
                }
            }
        }
    }

    fun isVideo(): Boolean {
        return mimeType?.contains("video/", true) == true
                || matchType(listOf(), listOf("mp4", "avi", "mov", "mkv", "3gp", "m4v", "webm"))
    }

    fun isImage(): Boolean {
        return (mimeType?.contains("image/", true) == true
                || matchType(listOf(), listOf("jpg", "jpeg", "png", "webp", "bmp", "heic"))) && !isGif()
    }

    fun isAudio(): Boolean {
        return mimeType?.contains("audio/", true) == true
                || matchType(listOf(), listOf("mp3", "wav", "flac", "m4a", "amr", "aac", "ogg"))
    }

    fun isMp3(): Boolean {
        return matchType(listOf(FilePickerMimeType.AUDIO_MP3), listOf("mp3"))
    }

    fun isWav(): Boolean {
        return matchType(listOf(FilePickerMimeType.AUDIO_WAV), listOf("wav"))
    }

    fun isFlac(): Boolean {
        return matchType(listOf(FilePickerMimeType.AUDIO_FLAC), listOf("flac"))
    }

    fun isM4a(): Boolean {
        return matchType(listOf(FilePickerMimeType.AUDIO_M4A), listOf("m4a"))
    }

    fun isAmr(): Boolean {
        return matchType(listOf(FilePickerMimeType.AUDIO_AMR), listOf("amr"))
    }

    fun isGif(): Boolean {
        return matchType(listOf(FilePickerMimeType.GIF), listOf("gif"))
    }

    fun isJpeg(): Boolean {
        return matchType(listOf(FilePickerMimeType.IMAGE_JPEG), listOf("jpg", "jpeg"))
    }

    fun isPng(): Boolean {
        return matchType(listOf(FilePickerMimeType.IMAGE_PNG), listOf("png"))
    }

    fun isWebp(): Boolean {
        return matchType(listOf(FilePickerMimeType.IMAGE_WEBP), listOf("webp"))
    }

    fun isTxt(): Boolean {
        return matchType(listOf(FilePickerMimeType.TXT), listOf("txt"))
    }

    fun isPdf(): Boolean {
        return matchType(listOf(FilePickerMimeType.PDF), listOf("pdf"))
    }

    fun isWordDoc(): Boolean {
        return matchType(listOf(FilePickerMimeType.DOC, FilePickerMimeType.DOCX), listOf("doc", "docx"))
    }

    fun isPPT(): Boolean {
        return matchType(listOf(FilePickerMimeType.PPT, FilePickerMimeType.PPTX), listOf("ppt", "pptx"))
    }

    fun isExcel(): Boolean {
        return matchType(listOf(FilePickerMimeType.EXCEL, FilePickerMimeType.EXCELX), listOf("xls", "xlsx"))
    }

    /**
     * 是否是压缩包。
     * Check if the media entity is an archive file (zip, rar, tar, gz, iso, 7z, br, lz4, zstd, xz).
     */
    fun isArchive(): Boolean {
        return isZip() || is7z() || isRar() || isTar() || isGz() || isIso() || isBz2()
                || isBr() || isLz4() || isZstd() || isXz()
    }

    fun isZip(): Boolean {
        return matchType(listOf(FilePickerMimeType.ZIP), listOf("zip"))
    }

    fun isRar(): Boolean {
        return matchType(listOf(FilePickerMimeType.RAR, FilePickerMimeType.RAR_VND, FilePickerMimeType.RAR_PLAIN), listOf("rar"))
    }

    fun isTar(): Boolean {
        return matchType(listOf(FilePickerMimeType.TAR), listOf("tar"))
    }

    fun isGz(): Boolean {
        return matchType(listOf(FilePickerMimeType.GZ, FilePickerMimeType.gzip), listOf("gz", "tgz"))
    }

    fun isIso(): Boolean {
        return matchType(listOf(FilePickerMimeType.ISO), listOf("iso"))
    }

    fun isBz2(): Boolean {
        return matchType(listOf(FilePickerMimeType.BZ2), listOf("bz2"))
    }

    fun is7z(): Boolean {
        return matchType(listOf(FilePickerMimeType.SEVEN_Z), listOf("7z"))
    }

    fun isBr(): Boolean {
        return matchType(listOf(FilePickerMimeType.BR), listOf("br"))
    }

    fun isLz4(): Boolean {
        return matchType(listOf(FilePickerMimeType.LZ4), listOf("lz4"))
    }

    fun isZstd(): Boolean {
        return matchType(listOf(FilePickerMimeType.ZSTD), listOf("zst", "zstd"))
    }

    fun isXz(): Boolean {
        return matchType(listOf(FilePickerMimeType.XZ), listOf("xz"))
    }

    /**
     * Check if the media entity is an APK file.
     * @return true if the mimeType is "application/vnd.android.package-archive", false otherwise.
     */
    fun isApk(): Boolean {
        return matchType(listOf(FilePickerMimeType.APK), listOf("apk"))
    }

    /**
     * 类型匹配：
     * 1. 优先精确匹配 mimeType；
     * 2. mimeType 缺失或为通用二进制类型（application/octet-stream，MediaStore 认不出类型时都会存这个）
     *    时，按文件名后缀兜底判断。
     */
    private fun matchType(mimes: List<String>, exts: List<String>): Boolean {
        if (mimes.any { mimeType?.equals(it, true) == true }) return true
        if (exts.isEmpty()) return false
        // mime 缺失、或是 MediaStore 的通用类型（octet-stream）时，才允许按文件后缀兜底判断
        val genericMime = mimeType.isNullOrBlank() || mimeType.equals(GENERIC_MIME, true)
        if (!genericMime) return false
        val ext = fileExtension ?: return false
        return ext in exts
    }

    /** 文件名后缀（小写），取 name 优先，其次 path 最后一段；无后缀返回 null */
    private val fileExtension: String?
        get() {
            val fileName = (name ?: path?.substringAfterLast('/'))?.lowercase() ?: return null
            return fileName.substringAfterLast('.', "").ifEmpty { null }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaEntity) return false
        return path == other.path
    }

    override fun hashCode(): Int {
        return path?.hashCode() ?: 0
    }
}



