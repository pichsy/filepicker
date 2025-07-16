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
//        XStatusBarHelper.transparentStatusBar(window)
    }

    val mViewModel by viewModels<FilePickerViewModel>()

    @SuppressLint("SetTextI18n")
    override fun afterOnCreate() {

        initTabLayout()
        binding.previewFragment.fastClick {
            startActivity(Intent(this, TestActivity::class.java))
        }

        // Paging3 Demo 按钮点击事件
        binding.btnPagingDemo.fastClick {
            startActivity(Intent(this, PagingDemoActivity::class.java))
        }

        // 图片分页 Demo 按钮点击事件
        binding.btnImagePagingDemo.fastClick {
            startActivity(Intent(this, ImagePagingDemoActivity::class.java))
        }

        // 本地图库 Paging3 Demo 按钮点击事件
        binding.btnLocalMediaPagingDemo.fastClick {
            startActivity(Intent(this, LocalMediaPickerActivity::class.java))
        }

        binding.btnFileQuery.fastClick {
            lifecycleScope.launch(Dispatchers.Main) {
                mViewModel.loadData(this@MainActivity)

//                // 获取最大选择数量
//                val maxSelectCount = binding.etMaxSelectCount.text.toString().toIntOrNull() ?: 0
//                // 获取最大文件大小（MB转字节）
//                val maxFileSizeMB = binding.etMaxFileSize.text.toString().toIntOrNull() ?: 200
//                val maxFileSize = /*maxFileSizeMB * 1024 * 1024*/Long.MAX_VALUE
//                // 获取类型
//                var queryTypes = mutableSetOf(
//                    QueryType.VIDEO, QueryType.IMAGE,
//                )
//
//                when (binding.rgType.checkedRadioButtonId) {
//                    binding.rbAll.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.VIDEO, QueryType.IMAGE
//                        )
//                    }
//
//                    binding.rbImage.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.IMAGE
//                        )
//                    }
//
//                    binding.rbVideo.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.VIDEO
//                        )
//                    }
//
//                    binding.rbAudio.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.AUDIO
//                        )
//                    }
//
//                    binding.rbDocument.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.NONE,
//                        )
//                    }
//
//                    binding.rbAip.id -> {
//                        queryTypes = mutableSetOf(
//                            QueryType.NONE,
//                        )
//                    }
//                }
//
//
//                val mediaResult = FileQueryHelper.queryAlbums(
//                    this@MainActivity,
//                    queryTypes = queryTypes,
//                    queryBuilder = { it ->
//                        it.sizeGreaterThan(0).and().sizeLessThan(maxFileSize)
//                        when (binding.rgType.checkedRadioButtonId) {
//                            binding.rbDocument.id -> {
//                                it.and()
//                                    .leftBracket()
//                                    .fileNameEndWith(".doc")
//                                    .or().fileNameEndWith(".docx")
//                                    .or().fileNameEndWith(".pdf")
//                                    .or().fileNameEndWith(".ppt")
//                                    .or().fileNameEndWith(".pptx")
//                                    .or().fileNameEndWith(".xls")
//                                    .or().fileNameEndWith(".xlsx")
//                                    .or().fileNameEndWith(".txt")
//                                    .rightBracket()
//                            }
//
//                            binding.rbAip.id -> {
//                                it.and()
//                                    .leftBracket()
//                                    .fileNameEndWith(".zip")
//                                    .or().fileNameEndWith(".rar")
//                                    .or().fileNameEndWith(".7z")
//                                    .or().fileNameEndWith(".tar")
//                                    .or().fileNameEndWith(".gz")
//                                    .or().fileNameEndWith(".bz2")
//                                    .or().fileNameEndWith(".iso")
//                                    .rightBracket()
//                            }
//                        }
//                    }
//                )
//
//                Log.d("MainActivity", "查询结果111：${mediaResult.mediaFolders.size} 个文件夹")
//                Log.d("MainActivity", "查询结果111：${mediaResult.mediaFolders.joinToString(",", transform = { it.name?.toString() ?: "" })}")
//                Log.d("MainActivity", "查询结果111 文件个数： ${mediaResult.mediaFolders.sumOf { it.mediaEntityList.size }}")
//
//                binding.tvResult.text = """
//                    查询结果：${mediaResult.mediaFolders.size} 个文件夹
//                    ${mediaResult.mediaFolders.joinToString(",", transform = { it.name?.toString() ?: "" })}
//                    -----------
//                    文件个数： ${mediaResult.mediaFolders.sumOf { it.mediaEntityList.size }}
//                """.trimIndent()

            }
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

            if (type == FilePicker.ofAll()
                || type == FilePicker.ofVideo()
                || type == FilePicker.ofImage()
                || type == FilePicker.ofAudio()
            ) {
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
        // 开始按钮点击事件
        binding.btnStartPaging.fastClick {

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

            if (type == FilePicker.ofAll()
                || type == FilePicker.ofVideo()
                || type == FilePicker.ofImage()
                || type == FilePicker.ofAudio()
            ) {
                // 权限请求
                XXPermissions.with(this).unchecked().permission(
                    Permission.READ_MEDIA_IMAGES,
                    Permission.READ_MEDIA_VIDEO,
                    Permission.READ_MEDIA_AUDIO,
                ).request { permissions, all ->
                    if (all) {
                        selectFilePaging(type, maxSelectCount, maxFileSize)
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
                        selectFile(type, maxSelectCount, maxFileSize)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
            }
        }

        initRecyclerView()
        initListener()
    }

    private fun initTabLayout() {
        binding.tabLayout.apply {
            addTab(
                newTab().also {
                    it.text = "全部"
                }
            )
            addTab(
                newTab().also {
                    it.text = "视频"
                }
            )
            addTab(
                newTab().also {
                    it.text = "图片"
                }
            )
        }
    }

    private fun initListener() {
//        binding.previewVideo.fastClick {
////            val intent = Intent(this, VideoPreviewActivity::class.java)
//////            intent.putExtra("videoUrl", "https://jianliu.oss-cn-hangzhou.aliyuncs.com/jianliu/render_video/ed96ba31-6902-4acd-bae6-13a4a9d46fde.mp4")
////            startActivity(intent)
//
//
//            VideoPreviewDialog(
//                this,
//                "https://jianliu.oss-cn-hangzhou.aliyuncs.com/jianliu/render_video/ed96ba31-6902-4acd-bae6-13a4a9d46fde.mp4",
//                "https://jianliu.oss-cn-hangzhou.aliyuncs.com/jianliu/render_video/ed96ba31-6902-4acd-bae6-13a4a9d46fde.mp4?x-oss-process=video/snapshot,t_10,f_jpg,w_360,m_fast,ar_auto,mode_crop",
//            ).showPopupWindow()
//        }
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
                            this@MainActivity,
                            mediaEntity.path,
                        ).showPopupWindow()
                    } else {
                        ImagePreviewDialog(this@MainActivity, mediaEntity.path).showPopupWindow()
                    }
                }
            }
        }
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int) {
        FilePicker.with(this)
            .setMaxSelectNumber(maxSelectCount)
            .setMaxFileSize(maxFileSize.toLong())
            .setSelectType(type)
            .setSingleClickEnable(true)
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
        FilePicker.with(this)
            .setMaxSelectNumber(maxSelectCount)
            .setMaxFileSize(maxFileSize.toLong())
            .setSelectType(type)
            .setSingleClickEnable(true)
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