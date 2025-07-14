package com.pichs.filepicker.entity

import java.io.Serializable

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