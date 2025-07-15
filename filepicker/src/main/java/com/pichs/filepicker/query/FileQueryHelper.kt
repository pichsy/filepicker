package com.pichs.filepicker.query

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.pichs.filepicker.entity.MediaResult
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import com.pichs.filepicker.utils.FilePickerFileUtils
import com.pichs.filepicker.utils.FilePickerLog
import com.pichs.filepicker.utils.FilePickerTimeFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max


/**
 * 使用时请注意，在某些设备上，需要申请到管理文件权限才能获取到文件类型的。
 * 如：华为手机，需要申请到管理文件权限才能获取到文件类型（.pdf,.doc,.xsl，.txt等非图片视频音频等类型的文件）。
 * 即：MANAGE_EXTERNAL_STORAGE权限
 * 图片类型，也依然需要读取媒体文件权限，READ_MEDIA_IMAGES,READ_MEDIA_VIDEOS,READ_MEDIA_AUDIOS权限。低版本的android系统，需要READ_EXTERNAL_STORAGE权限。
 * 本工具类不会判断这些，请接入者针对==>获取的类型，自行申请相应的权限。
 */
object FileQueryHelper {

    private const val VOLUME_NAME = "external"
    private const val MIME_TYPE_GIF = "image/gif"


    @SuppressLint("Range")
    suspend fun queryAlbums(
        context: Context,
        queryTypes: MutableSet<QueryType> = mutableSetOf(QueryType.VIDEO, QueryType.IMAGE),
        minSize: Long = 0L,
        maxSize: Long = 0L,
        queryBuilder: (QueryWhere.Builder) -> Unit = {},
        fastNumber: Int = 300,
        onFastCallBack: (list: (MutableList<MediaFolder>)) -> Unit
    ): MediaResult {
        return withContext(Dispatchers.IO) {
            FilePickerLog.e("相册获取, 开始查询---queryAlbums:type:${queryTypes.joinToString { it.type }}")
            val mediaResult = MediaResult()
            if (queryTypes.isEmpty()) {
                return@withContext mediaResult
            }

            val isNoneMedia = queryTypes.contains(QueryType.NONE)

            if (isNoneMedia) {
                // 移除所有非NONE的类型。
                queryTypes.removeAll { it != QueryType.NONE }
            }

            var isOnlyVideo = false
            var isOnlyImage = false
            var isOnlyAudio = false
            var isOnlyGif = false
            var isContainsGif = false

            if (!isNoneMedia) {
                isContainsGif = queryTypes.any { it == QueryType.GIF }
                // 是否仅仅是gif图片
                isOnlyGif = isOnlyGifNotImage(queryTypes)

                // 如果包含gif，那么就要查询图片
                if (isContainsGif) {
                    queryTypes.remove(QueryType.GIF)
                    queryTypes.add(QueryType.IMAGE)
                }
                // 去重，防止乱传参数

                if (queryTypes.size == 1) {
                    val queryType = queryTypes.firstOrNull()
                    when (queryType) {
                        QueryType.VIDEO -> {
                            isOnlyVideo = true
                        }

                        QueryType.IMAGE -> {
                            isOnlyImage = true
                        }

                        QueryType.AUDIO -> {
                            isOnlyAudio = true
                        }

                        else -> {
                            // nothing to do
                        }
                    }
                }
            }


            var contentUri: Uri? = null

            // 过滤器
            val queryWhereBuilder = QueryWhere.Builder()

            if (isNoneMedia) {
                contentUri = MediaStore.Files.getContentUri(VOLUME_NAME)
            } else if (isOnlyVideo) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if (isOnlyImage) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                if (isOnlyGif) {
                    queryWhereBuilder.leftBracket().mimeTypeEquals(MIME_TYPE_GIF).rightBracket()
                } else if (!isContainsGif) {
                    queryWhereBuilder.leftBracket().mimeTypeNotEquals(MIME_TYPE_GIF).rightBracket()
                }
            } else if (isOnlyAudio) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                // 其他类型组合，但不是 NONE 类型
                contentUri = MediaStore.Files.getContentUri(VOLUME_NAME)

                queryWhereBuilder.leftBracket()
                queryTypes.forEachIndexed { _, queryType ->
                    if (queryType == QueryType.IMAGE) {
                        if (isOnlyGif) {
                            queryWhereBuilder.mimeTypeEquals(MIME_TYPE_GIF).or()
                        } else if (!isContainsGif) {
                            queryWhereBuilder.mimeTypeStartWith(getMimeTypePrefix(queryType)).and().mimeTypeNotEquals(MIME_TYPE_GIF).or()
                        }
                    } else {
                        queryWhereBuilder.mimeTypeStartWith(getMimeTypePrefix(queryType)).or()
                    }
                }

                queryWhereBuilder.removeEndAndOr().rightBracket()
            }

