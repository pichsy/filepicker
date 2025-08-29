package com.pichs.filepicker.entity

import android.net.Uri
import android.content.ContentUris
import android.os.Parcelable
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
        fun fromPath(path: String): MediaEntity {
            return MediaEntity(path = path).apply {
                mimeType = when {
                    path.endsWith(".jpg", true) || path.endsWith(".jpeg", true) -> "image/jpeg"
                    path.endsWith(".png", true) -> "image/png"
                    path.endsWith(".gif", true) -> "image/gif"
                    path.endsWith(".mp4", true) -> "video/mp4"
                    path.endsWith(".mp3", true) -> "audio/mpeg"
                    path.endsWith(".wav", true) -> "audio/wav"
                    path.endsWith(".flac", true) -> "audio/flac"
                    path.endsWith(".m4a", true) -> "audio/mp4"
                    path.endsWith(".amr", true) -> "audio/amr"
                    path.endsWith(".txt", true) -> "text/plain"
                    path.endsWith(".pdf", true) -> "application/pdf"
                    path.endsWith(".doc", true) || path.endsWith(".docx", true) -> "application/msword"
                    path.endsWith(".ppt", true) || path.endsWith(".pptx", true) -> "application/vnd.ms-powerpoint"
                    path.endsWith(".xls", true) || path.endsWith(".xlsx", true) -> "application/vnd.ms-excel"
                    path.endsWith(".zip", true) -> "application/zip"
                    path.endsWith(".rar", true) -> "application/x-rar-compressed"
                    path.endsWith(".tar", true) -> "application/x-tar"
                    path.endsWith(".gz", true) -> "application/gzip"
                    path.endsWith(".iso", true) -> "application/x-iso9660-image"
                    path.endsWith(".7z", true) -> "application/x-7z-compressed"
                    path.endsWith(".apk", true) -> "application/vnd.android.package-archive"
                    else -> null
                }
            }
        }
    }

    fun isVideo(): Boolean {
        return mimeType?.contains("video/", true) == true
    }

    fun isImage(): Boolean {
        return (mimeType?.contains("image/", true) == true) && !isGif()
    }

    fun isAudio(): Boolean {
        return mimeType?.contains("audio/", true) == true
    }

    fun isMp3(): Boolean {
        return mimeType?.equals("audio/mpeg", true) == true
    }

    fun isWav(): Boolean {
        return mimeType?.equals("audio/wav", true) == true
    }

    fun isFlac(): Boolean {
        return mimeType?.equals("audio/flac", true) == true
    }

    fun isM4a(): Boolean {
        return mimeType?.equals("audio/mp4", true) == true
    }

    fun isAmr(): Boolean {
        return mimeType?.equals("audio/amr", true) == true
    }

    fun isGif(): Boolean {
        return mimeType?.equals("image/gif", true) == true
    }

    fun isJpeg(): Boolean {
        return mimeType?.equals("image/jpeg", true) == true
    }

    fun isPng(): Boolean {
        return mimeType?.equals("image/png", true) == true
    }

    fun isWebp(): Boolean {
        return mimeType?.equals("image/webp", true) == true
    }

    fun isTxt(): Boolean {
        return mimeType?.equals("text/plain", true) == true
    }

    fun isPdf(): Boolean {
        return mimeType?.equals("application/pdf", true) == true
    }

    fun isWordDoc(): Boolean {
        return mimeType?.equals("application/msword", true) == true
    }

    fun isPPT(): Boolean {
        return mimeType?.equals("application/vnd.ms-powerpoint", true) == true
    }

    fun isExcel(): Boolean {
        return mimeType?.equals("application/vnd.ms-excel", true) == true
    }

    /**
     * 是否是压缩包。
     * Check if the media entity is an archive file (zip, rar, tar, gz, iso, 7z).
     */
    fun isArchive(): Boolean {
        return isZip() || is7z() || isRar() || isTar() || isGz() || isIso()
    }

    fun isZip(): Boolean {
        return mimeType?.equals("application/zip", true) == true
    }

    fun isRar(): Boolean {
        return mimeType?.equals("application/x-rar-compressed", true) == true
    }

    fun isTar(): Boolean {
        return mimeType?.equals("application/x-tar", true) == true
    }

    fun isGz(): Boolean {
        return mimeType?.equals("application/gzip", true) == true
    }

    fun isIso(): Boolean {
        return mimeType?.equals("application/x-iso9660-image", true) == true
    }

    fun is7z(): Boolean {
        return mimeType?.equals("application/x-7z-compressed", true) == true
    }

    /**
     * Check if the media entity is an APK file.
     * @return true if the mimeType is "application/vnd.android.package-archive", false otherwise.
     */
    fun isApk(): Boolean {
        return mimeType?.equals("application/vnd.android.package-archive", true) == true
    }

    /**
     * 提供稳定ID（优先 MediaStore _ID，否则基于 path|size|addTime 的哈希）。
     */
    fun stableId(): Long {
        try {
            uri?.let { u ->
                val id = ContentUris.parseId(u)
                if (id != -1L) return id
            }
        } catch (_: Exception) {
        }
        val key = "${path?.lowercase() ?: ""}|${size}|${addTime ?: 0}"
        return key.hashCode().toLong()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaEntity) return false
        return path == other.path
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}



