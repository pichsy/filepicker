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
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }


    private fun loadVideoThumbnail(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(200, 200)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }


    fun loadVideoCover(path: String, view: ImageView) {
        Glide.with(view)
            .load(path)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(200, -1)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    private fun loadVideo(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .error(R.drawable.filepicker_placeholder_image)
            .placeholder(R.drawable.filepicker_placeholder_image)
            .override(280, -1)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    private fun loadImage(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .dontAnimate()
            .placeholder(R.drawable.filepicker_placeholder_image)
            .error(R.drawable.filepicker_placeholder_image)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
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


}