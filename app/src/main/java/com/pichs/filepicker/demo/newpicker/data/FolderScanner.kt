package com.pichs.filepicker.demo.newpicker.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件夹扫描器
 */
object FolderScanner {

    /**
     * 扫描所有文件夹
     */
    suspend fun scanFolders(context: Context, selectType: String): List<MediaFolder> = withContext(Dispatchers.IO) {
        val folderMap = mutableMapOf<String, MediaFolder>()
        
        val uri = MediaStore.Files.getContentUri("external")
        val projection = getProjection(selectType)
        val selection = getSelection(selectType)
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        
        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val mediaEntity = parseMediaEntityFromCursor(cursor)
                if (mediaEntity != null) {
                    val bucketName = getBucketName(cursor) ?: "未命名相册"
                    val bucketId = getBucketId(cursor) ?: bucketName
                    
                    val folder = folderMap.getOrPut(bucketId) {
                        MediaFolder(
                            name = bucketName,
                            folderPath = mediaEntity.path?.substringBeforeLast("/"),
                            mediaEntityList = mutableListOf()
                        )
                    }
                    folder.mediaEntityList.add(mediaEntity)
                }
            }
        }
        
        return@withContext folderMap.values.toList().sortedByDescending { it.mediaEntityList.size }
    }

    private fun getProjection(selectType: String): Array<String> {
        return if (selectType == FilePickerSelectType.IMAGE_VIDEO
            || selectType == FilePickerSelectType.IMAGE_VIDEO_GIF
            || selectType == FilePickerSelectType.IMAGE
            || selectType == FilePickerSelectType.VIDEO
        ) {
            arrayOf(
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
        } else {
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.BUCKET_ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
            )
        }
    }

    private fun getSelection(selectType: String): String {
        return when (selectType) {
            FilePickerSelectType.IMAGE -> {
                "${MediaStore.MediaColumns.SIZE}>0 AND (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE} != 'image/gif')"
            }
            FilePickerSelectType.VIDEO -> {
                "${MediaStore.MediaColumns.SIZE}>0 AND ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            }
            FilePickerSelectType.IMAGE_VIDEO -> {
                "${MediaStore.MediaColumns.SIZE}>0 AND ((${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE}!= 'image/gif') OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            }
            FilePickerSelectType.IMAGE_VIDEO_GIF -> {
                "${MediaStore.MediaColumns.SIZE}>0 AND (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            }
            else -> {
                "${MediaStore.MediaColumns.SIZE}>0 AND ((${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE}!= 'image/gif') OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
            }
        }
    }

    private fun getBucketName(cursor: Cursor): String? {
        val bucketNameIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        } catch (e: IllegalArgumentException) {
            -1
        }
        
        return if (bucketNameIndex != -1) {
            cursor.getString(bucketNameIndex)
        } else {
            null
        }
    }

    private fun getBucketId(cursor: Cursor): String? {
        val bucketIdIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        } catch (e: IllegalArgumentException) {
            -1
        }
        
        return if (bucketIdIndex != -1) {
            cursor.getString(bucketIdIndex)
        } else {
            null
        }
    }

    private fun parseMediaEntityFromCursor(cursor: Cursor): MediaEntity? {
        val idIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val mimeTypeIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val dataPathIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val displayNameIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val dateAddedIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val sizeIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val mediaTypeIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val widthIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val heightIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val orientationIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
        } catch (e: IllegalArgumentException) {
            -1
        }

        val durationIndex = try {
            cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
        } catch (e: IllegalArgumentException) {
            -1
        }

        if (dataPathIndex == -1 || mimeTypeIndex == -1) {
            return null
        }

        val filePath = cursor.getString(dataPathIndex)
        if (!File(filePath).exists()) {
            return null
        }

        val mimeType = cursor.getString(mimeTypeIndex)
        val id = if (idIndex != -1) cursor.getLong(idIndex) else -1L

        val dateAdded = if (dateAddedIndex != -1) {
            cursor.getLong(dateAddedIndex)
        } else {
            0L
        }

        val displayName = if (displayNameIndex != -1) {
            cursor.getString(displayNameIndex)
        } else {
            "未知文件"
        }

        val width = if (widthIndex != -1) cursor.getInt(widthIndex) else 0
        val height = if (heightIndex != -1) cursor.getInt(heightIndex) else 0
        val orientation = if (orientationIndex != -1) cursor.getInt(orientationIndex) else 0
        val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
        val duration = if (durationIndex != -1) cursor.getLong(durationIndex) else 0L
        val mediaType = if (mediaTypeIndex != -1) {
            cursor.getInt(mediaTypeIndex)
        } else {
            MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
        }

        val contentUri = when (mediaType) {
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            else -> MediaStore.Files.getContentUri("external")
        }

        return MediaEntity(
            uri = contentUri,
            name = displayName,
            path = filePath,
            mimeType = mimeType,
            width = width,
            height = height,
            orientation = orientation,
            size = size,
            duration = duration,
            time = dateAdded,
            selectedCount = 0
        )
    }
}
