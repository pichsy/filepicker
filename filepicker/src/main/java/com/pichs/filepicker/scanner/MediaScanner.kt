package com.pichs.filepicker.scanner

import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.entity.MediaFolder
import java.io.File
import kotlin.concurrent.thread

/**
 * 相册、视频扫描工具，输出现成 MediaFolder 和 MediaEntity
 * 过时的api，忽略
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

                val projection = if (type == FilePickerSelectType.IMAGE_VIDEO
                    || type == FilePickerSelectType.IMAGE_VIDEO_GIF
                    || type == FilePickerSelectType.IMAGE
                    || type == FilePickerSelectType.VIDEO
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
                } else if (type == FilePickerSelectType.AUDIO) {
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

                val selection = when (type) {
                    FilePickerSelectType.IMAGE -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE} != 'image/gif')"
                    }

                    FilePickerSelectType.GIF -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE} == 'image/gif')"
                    }

                    FilePickerSelectType.VIDEO -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
                    }

                    FilePickerSelectType.AUDIO -> {
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
                    }

                    FilePickerSelectType.IMAGE_VIDEO -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND ((${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE}!= 'image/gif') OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
                    }

                    FilePickerSelectType.IMAGE_VIDEO_GIF -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND (${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
                    }

                    FilePickerSelectType.DOCUMENT -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND (" +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.TXT}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.docx' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.ppt' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pptx' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xlsx')"
                    }

                    FilePickerSelectType.PDF -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.PDF}'"
                    }

                    FilePickerSelectType.DOC -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.doc' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.docx')"
                    }

                    FilePickerSelectType.EXCEL -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xls' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xlsx')"
                    }

                    FilePickerSelectType.PPT -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND (${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.ppt' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pptx')"
                    }

                    FilePickerSelectType.TXT -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.TXT}'"
                    }

                    FilePickerSelectType.APK -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.APK}'"
                    }

                    FilePickerSelectType.ZIP -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.ZIP}'"
                    }

                    FilePickerSelectType.RAR -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.RAR}'"
                    }

                    FilePickerSelectType.SEVEN_Z -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.SEVEN_Z}'"
                    }

                    FilePickerSelectType.BZ2 -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.BZ2}'"
                    }

                    FilePickerSelectType.ISO -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.ISO}'"
                    }

                    FilePickerSelectType.GZ -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.GZ}'"
                    }

                    FilePickerSelectType.TAR -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.TAR}'"
                    }

                    FilePickerSelectType.ZIP_ALL -> {
                        val mediaTypeSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        } else {
                            "(${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE})"
                        }
                        "${MediaStore.MediaColumns.SIZE}>0 AND $mediaTypeSelection AND (" +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.ZIP}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.TAR}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.SEVEN_Z}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.BZ2}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.RAR}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.ISO}' OR " +
                                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.${FilePickerSelectType.GZ}')"
                    }

                    else -> {
                        "${MediaStore.MediaColumns.SIZE}>0 AND ((${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} AND ${MediaStore.Files.FileColumns.MIME_TYPE}!= 'image/gif') OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"
                    }
                }

                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                return CursorLoader(fragment.requireContext(), uri, projection, selection, null, sortOrder)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
                thread {
                    try {
                        val folderMap = mutableMapOf<String, MediaFolder>()
                        data?.let {
                            val idIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val mimeTypeIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val bucketNameIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val bucketIdIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val dateAddedIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val displayNameIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val dataPathIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val sizeIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            // 剩下的都按idIndex 的获取方式来。
                            val mediaTypeIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val widthIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val heightIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val orientationIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            val durationIndex = try {
                                it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                            } catch (e: IllegalArgumentException) {
                                -1
                            }

                            while (it.moveToNext()) {
                                if (dataPathIndex == -1 || mimeTypeIndex == -1) {
                                    continue // 如果没有找到数据路径列，则跳过此条记录
                                }

                                val filePath = it.getString(dataPathIndex)

                                if (!File(filePath).exists()) {
                                    continue // 跳过不存在的文件
                                }

                                val mimeType = it.getString(mimeTypeIndex)

                                val id = if (idIndex != -1) {
                                    it.getLong(idIndex)
                                } else {
                                    -1L
                                }

                                val bucketName = if (bucketNameIndex != -1) {
                                    it.getString(bucketNameIndex) ?: "未命名相册"
                                } else {
                                    "未命名相册"
                                }
                                val bucketId = if (bucketIdIndex != -1) {
                                    it.getString(bucketIdIndex) ?: bucketName
                                } else {
                                    bucketName
                                }
                                val dateAdded = if (dateAddedIndex != -1) {
                                    it.getLong(dateAddedIndex)
                                } else {
                                    0L
                                }
                                val displayName = if (displayNameIndex != -1) {
                                    it.getString(displayNameIndex)
                                } else {
                                    "未知文件"
                                }
                                val width = if (widthIndex != -1) {
                                    it.getInt(widthIndex)
                                } else {
                                    0
                                }
                                val height = if (heightIndex != -1) {
                                    it.getInt(heightIndex)
                                } else {
                                    0
                                }
                                val orientation = if (orientationIndex != -1) {
                                    it.getInt(orientationIndex)
                                } else {
                                    0
                                }
                                val size = if (sizeIndex != -1) {
                                    it.getLong(sizeIndex)
                                } else {
                                    0L
                                }
                                val duration = if (durationIndex != -1) {
                                    it.getLong(durationIndex)
                                } else {
                                    0L
                                }

                                val mediaType = if (mediaTypeIndex != -1) {
                                    it.getInt(mediaTypeIndex)
                                } else {
                                    MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
                                }

                                val contentUri = when (mediaType) {
                                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        id
                                    )

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

                                val folder = folderMap.getOrPut(bucketId) {
                                    MediaFolder(
                                        name = bucketName,
                                        folderPath = filePath?.substringBeforeLast("/"),
                                        mediaEntityList = arrayListOf()
                                    )
                                }
                                folder.mediaEntityList.add(mediaEntity)
                            }
                        }
                        callback.onCompleted(folderMap.values.toList())
                    } catch (e: Exception) {
                        Log.e("MediaScanner", "Error scanning media: ${e.message}", e)
                        callback.onCompleted(emptyList())
                    }
                }
            }

            override fun onLoaderReset(loader: Loader<Cursor>) {
            }
        })
    }

}
