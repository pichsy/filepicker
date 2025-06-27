package com.pichs.filepicker.query

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.pichs.filepicker.entity.MediaResult
import com.pichs.filepicker.entity.MediaFolder
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


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
        queryBuilder: (QueryWhere.Builder) -> Unit = {},
    ): MediaResult {
        return withContext(Dispatchers.IO) {

            Log.e("相册获取", " 开始查询---queryAlbums:type:${queryTypes.joinToString { it.type }}")
            val mediaResult = MediaResult()
            if (queryTypes.isEmpty()) {
                return@withContext mediaResult
            }


            val isNoneMedia = queryTypes.contains(QueryType.NONE)

            Log.d("相册获取", "queryAlbums: isNoneMedia:$isNoneMedia")
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

                Log.d("相册获取", "queryAlbums: isContainsGif:$isContainsGif")
                // 如果包含gif，那么就要查询图片
                if (isContainsGif) {
                    queryTypes.remove(QueryType.GIF)
                    queryTypes.add(QueryType.IMAGE)
                }
                // 去重，防止乱传参数
                Log.d("相册获取", "queryAlbums: queryTypeList:${queryTypes.joinToString { it.type }}")

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

            Log.d("相册获取", "queryAlbums: isOnlyVideo:$isOnlyVideo")
            Log.d("相册获取", "queryAlbums: isOnlyImage:$isOnlyImage")
            Log.d("相册获取", "queryAlbums: isOnlyAudio:$isOnlyAudio")

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

            Log.d("相册获取", "contentUri:$contentUri")
            Log.d("相册获取", "section:${qf.section}")
            Log.d("相册获取", "selectionAllArgs:${qf.sectionArgs?.joinToString(",")}")

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
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
                MediaStore.Files.FileColumns.BUCKET_ID,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.Files.FileColumns.ORIENTATION
            }

            val sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"
            if (contentUri == null) {
                return@withContext mediaResult
            }

            val startTime = System.currentTimeMillis()
            Log.e("相册获取", " 开始查询---startTime:${startTime}")
            val cursor = contentResolver.query(
                contentUri, projection, qf.section, qf.sectionArgs, sortOrder
            )

            val endTime = System.currentTimeMillis()
            Log.e("相册获取", " 结束查询---endTime:${endTime}---耗时：${endTime - startTime}")

            Log.d("相册获取", "queryAlbums: cursor对象拿到了吗 count:${cursor?.count}")
            if (cursor == null) {
                return@withContext mediaResult
            }

            val startTimeWhile = System.currentTimeMillis()
            Log.e("相册获取", " 开始循环（整体）while---startTimeWhile:${startTimeWhile}")
            while (cursor.moveToNext()) {

                val startTimeOneWhile = System.currentTimeMillis()
                Log.e("相册获取", " 开始循环（单次)while---startTimeWhile:${startTimeOneWhile}")
                val id = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                val data = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                val displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val size = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE))
                val mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE))
                var width = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH))
                var height = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT))
                val duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION))
                val dateModified = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED))
                val bucketId = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID))
                val bucketDisplayName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME))
                val orientation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION))
                } else {
                    0
                }

                val endTimeOneWhile = System.currentTimeMillis()
                Log.e("相册获取", " 开始循环（单次) while---endTimeWhile:${endTimeOneWhile}---耗时：${endTimeOneWhile - startTimeOneWhile}")

                // 获取缩略图位置
                Log.d("相册获取", "queryAlbums: id:$id")
                Log.d("相册获取", "queryAlbums: data:$data")
                Log.d("相册获取", "queryAlbums: displayName:$displayName")
                Log.d("相册获取", "queryAlbums: size:$size")
                Log.d("相册获取", "queryAlbums: mimeType:$mimeType")
                Log.d("相册获取", "queryAlbums: width:$width")
                Log.d("相册获取", "queryAlbums: height:$height")
                Log.d("相册获取", "queryAlbums: duration:$duration")
                Log.d("相册获取", "queryAlbums: dateModified:$dateModified")
                Log.d("相册获取", "queryAlbums: bucketDisplayName:$bucketDisplayName")
                Log.d("相册获取", "queryAlbums: bucketId:$bucketId")
                Log.d("相册获取", "queryAlbums: orientation:$orientation")

                val fileCheckTimeStart = System.currentTimeMillis()
                Log.e("相册获取", " 开始文件判断---fileCheckTimeStart 耗时:${fileCheckTimeStart - endTimeOneWhile}")
                val file = File(data)
                // 文件有毛病,忽略。。。。
                val isExists = file.exists()
                val isFile = file.isFile
                val length = file.length()
                val isInHiddenDir = isFileInHiddenDir(data)

                val fileCheckTimeEnd = System.currentTimeMillis()
                Log.e("相册获取", " 结束文件判断---fileCheckTimeEnd:${fileCheckTimeEnd}---耗时：${fileCheckTimeEnd - fileCheckTimeStart}")

                Log.d("相册获取", "文件判断: isExists:$isExists")
                Log.d("相册获取", "文件判断: isFile:$isFile")
                Log.d("相册获取", "文件判断: length:$length")
                Log.d("相册获取", "文件判断: isInHiddenDir:$isInHiddenDir")

                if (!isExists || !isFile || length == 0L || isInHiddenDir) {
                    Log.d("相册获取", "queryAlbums: 文件有毛病-或者-无权读取-在隐藏目录,忽略。。。。")
                    continue
                }
                val fileCheckTimeEnd2 = System.currentTimeMillis()
                Log.e("相册获取", " 结束循环（单次) 文件判断没有中断---耗时：${fileCheckTimeEnd2 - fileCheckTimeEnd}")

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

                val withAppendedIdTimeEnd = System.currentTimeMillis()
                Log.e("相册获取", " 结束循环（单次) withAppendedId---耗时：${withAppendedIdTimeEnd - fileCheckTimeEnd2}")

                var bitmapEndTime = withAppendedIdTimeEnd
                if (width == 0 || height == 0) {
                    val options = BitmapFactory.Options()
                    options.inJustDecodeBounds = true
                    BitmapFactory.decodeFile(data, options)
                    width = options.outWidth
                    height = options.outHeight
                    bitmapEndTime = System.currentTimeMillis()
                    Log.e("相册获取", " 结束循环（单次) 图片处理了---耗时：${bitmapEndTime - withAppendedIdTimeEnd}")
                }

                val mediaEntity = MediaEntity(
                    uri = uri,
                    name = displayName ?: bucketDisplayName,
                    path = data,
                    size = size,
                    mimeType = mimeType,
                    width = width,
                    height = height,
                    duration = duration,
                    orientation = orientation,
                    time = dateModified,
                )
                Log.d("相册获取", "queryAlbums: mediaEntity:$mediaEntity")
                mediaResult.addMediaEntity(
                    MediaFolder(
                        name = bucketDisplayName,
                        folderPath = getFolderPath(data),
                    ), mediaEntity
                )
                Log.e("相册获取", " 结束循环（单次 到底共) while----endtimewhile:--耗时：${System.currentTimeMillis() - startTimeOneWhile}")
            }
            val endTimeWhile = System.currentTimeMillis()
            Log.e("相册获取", " 结束循环（整体）while---endTimeWhile:${endTimeWhile}---耗时：${endTimeWhile - startTimeWhile}")
            cursor.close()
            Log.d("相册获取", "queryAlbums: 最终结果-执行完毕---------folder:size：${mediaResult.mediaFolders.size}")
            return@withContext mediaResult
        }

    }


    private fun isFileInHiddenDir(path: String): Boolean {
        // 判断路径中是否有某个路径以.开头的，如果有，那么就是隐藏目录
        return path.contains("/.")
    }

    /**
     * 仅仅有GIF类型，没有IMAGE类型
     */
    private fun isOnlyGifNotImage(queryTypes: MutableSet<QueryType>): Boolean {
        // 仅仅有GIF类型，没有IMAGE类型，数组不止一个，也可以有其他类型，但不能有IMAGE类型
        return queryTypes.contains(QueryType.GIF) && queryTypes.contains(QueryType.IMAGE).not()
    }

    /**
     * 获取文件夹路径
     */
    private fun getFolderPath(filePath: String): String {
        if (filePath.isEmpty() || filePath.isBlank()) {
            return ""
        }
        return filePath.substring(0, filePath.lastIndexOf("/"))
    }

    private fun getMediaType(queryType: QueryType): Int {
        return when (queryType) {
            QueryType.IMAGE -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            QueryType.GIF -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            QueryType.VIDEO -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            QueryType.AUDIO -> MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
            else -> MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
        }
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