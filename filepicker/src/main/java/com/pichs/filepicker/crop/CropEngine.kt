package com.pichs.filepicker.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.pichs.filepicker.utils.FilePickerLog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 裁剪的编解码引擎。
 *
 * 只做两件事：
 * 1. 给裁剪页提供**屏幕级**的显示位图（按 maxSize 采样 + 按 EXIF 摆正方向）；
 * 2. 按裁剪框把结果导出成原图级的临时文件（不写相册、不覆盖原图）。
 *
 * 内存约定：显示解码永远采样到屏幕级；导出解码最多采样到 `outputMaxSize * 2`，
 * 大图（4000×3000 ≈ 48MB）不会整张进内存。
 */
object CropEngine {

    private const val TAG = "FilePickerCrop"

    /** 默认缓存目录名：Android/data/<包名>/cache/filepicker（内部缓存时是 cache/filepicker） */
    private const val CACHE_DIR = "filepicker"

    /** 默认编辑缓存根目录：外部缓存优先，取不到时退回内部缓存 */
    fun defaultCacheRoot(context: Context): File {
        val base = context.externalCacheDir ?: context.cacheDir
        return File(base, CACHE_DIR)
    }

    /** 实际使用的编辑缓存目录（接入方可配置，未配置用默认） */
    fun cacheRoot(context: Context): File = FilePickerEditCache.dir(context)

    /** 编辑产物目录（就是缓存根目录，文件名带时间戳） */
    fun editDir(context: Context): File = cacheRoot(context)

