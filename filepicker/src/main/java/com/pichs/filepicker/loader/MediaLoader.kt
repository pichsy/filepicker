package com.pichs.filepicker.loader

import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pichs.filepicker.R

object MediaLoader {

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
                loadImage(uri, view)
            }
        }
    }


    private fun loadGif(uri: Uri, view: ImageView) {
        Glide.with(view)
            .asGif()
            .load(uri)
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    private fun loadImage(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .override(200, 200)
            .dontTransform()
            .dontAnimate()
            .placeholder(R.drawable.placeholder_image)
            .error(R.drawable.placeholder_image)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    private fun loadVideo(uri: Uri, view: ImageView) {
        Glide.with(view)
            .load(uri)
            .dontTransform()
            .error(R.drawable.placeholder_image)
            .placeholder(R.drawable.placeholder_image)
            .override(200, 200)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

    fun loadVideoCover(path: String, view: ImageView) {
        Glide.with(view)
            .load(path)
            .dontTransform()
            .error(R.drawable.placeholder_image)
            .placeholder(R.drawable.placeholder_image)
            .override(200, -1)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .into(view)
    }

}