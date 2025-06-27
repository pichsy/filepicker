package com.pichs.filepicker.entity

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable

/**
 * 媒体文件夹
 */
@Parcelize
data class MediaFolder(
    var name: String? = null,
    var folderPath: String? = null,
    var coverImagePath: String? = null,
    var coverImageUri: Uri? = null,
    var mediaEntityList: MutableList<MediaEntity> = arrayListOf(),
) : Parcelable, Serializable {

    // 名字匹配equals, name,folderPath
//    override fun equals(other: Any?): Boolean {
//        if (this === other) return true
//        if (javaClass != other?.javaClass) return false
//
//        other as MediaFolder
//
//        if (name != other.name) return false
//        if (folderPath != other.folderPath) return false
//
//        return true
//    }


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
}