            val queryWhere = QueryWhere.Builder()
            // 通过这个进行其他条件的查询
            queryBuilder.invoke(queryWhere)

            val qf = queryWhereBuilder.build() + queryWhere.build()

            FilePickerLog.d(
                """相册获取, query参数sql语句：
                contentUri:$contentUri
                section:${qf.section}
                selectionAllArgs:${qf.sectionArgs?.joinToString(",")}
            """.trimIndent()
            )

            val contentResolver = context.contentResolver
            var projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.BUCKET_ID,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.Files.FileColumns.ORIENTATION
            }

            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"

            if (contentUri == null) {
                return@withContext mediaResult
            }

            if (!isActive) {
                return@withContext MediaResult()
            }
            val cursor = contentResolver.query(
                contentUri, projection, qf.section, qf.sectionArgs, sortOrder
            )

            if (!isActive) {
                return@withContext MediaResult()
            }

            if (cursor == null) {
                return@withContext mediaResult
            }

            var addCount = 0

            val startTimeWhile = System.currentTimeMillis()

            while (cursor.moveToNext()) {

                if (!isActive) {
                    FilePickerLog.e("相册获取->> job被取消了 ，break 不再循环。")
                    return@withContext MediaResult()
                }

                val startTimeOneWhile = System.currentTimeMillis()
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                val filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                val fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE))
                var width = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH))
                var height = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT))
                val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION))
                val dateAdd = max(0, cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000)// 注意：dateAdd是秒级别的时间戳
                val bucketId = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID))
                val foldName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME))
                val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION))
                } else {
                    0
                }

                val endTimeOneWhile = System.currentTimeMillis()

                // 获取缩略图位置
//                FilePickerLog.e {
//                    """
//                        相册获取--->信息：
//                        queryAlbums: id:$id
//                        queryAlbums: data:$filePath
//                        queryAlbums: fileName:$fileName
//                        size:$size
//                        mimeType:$mimeType
//                        width:$width
//                        height:$height
//                        duration:$duration
//                        dateModified:${dateAdd}
//                        foldName:$foldName
//                        folderPath:${FilePickerFileUtils.getFolderPath(filePath)}
//                        bucketId:$bucketId
//                        orientation:$orientation
//                        addTime*1000=${dateAdd} ms,  formatTime=${FilePickerTimeFormatUtils.formatTime(dateAdd)}
//                    """.trimIndent()
//                }

                val fileCheckTimeStart = System.currentTimeMillis()
//                FilePickerLog.e { "相册获取, 开始文件判断---fileCheckTimeStart 耗时:${fileCheckTimeStart - endTimeOneWhile}" }

                if (size <= minSize) {
                    FilePickerLog.d("相册获取, queryAlbums: 文件大小小于最小值，忽略。。。。")
                    continue
                }

                if (!(maxSize <= 0 || maxSize == Long.MAX_VALUE)) {
                    if (size > maxSize) {
                        FilePickerLog.d("相册获取, queryAlbums: 文件大小大于最大值，忽略。。。。")
                        continue
                    }
                }

                if (FilePickerFileUtils.isFileInHiddenDir(filePath)) {
                    FilePickerLog.d("相册获取, 文件判断: 在隐藏目录，不展示, 忽略======")
                    continue
                }

                val file = File(filePath)
                // 文件有毛病,忽略。。。。
                val isExists = FilePickerFileUtils.isFileExists(file)
                if (!isExists) {
                    FilePickerLog.d("相册获取, queryAlbums: 文件不存在，忽略。。。。")
                    continue
                }

                val isFile = FilePickerFileUtils.isFile(file)
                // 文件大小为0，忽略。。。
                if (!isFile) {
                    FilePickerLog.e("相册获取, queryAlbums: 文件不是文件，忽略。。。。")
                    continue
                }

                val length = FilePickerFileUtils.getFileSize(file)
                // 文件在隐藏目录，忽略。。。
                if (length <= 0) {
                    FilePickerLog.e("相册获取, queryAlbums: 文件大小为0，忽略。。。。")
                    continue
                }

                val fileCheckTimeEnd = System.currentTimeMillis()
