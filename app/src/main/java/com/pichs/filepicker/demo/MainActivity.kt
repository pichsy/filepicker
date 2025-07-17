package com.pichs.filepicker.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.autofill.Validators.and
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.pichs.filepicker.FilePicker
import com.pichs.filepicker.FilePickerSelectType
import com.pichs.filepicker.FilePickerUIConfig
import com.pichs.filepicker.FilePickerViewModel
import com.pichs.filepicker.common.ImagePreviewDialog
import com.pichs.filepicker.demo.databinding.ActivityMainBinding
import com.pichs.filepicker.demo.databinding.ItemImageBinding
import com.pichs.filepicker.demo.paging.PagingDemoActivity
import com.pichs.filepicker.demo.paging.ImagePagingDemoActivity
import com.pichs.filepicker.demo.newpicker.LocalMediaPickerActivity
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.common.VideoPreviewDialog
import com.pichs.filepicker.query.FileQueryHelper
import com.pichs.filepicker.query.QueryType
import com.pichs.xbase.binding.BindingActivity
import com.pichs.xbase.kotlinext.fastClick
import com.pichs.xbase.xlog.XLog
import com.pichs.xbase.xlog.XLogFileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BindingActivity<ActivityMainBinding>() {

    override fun beforeOnCreate(savedInstanceState: Bundle?) {
        super.beforeOnCreate(savedInstanceState)
    }

    @SuppressLint("SetTextI18n")
    override fun afterOnCreate() {
        binding.previewFragment.fastClick {
            startActivity(Intent(this, TestActivity::class.java))
        }

        // 开始按钮点击事件
        binding.btnStart.fastClick {
            // 获取最大选择数量
            val maxSelectCount = binding.etMaxSelectCount.text.toString().toIntOrNull() ?: 0
            // 获取最大文件大小（MB转字节）
            val maxFileSizeMB = binding.etMaxFileSize.text.toString().toIntOrNull() ?: 200
            val maxFileSize = maxFileSizeMB * 1024 * 1024
            // 获取类型
            val type = when (binding.rgType.checkedRadioButtonId) {
                binding.rbAll.id -> FilePicker.ofAll()
                binding.rbImage.id -> FilePicker.ofImage()
                binding.rbVideo.id -> FilePicker.ofVideo()
                binding.rbAudio.id -> FilePicker.ofAudio()
                binding.rbDocument.id -> FilePicker.ofDocument()
                binding.rbAip.id -> FilePicker.ofZipAll()
                else -> FilePicker.ofAll()
            }

            if (type == FilePicker.ofAll() || type == FilePicker.ofVideo() || type == FilePicker.ofImage() || type == FilePicker.ofAudio()) {
                // 权限请求
                XXPermissions.with(this).unchecked().permission(
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
            } else {
                // 权限请求
                XXPermissions.with(this).unchecked().permission(
                    Permission.MANAGE_EXTERNAL_STORAGE,
                ).request { permissions, all ->
                    if (all) {
                        selectFilePaging(type, maxSelectCount, maxFileSize)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
            }
        }

        initRecyclerView()
    }

    private fun initRecyclerView() {
        binding.recyclerView.linear(RecyclerView.HORIZONTAL).setup {
            addType<MediaEntity>(R.layout.item_image)

            onBind {
                val mediaEntity = getModel<MediaEntity>()
                val itemBinding = getBinding<ItemImageBinding>()
                itemBinding.tvIndex.text = "${modelPosition + 1}"
                Glide.with(this@MainActivity).load(mediaEntity.path).into(itemBinding.ivImg)
                itemBinding.ivImg.setOnClickListener {
                    XLog.d("MainActivity", "Clicked on image: ${mediaEntity.path}")
                    if (mediaEntity.isVideo()) {
                        VideoPreviewDialog(
                            context = this@MainActivity,
                            title = "视频预览",
                            videoUrl = mediaEntity.path,
                        ).showPopupWindow()
                    } else {
                        ImagePreviewDialog(
                            context = this@MainActivity,
                            title = "图片预览",
                            url = mediaEntity.path
                        ).showPopupWindow()
                    }
                }
            }
        }
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int) {
        FilePicker.with(this).setMaxSelectNumber(maxSelectCount).setMaxFileSize(maxFileSize.toLong()).setSelectType(type).setSingleClickEnable(true)
            .setOnSelectCallback { isUseOriginal, list ->
                XLog.d("FilePicker", "Selected files: ${list.size}")
                binding.recyclerView.models = list
            }.setUiConfig(
                FilePickerUIConfig(
                    isHideSelectTab = false,
                    allAlbumName = "全部",
                    confirmBtnText = "下一步",
                    isShowOriginal = false,
                    isPreviewPageIndexMode = true,
                    isShowSelectedListDeleteIcon = true,
                )
            ).start()
    }

    fun selectFilePaging(type: String, maxSelectCount: Int, maxFileSize: Int) {
        FilePicker.with(this).setMaxSelectNumber(maxSelectCount).setMaxFileSize(maxFileSize.toLong()).setSelectType(type).setSingleClickEnable(true)
            .setOnSelectCallback { isUseOriginal, list ->
                XLog.d("FilePicker", "Selected files: ${list.size}")

                binding.recyclerView.models = list
            }.setUiConfig(
                FilePickerUIConfig(
                    isHideSelectTab = false,
                    allAlbumName = "全部",
                    confirmBtnText = "下一步",
                    isShowOriginal = false,
                    isPreviewPageIndexMode = true,
                    isShowSelectedListDeleteIcon = true,
                )
            ).start()
    }
}