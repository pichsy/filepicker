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

    /**
     * 值相等。旧实现三个字段都写反了（相等时 `return false`），导致除了同一个引用以外
     * 永远不相等：`mediaFolders.remove(folder)`、`contains` 之类的操作全部失效。
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MediaFolder) return false
        return name.equals(other.name, true) &&
                folderPath.equals(other.folderPath, true) &&
                mediaEntityList == other.mediaEntityList
    }

}