//                FilePickerLog.e { "相册获取, 结束文件判断---fileCheckTimeEnd:${fileCheckTimeEnd}---耗时：${fileCheckTimeEnd - fileCheckTimeStart}" }

                val uri = ContentUris.withAppendedId(
                    if (mimeType?.startsWith("video/", true) == true) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if (mimeType?.startsWith("image/", true) == true) {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if (mimeType?.startsWith("audio/", true) == true) {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri(VOLUME_NAME)
                    }, id
                )

//                if (width == 0 || height == 0) {
//                    val options = BitmapFactory.Options()
//                    options.inJustDecodeBounds = true
//                    BitmapFactory.decodeFile(data, options)
//                    width = options.outWidth
//                    height = options.outHeight
//                }

                val mediaEntity = MediaEntity(
                    uri = uri,
                    name = fileName ?: FilePickerFileUtils.getFileName(filePath = filePath),
                    path = filePath,
                    size = size,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    duration = duration,
                    orientation = orientation,
                    addTime = dateAdd,
                )
                addCount++
                mediaResult.addMediaEntity(foldName ?: FilePickerFileUtils.getFolderName(filePath), FilePickerFileUtils.getFolderPath(filePath), mediaEntity)

//                FilePickerLog.e { "相册获取, 结束循环（单次 到底共) while----endtimewhile:--耗时：${System.currentTimeMillis() - startTimeOneWhile}" }
                if (addCount == fastNumber) {
                    if (isActive) {
                        FilePickerLog.e("相册获取, queryAlbums: 快速回调，已获取到${fastNumber}数据，返回结果。")
                        onFastCallBack(mediaResult.mediaFolders.toMutableList())
                    }
                }
            }
            val endTimeWhile = System.currentTimeMillis()
            FilePickerLog.e("相册获取, 结束循环（整体）while---endTimeWhile:${endTimeWhile}---耗时：${endTimeWhile - startTimeWhile}")
            cursor.close()
            FilePickerLog.d("相册获取, queryAlbums: 最终结果-执行完毕---------folder:size：${mediaResult.mediaFolders.size}")
            return@withContext mediaResult
        }
    }

    @SuppressLint("Range")
    suspend fun queryAlbums(
        context: Context,
        queryTypes: MutableSet<QueryType> = mutableSetOf(QueryType.VIDEO, QueryType.IMAGE),
        queryBuilder: (QueryWhere.Builder) -> Unit = {},
    ): MediaResult {
        return withContext(Dispatchers.IO) {

            Log.e("相册获取", " 开始查询---queryAlbums:type:${queryTypes.joinToString { it.type }}")
            val mediaResult = MediaResult()
            if (queryTypes.isEmpty()) {
                return@withContext mediaResult
            }

            val isNoneMedia = queryTypes.contains(QueryType.NONE)

            if (isNoneMedia) {
                // 移除所有非NONE的类型。
                queryTypes.removeAll { it != QueryType.NONE }
            }

            var isOnlyVideo = false
            var isOnlyImage = false
            var isOnlyAudio = false
            var isOnlyGif = false
            var isContainsGif = false

            if (!isNoneMedia) {
                isContainsGif = queryTypes.any { it == QueryType.GIF }
                // 是否仅仅是gif图片
                isOnlyGif = isOnlyGifNotImage(queryTypes)

                // 如果包含gif，那么就要查询图片
                if (isContainsGif) {
                    queryTypes.remove(QueryType.GIF)
                    queryTypes.add(QueryType.IMAGE)
                }
                // 去重，防止乱传参数

                if (queryTypes.size == 1) {
                    val queryType = queryTypes.firstOrNull()
                    when (queryType) {
                        QueryType.VIDEO -> {
                            isOnlyVideo = true
                        }

                        QueryType.IMAGE -> {
                            isOnlyImage = true
                        }

                        QueryType.AUDIO -> {
                            isOnlyAudio = true
                        }

                        else -> {
                            // nothing to do
                        }
                    }
                }
            }


            var contentUri: Uri? = null

            // 过滤器
            val queryWhereBuilder = QueryWhere.Builder()

            if (isNoneMedia) {
                contentUri = MediaStore.Files.getContentUri(VOLUME_NAME)
            } else if (isOnlyVideo) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else if (isOnlyImage) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                if (isOnlyGif) {
                    queryWhereBuilder.leftBracket().mimeTypeEquals(MIME_TYPE_GIF).rightBracket()
                } else if (!isContainsGif) {
                    queryWhereBuilder.leftBracket().mimeTypeNotEquals(MIME_TYPE_GIF).rightBracket()
                }
            } else if (isOnlyAudio) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                // 其他类型组合，但不是 NONE 类型
                contentUri = MediaStore.Files.getContentUri(VOLUME_NAME)

                queryWhereBuilder.leftBracket()
                queryTypes.forEachIndexed { _, queryType ->
                    if (queryType == QueryType.IMAGE) {
                        if (isOnlyGif) {
                            queryWhereBuilder.mimeTypeEquals(MIME_TYPE_GIF).or()
                        } else if (!isContainsGif) {
                            queryWhereBuilder.mimeTypeStartWith(getMimeTypePrefix(queryType)).and().mimeTypeNotEquals(MIME_TYPE_GIF).or()
                        }
                    } else {
                        queryWhereBuilder.mimeTypeStartWith(getMimeTypePrefix(queryType)).or()
                    }
                }

                queryWhereBuilder.removeEndAndOr().rightBracket()
            }

            val queryWhere = QueryWhere.Builder()
            // 通过这个进行其他条件的查询
            queryBuilder.invoke(queryWhere)

            val qf = queryWhereBuilder.build() + queryWhere.build()

            FilePickerLog.d("相册获取", "contentUri:$contentUri")
            FilePickerLog.d("相册获取", "section:${qf.section}")
            FilePickerLog.d("相册获取", "selectionAllArgs:${qf.sectionArgs?.joinToString(",")}")

            val contentResolver = context.contentResolver
            var projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Files.FileColumns.DURATION,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.BUCKET_ID,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.Files.FileColumns.ORIENTATION
            }

            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"

            if (contentUri == null) {
                return@withContext mediaResult
            }

            val cursor = contentResolver.query(
                contentUri, projection, qf.section, qf.sectionArgs, sortOrder
            )

            if (cursor == null) {
                return@withContext mediaResult
            }

            val startTimeWhile = System.currentTimeMillis()
            while (cursor.moveToNext()) {

                val startTimeOneWhile = System.currentTimeMillis()
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                val filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                val fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE))
                var width = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH))
                var height = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT))
                val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION))
                val dateAdd = max(0, cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000)// 注意：dateAdd是秒级别的时间戳
                val bucketId = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID))
                val foldName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME))
                val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION))
                } else {
                    0
                }

                val endTimeOneWhile = System.currentTimeMillis()

                // 获取缩略图位置
                FilePickerLog.d("相册获取", "queryAlbums: id:$id")
                FilePickerLog.d("相册获取", "queryAlbums: data:$filePath")
                FilePickerLog.d("相册获取", "queryAlbums: fileName:$fileName")
                FilePickerLog.d("相册获取", "queryAlbums: size:$size")
                FilePickerLog.d("相册获取", "queryAlbums: mimeType:$mimeType")
                FilePickerLog.d("相册获取", "queryAlbums: width:$width")
                FilePickerLog.d("相册获取", "queryAlbums: height:$height")
                FilePickerLog.d("相册获取", "queryAlbums: duration:$duration")
                FilePickerLog.d("相册获取", "queryAlbums: dateModified:${dateAdd}")
                FilePickerLog.d("相册获取", "queryAlbums: foldName:$foldName")
                FilePickerLog.d("相册获取", "queryAlbums: folderPath:${FilePickerFileUtils.getFolderPath(filePath)}")
                FilePickerLog.d("相册获取", "queryAlbums: bucketId:$bucketId")
                FilePickerLog.d("相册获取", "queryAlbums: orientation:$orientation")
                FilePickerLog.d(
                    "相册获取", "queryAlbums: addTime*1000=${dateAdd} ms,  formatTime=${FilePickerTimeFormatUtils.formatTime(dateAdd)}"
                )

                val fileCheckTimeStart = System.currentTimeMillis()
                FilePickerLog.e("相册获取", " 开始文件判断---fileCheckTimeStart 耗时:${fileCheckTimeStart - endTimeOneWhile}")

                if (FilePickerFileUtils.isFileInHiddenDir(filePath)) {
                    FilePickerLog.d("相册获取", "文件判断: 在隐藏目录，不展示, 忽略======")
                    continue
                }

                val file = File(filePath)
                // 文件有毛病,忽略。。。。
                val isExists = FilePickerFileUtils.isFileExists(file)
                if (!isExists) {
                    FilePickerLog.d("相册获取", "queryAlbums: 文件不存在，忽略。。。。")
                    continue
                }

                val isFile = FilePickerFileUtils.isFile(file)
                // 文件大小为0，忽略。。。
                if (!isFile) {
                    FilePickerLog.d("相册获取", "queryAlbums: 文件不是文件，忽略。。。。")
                    continue
                }

                val length = FilePickerFileUtils.getFileSize(file)
                // 文件在隐藏目录，忽略。。。
                if (length <= 0) {
                    FilePickerLog.d("相册获取", "queryAlbums: 文件大小为0，忽略。。。。")
                    continue
                }

                val fileCheckTimeEnd = System.currentTimeMillis()
                FilePickerLog.e("相册获取", " 结束文件判断---fileCheckTimeEnd:${fileCheckTimeEnd}---耗时：${fileCheckTimeEnd - fileCheckTimeStart}")

                val uri = ContentUris.withAppendedId(
                    if (mimeType?.startsWith("video/", true) == true) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if (mimeType?.startsWith("image/", true) == true) {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if (mimeType?.startsWith("audio/", true) == true) {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri(VOLUME_NAME)
                    }, id
                )

//                if (width == 0 || height == 0) {
//                    val options = BitmapFactory.Options()
//                    options.inJustDecodeBounds = true
//                    BitmapFactory.decodeFile(data, options)
//                    width = options.outWidth
//                    height = options.outHeight
//                }

                val mediaEntity = MediaEntity(
                    uri = uri,
                    name = fileName ?: FilePickerFileUtils.getFileName(filePath = filePath),
                    path = filePath,
                    size = size,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    duration = duration,
                    orientation = orientation,
                    addTime = dateAdd,
                )

                FilePickerLog.d("相册获取", "queryAlbums: mediaEntity:$mediaEntity")
                mediaResult.addMediaEntity(
                    foldName ?: FilePickerFileUtils.getFolderName(filePath), FilePickerFileUtils.getFolderPath(filePath), mediaEntity
                )
                FilePickerLog.e("相册获取", " 结束循环（单次 到底共) while----endtimewhile:--耗时：${System.currentTimeMillis() - startTimeOneWhile}")
            }
            val endTimeWhile = System.currentTimeMillis()
            FilePickerLog.e("相册获取", " 结束循环（整体）while---endTimeWhile:${endTimeWhile}---耗时：${endTimeWhile - startTimeWhile}")
            cursor.close()
            FilePickerLog.d("相册获取", "queryAlbums: 最终结果-执行完毕---------folder:size：${mediaResult.mediaFolders.size}")
            return@withContext mediaResult
        }
    }


    /**
     * 仅仅有GIF类型，没有IMAGE类型
     */
    private fun isOnlyGifNotImage(queryTypes: MutableSet<QueryType>): Boolean {
        // 仅仅有GIF类型，没有IMAGE类型，数组不止一个，也可以有其他类型，但不能有IMAGE类型
        return queryTypes.contains(QueryType.GIF) && queryTypes.contains(QueryType.IMAGE).not()
    }

    private fun getMimeTypePrefix(queryType: QueryType): String {
        return when (queryType) {
            QueryType.IMAGE -> "image/"
            QueryType.GIF -> "image/gif"
            QueryType.VIDEO -> "video/"
            QueryType.AUDIO -> "audio/"
            else -> ""
        }
    }

}