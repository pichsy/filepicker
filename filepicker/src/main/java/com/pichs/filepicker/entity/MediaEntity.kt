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
                    path.endsWith(".apk", true) -> FilePickerMimeType.APK
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
        return mimeType?.equals(FilePickerMimeType.AUDIO_MP3, true) == true
    }

    fun isWav(): Boolean {
        return mimeType?.equals(FilePickerMimeType.AUDIO_WAV, true) == true
    }

    fun isFlac(): Boolean {
        return mimeType?.equals(FilePickerMimeType.AUDIO_FLAC, true) == true
    }

    fun isM4a(): Boolean {
        return mimeType?.equals(FilePickerMimeType.AUDIO_M4A, true) == true
    }

    fun isAmr(): Boolean {
        return mimeType?.equals(FilePickerMimeType.AUDIO_AMR, true) == true
    }

    fun isGif(): Boolean {
        return mimeType?.equals(FilePickerMimeType.GIF, true) == true
    }

    fun isJpeg(): Boolean {
        return mimeType?.equals(FilePickerMimeType.IMAGE_JPEG, true) == true
    }

    fun isPng(): Boolean {
        return mimeType?.equals(FilePickerMimeType.IMAGE_PNG, true) == true
    }

    fun isWebp(): Boolean {
        return mimeType?.equals(FilePickerMimeType.IMAGE_WEBP, true) == true
    }

    fun isTxt(): Boolean {
        return mimeType?.equals(FilePickerMimeType.TXT, true) == true
    }

    fun isPdf(): Boolean {
        return mimeType?.equals(FilePickerMimeType.PDF, true) == true
    }

    fun isWordDoc(): Boolean {
        return mimeType?.equals(FilePickerMimeType.DOC, true) == true
                || mimeType?.equals(FilePickerMimeType.DOCX, true) == true
    }

    fun isPPT(): Boolean {
        return mimeType?.equals(FilePickerMimeType.PPT, true) == true
                || mimeType?.equals(FilePickerMimeType.PPTX, true) == true
    }

    fun isExcel(): Boolean {
        return mimeType?.equals(FilePickerMimeType.EXCEL, true) == true
                || mimeType?.equals(FilePickerMimeType.EXCELX, true) == true
    }

    /**
     * 是否是压缩包。
     * Check if the media entity is an archive file (zip, rar, tar, gz, iso, 7z).
     */
    fun isArchive(): Boolean {
        return isZip() || is7z() || isRar() || isTar() || isGz() || isIso()
    }

    fun isZip(): Boolean {
        return mimeType?.equals(FilePickerMimeType.ZIP, true) == true
    }

    fun isRar(): Boolean {
        return mimeType?.equals(FilePickerMimeType.RAR, true) == true
                || mimeType?.equals(FilePickerMimeType.RAR_VND, true) == true
    }

    fun isTar(): Boolean {
        return mimeType?.equals(FilePickerMimeType.TAR, true) == true
    }

    fun isGz(): Boolean {
        return mimeType?.equals(FilePickerMimeType.GZ, true) == true
    }

    fun isIso(): Boolean {
        return mimeType?.equals(FilePickerMimeType.ISO, true) == true
    }

    fun is7z(): Boolean {
        return mimeType?.equals(FilePickerMimeType.SEVEN_Z, true) == true
    }

    /**
     * Check if the media entity is an APK file.
     * @return true if the mimeType is "application/vnd.android.package-archive", false otherwise.
     */
    fun isApk(): Boolean {
        return mimeType?.equals(FilePickerMimeType.APK, true) == true
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