    /**
     * 清理编辑缓存（接入方可自行调用）。
     *
     * 清理时机由宿主决定：我们只在选择器启动时做"超过 24 小时"的兜底清理，
     * 不会在返回结果后删除 —— 宿主可能还需要把文件另存到自己的位置。
     */
    fun clearCache(context: Context): Boolean {
        return try {
            cacheRoot(context).deleteRecursively()
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "清理编辑缓存失败: ${e.message}")
            false
        }
    }

    /** 当前编辑缓存大小（字节） */
    fun cacheSize(context: Context): Long {
        return try {
            cacheRoot(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Throwable) {
            0L
        }
    }

    /** 兜底清理：删掉超过 [maxAgeMillis] 的残留文件（防崩溃/被杀进程遗留） */
    fun pruneCache(context: Context, maxAgeMillis: Long = 24L * 60 * 60 * 1000) {
        try {
            val dir = cacheRoot(context)
            if (!dir.exists()) return
            val deadline = System.currentTimeMillis() - maxAgeMillis
            dir.walkTopDown().filter { it.isFile && it.lastModified() < deadline }.forEach { it.delete() }
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "清理过期编辑缓存失败: ${e.message}")
        }
    }

    /**
     * 解码用于显示的位图（已经按 EXIF 方向摆正）。
     *
     * @param maxSize 最长边上限，一般传屏幕长边
     */
    fun openDisplayBitmap(context: Context, uri: Uri?, path: String?, maxSize: Int): Bitmap? {
        val bounds = decodeBounds(context, uri, path) ?: return null
        val sample = calculateInSampleSize(bounds.first, bounds.second, maxSize)
        val raw = decodeBitmap(context, uri, path, sample) ?: return null
        return applyExifOrientation(context, uri, path, raw)
    }

    /**
     * 按裁剪框导出文件。
     *
     * @param ratio 裁剪框在图片中的归一化位置（0~1，坐标基于「已按 EXIF 摆正 + 用户旋转后」的图片）
     * @param transform 用户旋转/镜像的累计变换（作用在 EXIF 摆正后的原图上，再裁剪）
     * @param outputMaxSize 输出最长边上限；<=0 表示不缩放（仍受 decodeMax 限制）
     * @return 生成的临时文件；失败返回 null
     */
    fun export(
        context: Context,
        uri: Uri?,
        path: String?,
        ratio: CropGeometry.RectF2,
        transform: android.graphics.Matrix?,
        outputMaxSize: Int,
        quality: Int,
    ): File? {
        return try {
            val decodeMax = if (outputMaxSize > 0) {
                (outputMaxSize * 2).coerceIn(1024, 4096)
            } else {
                4096
            }
            var bitmap = openDisplayBitmap(context, uri, path, decodeMax) ?: return null
            if (transform != null && !transform.isIdentity) {
                bitmap = applyTransform(bitmap, transform) ?: return null
            }
            val cropped = cropBitmap(bitmap, ratio) ?: return null
            val output = scaleDown(cropped, outputMaxSize)
            val dir = editDir(context).apply { mkdirs() }
            val file = File(dir, "edit_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), out)
            }
            FilePickerLog.d(
                TAG,
                "导出裁剪图: ${file.absolutePath} ${output.width}x${output.height} " +
                    "ratio=[${ratio.left}, ${ratio.top}, ${ratio.right}, ${ratio.bottom}] " +
                    "transform=${if (transform == null || transform.isIdentity) "identity" else "custom"}"
            )
            file
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "导出裁剪图失败: ${e.message}")
            null
        }
    }

    /** 删除临时文件（取消编辑时用） */
    fun deleteQuietly(file: File?) {
        try {
            if (file != null && file.exists()) file.delete()
        } catch (ignored: Throwable) {
        }
    }

    // ------------------------------------------------------------------
    // 解码
    // ------------------------------------------------------------------

    private fun decodeBounds(context: Context, uri: Uri?, path: String?): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri, path)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return options.outWidth to options.outHeight
    }

    private fun decodeBitmap(context: Context, uri: Uri?, path: String?, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return try {
            openStream(context, uri, path)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "解码失败: ${e.message}")
            null
        }
    }

    private fun openStream(context: Context, uri: Uri?, path: String?): InputStream? {
        if (!path.isNullOrEmpty()) {
            try {
                return File(path).inputStream()
            } catch (ignored: Throwable) {
            }
        }
        if (uri != null) {
            try {
                return context.contentResolver.openInputStream(uri)
            } catch (ignored: Throwable) {
            }
        }
        return null
    }

    private fun readOrientation(context: Context, uri: Uri?, path: String?): Int {
        return try {
            openStream(context, uri, path)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri?, path: String?, bitmap: Bitmap): Bitmap {
        val orientation = readOrientation(context, uri, path)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (result != bitmap) bitmap.recycle()
            result
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "按 EXIF 摆正失败: ${e.message}")
            bitmap
        }
    }

    // ------------------------------------------------------------------
    // 变换 / 输出
    // ------------------------------------------------------------------

    private fun applyTransform(bitmap: Bitmap, transform: android.graphics.Matrix): Bitmap? {
        return try {
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
            if (result != bitmap) bitmap.recycle()
            result
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "应用旋转/镜像失败: ${e.message}")
            null
        }
    }

    private fun cropBitmap(bitmap: Bitmap, ratio: CropGeometry.RectF2): Bitmap? {
        val w = bitmap.width
        val h = bitmap.height
        var left = (ratio.left * w).toInt()
        var top = (ratio.top * h).toInt()
        var right = (ratio.right * w).toInt()
        var bottom = (ratio.bottom * h).toInt()
        left = left.coerceIn(0, w - 1)
        top = top.coerceIn(0, h - 1)
        right = right.coerceIn(left + 1, w)
        bottom = bottom.coerceIn(top + 1, h)
        if (right - left <= 0 || bottom - top <= 0) return null
        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Throwable) {
            FilePickerLog.e(TAG, "裁剪失败: ${e.message}")
            null
        }
    }

    private fun scaleDown(bitmap: Bitmap, outputMaxSize: Int): Bitmap {
        if (outputMaxSize <= 0) return bitmap
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= outputMaxSize) return bitmap
        val scale = outputMaxSize.toFloat() / maxSide
        return try {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } catch (e: Throwable) {
            bitmap
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        if (maxSize <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) > maxSize) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}
