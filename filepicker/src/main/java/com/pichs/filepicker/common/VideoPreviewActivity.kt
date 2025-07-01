package com.pichs.filepicker.common

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.pichs.filepicker.databinding.ActivityFilepickerVideoPreviewBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.FilePickerUriUtils
import com.pichs.xwidget.utils.XStatusBarHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import java.io.File

@OptIn(UnstableApi::class)
class VideoPreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilepickerVideoPreviewBinding

    private val isShowToolbarFlow = MutableStateFlow(true)

    private var videoUrl = ""
    private var videoCover: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        XStatusBarHelper.transparentStatusBar(this.window)
        super.onCreate(savedInstanceState)
        binding = ActivityFilepickerVideoPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            launch {
                isShowToolbarFlow.collectLatest {
                    binding.statusBarSpace.isVisible = it
                    binding.clToolbar.isVisible = it
                }
            }
        }

        binding.ivBack.setOnClickListener {
            finish()
        }

        videoUrl = intent.getStringExtra("videoUrl") ?: ""
        videoCover = intent.getStringExtra("videoCover")

        if (videoUrl.isNotEmpty()) {
            Toast.makeText(applicationContext, "请传入正确的视频地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = if (videoUrl.startsWith("http") || videoUrl.startsWith("https")) {
            videoUrl.toUri()
        } else if (File(videoUrl).exists()) {
            FilePickerUriUtils.getUriFromFile(this, File(videoUrl))
        } else {
            null
        }

        binding.videoPlayerView.loadCover(
            MediaEntity(
                uri = uri,
                path = videoUrl,
                mimeType = "video/*",
            ), videoCover
        )

        binding.videoPlayerView.setOnSingleClickListener {
            isShowToolbarFlow.update { isShowToolbarFlow.value.not() }
        }
    }

    override fun onDestroy() {
        binding.videoPlayerView.releasePlayer()
        super.onDestroy()
    }

}