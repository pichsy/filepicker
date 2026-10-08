package com.pichs.filepicker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 诊断探针（**不是**回归测试，失败不代表产品有问题）。
 *
 * 回答一个关于"性能不能变慢"的问题：`MediaLoader.loadImageThumbnail` 里那个"首帧占位"请求
 * （`load(uri).override(200,200)...skipMemoryCache(true).diskCacheStrategy(NONE)`）
 * 在本机究竟付出了什么代价、以及这个代价会不会在重复绑定中不断重复。
 *
 * 为什么可疑：
 * - `ThumbFetcher.build()` 从不返回 null，因此 [MediaStoreImageThumbLoader]（registry 中排在
 *   `UriLoader` 之前，见 Glide `RegistryFactory` 第 331 行 vs 第 342 行）一定会抢到
 *   media store 图片 uri；
 * - 而本机（SDK 31）实测 `legacy thumbnails uri 可读=false 行数=0`，
 *   `ThumbnailStreamOpener.open()` 返回 null，缩略图取不到；
 * - `MultiModelLoader.buildLoadData` 会把该 uri 下所有 handle 的 loader（含 `UriLoader`）
 *   收进同一个 `MultiFetcher` 依次尝试 → 于是回退去**读并解码原文件**。
 * - 偏偏这个请求带 `skipMemoryCache(true)` + `DiskCacheStrategy.NONE`，结果不缓存。
 *
 * 探针做两件事：
 * 1. 报出该请求的真实来源/耗时/解码尺寸（证明它读的是原文件还是系统缩略图）；
 * 2. 连续绑定 3 次（第 2 次前只清内存缓存），统计"缩略图子请求"实际执行了几次 ——
 *    用来判断这笔解码开销是"只在首屏付一次"还是"每次冷绑定都付"。
 */
@RunWith(AndroidJUnit4::class)
class FastThumbnailPathProbeTest {

    private companion object {
        /** 造一张够大的图，让"多解码一次原图"在耗时上看得出来 */
        const val BIG = 2400
        const val GREEN = 0xFF00FF00.toInt()
    }

