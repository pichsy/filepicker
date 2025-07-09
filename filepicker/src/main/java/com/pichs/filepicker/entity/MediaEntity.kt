package com.pichs.filepicker.entity

import android.net.Uri
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
    var time: Long? = null,
    var selectedCount: Int = 0,
) : Parcelable {

    var tempSelected: Boolean = false

    companion object {
        fun fromPath(path: String): MediaEntity {
            return MediaEntity(path = path)
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

    fun isArchive(): Boolean {
        return isZip() || isRar() || isTar() || isGz()
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

    /**
     * Check if the media entity is an APK file.
     * @return true if the mimeType is "application/vnd.android.package-archive", false otherwise.
     */
    fun isApk(): Boolean {
        return mimeType?.equals("application/vnd.android.package-archive", true) == true
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


data class FilePickerTempSelected(
    var isDelete: Boolean = false,
    val mediaEntity: MediaEntity,
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilePickerTempSelected) return false
        return mediaEntity == other.mediaEntity
    }

    override fun hashCode(): Int {
        return mediaEntity?.path?.hashCode() ?: 0
    }
}

