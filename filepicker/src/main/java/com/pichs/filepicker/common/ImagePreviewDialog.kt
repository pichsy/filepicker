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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pichs.filepicker.R
import com.pichs.filepicker.databinding.DialogFilepickerImagePreviewBinding
import com.pichs.filepicker.databinding.DialogFilepickerVideoPreviewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import razerdp.basepopup.BasePopupWindow
import java.io.File

/**
 * @param context Context
 * @param url 图片地址， 可以是本地路径或网络地址
 */
@OptIn(UnstableApi::class)
class ImagePreviewDialog(
    context: Context,
    val url: String?,
    var hideStatusHeight: Boolean = false
) : BasePopupWindow(context), CoroutineScope by MainScope() {

    private lateinit var binding: DialogFilepickerImagePreviewBinding

    private val isShowToolbarFlow = MutableStateFlow(true)

    init {
        setContentView(R.layout.dialog_filepicker_image_preview)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = DialogFilepickerImagePreviewBinding.bind(contentView)
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

        if (url.isNullOrEmpty()) {
            Toast.makeText(context.applicationContext, "请传入正确的视频地址", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        binding.ivBack.setOnClickListener {
            dismiss()
        }

        binding.photoView.setOnClickListener {
            isShowToolbarFlow.update { isShowToolbarFlow.value.not() }
        }

        Glide.with(binding.photoView)
            .load(url)
            .dontTransform()
            .dontAnimate()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.photoView)

    }

}