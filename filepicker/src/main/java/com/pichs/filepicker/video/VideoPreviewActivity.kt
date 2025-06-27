package com.pichs.filepicker.video

import android.net.Uri
import android.os.Bundle
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

        videoUrl = if (videoUrl.isEmpty()) {
            "/sdcard/DCIM/Camera/VID_20250627_183943.mp4" // 默认测试视频路径
        } else {
            videoUrl
        }

        videoUrl = "/sdcard/DCIM/Camera/VID_20250627_183943.mp4"

        binding.videoPlayerView.loadCover(
            MediaEntity(
                uri = Uri.parse(videoUrl),
                path = videoUrl,
                mimeType = "video/*",
            )
        )

//        binding.videoPlayerView.loadVideoAndPlay(videoUrl)
        binding.videoPlayerView.setOnControllerVisibilityChangedListener {
            binding.clToolbar.isVisible = it
        }
    }

    override fun onDestroy() {
        binding.videoPlayerView.releasePlayer()
        super.onDestroy()
    }

}