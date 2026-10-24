package com.pichs.filepicker.loader

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.signature.MediaStoreSignature
import com.pichs.filepicker.R
import com.pichs.filepicker.entity.MediaEntity
import java.io.File

/**
 * 媒体加载统一入口。
 *
 * ## 一致性（本次修复的核心）
 * 旧实现的网格缩略图带 `override(200,200)`，尺寸落进 Glide 的 MediaStore 缩略图分支
 * （`MediaStoreUtil.isThumbnailSize` 判定 <= 512x384），于是走 `ThumbFetcher` 去读
 * 系统 `thumbnails` 表里**预生成的缩略图缓存文件**；而大图预览没有 override，尺寸是整屏，
 * 会跳过该分支直接读原文件。两者是两份不同的字节来源，系统缩略图表一旦过期/错位，
 * 就会出现"缩略图是 A、点开是 B"。
 *
 * 现在约定：
 * 1. 缩略图的**权威来源是真实文件**（与原图同一份字节），系统缩略图只当首帧占位，
 *    因此最终展示的一定与原图同源；
 * 2. 缩略图与原图都带 [MediaStoreSignature]（mime + 修改时间 + 方向），
 *    文件被覆盖/编辑/ID 复用后 Glide 缓存立即失效，不会继续显示旧图；
 *    注意两边都要带，只给缩略图加会让"旧图残留在原图缓存"里，问题只是换了个方向。
 *
 * ## 性能（不能变慢）
 * - 首帧仍然复用系统预生成缩略图（几十 KB 的小文件），首屏观感与旧实现一致；
 * - 权威图只在"该格第一次真正显示"时解码一次原文件，Glide 会按 `inSampleSize` 降采样，
 *   解码量很小；之后命中 Glide 内存/磁盘缓存，重复滚动、二次进入选择器都不再读原图；
 * - 只有真正被显示的格子会发请求，RecyclerView 回收时 Glide 自动取消；
 * - 缩略图统一 `PREFER_RGB_565`（带 alpha 的图 Glide 会自动保留 ARGB_8888），
 *   且每个逻辑图片在缓存里只保留"权威图"一份，内存不高于旧实现（单张 ARGB_8888 缩略图）；
 * - 系统缩略图那一路不进内存/磁盘缓存，只做冷启动首帧过渡，避免缓存里存两份 200x200。
 */
object MediaLoader {

    /**
     * 缩略图目标边长。4 列网格单元约 1/4 屏宽，200 足够；
     * 数值越小解码越快、内存越小，不要盲目调大。
     */
    private const val THUMB_SIZE = 200

    /** 视频封面目标尺寸：MediaStore 视频缩略图分支要求 <= 512x384，取上限保证清晰度 */
    private const val VIDEO_COVER_WIDTH = 512
    private const val VIDEO_COVER_HEIGHT = 384

    // ------------------------------------------------------------------
    // 推荐入口：直接传 MediaEntity（携带 path / dateModified / orientation）
    // ------------------------------------------------------------------

    /** 网格 / 相册封面 / 已选列表的缩略图 */
    fun loadThumbnail(entity: MediaEntity?, view: ImageView) {
        if (entity == null) return
        val mime = entity.mimeType
        when {
            mime.equals("image/gif", true) -> loadGif(entity, view)
            isVideo(mime, entity) -> loadVideoThumbnail(entity, view)
            else -> loadImageThumbnail(entity, view)
        }
    }

    /** 大图预览（原图） */
    fun loadImage(entity: MediaEntity?, view: ImageView) {
        if (entity == null) return
        val mime = entity.mimeType
        when {
            mime.equals("image/gif", true) -> loadGif(entity, view)
            isVideo(mime, entity) -> loadVideoCover(entity, view)
            else -> loadOriginalImage(entity, view)
        }
    }

