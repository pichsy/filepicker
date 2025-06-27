package com.pichs.filepicker.entity

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
        val folder = mediaFolders.find { it == mediaFolder }
        if (folder != null) {
            folder.add(mediaEntity)
        } else {
            mediaFolder.add(mediaEntity)
            mediaFolders.add(mediaFolder)
        }
    }

    fun addMediaEntity(albumPath: String, mediaEntity: MediaEntity) {
        // 首先获取列表中的文件夹
        val folder = mediaFolders.find { it.folderPath == albumPath }
        if (folder != null) {
            folder.add(mediaEntity)
        } else {
            val mediaFolder = MediaFolder()
            mediaFolder.folderPath = albumPath
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
