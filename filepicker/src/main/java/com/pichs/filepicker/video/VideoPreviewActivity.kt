package com.pichs.filepicker.video

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import com.pichs.filepicker.databinding.ActivityFilepickerVideoPreviewBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.xwidget.utils.XStatusBarHelper

@OptIn(UnstableApi::class)
class VideoPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilepickerVideoPreviewBinding

    private var videoUrl = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        XStatusBarHelper.transparentStatusBar(this.window)
        super.onCreate(savedInstanceState)
        binding = ActivityFilepickerVideoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener {
            finish()
        }

        videoUrl = intent.getStringExtra("videoUrl") ?: ""

        if (videoUrl.isNotEmpty()) {
            Toast.makeText(applicationContext, "请传入正确的视频地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.videoPlayerView.loadCover(
            MediaEntity(
                uri = Uri.parse(videoUrl),
                path = videoUrl,
                mimeType = "video/*",
            )
        )

        binding.videoPlayerView.setOnControllerVisibilityChangedListener {
            binding.clToolbar.isVisible = it
        }
    }

    override fun onDestroy() {
        binding.videoPlayerView.releasePlayer()
        super.onDestroy()
    }

}