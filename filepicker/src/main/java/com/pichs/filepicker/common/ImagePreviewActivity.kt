package com.pichs.filepicker.common

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.pichs.xwidget.utils.XStatusBarHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.pichs.filepicker.databinding.ActivityFilepickerImagePreviewBinding
import kotlinx.coroutines.flow.update

@OptIn(UnstableApi::class)
class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFilepickerImagePreviewBinding

    private val isShowToolbarFlow = MutableStateFlow(true)

    private var imageUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        XStatusBarHelper.transparentStatusBar(this.window)
        super.onCreate(savedInstanceState)
        binding = ActivityFilepickerImagePreviewBinding.inflate(layoutInflater)
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

        imageUrl = intent.getStringExtra("url") ?: ""

        if (imageUrl.isNotEmpty()) {
            Toast.makeText(applicationContext, "请传入正确的图片地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Glide.with(this)
            .load(imageUrl)
            .dontTransform()
            .dontAnimate()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.photoView)


        binding.photoView.setOnClickListener {
            isShowToolbarFlow.update { isShowToolbarFlow.value.not() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}