package com.pichs.filepicker.demo

import android.Manifest
import android.content.ContentValues
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.pichs.filepicker.demo.databinding.ActivityDataFactoryBinding
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * 测试数据制造页。
 * 批量生成各类型测试文件，方便测试选择器：
 * - 文档类（txt/doc/docx/xls/xlsx/ppt/pptx/pdf）→ Documents/TestData
 * - 图片（随机彩色、屏幕宽高）→ DCIM/TestData
 * - 视频（选一个复制N份）→ DCIM/TestData
 * - 音乐（mp3）→ Music/TestData
 * - 压缩包及其他（zip/tar/gz/rar/7z/iso/apk）→ Download/TestData
 */
class DataFactoryActivity : BindingActivity<ActivityDataFactoryBinding>() {

    companion object {
        private const val BASE_DIR = "TestData"
        private const val MAX_COUNT = 500

        /** OLE2 复合文档魔数（doc/xls/ppt 的文件头） */
        private val OLE_HEADER = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )
    }

    private val timeFormat by lazy { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA) }

    private var generating = false
    private var pendingVideoCopyCount = 1

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch { copyVideoToDcim(uri, pendingVideoCopyCount) }
        }

    override fun beforeOnCreate(savedInstanceState: Bundle?) {
        super.beforeOnCreate(savedInstanceState)
    }

    override fun afterOnCreate() {
        binding.btnGenerate.fastClick { startGenerate() }
        binding.btnPickVideo.fastClick { pickVideoAndCopy() }
    }

    // ---------------------------------------------------------------- 入口

    private fun readCount(): Int {
        return binding.etCount.text.toString().toIntOrNull()?.coerceIn(1, MAX_COUNT) ?: 1
    }

    private fun pickVideoAndCopy() {
        if (generating) {
            toast("正在生成中，稍等")
            return
        }
        pendingVideoCopyCount = readCount()
        pickVideoLauncher.launch("video/*")
    }

    private fun startGenerate() {
        if (generating) {
            toast("正在生成中，稍等")
            return
        }
        val count = readCount()
        val tasks = buildTasks(count)
        if (tasks.isEmpty()) {
            toast("至少勾选一种类型")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Q+ 走 MediaStore 写公共目录，不需要权限
            runTasks(tasks, count)
        } else {
            XXPermissions.with(this)
                .permission(Permission.WRITE_EXTERNAL_STORAGE)
                .request { _, all ->
                    if (all) runTasks(tasks, count) else toast("没有存储权限，写不进去")
                }
        }
    }

    // ---------------------------------------------------------------- 任务构建

    /** 每种类型 x 数量，逐文件生成 */
    private data class GenTask(val label: String, val dir: String, val mime: String, val producer: (Int) -> ByteArray)

    private fun buildTasks(count: Int): List<GenTask> {
        val tasks = mutableListOf<GenTask>()
        if (binding.cbImage.isChecked) {
            tasks += GenTask("图片", "DCIM", "image/jpeg") { i -> generateImageBytes(i) }
        }
        if (binding.cbTxt.isChecked) {
            tasks += GenTask("txt", "Documents", "text/plain") { buildTxtBytes() }
        }
        if (binding.cbDoc.isChecked) {
            tasks += GenTask("doc", "Documents", "application/msword") { buildOleBytes() }
            tasks += GenTask("docx", "Documents", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") { buildZipBytes() }
        }
        if (binding.cbXls.isChecked) {
            tasks += GenTask("xls", "Documents", "application/vnd.ms-excel") { buildOleBytes() }
            tasks += GenTask("xlsx", "Documents", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { buildZipBytes() }
        }
        if (binding.cbPpt.isChecked) {
            tasks += GenTask("ppt", "Documents", "application/vnd.ms-powerpoint") { buildOleBytes() }
            tasks += GenTask("pptx", "Documents", "application/vnd.openxmlformats-officedocument.presentationml.presentation") { buildZipBytes() }
        }
        if (binding.cbPdf.isChecked) {
            tasks += GenTask("pdf", "Documents", "application/pdf") { buildPdfBytes() }
        }
        if (binding.cbMp3.isChecked) {
            tasks += GenTask("mp3", "Music", "audio/mpeg") { buildMp3Bytes() }
        }
        if (binding.cbZip.isChecked) {
            tasks += GenTask("zip", "Download", "application/zip") { buildZipBytes() }
        }
        if (binding.cbTar.isChecked) {
            tasks += GenTask("tar", "Download", "application/x-tar") { buildTarBytes() }
            tasks += GenTask("gz", "Download", "application/gzip") { buildGzBytes() }
        }
        if (binding.cbOther.isChecked) {
            tasks += GenTask("rar", "Download", "application/vnd.rar") { buildRarBytes() }
            tasks += GenTask("7z", "Download", "application/x-7z-compressed") { buildSevenZipBytes() }
            tasks += GenTask("iso", "Download", "application/x-iso9660-image") { buildIsoBytes() }
        }
        if (binding.cbApk.isChecked) {
            tasks += GenTask("apk", "Download", "application/vnd.android.package-archive") { buildApkBytes() }
        }
        return tasks
    }

    private fun runTasks(tasks: List<GenTask>, count: Int) {
        generating = true
        binding.btnGenerate.isEnabled = false
        binding.tvLog.text = ""
        binding.progressBar.max = tasks.size * count
        binding.progressBar.progress = 0
        binding.progressBar.visibility = android.view.View.VISIBLE
        val stamp = timeFormat.format(Date())

        lifecycleScope.launch {
            var ok = 0
            var fail = 0
            for (task in tasks) {
                for (i in 0 until count) {
                    try {
                        val name = "${task.label.uppercase()}_TEST_${stamp}_${i + 1}.${task.label}"
                        // 内容生成 + 落盘都放 IO 线程
                        val bytes = withContext(Dispatchers.IO) { task.producer(i) }
                        saveFile(task.dir, name, task.mime, bytes)
                        ok++
                    } catch (e: Exception) {
                        fail++
                        logLine("✗ ${task.label} 第${i + 1}个失败: ${e.message}")
                    }
                    onMain { binding.progressBar.incrementProgressBy(1) }
                }
                logLine("✓ ${task.label} x$count → ${task.dir}/$BASE_DIR")
            }
            logLine("完成：成功 $ok 个，失败 $fail 个")
            generating = false
            binding.btnGenerate.isEnabled = true
        }
    }

    private suspend fun copyVideoToDcim(uri: Uri, count: Int) {
        generating = true
        binding.btnPickVideo.isEnabled = false
        binding.tvLog.text = ""
        binding.progressBar.max = count
        binding.progressBar.progress = 0
        binding.progressBar.visibility = android.view.View.VISIBLE
        logLine("开始复制视频 x$count ...")
        try {
            val result = withContext(Dispatchers.IO) {
                val mime = contentResolver.getType(uri) ?: "video/mp4"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp4"
                val originName = queryDisplayName(uri) ?: "picked_video"
                val baseName = originName.substringBeforeLast('.')
                var ok = 0
                repeat(count) { i ->
                    val name = "${baseName}_copy_${i + 1}_${System.currentTimeMillis()}.$ext"
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            saveStream("DCIM", name, mime, input)
                        }
                        ok++
                    } catch (e: Exception) {
                        logLine("✗ $name 失败: ${e.message}")
                    }
                    onMain { binding.progressBar.incrementProgressBy(1) }
                }
                ok
            }
            logLine("✓ 视频复制完成：成功 $result 个 → DCIM/$BASE_DIR")
        } finally {
            generating = false
            binding.btnPickVideo.isEnabled = true
        }
    }

    // ---------------------------------------------------------------- 落盘

    /** 写公共目录，Q+ 走 MediaStore（带 RELATIVE_PATH），低版本走 File + MediaScanner 扫描 */
    private suspend fun saveFile(dir: String, displayName: String, mimeType: String, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$BASE_DIR")
                }
                val uri = contentResolver.insert(filesCollection(), values)
                    ?: throw IOException("MediaStore insert 失败")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("打开输出流失败")
            } else {
                val file = File(publicDir(dir), displayName)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(this@DataFactoryActivity, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            }
        }

    private suspend fun saveStream(dir: String, displayName: String, mimeType: String, input: java.io.InputStream) =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$BASE_DIR")
                }
                val uri = contentResolver.insert(filesCollection(), values)
                    ?: throw IOException("MediaStore insert 失败")
                contentResolver.openOutputStream(uri)?.use { input.copyTo(it) }
                    ?: throw IOException("打开输出流失败")
            } else {
                val file = File(publicDir(dir), displayName)
                file.outputStream().use { input.copyTo(it) }
                MediaScannerConnection.scanFile(this@DataFactoryActivity, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            }
        }

    private fun filesCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

    private fun publicDir(dir: String): File {
        val envDir = when (dir) {
            "Music" -> Environment.DIRECTORY_MUSIC
            "DCIM" -> Environment.DIRECTORY_DCIM
            "Download" -> Environment.DIRECTORY_DOWNLOADS
            else -> Environment.DIRECTORY_DOCUMENTS
        }
        return File(Environment.getExternalStoragePublicDirectory(envDir), BASE_DIR).apply { mkdirs() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- 内容生成

    /**
     * 随机彩色测试图：屏幕宽高，鲜艳渐变底 + 随机圆形色块 + 文字标识。
     * 颜色全部 HSV 随机（高饱和高明度），保证肉眼鲜艳。
     */
    private fun generateImageBytes(index: Int): ByteArray {
        val metrics = Resources.getSystem().displayMetrics
        val w = metrics.widthPixels.coerceAtLeast(200)
        val h = metrics.heightPixels.coerceAtLeast(200)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 鲜艳渐变底
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            vividColor(), vividColor(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // 随机彩色圆形色块
        paint.shader = null
        repeat(15) {
            paint.color = vividColor()
            paint.alpha = 80 + Random.nextInt(120)
            val r = (Random.nextInt(40, w.coerceAtLeast(h) / 3)).toFloat()
            canvas.drawCircle(Random.nextInt(w).toFloat(), Random.nextInt(h).toFloat(), r, paint)
        }

        // 标识文字
        paint.alpha = 255
        paint.color = Color.WHITE
        paint.textSize = h / 10f
        canvas.drawText("TEST IMG $index", w * 0.08f, h * 0.52f, paint)

        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    /** 高饱和高明度随机色，肉眼鲜艳 */
    private fun vividColor(): Int {
        return Color.HSVToColor(
            floatArrayOf(
                Random.nextFloat() * 360f,
                0.85f + Random.nextFloat() * 0.15f,
                0.85f + Random.nextFloat() * 0.15f
            )
        )
    }

    private fun buildTxtBytes(): ByteArray = buildString {
        repeat(300) { i ->
            appendLine("第${i + 1}行：这是 filepicker demo 生成的测试文本。时间戳：${System.currentTimeMillis()} 随机数：${Random.nextInt(100000)}")
        }
    }.toByteArray()

    /** 真实 PDF（单页 + Helvetica 文字），后续补零到随机大小 */
    private fun buildPdfBytes(): ByteArray {
        val content = "BT /F1 24 Tf 72 720 Td (FilePicker Demo Test PDF) Tj ET".toByteArray()
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${content.size} >>\nstream\n" + String(content) + "\nendstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        )
        val bos = ByteArrayOutputStream()
        bos.write("%PDF-1.4\n".toByteArray())
        val offsets = IntArray(objects.size + 1)
        objects.forEachIndexed { i, obj ->
            offsets[i + 1] = bos.size()
            bos.write("${i + 1} 0 obj\n$obj\nendobj\n".toByteArray())
        }
        val xrefPos = bos.size()
        bos.write("xref\n0 ${objects.size + 1}\n".toByteArray())
        bos.write("0000000000 65535 f \n".toByteArray())
        for (i in 1..objects.size) {
            bos.write("%010d 00000 n \n".format(offsets[i]).toByteArray())
        }
        bos.write(
            "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n".toByteArray()
        )
        return padRandom(bos.toByteArray())
    }

    /** OLE2 头（doc/xls/ppt 魔数）+ 随机填充 */
    private fun buildOleBytes(): ByteArray {
        return padRandom(OLE_HEADER)
    }

    /** 真实 zip 容器（zip/docx/xlsx/pptx 通用） */
    private fun buildZipBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            repeat(3) { i ->
                zos.putNextEntry(ZipEntry("test_entry_$i.txt"))
                zos.write(buildTxtBytes())
                zos.closeEntry()
            }
        }
        return padRandom(bos.toByteArray())
    }

    /** 静音 mp3：ID3v2 头 + MPEG-1 Layer3 128kbps 44.1kHz 帧 */
    private fun buildMp3Bytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00))
        // 417字节/帧
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00
        repeat(500) { bos.write(frame) }
        return padRandom(bos.toByteArray())
    }

    /** 真实 tar（512字节头带校验和） */
    private fun buildTarBytes(): ByteArray {
        val data = "filepicker demo tar test. ${System.currentTimeMillis()}\n".toByteArray()
        val header = ByteArray(512)
        "test.txt".toByteArray().copyInto(header, 0, 0, minOf(100, "test.txt".length))
        fun octal(value: Long, len: Int, offset: Int) {
            val s = "%0${len - 1}o ".format(value)
            s.toByteArray().copyInto(header, offset)
        }
        octal(0b110_000_100L, 8, 100)   // mode 0644
        octal(0L, 8, 108)               // uid
        octal(0L, 8, 116)               // gid
        octal(data.size.toLong(), 12, 124)  // size
        octal(System.currentTimeMillis() / 1000, 12, 136)  // mtime
        // checksum 先填空格再计算
        repeat(8) { header[148 + it] = ' '.code.toByte() }
        header[257] = '0'.code.toByte() // ustar
        val checksum = header.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        "%06o  ".format(checksum).toByteArray().copyInto(header, 148)

        val bos = ByteArrayOutputStream()
        bos.write(header)
        bos.write(data)
        val remain = (512 - data.size % 512) % 512
        bos.write(ByteArray(remain))
        // 两块512的结束块
        bos.write(ByteArray(1024))
        return padRandom(bos.toByteArray())
    }

    /** 真实 gzip */
    private fun buildGzBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(buildTxtBytes()) }
        return bos.toByteArray()
    }

    /** Rar! 魔数 + 填充 */
    private fun buildRarBytes(): ByteArray {
        return padRandom(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
    }

    /** 7z 魔数 + 填充 */
    private fun buildSevenZipBytes(): ByteArray {
        return padRandom(
            byteArrayOf(
                0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C
            )
        )
    }

    /** iso9660 魔数（偏移 0x8001 处 CD001）+ 填充 */
    private fun buildIsoBytes(): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write(ByteArray(0x8001))
        bos.write("CD001".toByteArray())
        return padRandom(bos.toByteArray(), minKb = 512, maxKb = 1024)
    }

    /** 复制本 app 安装包，真实的 apk */
    private fun buildApkBytes(): ByteArray {
        return File(applicationInfo.sourceDir).readBytes()
    }

    /** 随机补 20KB~300KB，方便测文件大小过滤 */
    private fun padRandom(bytes: ByteArray, minKb: Int = 20, maxKb: Int = 300): ByteArray {
        val extra = Random.nextInt(minKb, maxKb + 1) * 1024
        val pad = ByteArray(extra)
        Random.nextBytes(pad)
        return bytes + pad
    }

    // ---------------------------------------------------------------- UI

    private fun logLine(line: String) {
        onMain { binding.tvLog.append("$line\n") }
    }

    private fun onMain(block: () -> Unit) {
        runOnUiThread(block)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
