package com.pichs.filepicker.demo

import android.content.Intent
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.databinding.ItemRvAlbumBinding
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.video.VideoPreviewActivity
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick
import com.pichs.xbase.xlog.XLog

class MainActivity : BindingActivity<ActivityMainBinding>() {


    override fun afterOnCreate() {
        // 开始按钮点击事件
        binding.btnStart.fastClick {
            // 获取最大选择数量
            val maxSelectCount = binding.etMaxSelectCount.text.toString().toIntOrNull() ?: 0
            // 获取最大文件大小（MB转字节）
            val maxFileSizeMB = binding.etMaxFileSize.text.toString().toIntOrNull() ?: 200
            val maxFileSize = maxFileSizeMB * 1024 * 1024
            // 获取类型
            val type = when (binding.rgType.checkedRadioButtonId) {
                binding.rbAll.id -> "all"
                binding.rbImage.id -> "image"
                binding.rbVideo.id -> "video"
                else -> "all"
            }
            // 权限请求
            XXPermissions.with(this)
                .unchecked()
                .permission(
                    Permission.READ_MEDIA_IMAGES,
                    Permission.READ_MEDIA_VIDEO,
                    Permission.READ_MEDIA_AUDIO,
                ).request { permissions, all ->
                    if (all) {
                        selectFile(type, maxSelectCount, maxFileSize)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
        }

        initRecyclerView()

        initListener()
    }

    private fun initListener() {
//        VideoPreviewActivity:
        binding.previewVideo.fastClick {
            val intent = Intent(this, VideoPreviewActivity::class.java)
//            intent.putExtra("videoUrl", "https://jianliu.oss-cn-hangzhou.aliyuncs.com/jianliu/render_video/ed96ba31-6902-4acd-bae6-13a4a9d46fde.mp4")
            startActivity(intent)
        }
    }

    private fun initRecyclerView() {

        binding.recyclerView.linear(RecyclerView.HORIZONTAL).setup {
            addType<MediaEntity>(com.pichs.filepicker.R.layout.item_rv_album)

            onBind {
                val mediaEntity = getModel<MediaEntity>()
                val binding = getBinding<ItemRvAlbumBinding>()
                binding.clSelectArea.isVisible = false
                Glide.with(this@MainActivity)
                    .load(mediaEntity.path)
                    .into(binding.ivCoverImage)
            }
        }
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int) {
        FilePicker.with(this)
            .setMaxSelectNumber(maxSelectCount)
            .setMaxFileSize(maxFileSize.toLong())
            .setRequestCode(1029)
            .apply {
                when (type) {
                    "all" -> selectAll()
                    "image" -> selectImage()
                    "video" -> selectVideo()
                }
            }
            .setOnSelectCallback {
                XLog.d("FilePicker", "Selected files: ${it.size}")
                binding.recyclerView.models = it
            }.build().start()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        FilePicker.get().onActivityResult(requestCode, resultCode, data)
    }

}