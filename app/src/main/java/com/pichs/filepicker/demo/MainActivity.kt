package com.pichs.filepicker.demo

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.autofill.Validators.and
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.drake.brv.utils.divider
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
import com.pichs.filepicker.demo.databinding.ItemTypeChipBinding
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

        // 数据制造入口：批量生成测试文件
        binding.btnDataFactory.fastClick {
            startActivity(Intent(this, DataFactoryActivity::class.java))
        }

        // 开始按钮点击事件
        binding.btnStart.fastClick {
            // 获取最大选择数量
            val maxSelectCount = binding.etMaxSelectCount.text.toString().toIntOrNull() ?: 0
            // 获取最大文件大小（MB转字节）
            val maxFileSizeMB = binding.etMaxFileSize.text.toString().toIntOrNull() ?: 200
            val maxFileSize = maxFileSizeMB * 1024 * 1024
            // 获取类型
            val type = selectedType

            if (type == FilePickerSelectType.ofAll() || type == FilePickerSelectType.ofVideo() || type == FilePickerSelectType.ofImage() || type == FilePickerSelectType.ofAudio() || type == FilePickerSelectType.ofGif() || type == FilePickerSelectType.ofAllWithGif()) {
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
                        selectFile(type, maxSelectCount, maxFileSize)
                    } else {
                        XXPermissions.startPermissionActivity(this, permissions)
                    }
                }
            }
        }
        initRecyclerView()
        initTypeList()
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
                            title = mediaEntity.name,
                            videoUrl = mediaEntity.path,
                        ).showPopupWindow()
                    } else {
                        ImagePreviewDialog(
                            context = this@MainActivity, title = mediaEntity.name, url = mediaEntity.path
                        ).showPopupWindow()
                    }
                }
            }
        }
    }

    private var selectedDataList = mutableListOf<MediaEntity>()

    /** 类型单选列表 */
    private data class PickerType(val label: String, val type: String)

    private var selectedType: String = FilePickerSelectType.ofAll()

    private fun initTypeList() {
        val types = listOf(
            PickerType("全部", FilePickerSelectType.ofAll()),
            PickerType("图片", FilePickerSelectType.ofImage()),
            PickerType("视频", FilePickerSelectType.ofVideo()),
            PickerType("GIF", FilePickerSelectType.ofGif()),
            PickerType("音频", FilePickerSelectType.ofAudio()),
            PickerType("文档", FilePickerSelectType.ofDocument()),
            PickerType("pdf", FilePickerSelectType.ofPdf()),
            PickerType("doc", FilePickerSelectType.ofDoc()),
            PickerType("ppt", FilePickerSelectType.ofPpt()),
            PickerType("excel", FilePickerSelectType.ofExcel()),
            PickerType("txt", FilePickerSelectType.ofTxt()),
            PickerType("APK", FilePickerSelectType.ofApk()),
            PickerType("zip_all", FilePickerSelectType.ofZipAll()),
            PickerType("zip", FilePickerSelectType.ofZip()),
            PickerType("rar", FilePickerSelectType.ofRar()),
            PickerType("7z", FilePickerSelectType.of7Z()),
            // 自定义后缀过滤示例：任意后缀组合，不再需要为每种后缀加 API
            PickerType("自定义(xz,br)", FilePickerSelectType.ofExtensions("xz", "br")),
            PickerType("自定义(GZ,TGZ)", FilePickerSelectType.ofExtensions("gz,tgz")),
        )
        binding.rvType.layoutManager = GridLayoutManager(this, 2, GridLayoutManager.HORIZONTAL, false)
        binding.rvType.divider {
            setDivider(8, true)
        }.setup {
            addType<PickerType>(R.layout.item_type_chip)
            onBind {
                val item = getModel<PickerType>()
                val itemBinding = getBinding<ItemTypeChipBinding>()
                itemBinding.tvTypeName.text = item.label
                val selected = item.type == selectedType
                itemBinding.tvTypeName.isChecked = selected
//                itemBinding.tvTypeName.setBackgroundColor(Color.TRANSPARENT)
//                itemBinding.tvTypeName.setTextColor(if (selected) 0xFF2196F3.toInt() else 0xFFCCCCCC.toInt())
                itemBinding.tvTypeName.paint.isFakeBoldText = selected
                itemBinding.tvTypeName.setOnClickListener {
                    if (selectedType != item.type) {
                        selectedType = item.type
                        binding.rvType.adapter?.notifyDataSetChanged()
                    }
                }
            }
        }
        binding.rvType.models = types
    }

    fun selectFile(type: String, maxSelectCount: Int, maxFileSize: Int) {
        FilePicker.with(this).setMaxSelectNumber(maxSelectCount)
            .setMaxFileSize(maxFileSize.toLong()).setSelectType(type)
            .setSingleClickEnable(true)
            .setSelectedList(selectedDataList).setOnSelectCallback { isUseOriginal, list ->
                XLog.d("FilePicker", "Selected files: ${list.size}")
                selectedDataList = list
                binding.recyclerView.models = list
            }.setUiConfig(
                FilePickerUIConfig(
                    isHideSelectTab = false,
                    allAlbumName = "全部",
                    confirmBtnText = "发送",
                    isShowOriginal = false,
                    isPreviewPageIndexMode = true,
                    isShowSelectedListDeleteIcon = true,
                    folderNickNameMap = hashMapOf(
                        "DCIM" to "相册", "Download" to "下载"
                    )
                )
            ).start()
    }

}