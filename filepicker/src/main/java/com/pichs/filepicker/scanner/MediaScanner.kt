package com.pichs.filepicker.scanner

import android.content.ContentUris
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import java.io.File

/**
 * 相册、视频扫描工具，输出现成 MediaFolder 和 MediaEntity
 */
object MediaScanner {

    interface ScanCallback {
        fun onCompleted(folders: List<MediaFolder>)
    }

    private var times = 1032

    fun scanMedia(type: String, fragment: Fragment, callback: ScanCallback) {
        val loaderId = times++
        LoaderManager.getInstance(fragment).initLoader(loaderId, null, object : LoaderManager.LoaderCallbacks<Cursor> {
            override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                    MediaStore.Files.FileColumns.BUCKET_ID,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.MediaColumns.ORIENTATION,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.Video.VideoColumns.DURATION
                )

                val selection = if (type == "image") {
                    "${MediaStore.MediaColumns.SIZE}>0 and ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}".trimIndent()
                } else if (type == "video") {
                    "${MediaStore.MediaColumns.SIZE}>0 and ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}".trimIndent()
                } else {
                    """
                   ${MediaStore.MediaColumns.SIZE}>0 and (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} 
                    OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})
                    """.trimIndent()
                }

                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                return CursorLoader(fragment.requireContext(), uri, projection, selection, null, sortOrder)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
                val folderMap = mutableMapOf<String, MediaFolder>()

                data?.let {
                    val idIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val mimeTypeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val bucketNameIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    val bucketIdIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                    val dateAddedIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dataPathIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val widthIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                    val heightIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                    val orientationIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                    val sizeIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val durationIndex = it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                    val mediaTypeIndex = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

                    while (it.moveToNext()) {
                        val filePath = it.getString(dataPathIndex)

                        if (!File(filePath).exists()) {
                            continue // 跳过不存在的文件
                        }

                        val id = it.getLong(idIndex)
                        val mimeType = it.getString(mimeTypeIndex)
                        val bucketName = it.getString(bucketNameIndex) ?: "未命名相册"
                        val bucketId = it.getString(bucketIdIndex) ?: bucketName
                        val dateAdded = it.getLong(dateAddedIndex)
                        val displayName = it.getString(displayNameIndex)
                        val width = it.getInt(widthIndex)
                        val height = it.getInt(heightIndex)
                        val orientation = it.getInt(orientationIndex)
                        val size = it.getLong(sizeIndex)
                        val duration = it.getLong(durationIndex)
                        val mediaType = it.getInt(mediaTypeIndex)

                        val contentUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            else -> continue
                        }

                        val mediaEntity = MediaEntity(
                            uri = contentUri,
                            name = displayName,
                            path = filePath,
                            type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) "image" else "video",
                            mimeType = mimeType,
                            width = width,
                            height = height,
                            orientation = orientation,
                            size = size,
                            duration = duration,
                            time = dateAdded,
                            selectedCount = 0
                        )
                        val folder = folderMap.getOrPut(bucketId) {
                            MediaFolder(
                                name = bucketName,
                                folderPath = filePath?.substringBeforeLast("/"),
                                coverImagePath = filePath,
                                coverImageUri = contentUri,
                                mediaEntityList = arrayListOf()
                            )
                        }
                        folder.mediaEntityList.add(mediaEntity)
                    }
                }
                callback.onCompleted(folderMap.values.toList())
            }

            override fun onLoaderReset(loader: Loader<Cursor>) {
            }
        })
    }

}
