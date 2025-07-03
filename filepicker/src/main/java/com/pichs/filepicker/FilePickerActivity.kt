package com.pichs.filepicker

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import com.pichs.filepicker.FilePickerFragment.Companion.SELECT_TYPE_ALL
import com.pichs.filepicker.FilePickerFragment.Companion.SELECT_TYPE_IMAGE
import com.pichs.filepicker.FilePickerFragment.Companion.SELECT_TYPE_VIDEO
import com.pichs.filepicker.databinding.ActivityFilepickerMainBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.NavigationBarUtils
import com.pichs.filepicker.utils.PadUtils
import com.pichs.xwidget.utils.XDisplayHelper
import com.pichs.xwidget.utils.XStatusBarHelper
import kotlinx.coroutines.flow.update

class FilePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilepickerMainBinding

    private val viewModel by viewModels<FilePickerViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {

        // 判断一下，如果是是手机，则直接锁定竖屏。
        if (PadUtils.isTablet(this)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        XStatusBarHelper.transparentStatusBar(window)
        super.onCreate(savedInstanceState)
        binding = ActivityFilepickerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkNavigationBar()

        var maxSelectNumber = intent.getIntExtra("maxSelectNumber", 0)
        var maxFileSize = intent.getLongExtra("maxFileSize", 0L)
        var minFileSize = intent.getLongExtra("minFileSize", 0L)
        var selectType = intent.getStringExtra("selectType") ?: SELECT_TYPE_ALL
        var selectDataList = intent.getParcelableArrayListExtra<MediaEntity>("selectedDataList") ?: mutableListOf()

        val uiConfig = intent.getParcelableExtra<FilePickerUIConfig>("uiConfig")

        if (uiConfig != null) {
            viewModel.uiConfig = uiConfig
        }

        viewModel.originalCheckedFlow.update { viewModel.uiConfig.isOriginalChecked }
        Log.d(
            "FilePickerActivity", """
            maxSelectNumber: $maxSelectNumber, 
            maxFileSize: $maxFileSize, 
            minFileSize: $minFileSize,
             selectType: $selectType, 
             selectDataList: ${selectDataList.size}
        """.trimIndent()
        )

        // 强行 纠正数据。
        if (maxSelectNumber < 0 || maxSelectNumber == Int.MAX_VALUE) {
            maxSelectNumber = 0
        }
        if (selectType != SELECT_TYPE_ALL && selectType != SELECT_TYPE_IMAGE && selectType != SELECT_TYPE_VIDEO) {
            selectType = SELECT_TYPE_ALL
        }

        viewModel.maxFileSize.value = maxFileSize
        viewModel.minFileSize.value = minFileSize
        viewModel.selectType.value = selectType
        viewModel.maxSelectNumber.value = maxSelectNumber

        viewModel.userUseSelectDataList.value = selectDataList.toMutableList()

        // 如果有传入已选数据，则添加到已选列表中
        if (selectDataList.isNotEmpty()) {
            viewModel.selectedData.addAll(selectDataList)
            viewModel.tempSelectData.addAll(selectDataList)
        }

        // 这里可以直接使用viewModel.updateCurrentFolderDataList()方法来更新当前文件夹数据列表
        // 例如：viewModel.updateCurrentFolderDataList(selectDataList)
//        viewModel.addSelectedDataList(selectDataList)

        val fragment = FilePickerFragment.newInstance()

        supportFragmentManager.beginTransaction().add(binding.flContainer.id, fragment).show(fragment).commitAllowingStateLoss()

        addOnNewIntentListener {
            checkNavigationBar()
        }
    }

    private fun checkNavigationBar() {
        if (NavigationBarUtils.hasNavigationBar(this)) {
            binding.flContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = XDisplayHelper.getNavigationBarHeight(this@FilePickerActivity)
            }
        } else {
            binding.flContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 0
            }
        }
    }

}