    private lateinit var context: Context
    private var uri: Uri? = null
    private var filePath: String? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearGlideCaches()
        createBigImage()
    }

    @After
    fun tearDown() {
        uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        clearGlideCaches()
    }

    @Test
    fun fastThumbnailRequest_costAndRepeatCount() {
        // ---- 第 1 部分：这个请求到底是什么 ----
        val probe = submit(full = false, thumb = false)
        println(
            "[PROBE] 单个缩略图形态请求: 成功=${probe.succeeded} 耗时=${probe.ms}ms " +
                    "来源=${probe.source} 颜色=${probe.centerHex} 尺寸=${probe.size} 异常=${probe.error}"
        )

        // ---- 第 2 部分：重复绑定 3 次，数缩略图子请求执行了几次 ----
        val stats = Stats()
        clearGlideCaches()
        val t1 = submit(full = true, thumb = true, stats = stats)   // 冷
        clearMemoryOnly()
        val t2 = submit(full = true, thumb = true, stats = stats)   // 只清内存
        val t3 = submit(full = true, thumb = true, stats = stats)   // 全热

        println(
            "[PROBE] 3 次连续绑定耗时: 冷=${t1.ms}ms(${t1.source}) " +
                    "清内存后=${t2.ms}ms(${t2.source}) 全热=${t3.ms}ms(${t3.source})"
        )
        println(
            "[PROBE] 3 次连续绑定: 缩略图子请求执行 ${stats.thumbRuns} 次" +
                    "（成功 ${stats.thumbOk}, 失败 ${stats.thumbFailed}），" +
                    "主请求执行 ${stats.fullRuns} 次（内存命中 ${stats.fullMemory}）"
        )

        if (probe.centerHex == "#FF00FF00") {
            println(
                "[PROBE] 结论：该请求取到的是**文件内容**而非系统缩略图 → 系统 thumbnails 表为空，\n" +
                        "         MultiFetcher 已回退到 UriLoader 读原文件并降采样。\n" +
                        "         即 MediaLoader 注释里『首帧复用系统预生成缩略图（几十 KB 的小文件）』\n" +
                        "         在本机不成立：首帧是另一次原图解码，且因 skipMemoryCache+NONE 不进缓存。"
            )
        } else {
            println("[PROBE] 结论：该请求没有取到文件内容（颜色=${probe.centerHex}），需人工判读")
        }

        // ---- 第 3 部分：走真实生产代码 MediaLoader.loadThumbnail，排除"复刻失真" ----
        val entity = com.pichs.filepicker.entity.MediaEntity(
            uri = uri,
            path = filePath,
            mimeType = "image/jpeg",
        )
        clearGlideCaches()
        val real1 = timeRealMediaLoader(entity)
        clearMemoryOnly()
        val real2 = timeRealMediaLoader(entity)
        println("[PROBE] 真实 MediaLoader.loadThumbnail: 冷=${real1}ms 清内存后=${real2}ms")

        println(
            if (stats.thumbRuns > 1) {
                "[PROBE] 重复性：缩略图子请求被执行了多次 → 这笔原图解码在每次冷绑定都会重复付出。"
            } else {
                "[PROBE] 重复性：缩略图子请求只执行了 1 次 → 开销只在真正冷启动时付一次，可接受。"
            }
        )

        assertTrue("探针仅用于取证，这里只做一次恒真断言", stats.fullRuns > 0)
    }

    // ------------------------------------------------------------------

    private class Stats {
        var thumbRuns = 0
        var thumbOk = 0
        var thumbFailed = 0
        var fullRuns = 0
        var fullMemory = 0
    }

    private class Outcome {
        var succeeded = false
        var ms = 0L
        var source: DataSource? = null
        var centerHex: String = "-"
        var width = 0
        var height = 0
        var error: String = "-"
        val size: String get() = "${width}x$height"
    }

    /**
     * @param full 是否提交"主请求"（File + AUTOMATIC，对应 MediaLoader 的权威图请求）
     * @param thumb 是否挂上"缩略图子请求"（uri + NONE + skipMemoryCache，对应 fastThumbnail）
     */
    private fun submit(full: Boolean, thumb: Boolean, stats: Stats? = null): Outcome {
        val out = Outcome()
        val latch = CountDownLatch(1)
        val start = System.currentTimeMillis()

        val thumbBuilder = Glide.with(context)
            .asBitmap()
            .load(uri!!)
            .override(200, 200)
            .dontTransform()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .addListener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean
                ): Boolean {
                    stats?.let { it.thumbRuns++; it.thumbFailed++ }
                    return false
                }

                override fun onResourceReady(
                    resource: Bitmap, model: Any, target: Target<Bitmap>,
                    dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    stats?.let { it.thumbRuns++; it.thumbOk++ }
                    return false
                }
            })

        onMain {
            val primary = Glide.with(context)
                .asBitmap()
                .load(if (full) File(filePath!!) else uri!!)
                .override(200, 200)
                .dontTransform()
                .dontAnimate()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .addListener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(
                        e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean
                    ): Boolean {
                        out.succeeded = false
                        out.error = e?.message?.take(140) ?: "null"
                        out.ms = System.currentTimeMillis() - start
                        latch.countDown()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap, model: Any, target: Target<Bitmap>,
                        dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean {
                        out.succeeded = true
                        out.source = dataSource
                        out.width = resource.width
                        out.height = resource.height
                        out.centerHex = "#%08X".format(
                            resource.getPixel(resource.width / 2, resource.height / 2)
                        )
                        out.ms = System.currentTimeMillis() - start
                        stats?.let { it.fullRuns++; if (dataSource == DataSource.MEMORY_CACHE) it.fullMemory++ }
                        latch.countDown()
                        return false
                    }
                })

            if (thumb) primary.thumbnail(thumbBuilder)
            primary.submit()
        }

        latch.await(35, TimeUnit.SECONDS)
        return out
    }

    /** 走真实生产代码，返回"从开始到 view 上出现真实图"的毫秒数 */
    private fun timeRealMediaLoader(entity: com.pichs.filepicker.entity.MediaEntity): Long {
        val iv = android.widget.ImageView(context)
        val start = System.currentTimeMillis()
        onMain { com.pichs.filepicker.loader.MediaLoader.loadThumbnail(entity, iv) }
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val ready = onMain {
                val bmp = (iv.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp != null && !bmp.isRecycled &&
                        bmp.getPixel(bmp.width / 2, bmp.height / 2) == GREEN
            }
            if (ready) return System.currentTimeMillis() - start
            Thread.sleep(20)
        }
        return -1
    }

    private fun createBigImage() {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fp_probe_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        assertNotNull("MediaStore 插入失败", inserted)
        resolver.openOutputStream(inserted!!)!!.use { writeSolidJpeg(it, BIG, GREEN) }
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
        uri = inserted
        filePath = path
        println("[PROBE] 造图 ${BIG}x$BIG 纯绿 uri=$inserted path=$path")
    }

    private fun writeSolidJpeg(out: OutputStream, size: Int, color: Int) {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
        bmp.recycle()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun clearMemoryOnly() {
        onMain { Glide.get(context).clearMemory() }
    }

    private fun clearGlideCaches() {
        clearMemoryOnly()
        Glide.get(context).clearDiskCache()
        Thread.sleep(800)
    }
}
