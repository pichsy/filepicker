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
    var mediaEntityList: MutableList<MediaEntity> = arrayListOf(),
) : Parcelable {

    fun add(imageItem: MediaEntity) {
        this.mediaEntityList.add(imageItem)
    }

    fun add(index: Int, imageItem: MediaEntity) {
        this.mediaEntityList.add(index, imageItem)
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (folderPath?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaFolder

        if (name != other.name) return false
        if (folderPath != other.folderPath) return false
        if (mediaEntityList != other.mediaEntityList) return false
        if (mediaEntityList.size != other.mediaEntityList.size) return false

        return true
    }

}