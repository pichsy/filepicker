package com.pichs.filepicker.demo.newpicker.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地媒体分页数据源
 * 使用稳定的分页策略，避免数据位置变化
 */
class LocalMediaPagingSource(
    private val selectType: String,
    private val context: Context,
    private val pageSize: Int = 100,
    private val onTotalCountChanged: ((Int) -> Unit)? = null
) : PagingSource<Int, MediaEntity>() {

    companion object {
        private const val STARTING_PAGE_INDEX = 0
        private const val FIRST_BATCH_SIZE = 300  // 首批快速加载300条
    }

    // 缓存全部数据，确保分页过程中数据稳定
    private var allMediaList: List<MediaEntity>? = null
    // 首批数据加载状态
    private var firstBatchLoaded = false
    // 全量数据加载状态
    private var isLoadingAll = false

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaEntity> {
        return try {
            val page = params.key ?: STARTING_PAGE_INDEX

            // 处理首次加载：快速显示前300条数据
            if (allMediaList == null) {
                if (page == 0 && !firstBatchLoaded) {
                    // 首次加载：快速加载前300条数据
                    val firstBatch = loadFirstBatch(FIRST_BATCH_SIZE)
                    firstBatchLoaded = true

                    // 先显示首批数量
                    onTotalCountChanged?.invoke(firstBatch.size)

                    // 启动后台加载全量数据
                    if (!isLoadingAll) {
                        isLoadingAll = true
                        loadRemainingDataInBackground()
                    }

                    return LoadResult.Page(
                        data = firstBatch,
                        prevKey = null,
                        nextKey = if (firstBatch.size >= pageSize) 1 else null
                    )
                } else {
                    // 等待全量数据加载完成
                    waitForAllDataLoaded()
                }
            }

            // 刷新时重新加载全部数据
            if (params is LoadParams.Refresh) {
                allMediaList = loadAllMediaFromDatabase()
            }

            // 正常分页逻辑
            val totalList = allMediaList ?: emptyList()
            val startIndex = page * pageSize
            val endIndex = minOf(startIndex + pageSize, totalList.size)

            val pageData = if (startIndex < totalList.size) {
                totalList.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            LoadResult.Page(
                data = pageData,
                prevKey = if (page == STARTING_PAGE_INDEX) null else page - 1,
                nextKey = if (endIndex >= totalList.size) null else page + 1
            )
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MediaEntity>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    /**
     * 清除缓存，强制重新加载
     */
    fun clearCache() {
        allMediaList = null
        firstBatchLoaded = false
        isLoadingAll = false
    }

    /**
     * 快速加载首批数据（前300条）
     */
    private suspend fun loadFirstBatch(limit: Int): List<MediaEntity> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaEntity>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = getProjection()
        val selection = getSelection()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val mediaEntity = parseMediaEntityFromCursor(cursor)
                if (mediaEntity != null) {
                    mediaList.add(mediaEntity)
                    count++
                }
            }
        }

        return@withContext mediaList
    }

    /**
     * 后台加载剩余数据
     */
    private fun loadRemainingDataInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 加载全量数据
                val fullData = loadAllMediaFromDatabase()
                allMediaList = fullData
                isLoadingAll = false

                // 通知文件总数变化
                onTotalCountChanged?.invoke(fullData.size)
            } catch (e: Exception) {
                isLoadingAll = false
                // 可以在这里处理错误，比如记录日志
            }
        }
    }

    /**
     * 等待全量数据加载完成
     */
    private suspend fun waitForAllDataLoaded() {
        // 如果正在加载，等待完成
        while (isLoadingAll && allMediaList == null) {
            delay(50) // 每50ms检查一次
        }

        // 如果还没有数据，直接加载
        if (allMediaList == null) {
            allMediaList = loadAllMediaFromDatabase()
        }
    }

    /**
     * 一次性加载所有媒体数据，确保分页过程中数据稳定
     */
    private suspend fun loadAllMediaFromDatabase(): List<MediaEntity> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaEntity>()

        val uri = MediaStore.Files.getContentUri("external")
        val projection = getProjection()
        val selection = getSelection()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            mediaList.addAll(parseCursorToMediaList(cursor))
        }

        return@withContext mediaList
    }

    /**
     * 获取查询字段（复用原有逻辑）
     */
    private fun getProjection(): Array<String> {
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
        } else if (selectType == FilePickerSelectType.AUDIO) {
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

    /**
     * 获取查询条件（复用原有逻辑）
     */
    private fun getSelection(): String {
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

    /**
     * 解析 Cursor 数据为 MediaEntity 列表（复用原有逻辑）
     */
    private fun parseCursorToMediaList(data: Cursor?): List<MediaEntity> {
        val mediaList = mutableListOf<MediaEntity>()

        data?.let { cursor ->
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

            val bucketNameIndex = try {
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            } catch (e: IllegalArgumentException) {
                -1
            }

            val bucketIdIndex = try {
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            } catch (e: IllegalArgumentException) {
                -1
            }

            val dateAddedIndex = try {
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            } catch (e: IllegalArgumentException) {
                -1
            }

            val displayNameIndex = try {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            } catch (e: IllegalArgumentException) {
                -1
            }

            val dataPathIndex = try {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
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

            while (cursor.moveToNext()) {
                if (dataPathIndex == -1 || mimeTypeIndex == -1) {
                    continue
                }

                val filePath = cursor.getString(dataPathIndex)
                if (!File(filePath).exists()) {
                    continue
                }

                val mimeType = cursor.getString(mimeTypeIndex)
                val id = if (idIndex != -1) cursor.getLong(idIndex) else -1L

                val bucketName = if (bucketNameIndex != -1) {
                    cursor.getString(bucketNameIndex) ?: "未命名相册"
                } else {
                    "未命名相册"
                }

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

                val mediaEntity = MediaEntity(
                    uri = contentUri,
                    name = displayName,
                    path = filePath,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    orientation = orientation,
                    size = size,
                    duration = duration,
                    addTime = dateAdded,
                )

                mediaList.add(mediaEntity)
            }
        }

        return mediaList
    }

    /**
     * 从 Cursor 解析单个 MediaEntity
     */
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
            addTime = dateAdded,
        )
    }
}
