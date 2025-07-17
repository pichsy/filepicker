package com.pichs.filepicker.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 媒体文件夹
 */
@Parcelize
data class MediaFolder(
    var name: String? = null,
    var folderPath: String? = null,
    var mediaEntityList: MutableList<MediaEntity> = mutableListOf(),
    internal var tag: String? = null,
    var nickName: String? = null,
) : Parcelable {

    fun add(imageItem: MediaEntity) {
        this.mediaEntityList.add(imageItem)
    }

    fun add(index: Int, imageItem: MediaEntity) {
        this.mediaEntityList.add(index, imageItem)
    }

    override fun hashCode(): Int {
        var result = name?.lowercase()?.hashCode() ?: 0
        result = 31 * result + (folderPath?.lowercase()?.hashCode() ?: 0)
        result = 31 * result + mediaEntityList.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaFolder

        if (name.equals(other.name, true)) return false
        if (folderPath.equals(other.folderPath, true)) return false
        if (mediaEntityList != other.mediaEntityList) return false
        if (mediaEntityList.size != other.mediaEntityList.size) return false

        return true
    }

}