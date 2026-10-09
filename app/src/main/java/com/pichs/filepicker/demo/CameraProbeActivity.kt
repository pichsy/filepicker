package com.pichs.filepicker.demo

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pichs.filepicker.demo.databinding.ActivityCameraProbeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相机 intent 探测页 —— **临时验证用，不属于库的一部分**，验证完可以整个删掉。
 *
 * 要回答的问题是：有没有办法"一次打开相机、让用户自己在相机里选拍照还是录像、并且还能拿到结果"。
 *
 * 每个按钮对应一种候选方案，点完看两件事：
 * 1. 实际打开了什么界面（自己看屏幕 / 截图）；
 * 2. 回来时到底能拿到什么 —— resultCode、`data`、`EXTRA_OUTPUT` 产物有没有内容、
 *    MediaStore 里有没有新增条目。只看文档回答不了这个，必须真机点一遍。
 *
 * 结果同时打到 logcat（tag = [TAG]），方便 `adb logcat -s CameraProbe` 抓。
 */
class CameraProbeActivity : AppCompatActivity() {

    private enum class Variant(val title: String) {
        IMAGE_CAPTURE("1. 拍照  ACTION_IMAGE_CAPTURE + EXTRA_OUTPUT"),
        VIDEO_CAPTURE("2. 录像  ACTION_VIDEO_CAPTURE + EXTRA_OUTPUT"),
        CHOOSER_IMAGE_FIRST("3. 组合  chooser(拍照) + EXTRA_INITIAL_INTENTS[录像]"),
        CHOOSER_VIDEO_FIRST("4. 组合  chooser(录像) + EXTRA_INITIAL_INTENTS[拍照]"),
        STILL_IMAGE_CAMERA("5. 完整相机  INTENT_ACTION_STILL_IMAGE_CAMERA"),
        VIDEO_CAMERA("6. 完整相机  INTENT_ACTION_VIDEO_CAMERA"),
    }

    private lateinit var binding: ActivityCameraProbeBinding

    private val buffer = StringBuilder()

    private var pendingVariant: Variant? = null

    /** 我们自己造的 EXTRA_OUTPUT，用于回来时判断"产物到底有没有内容" */
    private val pendingOutputs = mutableListOf<Uri>()

    /** 进入相机的时间（秒）—— MediaStore 的 DATE_ADDED 是秒级 */
    private var launchAtSeconds: Long = 0L

    /** chooser / STILL_IMAGE_CAMERA 没有可依赖的结果契约，用"扫新增"兜底验证 */
    private var needScanNewMedia = false

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onReturned(result.resultCode, result.data)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraProbeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        log("设备：${Build.MANUFACTURER} ${Build.MODEL}  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        log("targetSdk=${applicationInfo.targetSdkVersion}（>=30 才会受包可见性限制）")
        log("")

        Variant.entries.forEach { variant ->
            addButton(variant.title) { launchVariant(variant) }
        }
        addButton("7. 列出各 action 能解析到的 Activity（含包可见性影响）") { listHandlers() }
        addButton("8. 清空日志") { buffer.setLength(0); render() }
        addButton("9. 清理本次探测留下的空记录") { cleanupEmpty() { msg -> log(msg) } }
    }

    override fun onResume() {
        super.onResume()
        // 没有结果契约的方案，只能靠"回前台后看 MediaStore 有没有新条目"来判断
        if (needScanNewMedia) {
            needScanNewMedia = false
            scanNewMediaSince(launchAtSeconds)
        }
    }

    // ------------------------------------------------------------------
    // 启动各方案
    // ------------------------------------------------------------------

    private fun launchVariant(variant: Variant) {
        pendingVariant = variant
        pendingOutputs.clear()
        launchAtSeconds = System.currentTimeMillis() / 1000

        log("──────── ${variant.title} ────────")

        val intent = try {
            buildIntent(variant)
        } catch (e: Exception) {
            log("构造 intent 失败：${e.javaClass.simpleName}: ${e.message}")
            return
        }
        if (intent == null) {
            log("无法构造（例如建不出输出 Uri），放弃")
            return
        }

        log("action = ${intent.action}")
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.get(key)
            log("  extra[$key] = ${describe(value)}")
        }

