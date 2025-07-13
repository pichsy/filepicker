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
            mediaFolder.name = getFolderNickName(mediaFolder.name ?: "")
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
            mediaFolder.name = getFolderNickName(albumName)
            mediaFolder.add(mediaEntity)
            mediaFolders.add(mediaFolder)
        }
    }

    private fun getFolderNickName(name: String): String {
        if (name.isEmpty()) return ""
        return if (name.equals("DCIM", true) || name.equals("Camera", true)) {
            "相机"
        } else if (name.equals("Pictures", true)) {
            "图片"
        } else if (name.equals("Movies", true)) {
            "视频"
        } else if (name.equals("Audio", true)) {
            "音频"
        } else if (name.equals("Music", true)) {
            "音乐"
        } else if (name.equals("Screenshots", true)) {
            "截图"
        } else if (name.equals("Download", true)) {
            "下载"
        } else {
            name
        }
    }

    fun isEmpty(): Boolean {
        return mediaFolders.isEmpty()
    }

    fun clear() {
        mediaFolders.clear()
    }

}
