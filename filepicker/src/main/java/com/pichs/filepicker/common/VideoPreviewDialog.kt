package com.pichs.filepicker.common

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import androidx.databinding.DataBindingUtil.setContentView
import com.pichs.filepicker.R
import com.pichs.filepicker.databinding.DialogFilepickerVideoPreviewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import razerdp.basepopup.BasePopupWindow
import java.io.File

/**
 * @param context Context
 * @param videoUrl 视频地址， 可以是本地路径或网络地址
 * @param videoCover 视频封面，如果是本地视频可不传，如果是网络视频建议传入封面地址。不然就是黑的封面。
 */
@OptIn(UnstableApi::class)
class VideoPreviewDialog(
    context: Context,
    val videoUrl: String?,
    val videoCover: String? = null,
    var hideStatusHeight: Boolean = false
) : BasePopupWindow(context), CoroutineScope by MainScope() {

    private lateinit var binding: DialogFilepickerVideoPreviewBinding

    private val isShowToolbarFlow = MutableStateFlow(true)

    init {
        setContentView(R.layout.dialog_filepicker_video_preview)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = DialogFilepickerVideoPreviewBinding.bind(contentView)
        setOutSideDismiss(false)
        setOutSideTouchable(false)
        setPopupGravity(Gravity.CENTER)
        binding.statusBarView.isVisible = !hideStatusHeight

        launch {
            isShowToolbarFlow.collectLatest {
                binding.statusBarView.isVisible = it && !hideStatusHeight
                binding.clToolbar.isVisible = it
            }
        }

        if (videoUrl.isNullOrEmpty()) {
            Toast.makeText(context.applicationContext, "请传入正确的视频地址", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        binding.ivBack.setOnClickListener {
            dismiss()
        }

        val uri = if (videoUrl.startsWith("http") || videoUrl.startsWith("https")) {
            videoUrl.toUri()
        } else if (File(videoUrl).exists()) {
            FilePickerUriUtils.getUriFromFile(context, File(videoUrl))
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

    override fun onBeforeDismiss(): Boolean {
        binding.videoPlayerView.releasePlayer()
        return super.onBeforeDismiss()
    }

}