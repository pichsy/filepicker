package com.pichs.filepicker.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pichs.filepicker.utils.FilePickerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统相机（拍照 / 录像）的接驳层：造输出 Uri、起相机、回来落定。
 *
 * ## 为什么输出走 MediaStore，而不是 FileProvider
 * 这是一个库 —— 加进 manifest 的任何东西都会**无条件合并进每一个宿主 App**。FileProvider
 * 要一条 `<provider>` 声明，还会和宿主自己常用的 `${applicationId}.fileprovider` authority
 * 撞车（同一个 authority 出现两次会直接编译失败）。MediaStore 的 insert 不需要任何
 * manifest 声明，所以整条链路对宿主是零侵入的：
 * - API 29+：`RELATIVE_PATH`，**不需要任何存储权限**，且**不能**带 `IS_PENDING`
 *   （原因见 [createOutput]，那会把相机 App 搞崩）；
 * - API 24–28：先建空文件、再 insert 带 `DATA` 的行，需要宿主已授予
 *   WRITE_EXTERNAL_STORAGE —— 与本库查询相册本身的前提一致。
 *
 * 另一个好处：产物从落盘那一刻起就在 MediaStore 里，选择器下次重扫（[com.pichs.filepicker.FilePickerViewModel.loadData]）
 * 时它还在，不会出现"列表里能看到、重扫后消失"的脏数据。
 *
 * 注意：这里**不声明 CAMERA 权限**。系统相机是由相机 App 自己完成拍摄的，调用方不需要该权限；
 * 但反过来，宿主 App 一旦**声明了** CAMERA 却未授权，`ACTION_IMAGE_CAPTURE` 会直接抛
 * SecurityException（官方 javadoc 明写），这一条由调用方（FilePickerFragment）先检查。
 */
internal object FilePickerCaptureHelper {

    private const val TAG = "FilePickerCapture"

    private const val MIME_IMAGE = "image/jpeg"
    private const val MIME_VIDEO = "video/mp4"

    /** 与系统相机同相册，拍完的照片会和系统相机拍的照片放在一起 */
    private val RELATIVE_PATH = Environment.DIRECTORY_DCIM + "/Camera"

    /**
     * 一次拍摄的落地信息。回执必须带着它回来，才能对上"这次"的输出，
     * 并在取消/失败时精确清理，而不是误删上一次的产物。
     *
     * @param preQFile API 24–28 建出来的空文件（API 29+ 为 null，走 MediaStore 记录即可）
     */
    data class PendingCapture(
        val outputUri: Uri,
        val isVideo: Boolean,
        val preQFile: File? = null,
    )

    /** 拍照的输出 Uri；宿主没有写外部存储权限、或 MediaStore 拒绝时为 null */
    fun createImageOutput(context: Context): PendingCapture? = createOutput(context, isVideo = false)

    /** 录像的输出 Uri；失败原因同 [createImageOutput] */
    fun createVideoOutput(context: Context): PendingCapture? = createOutput(context, isVideo = true)