    /** 视频播放器封面：优先用 content uri，才能命中 MediaStore 视频缩略图分支拿到真实帧 */
    fun loadVideoCover(entity: MediaEntity?, view: ImageView) {
        if (entity == null) return
        val model: Any = entity.uri
            ?: entity.path?.takeIf { it.isNotEmpty() }?.let(::File)
            ?: return
        Glide.with(view)
            .load(model)
            .override(VIDEO_COVER_WIDTH, VIDEO_COVER_HEIGHT)
            .dontTransform()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .signature(signatureOf(entity))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(view)
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    /**
     * 图片缩略图：权威来源＝真实文件；首帧先顶系统缩略图。
     *
     * 用 [File] 作为 model 会走 Glide 的 FileLoader，不会进入
     * `MediaStoreImageThumbLoader`，因此拿到的永远是从原图降采样出来的内容。
     */
    private fun loadImageThumbnail(entity: MediaEntity, view: ImageView) {
        val uri = entity.uri
        val path = entity.path
        val model: Any = if (!path.isNullOrEmpty()) File(path) else uri ?: return

        val request = Glide.with(view)
            .load(model)
            .override(THUMB_SIZE, THUMB_SIZE)
            .dontTransform()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .signature(signatureOf(entity))
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

        // 极少数情况下按路径直读会失败（如 Android 14 部分授权 SELECTED 之外的文件、文件被移走），
        // 此时退回 content uri，避免直接掉到错误占位图
        if (model is File && uri != null) {
            request.error(
                Glide.with(view)
                    .load(uri)
                    .override(THUMB_SIZE, THUMB_SIZE)
                    .dontTransform()
                    .dontAnimate()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .placeholder(R.drawable.filepicker_placeholder_image)
                    .error(R.drawable.filepicker_placeholder_image)
                    .signature(signatureOf(entity))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            )
        } else {
            request.error(R.drawable.filepicker_placeholder_image)
        }

        // 首帧占位：系统预生成缩略图（快）；拿到权威图后 Glide 会自动替换并取消它
        fastThumbnail(entity, view)?.let { request.thumbnail(it) }

        request.into(view)
    }

    /**
     * 首帧占位请求：系统预生成缩略图。
     *
     * 刻意 `skipMemoryCache(true)` + `DiskCacheStrategy.NONE`：它只在"某一格第一次冷显示"
     * 时顶上第一帧，权威图到位后 Glide 会立刻取消它。不占内存/磁盘缓存，可以保证
     * 每个逻辑图片在缓存里只有"权威图"一份，内存占用与旧实现（单张缩略图）持平。
     */
    private fun fastThumbnail(entity: MediaEntity, view: ImageView): RequestBuilder<Drawable>? {
        val uri = entity.uri ?: return null
        return Glide.with(view)
            .load(uri)
            .override(THUMB_SIZE, THUMB_SIZE)
            .dontTransform()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
    }

    /** 大图预览：读原文件，整屏尺寸会跳过 MediaStore 缩略图分支，带 signature 保证不显示旧图 */
    private fun loadOriginalImage(entity: MediaEntity, view: ImageView) {
        val uri = entity.uri ?: return
        // 与网格缩略图同 model（File），命中它的内存/磁盘缓存，先秒出一张小图顶上，
        // 大图解码完成后再替换，翻页不会先看到一片黑。
        val thumbModel: Any = entity.path?.takeIf { it.isNotEmpty() }?.let(::File) ?: uri
        // 关键：**不要按原图全尺寸解码**。一张 4000x3000 的照片是 ~48MB 的 Bitmap，
        // 预览页每翻一页解一张，既卡又容易 OOM；按屏幕 2 倍采样后屏幕内看不出差别，
        // 放大也还有余量，解码量和内存都降一个数量级。
        val dm = view.resources.displayMetrics
        val targetWidth = (dm.widthPixels * 2).coerceAtLeast(1)
        val targetHeight = (dm.heightPixels * 2).coerceAtLeast(1)
        Glide.with(view)
            .load(uri)
            .override(targetWidth, targetHeight)
            .downsample(DownsampleStrategy.AT_MOST)
            .dontTransform()
            .dontAnimate()
            .thumbnail(
                Glide.with(view)
                    .load(thumbModel)
                    .override(THUMB_SIZE, THUMB_SIZE)
                    .dontAnimate()
                    .format(DecodeFormat.PREFER_RGB_565)
                    .signature(signatureOf(entity))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            )
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .signature(signatureOf(entity))
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }

    private fun loadGif(entity: MediaEntity, view: ImageView) {
        val uri = entity.uri ?: return
        Glide.with(view)
            .asGif()
            .load(uri)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .signature(signatureOf(entity))
            // GIF 保持 RESOURCE：asGif 的编码器是 SOURCE，AUTOMATIC 会把本地 GIF 的
            // 磁盘缓存丢掉，内存缓存被逐出后每次重绑都要重新读文件+解码
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    /**
     * 视频缩略图：只能交给 MediaStore 视频缩略图分支，无法像图片那样用原文件降采样
     * （原文件是视频，Glide 解不了帧）。加 signature 至少保证视频变更后不吃旧缓存。
     */
    private fun loadVideoThumbnail(entity: MediaEntity, view: ImageView) {
        val uri = entity.uri ?: return
        Glide.with(view)
            .load(uri)
            .override(THUMB_SIZE, THUMB_SIZE)
            .dontTransform()
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .signature(signatureOf(entity))
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }

    private fun isVideo(mime: String?, entity: MediaEntity): Boolean =
        mime?.startsWith("video/", true) == true || (mime.isNullOrBlank() && entity.isVideo())

    /** 缓存失效签名：mime + 修改时间 + 方向任一变化都会让旧缓存失效 */
    private fun signatureOf(entity: MediaEntity): MediaStoreSignature =
        MediaStoreSignature(entity.mimeType ?: "", entity.dateModified, entity.orientation ?: 0)

    // ------------------------------------------------------------------
    // 兼容旧 API（未携带修改时间，无法做缓存失效；新代码请用上面的 entity 版本）
    // ------------------------------------------------------------------

    @Deprecated(
        message = "改用 loadThumbnail(entity, view)：携带 path/修改时间，缩略图与原图同源且缓存可失效",
        replaceWith = ReplaceWith("loadThumbnail(entity, view)")
    )
    fun loadImageThumbnail(uri: Uri?, mimeType: String? = null, view: ImageView) {
        if (uri == null) return
        // 兼容入口：包成 MediaEntity 后走同一套逻辑，老调用方也能拿到
        // "权威图 = 真实文件 + 缓存签名" 的一致性保证，不再直接命中系统缩略图表
        loadThumbnail(MediaEntity(uri = uri, mimeType = mimeType), view)
    }

    @Deprecated(
        message = "改用 loadImage(entity, view)：缓存可失效，不会显示旧图",
        replaceWith = ReplaceWith("loadImage(entity, view)")
    )
    fun loadImage(uri: Uri?, mimeType: String? = null, view: ImageView) {
        if (uri == null) return
        loadImage(MediaEntity(uri = uri, mimeType = mimeType), view)
    }

    fun loadVideoCover(path: String, view: ImageView) {
        Glide.with(view)
            .load(path)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(THUMB_SIZE, THUMB_SIZE)
            .diskCacheStrategy(resolveDiskCacheStrategy(path))
            .into(view)
    }

    /**
     * 本地数据（content://、file://、文件路径）用 AUTOMATIC：对本地 Bitmap（图片/视频封面）
     * 的磁盘 resource cache 写入/读取与 RESOURCE 完全一致（BitmapEncoder 为 TRANSFORMED，
     * AUTOMATIC 的 LOCAL+TRANSFORMED 分支允许写 resource cache），仅省去冗余配置差异。
     * GIF 例外：GifDrawableEncoder 为 SOURCE，AUTOMATIC 不写 resource cache，
     * 会丢掉 GIF 的磁盘缓存，故 loadGif 单独维持 RESOURCE。
     * 远程 URL 维持 RESOURCE，行为不变。
     * 注意不要改成 NONE：内存缓排放不下整个相册时，靠这份磁盘 resource cache 才能在回滚列表时快速复显。
     */
    private fun resolveDiskCacheStrategy(uri: Uri?): DiskCacheStrategy {
        if (uri == null) return DiskCacheStrategy.RESOURCE
        return when (uri.scheme?.lowercase()) {
            null, "content", "file" -> DiskCacheStrategy.AUTOMATIC
            else -> DiskCacheStrategy.RESOURCE
        }
    }

    private fun resolveDiskCacheStrategy(path: String?): DiskCacheStrategy =
        resolveDiskCacheStrategy(path?.let { Uri.parse(it) })


}
