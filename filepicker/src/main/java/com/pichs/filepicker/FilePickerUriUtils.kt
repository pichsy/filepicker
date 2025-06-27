package com.pichs.filepicker

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import android.util.Log
import androidx.annotation.StringDef
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

/**
 * create by pichs
 * UriUtils
 * 最强大的Uri工具类没有之一
 * 注意: {
 * 请注意你配置的FileProvider的必须是
 * android:authorities="${applicationId}.fileprovider"
 * 最好业界统一
 * }
 */
object FilePickerUriUtils {
    private val TAG = FilePickerUriUtils::class.java.simpleName
    fun getUriFromImage(context: Context, name: String?): Uri? {
        try {
            val file = File(URI(name))
            return getUri(context, UriType.IMAGE, file)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        return null
    }

    fun getUriFromImage(context: Context, name: String?, authority: String?): Uri? {
        try {
            val file = File(URI(name))
            return getUri(context, UriType.IMAGE, file, authority)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        return null
    }

    fun getUriFromFile(context: Context, file: File?): Uri? {
        return getUriFromFile(context, null, file)
    }

    /**
     * 获取File的Uri
     *
     * @param context 上下文
     * @param file    文件
     * @return Uri
     */
    fun getUriFromFile(context: Context, authority: String?, file: File?): Uri? {
        if (file == null) {
            return null
        }
        var authorityTmp = authority
        if (authorityTmp.isNullOrEmpty()) {
            authorityTmp = context.packageName + ".fileprovider"
        }
        try {
            return if (Build.VERSION.SDK_INT >= 24) {
                FileProvider.getUriForFile(context, authorityTmp, file)
            } else Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getUri(context: Context, @UriType fileType: String, file: File?): Uri? {
        return getUri(context, fileType, file, null)
    }

    /**
     * Get file uri
     */
    fun getUri(context: Context, @UriType fileType: String, file: File?, authority: String?): Uri? {
        if (file == null || !file.exists()) {
            return null
        }
        var fileTypeTmp = fileType
        var uri: Uri? = null
        if (Build.VERSION.SDK_INT < 24) {
            uri = Uri.fromFile(file)
        } else {
            if (TextUtils.isEmpty(fileTypeTmp)) {
                fileTypeTmp = UriType.FILE
            }
            if (UriType.IMAGE == fileTypeTmp) {
                uri = getImageContentUri(context, file)
            } else if (UriType.VIDEO == fileTypeTmp) {
                uri = getVideoContentUri(context, file)
            } else if (UriType.AUDIO == fileTypeTmp) {
                uri = getAudioContentUri(context, file)
            } else if (UriType.FILE == fileTypeTmp) {
                uri = getFileContentUri(context, file)
            }
        }
        if (uri == null) {
            uri = getUriFromFile(context, authority, file)
        }
        if (uri == null) {
            uri = forceGetFileUri(file)
        }
        return uri
    }

    /**
     * 判断是否是rootUri
     */
    private fun isRootUri(uri: Uri?): Boolean {
        if (uri == null) {
            return false
        }
        if (ContentResolver.SCHEME_CONTENT != uri.scheme) {
            return false
        }
        val paths = uri.pathSegments
        return paths != null && "root" == paths[0]
    }

    /**
     * 获取文件的真实路劲 ，终级版 （超多适配...）
     */
    fun getFileRealPath(context: Context?, uri: Uri?): String? {
        if (context == null) {
            Log.e(TAG, "getFileRealPath current activity is null.")
            return null
        }
        if (uri == null) {
            Log.e(TAG, "getFileRealPath uri is null.")
            return null
        }
        if (File(uri.toString()).exists()) {
            return uri.toString()
        }
        var realPath: String? = null
        if (Build.VERSION.SDK_INT >= 19) {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if ("com.android.externalstorage.documents" == uri.authority) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        realPath = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    } else if ("home".equals(type, ignoreCase = true)) {
                        realPath = Environment.getExternalStorageDirectory().toString() + "/documents/" + split[1]
                    }
                } else if ("com.android.providers.downloads.documents" == uri.authority) {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("raw:")) {
                        realPath = id.substring(4)
                    } else {
                        val contentUriPrefixesToTry = arrayOf(
                            "content://downloads/public_downloads", "content://downloads/my_downloads", "content://downloads/all_downloads"
                        )
                        for (download_uri in contentUriPrefixesToTry) {
                            try {
                                val contentUri = ContentUris.withAppendedId(Uri.parse(download_uri), java.lang.Long.valueOf(id))
                                realPath = getDataColumn(context, contentUri, null, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        if (TextUtils.isEmpty(realPath)) {
                            // path could not be retrieved using ContentResolver, therefore copy file to accessible cache using streams
                            val fileName = getFileName(context, uri, id)
                            val cacheDir = File(context.cacheDir, "documents")
                            if (!cacheDir.exists()) {
                                cacheDir.mkdirs()
                            }
                            val file = generateFileName(fileName, cacheDir)
                            if (file != null) {
                                realPath = file.absolutePath
                                saveFileFromUri(context, uri, realPath)
                            }
                        }
                    }
                } else if ("com.android.providers.media.documents" == uri.authority) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    val contentUri: Uri
                    contentUri = if ("image" == type) {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else if ("video" == type) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else if ("audio" == type) {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                    val selection = MediaStore.Files.FileColumns._ID + "=?"
                    val selectionArgs = arrayOf(split[1])
                    try {
                        realPath = getDataColumn(context, contentUri, selection, selectionArgs)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
                realPath = uri.path
            } else if (isRootUri(uri)) {
                val pathArr = uri.toString().split("root".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (pathArr.size > 1) {
                    realPath = pathArr[1]
                }
            } else if ("es.fileexplorer.filebrowser.ezfilemanager.externalstorage.documents" == uri.authority) {
                val path = uri.path
                if (path != null) {
                    val split = path.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (split.size > 1) {
                        realPath = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                }
            } else if ("com.tencent.mtt.fileprovider" == uri.authority) {
                val path = uri.path
                if (path != null) {
                    val split = path.split("QQBrowser".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (split.size > 1) {
                        realPath = Environment.getExternalStorageDirectory().toString() + split[1]
                    }
                }
            } else if ("com.google.android.apps.photos.content" == uri.authority) {
                return uri.lastPathSegment
            }
        }
        // 终极路径查询....
        if (TextUtils.isEmpty(realPath)) {
            realPath = queryRealPath(context, uri)
        }
        return realPath
    }

    /**
     * 查询真实路径，针对某些手机走系统的文件管理系统，所产生的uri
     */
    @SuppressLint("Range")
    private fun queryRealPath(context: Context, uri: Uri): String? {
        var filePath: String? = null
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
                val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Files.FileColumns.DATA), null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                    }
                    cursor.close()
                }
            } else if (ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true)) {
                filePath = uri.path
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return filePath
    }

    /**
     * forceGetFileUri
     */
    private fun forceGetFileUri(file: File): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                @SuppressLint("PrivateApi", "DiscouragedPrivateApi") val rMethod =
                    StrictMode::class.java.getDeclaredMethod("disableDeathOnFileUriExposure")
                rMethod.invoke(null)
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        return Uri.parse("file://" + file.absolutePath)
    }

    /**
     * getFileContentUri
     */
    private fun getFileContentUri(context: Context, file: File): Uri? {
        val volumeName = "external"
        val filePath = file.absolutePath
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        var uri: Uri? = null
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri(volumeName), projection, MediaStore.Images.Media.DATA + "=? ", arrayOf(filePath), null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range") val id = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                uri = MediaStore.Files.getContentUri(volumeName, id.toLong())
            }
            cursor.close()
        }
        return uri
    }

    /**
     * Gets the content:// URI from the given corresponding path to a file
     */
    private fun getImageContentUri(context: Context, imageFile: File): Uri? {
        val filePath = imageFile.absolutePath
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            MediaStore.Images.Media.DATA + "=? ",
            arrayOf(filePath),
            null
        )
        var uri: Uri? = null
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range") val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
                val baseUri = Uri.parse("content://media/external/images/media")
                uri = Uri.withAppendedPath(baseUri, "" + id)
            }
            cursor.close()
        }
        if (uri == null) {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DATA, filePath)
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
        return uri
    }

    /**
     * Gets the content:// URI from the given corresponding path to a file
     */
    private fun getVideoContentUri(context: Context, videoFile: File): Uri? {
        var uri: Uri? = null
        val filePath = videoFile.absolutePath
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media._ID),
            MediaStore.Video.Media.DATA + "=? ",
            arrayOf(filePath),
            null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range") val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
                val baseUri = Uri.parse("content://media/external/video/media")
                uri = Uri.withAppendedPath(baseUri, "" + id)
            }
            cursor.close()
        }
        if (uri == null) {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DATA, filePath)
            uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }
        return uri
    }

    /**
     * Gets the content:// URI from the given corresponding path to a file
     */
    private fun getAudioContentUri(context: Context, audioFile: File): Uri? {
        var uri: Uri? = null
        val filePath = audioFile.absolutePath
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            MediaStore.Audio.Media.DATA + "=? ",
            arrayOf(filePath),
            null
        )
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range") val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
                val baseUri = Uri.parse("content://media/external/audio/media")
                uri = Uri.withAppendedPath(baseUri, "" + id)
            }
            cursor.close()
        }
        if (uri == null) {
            val values = ContentValues()
            values.put(MediaStore.Audio.Media.DATA, filePath)
            uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
        }
        return uri
    }

    /**
     * 在数据库中查询路径
     */
    @SuppressLint("Range")
    @Throws(Exception::class)
    private fun getDataColumn(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        try {
            cursor = context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * 获取文件名字
     */
    private fun getFileName(context: Context, uri: Uri, id: String): String? {
        val mimeType = context.contentResolver.getType(uri)
        var filename: String? = null
        if (mimeType == null) {
            filename = id
        } else {
            val returnCursor = context.contentResolver.query(
                uri, null, null, null, null
            )
            if (returnCursor != null) {
                val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                returnCursor.moveToFirst()
                filename = returnCursor.getString(nameIndex)
                returnCursor.close()
            }
        }
        return filename
    }

    /**
     * 创建文件，名字有就增加 (1 ++)
     */
    fun generateFileName(name: String?, directory: File?): File? {
        var name = name ?: return null
        var file = File(directory, name)
        if (file.exists()) {
            var fileName = name
            var extension = ""
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                fileName = name.substring(0, dotIndex)
                extension = name.substring(dotIndex)
            }
            var index = 0
            while (file.exists()) {
                index++
                name = "$fileName($index)$extension"
                file = File(directory, name)
            }
        }
        try {
            if (!file.createNewFile()) {
                return null
            }
        } catch (e: IOException) {
            Log.w(TAG, e)
            return null
        }
        return file
    }

    /**
     * 拷贝文件到缓存中
     */
    private fun saveFileFromUri(context: Context, uri: Uri, destinationPath: String?) {
        var `is`: InputStream? = null
        var bos: BufferedOutputStream? = null
        try {
            `is` = context.contentResolver.openInputStream(uri)
            bos = BufferedOutputStream(FileOutputStream(destinationPath, false))
            val buf = ByteArray(1024)
            if (`is` != null) {
                `is`.read(buf)
                do {
                    bos.write(buf)
                } while (`is`.read(buf) != -1)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                `is`?.close()
                bos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 保存bitmap为图片 并返回Uri
     *
     * @param context  上下文
     * @param bitmap   Bitmap
     * @param fileName 文件名字
     * @return Uri
     */
    fun saveBitmapAndReturnUri(context: Context, bitmap: Bitmap?, fileName: String?): Uri? {
        if (null == bitmap) {
            return null
        }
        val fileDir = getAppExternalFileDir(context)
        if (!fileDir.exists()) {
            fileDir.mkdirs()
        }
        val destFile = File(fileDir, fileName)
        try {
            val bos = BufferedOutputStream(FileOutputStream(destFile))
            //压缩保存到本地
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos)
            bos.flush()
            bos.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
        return getUri(context, UriType.IMAGE, destFile)
    }

    /**
     * 获取app ExternalFileDir 路经
     *
     * @param context 上下文
     * @return File
     */
    private fun getAppExternalFileDir(context: Context?): File {
        var file: File? = null
        try {
            if (context != null) {
                file = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (file == null) {
            file = File(Environment.getExternalStorageDirectory().toString() + File.separator + "Pictures")
        }
        return file
    }

    @StringDef(value = [UriType.IMAGE, UriType.AUDIO, UriType.VIDEO, UriType.FILE])
    @Retention(AnnotationRetention.SOURCE)
    annotation class UriType {
        companion object {
            // Image
            const val IMAGE = "image/*"

            // Audio
            const val AUDIO = "audio/*"

            // Video
            const val VIDEO = "video/*"

            // File
            const val FILE = "*/*"
        }
    }
}