    /** 组装交给系统相机的 Intent */
    fun buildCaptureIntent(capture: PendingCapture): Intent {
        return Intent(
            if (capture.isVideo) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE
        ).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, capture.outputUri)
            if (capture.isVideo) {
                // 0 = 低质量，1 = 高质量；默认给高质量，和系统相机自己的默认一致
                putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            }
            // 相机 App 需要写这个 Uri，个别 ROM 还需要先读一下
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    /**
     * 这次输出里到底有没有内容。
     *
     * **不能只看 MediaStore 的 SIZE**：我们建这条记录时文件还是 0 字节（pre-Q 先建空文件再 insert，
     * Q+ 的 `IS_PENDING` 期间元数据由延迟扫描回填），相机写完之后 SIZE 什么时候回填并不保证。
     * 只看 SIZE 会把"刚拍成功、SIZE 还没回填"的照片当成空产出，进而把它删掉 —— 这是最糟的失败模式。
     * 所以这里直接真读一个字节。
     *
     * 异常时返回 true（当作有内容）：读不到 ≠ 没有内容，宁可留下一条记录，也不删用户拍的东西。
     */
    fun hasContent(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
        } catch (e: Exception) {
            FilePickerLog.e("$TAG 读取输出内容失败: ${e.message}")
            true
        }
    }

    /**
     * 拍摄结束后的落定，返回最终应当使用的 Uri。
     *
     * **判定顺序很关键**：先看我们自己的输出有没有内容，只有它确实是空的时候才回退到回执里的
     * `data.data`。反过来的话会出事 —— 很多相机即使老老实实写了 `EXTRA_OUTPUT`，回执 `data` 里
     * 仍然塞着一个**缩略图** uri，那样子改用 `data.data` 就等于把全尺寸原图删掉、只留一张糊图。
     */
    fun finalizeCapture(context: Context, capture: PendingCapture, resultData: Intent?): Uri {
        if (hasContent(context, capture.outputUri)) {
            settle(context, capture)
            return capture.outputUri
        }

        val returned = resultData?.data
        if (returned != null && returned != capture.outputUri) {
            // 我们那条确实是空的（相机没理会 EXTRA_OUTPUT），才改用回执里的 uri，并清掉自己那条
            FilePickerLog.d("$TAG 相机未使用 EXTRA_OUTPUT，改用回执里的 uri：$returned")
            discard(context, capture)
            return returned
        }

        settle(context, capture)
        return capture.outputUri
    }

    /**
     * 取消 / 失败时清理这次的痕迹。
     *
     * 不做这一步的后果很直观：用户点了相机又返回，相册里就多出一条打不开的空记录，
     * 而且会被下次重扫收进选择器列表里。
     *
     * **但清理前必须先确认这次输出确实是空的**：部分 ROM / 三方相机会先写盘再回
     * `RESULT_CANCELED`，而 Q+ 上 `delete()` 会把文件本体一起删掉 —— 那就是把用户刚拍到的
     * 东西永久删了。有内容时只做落定（让它作为一张正常照片留在系统相册里），一个字都不删。
     */
    fun discard(context: Context, capture: PendingCapture) {
        if (hasContent(context, capture.outputUri)) {
            FilePickerLog.d("$TAG 输出已有内容，跳过清理，仅落定")
            settle(context, capture)
            return
        }

        try {
            context.contentResolver.delete(capture.outputUri, null, null)
        } catch (e: Exception) {
            FilePickerLog.e("$TAG 清理 MediaStore 记录失败: ${e.message}")
        }
        capture.preQFile?.let { file ->
            try {
                if (file.exists() && file.length() == 0L) {
                    file.delete()
                }
            } catch (e: Exception) {
                FilePickerLog.e("$TAG 清理空文件失败: ${e.message}")
            }
        }
    }

    /**
     * 最后一道兜底：个别老 ROM / 三方相机既不理 `EXTRA_OUTPUT`、也不回 `data.data`，
     * 只在回执里塞一张缩略图 Bitmap（官方文档提到的那个 `"data"` extra）。
     *
     * 这种情况下至少把它存下来 —— 拿到的是一张低分辨率图，但好过用户拍完什么都没有。
     * 落盘刻意仍走 MediaStore（和正常产物同一条路）：否则它不在系统媒体库里，
     * 选择器下一次重扫就会把这条记录冲掉。
     *
     * 只在正常路径确认拿不到内容之后才调用。拿不到 bitmap 时返回 null。
     */
    @Suppress("DEPRECATION")
    suspend fun saveThumbnailFallback(context: Context, resultData: Intent?): Uri? {
        val bitmap = resultData?.getParcelableExtra<Bitmap>("data") ?: return null
        return withContext(Dispatchers.IO) {
            val capture = createOutput(context, isVideo = false) ?: return@withContext null
            try {
                context.contentResolver.openOutputStream(capture.outputUri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                } ?: return@withContext null
                FilePickerLog.d("$TAG 使用回执里的缩略图 Bitmap 兜底：${capture.outputUri}")
                settle(context, capture)
                capture.outputUri
            } catch (e: Exception) {
                FilePickerLog.e("$TAG 缩略图兜底落盘失败: ${e.message}")
                discard(context, capture)
                null
            }
        }
    }

    /**
     * 落定。
     *
     * 29+ 无事可做 —— 我们刻意没设 `IS_PENDING`（见 [createOutput] 的注释），
     * 记录从 insert 那一刻起就是"已落定"状态。
     * pre-Q 需要主动让 MediaScanner 收编，否则相册和选择器都看不到它。
     */
    private fun settle(context: Context, capture: PendingCapture) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }
        try {
            capture.preQFile?.absolutePath?.let { path ->
                MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
            }
        } catch (e: Exception) {
            FilePickerLog.e("$TAG 落定失败: ${e.message}")
        }
    }

    // ------------------------------------------------------------------

    private fun createOutput(context: Context, isVideo: Boolean): PendingCapture? {
        val displayName = buildDisplayName(isVideo)
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val mimeType = if (isVideo) MIME_VIDEO else MIME_IMAGE

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
                    // ⚠ 刻意**不设** IS_PENDING —— 反直觉，但这是实测出来的硬结论，别加回来。
                    //
                    // AOSP 的 MediaProvider 对 pending 项有一道 owner 检查：IS_PENDING=1 的行
                    // **只有创建它的 uid 能操作**，URI 授权（FLAG_GRANT_WRITE_URI_PERMISSION）不顶用。
                    // 而我们的用法恰恰是"我建行、相机 App 来写"，相机是另一个 uid ——
                    // 于是相机在保存照片时会直接把**相机 App 自己**搞崩：
                    //
                    //   FATAL EXCEPTION: pool-3-thread-1
                    //   Process: com.huawei.camera
                    //   java.lang.IllegalStateException: Only owner is able to interact with pending item
                    //       content://media/external/images/media/883
                    //     at com.huawei.camera2.utils.ActivityUtil.writeDataToUri
                    //     at com.huawei.camera2.ui.element.ReviewPage.handlePhotoDataResult
                    //
                    // （华为 BRE-AL80 / Android 12 实测。报错文本来自 AOSP MediaProvider，
                    //  所以这是 10+ 的通用行为，不是华为特有。）
                    //
                    // 代价：拍摄期间这条记录对其它 App 可见（含 0 字节的空壳）。取消拍摄时
                    // discard() 会把它删掉，所以只存在"用户正在拍"的那几秒。
                }
                context.contentResolver.insert(collection, values)
                    ?.let { PendingCapture(it, isVideo) }
            } else {
                val file = createPreQFile(displayName) ?: return null
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    // pre-Q 必须先有真实文件，MediaProvider 才认这条记录
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                }
                val uri = context.contentResolver.insert(collection, values)
                if (uri == null) {
                    // insert 失败就把空文件收掉，不留垃圾
                    file.delete()
                    null
                } else {
                    PendingCapture(uri, isVideo, file)
                }
            }
        } catch (e: Exception) {
            // 宿主没给 WRITE_EXTERNAL_STORAGE（pre-Q）、或 MediaProvider 拒绝，都会走到这里
            FilePickerLog.e("$TAG 创建输出 Uri 失败: ${e.message}")
            null
        }
    }

    private fun createPreQFile(displayName: String): File? {
        return try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            if (!dir.exists() && !dir.mkdirs()) {
                FilePickerLog.e("$TAG 创建目录失败: ${dir.absolutePath}")
                return null
            }
            val file = File(dir, displayName)
            if (file.exists()) {
                // 名字撞车（同一秒内重复触发）：宁可这次不拍，也不覆盖已有文件
                FilePickerLog.e("$TAG 输出文件已存在: ${file.absolutePath}")
                return null
            }
            if (!file.createNewFile()) return null
            file
        } catch (e: Exception) {
            FilePickerLog.e("$TAG 创建输出文件失败: ${e.message}")
            null
        }
    }

    /** IMG_20261008_153012_137.jpg 这种，和系统相机的命名习惯接近 */
    private fun buildDisplayName(isVideo: Boolean): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val random = (100..999).random()
        val ext = if (isVideo) "mp4" else "jpg"
        val prefix = if (isVideo) "VID" else "IMG"
        return "${prefix}_${stamp}_$random.$ext"
    }
}
