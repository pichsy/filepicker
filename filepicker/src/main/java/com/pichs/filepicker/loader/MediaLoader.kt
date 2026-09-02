package com.pichs.filepicker.loader

import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pichs.filepicker.R

object MediaLoader {

    fun loadImageThumbnail(uri: Uri?, mimeType: String? = null, view: ImageView) {
        if (uri == null) return
        if (mimeType == null) {
            loadImageThumbnail(uri, view)
            return
        }
        when {
            mimeType.equals("image/gif", true) -> {
                loadGif(uri, view)
            }

            mimeType.startsWith("image/", true) -> {
                loadImageThumbnail(uri, view)
            }

            mimeType.startsWith("video/", true) -> {
                loadVideoThumbnail(uri, view)
            }

            else -> {
                loadImageThumbnail(uri, view)
            }
        }
    }


    private fun loadGif(uri: Uri, view: ImageView) {
        Glide.with(view)
            .asGif()
            .load(uri)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            // GIF 保持 RESOURCE：asGif 的编码器是 SOURCE，AUTOMATIC 会把本地 GIF 的
            // 磁盘缓存丢掉，内存缓存被逐出后每次重绑都要重新读文件+解码
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    private fun loadImageThumbnail(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .override(200, 200)
            .dontTransform()
            .dontAnimate()
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }


    private fun loadVideoThumbnail(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(200, 200)
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }


    fun loadVideoCover(path: String, view: ImageView) {
        Glide.with(view)
            .load(path)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(200, -1)
            .diskCacheStrategy(resolveDiskCacheStrategy(path))
            .into(view)
    }

    private fun loadVideo(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(280, -1)
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }

    private fun loadImage(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .dontAnimate()
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .diskCacheStrategy(resolveDiskCacheStrategy(uri))
            .into(view)
    }


    fun loadImage(uri: Uri?, mimeType: String? = null, view: ImageView) {
        if (uri == null) return
        if (mimeType == null) {
            loadImage(uri, view)
            return
        }
        when {
            mimeType.equals("image/gif", true) -> {
                loadGif(uri, view)
            }

            mimeType.startsWith("image/", true) -> {
                loadImage(uri, view)
            }

            mimeType.startsWith("video/", true) -> {
                loadVideo(uri, view)
            }

            else -> {
                loadImageThumbnail(uri, view)
            }
        }
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