        // resolveActivity 在 targetSdk>=30 且宿主没声明 <queries> 时可能返回 null，
        // 而 startActivity 依然能成功 —— 这两者不一致本身就是这次要观察的点
        val resolved = try {
            intent.resolveActivity(packageManager)?.let { "${it.packageName}/${it.className}" } ?: "null"
        } catch (e: Exception) {
            "抛异常 ${e.javaClass.simpleName}"
        }
        log("resolveActivity = $resolved")

        needScanNewMedia = variant == Variant.STILL_IMAGE_CAMERA ||
                variant == Variant.VIDEO_CAMERA ||
                variant.name.startsWith("CHOOSER")

        try {
            launcher.launch(intent)
            log("→ 已发出，观察相机界面…")
        } catch (e: Exception) {
            needScanNewMedia = false
            log("✗ launch 失败：${e.javaClass.simpleName}: ${e.message}")
            log("   （ActivityNotFoundException = 没有 App 响应；SecurityException = 权限问题）")
        }
    }

    private fun buildIntent(variant: Variant): Intent? {
        return when (variant) {
            Variant.IMAGE_CAPTURE -> imageCaptureIntent()
            Variant.VIDEO_CAPTURE -> videoCaptureIntent()

            Variant.CHOOSER_IMAGE_FIRST -> {
                val photo = imageCaptureIntent()
                val video = videoCaptureIntent()
                Intent.createChooser(photo, "拍照 还是 录像？（chooser: 拍照优先）").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(video))
                }
            }

            Variant.CHOOSER_VIDEO_FIRST -> {
                val photo = imageCaptureIntent()
                val video = videoCaptureIntent()
                Intent.createChooser(video, "录像 还是 拍照？（chooser: 录像优先）").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(photo))
                }
            }

            Variant.STILL_IMAGE_CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)

            Variant.VIDEO_CAMERA -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
        }
    }

    private fun imageCaptureIntent(): Intent {
        val uri = createOutputUri(isVideo = false)
        if (uri != null) pendingOutputs.add(uri)
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            uri?.let { putExtra(MediaStore.EXTRA_OUTPUT, it) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun videoCaptureIntent(): Intent {
        val uri = createOutputUri(isVideo = true)
        if (uri != null) pendingOutputs.add(uri)
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            uri?.let { putExtra(MediaStore.EXTRA_OUTPUT, it) }
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    private fun createOutputUri(isVideo: Boolean): Uri? {
        val name = (if (isVideo) "PROBE_VID_" else "PROBE_IMG_") +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "_" +
                (100..999).random() + if (isVideo) ".mp4" else ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return try {
            contentResolver.insert(collection, values)
        } catch (e: Exception) {
            log("建输出 Uri 失败：${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // 回来后看拿到了什么
    // ------------------------------------------------------------------

    private fun onReturned(resultCode: Int, data: Intent?) {
        val variant = pendingVariant
        pendingVariant = null
        if (variant == null) return

        log("← 回来了")
        log("resultCode = $resultCode（-1=RESULT_OK, 0=RESULT_CANCELED）")
        log("data = ${if (data == null) "null" else "非 null"}")

        if (data != null) {
            log("  data.data = ${data.data}")
            data.extras?.keySet()?.forEach { key ->
                log("  data.extra[$key] = ${describe(data.extras?.get(key))}")
            }
            // 官方文档明说 ACTION_IMAGE_CAPTURE 可能只回一张缩略图 Bitmap
            val thumb = data.getParcelableExtra<android.os.Parcelable>("data")
            if (thumb != null) log("  ⚠ 回执里带了 \"data\"（缩略图 Bitmap: $thumb）—— 不是原图")
        }

        if (pendingOutputs.isEmpty()) {
            log("这次没有用 EXTRA_OUTPUT（该方案本身不支持）")
        } else {
            log("我们指定的 EXTRA_OUTPUT 产物：")
            pendingOutputs.forEach { uri ->
                CoroutineScope(Dispatchers.Main).launch {
                    val size = readSize(uri)
                    val readable = hasContent(uri)
                    log("  $uri  size=$size  有内容=$readable")
                    if (!readable) {
                        log("    → 是空的。该方案下这条记录应当清理（见按钮 9）")
                    }
                }
            }
        }
        log("")
    }

    /** 扫 MediaStore：从 [sinceSeconds] 之后新增的条目（给没有结果契约的方案用） */
    private fun scanNewMediaSince(sinceSeconds: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            val found = withContext(Dispatchers.IO) {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                )
                val selection = "${MediaStore.Files.FileColumns.DATE_ADDED} >= ?"
                val args = arrayOf((sinceSeconds - 3).toString())
                val order = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                val out = mutableListOf<String>()
                try {
                    contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        projection, selection, args, order
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(0)
                            val name = cursor.getString(1)
                            val mime = cursor.getString(2)
                            val size = cursor.getLong(3)
                            val added = cursor.getLong(4)
                            out.add("id=$id name=$name mime=$mime size=$size DATE_ADDED=$added")
                        }
                    }
                } catch (e: Exception) {
                    out.add("查询失败：${e.message}")
                }
                out
            }
            log("扫新增（DATE_ADDED >= ${sinceSeconds - 3}）：命中 ${found.size} 条")
            found.forEach { log("  $it") }
            if (found.isEmpty()) {
                log("  → 没有新条目：这条路拿不到任何东西，等于把用户扔进相机就不管了")
            }
            log("")
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun listHandlers() {
        log("════ 各 action 的解析情况 ════")
        val actions = listOf(
            MediaStore.ACTION_IMAGE_CAPTURE,
            MediaStore.ACTION_VIDEO_CAPTURE,
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            MediaStore.INTENT_ACTION_VIDEO_CAMERA,
        )
        actions.forEach { action ->
            val intent = Intent(action)
            val resolved = try {
                intent.resolveActivity(packageManager)?.let { "${it.packageName}/${it.className}" } ?: "null"
            } catch (e: Exception) {
                "抛异常 ${e.javaClass.simpleName}"
            }
            val handlers = try {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                emptyList()
            }
            log("$action")
            log("  resolveActivity = $resolved")
            log("  queryIntentActivities = ${handlers.size} 个")
            handlers.forEach { log("    - ${it.activityInfo.packageName}/${it.activityInfo.name}") }
        }
        log("注：targetSdk>=30 且没有 <queries> 时 resolveActivity/query 可能被包可见性过滤成空，")
        log("    但 startActivity 仍然能成功 —— 所以这里空不代表设备上没有相机 App。")
        log("")
    }

    private fun readSize(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun hasContent(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { it.read() != -1 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /** 只清理自己造的空记录，绝不碰有内容的（有内容说明相机真写进去了） */
    private fun cleanupEmpty(onLog: (String) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val deleted = withContext(Dispatchers.IO) {
                val ids = mutableListOf<Uri>()
                try {
                    contentResolver.query(
                        MediaStore.Files.getContentUri("external"),
                        arrayOf(MediaStore.Files.FileColumns._ID),
                        "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                        arrayOf("PROBE_%"),
                        null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            ids.add(
                                ContentUris.withAppendedId(
                                    MediaStore.Files.getContentUri("external"), c.getLong(0)
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    onLog("查询探测记录失败：${e.message}")
                }
                var count = 0
                ids.forEach { uri ->
                    // 只删空记录：有内容的说明相机真写进去了，留着
                    if (!hasContent(uri)) {
                        try {
                            count += contentResolver.delete(uri, null, null)
                        } catch (_: Exception) {
                        }
                    }
                }
                count
            }
            onLog("清理完成：删除 $deleted 条空记录（有内容的都留着）")
        }
    }

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Uri -> value.toString()
        is Array<*> -> value.joinToString(", ") { describe(it) }
        else -> value.toString()
    }

    private fun addButton(title: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            text = title
            textSize = 12f
            isAllCaps = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
        binding.llButtons.addView(button)
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        buffer.append(message).append('\n')
        render()
    }

    private fun render() {
        binding.tvLog.text = buffer.toString()
        binding.tvLog.post {
            (binding.tvLog.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    companion object {
        private const val TAG = "CameraProbe"
    }
}
