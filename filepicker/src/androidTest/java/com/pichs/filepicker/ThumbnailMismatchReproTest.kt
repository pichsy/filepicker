package com.pichs.filepicker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.loader.MediaLoader
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 在真机上**造出**"缩略图是 A、点开是 B"的条件，验证修复前后。
 *
 * 造条件的办法（对应真实世界的成因）：
 * 1. 往 MediaStore 插一张纯红图片 A（拿到 uri + 同一份文件的绝对路径）；
 * 2. 用**旧写法**（`load(uri).override(200,200).dontTransform().diskCacheStrategy(AUTOMATIC)`，
 *    与修复前的 `MediaLoader.loadImageThumbnail(uri, ...)` 完全一致）加载一次，
 *    让 Glide 把 200x200 的位图写进它按【uri + 尺寸 + 无签名】键控的缓存；
 * 3. 直接把该文件内容覆盖成纯蓝图片 B —— uri 不变、路径不变（现实中就是同一个
 *    MediaStore 行背后的文件被替换/覆盖，或 _ID 被复用）；
 * 4. 清掉 Glide 内存缓存，再加载一次：旧写法命中了旧缓存 → 仍然是红；
 *    而大图预览因为尺寸不同、缓存键不同 → 读文件 → 是蓝。**错位复现**。
 *
 * 加载出来的位图都会落盘到 `getExternalFilesDir`，方便 `adb pull` 出来用眼睛核对。
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailMismatchReproTest {

    companion object {
        private const val RED = 0xFFFF0000.toInt()
        private const val BLUE = 0xFF0000FF.toInt()
        private const val GREEN = 0xFF00FF00.toInt()

        /** JPEG 有损 + 缩放，取色允许的通道误差 */
        private const val TOL = 60
        private const val THUMB = 200
    }

    private lateinit var context: Context
    private var uri: Uri? = null
    private var filePath: String? = null
    private var dateModified = 0L

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearGlideCaches()
    }

    @After
    fun tearDown() {
        uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        clearGlideCaches()
    }

    // ------------------------------------------------------------------
    // 核心用例
    // ------------------------------------------------------------------

    /** 复现：旧写法下网格缩略图 = 旧图(红)，大图预览 = 新图(蓝)，两者不一致 */
    @Test
    fun oldShape_reproduces_mismatch_betweenThumbnailAndOriginal() {
        createImage(RED)
        printDeviceThumbnailTableDiag()

        assertNear(RED, centerColor(loadOldShapeThumb()), "第一次缩略图应为红")

        overwriteFileWith(BLUE)
        // 只清内存缓存，逼第二次走磁盘缓存
        onMain { Glide.get(context).clearMemory() }

        val thumbAfter = loadOldShapeThumb()
        val previewAfter = loadFullSize()
        save(thumbAfter, "1_旧写法_网格缩略图(200x200).png")
        save(previewAfter, "2_旧写法_大图预览(整屏).png")

        println(
            "[REPRO] 同一 uri 背后文件已变成蓝色：旧写法的网格缩略图=${describe(centerColor(thumbAfter))}，" +
                    "大图预览=${describe(centerColor(previewAfter))}"
        )
        assertNear(RED, centerColor(thumbAfter), "旧写法：缩略图命中按 uri 键控的旧缓存，仍是旧图(红)")
        assertNear(BLUE, centerColor(previewAfter), "旧写法：大图尺寸不同→缓存键不同→读文件→新图(蓝)")
    }

    /** 修复：MediaLoader.loadThumbnail(entity, view) 必须显示当前文件内容，而不是旧缓存 */
    @Test
    fun fixed_mediaLoader_showsCurrentFileContent_notStaleCache() {
        createImage(RED)
        // 先用旧写法把"按 uri 键控的 200x200 红图"塞进磁盘缓存，制造脏缓存
        assertNear(RED, centerColor(loadOldShapeThumb()), "预置旧缓存失败")
        overwriteFileWith(BLUE)
        onMain { Glide.get(context).clearMemory() }

        val entity = MediaEntity(
            uri = uri,
            path = filePath,
            mimeType = "image/jpeg",
            dateModified = dateModified,
        )
        val actual = loadViaMediaLoader(entity)
        save(actual, "3_修复后_MediaLoader缩略图.png")
        println("[FIX] MediaLoader.loadThumbnail(entity) 显示=${describe(centerColor(actual))}（文件当前是蓝色）")
        assertNear(BLUE, centerColor(actual), "修复后缩略图必须显示当前文件内容(蓝)，而不是旧缓存(红)")
    }

    /** 机制说明 + 固有边界：签名是缓存失效的唯一依据，dateModified 不变时缓存键不变 */
    @Test
    fun signature_isCacheKey_soItOnlyInvalidatesWhenDateModifiedChanges() {
        createImage(RED)
        val entityV1 = MediaEntity(
            uri = uri,
            path = filePath,
            mimeType = "image/jpeg",
            dateModified = dateModified,
        )
        // 1) 新写法第一次：红，并以 signature(dateModified) 写入磁盘缓存
        assertNear(RED, centerColor(loadViaMediaLoader(entityV1)), "首次应为红")

        // 2) 文件覆盖成蓝，但 dateModified 不变 → 缓存键不变 → 仍然命中旧图（固有边界，如实断言）
        overwriteFileWith(BLUE)
        onMain { Glide.get(context).clearMemory() }
        assertNear(
            RED, centerColor(loadViaMediaLoader(entityV1)),
            "签名不变 → 缓存键不变 → 仍命中旧图（这是缓存键方案的固有边界）"
        )

        // 3) dateModified 变化（真实世界里文件被编辑/替换后 MediaStore 会更新它）→ 缓存失效 → 新图
        val entityV2 = MediaEntity(
            uri = uri,
            path = filePath,
            mimeType = "image/jpeg",
            dateModified = dateModified + 2000L,
        )
        val after = loadViaMediaLoader(entityV2)
        save(after, "4_dateModified变化后_MediaLoader缩略图.png")
        println("[BOUNDARY] dateModified 变化后显示=${describe(centerColor(after))}")
        assertNear(BLUE, centerColor(after), "dateModified 变化后必须失效并显示新图(蓝)")
    }

    /** 环境诊断：本机传统 thumbnails 表是否可用（决定错位是走系统缩略图表还是走 Glide 磁盘缓存） */
    private fun printDeviceThumbnailTableDiag() {
        val id = uri?.lastPathSegment
        val thumbUri = Uri.parse("content://media/external/images/thumbnails/$id")
        val readable = runCatching {
            context.contentResolver.openInputStream(thumbUri)?.use { true } ?: false
        }.getOrElse { false }
        val rows = runCatching {
            context.contentResolver.query(thumbUri, arrayOf("_id"), null, null, null)
                ?.use { it.count } ?: -1
        }.getOrElse { -1 }
        println("[ENV] sdk=${Build.VERSION.SDK_INT} legacy thumbnails uri 可读=$readable 行数=$rows")
    }

    // ------------------------------------------------------------------
    // 造条件
    // ------------------------------------------------------------------

    private fun createImage(color: Int): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fp_mismatch_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("MediaStore 插入失败，无法造出测试条件", inserted)
        resolver.openOutputStream(inserted!!)!!.use { writeSolidJpeg(it, color) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                inserted,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null, null,
            )
        }

        val path = resolver.query(inserted, arrayOf(MediaStore.Images.Media.DATA), null, null, null)!!.use {
            assertTrue("查不到 _data", it.moveToFirst())
            it.getString(0)
        }
        val modified = resolver.query(
            inserted, arrayOf(MediaStore.Images.Media.DATE_MODIFIED), null, null, null
        )!!.use {
            assertTrue(it.moveToFirst())
            it.getLong(0) * 1000L
        }

        uri = inserted
        filePath = path
        dateModified = modified
        println("[ENV] 造图完成 uri=$inserted path=$path dateModified=$modified 存在=${File(path).exists()}")
        return inserted
    }

    private fun overwriteFileWith(color: Int) {
        val f = File(filePath!!)
        FileOutputStream(f).use { writeSolidJpeg(it, color) }
        println("[ENV] 已把 ${f.name} 的内容覆盖为 ${describe(color)}（uri 未变，未重新扫描）")
    }

    private fun writeSolidJpeg(out: OutputStream, color: Int) {
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        bmp.recycle()
    }

    // ------------------------------------------------------------------
    // 两种加载写法
    // ------------------------------------------------------------------

    /** 与修复前 `MediaLoader.loadImageThumbnail(uri, view)` 完全一致的写法（无签名） */
    private fun loadOldShapeThumb(): Bitmap =
        Glide.with(context).asBitmap()
            .load(uri!!)
            .override(THUMB, THUMB)
            .dontTransform()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .submit()
            .get(20, TimeUnit.SECONDS)

    /** 大图预览的写法：不加 override（整屏/原始尺寸），缓存键与缩略图不同 */
    private fun loadFullSize(): Bitmap =
        Glide.with(context).asBitmap()
            .load(uri!!)
            .dontTransform()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .submit()
            .get(20, TimeUnit.SECONDS)

    /** 走真实生产代码：MediaLoader.loadThumbnail(entity, imageView) */
    private fun loadViaMediaLoader(entity: MediaEntity): Bitmap {
        val iv = onMain { ImageView(context) }
        onMain { MediaLoader.loadThumbnail(entity, iv) }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val bitmap = onMain {
                (iv.drawable as? BitmapDrawable)?.bitmap?.let {
                    if (!it.isRecycled) it.copy(Bitmap.Config.ARGB_8888, false) else null
                }
            }
            // placeholder 不是这三种纯色，所以匹配到纯色即认为真实图已就绪
            if (bitmap != null) {
                val c = centerColor(bitmap)
                if (near(c, RED) || near(c, BLUE) || near(c, GREEN)) return bitmap
            }
            Thread.sleep(50)
        }
        throw AssertionError("MediaLoader.loadThumbnail 10s 内没有出图（drawable=${onMain { iv.drawable }}）")
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 把加载出来的位图落盘，便于 adb pull 出来人工核对 */
    private fun save(bitmap: Bitmap, name: String) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val f = File(dir, name)
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("[DUMP] ${f.absolutePath}")
    }

    private fun <T> onMain(block: () -> T): T {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun clearGlideCaches() {
        onMain { Glide.get(context).clearMemory() }
        // clearDiskCache 必须在后台线程调用
        Glide.get(context).clearDiskCache()
        Thread.sleep(800)
    }

    private fun centerColor(bitmap: Bitmap): Int = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)

    private fun near(a: Int, b: Int): Boolean = abs(red(a) - red(b)) <= TOL &&
            abs(green(a) - green(b)) <= TOL &&
            abs(blue(a) - blue(b)) <= TOL

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    private fun describe(c: Int) = when {
        near(c, RED) -> "红"
        near(c, BLUE) -> "蓝"
        near(c, GREEN) -> "绿"
        else -> "其他(#${Integer.toHexString(c)})"
    }

    private fun assertNear(expected: Int, actual: Int, message: String) {
        assertTrue("$message —— 期望 ${describe(expected)}，实际 ${describe(actual)}", near(expected, actual))
    }
}
