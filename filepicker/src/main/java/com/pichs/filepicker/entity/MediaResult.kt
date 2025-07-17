package com.pichs.filepicker.entity

import com.pichs.filepicker.utils.FilePickerFileUtils

/**
 * 媒体返回结果，全部
 */
data class MediaResult(
    var mediaFolders: ArrayList<MediaFolder> = ArrayList(),
) {

    fun addAlbumFolder(mediaFolder: MediaFolder) {
        mediaFolders.add(mediaFolder)
    }

    fun addAlbumFolder(index: Int, mediaFolder: MediaFolder) {
        mediaFolders.add(index, mediaFolder)
    }

    fun removeAlbumFolder(mediaFolder: MediaFolder) {
        mediaFolders.remove(mediaFolder)
    }

    fun removeAlbumFolder(index: Int) {
        mediaFolders.removeAt(index)
    }

    fun getAlbumFolder(index: Int): MediaFolder {
        return mediaFolders[index]
    }

    fun addMediaEntity(mediaFolder: MediaFolder, mediaEntity: MediaEntity) {
        // 首先获取列表中的文件夹
        val folder = mediaFolders.find { mediaFolder.folderPath?.equals(it.folderPath, true) == true }
        if (folder != null) {
            folder.add(mediaEntity)
        } else {
            mediaFolder.add(mediaEntity)
            mediaFolder.name = mediaFolder.name
            mediaFolders.add(mediaFolder)
        }
    }

    fun addMediaEntity(albumName: String, albumPath: String, mediaEntity: MediaEntity) {
        // 首先获取列表中的文件夹
        val folder = mediaFolders.find { albumPath.equals(it.folderPath, true) }
        if (folder != null) {
            folder.add(mediaEntity)
        } else {
            val mediaFolder = MediaFolder()
            mediaFolder.folderPath = albumPath
            mediaFolder.name = albumName
            mediaFolder.add(mediaEntity)
            mediaFolders.add(mediaFolder)
        }
    }

    fun isEmpty(): Boolean {
        return mediaFolders.isEmpty()
    }

    fun clear() {
        mediaFolders.clear()
    }

}
