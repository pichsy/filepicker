package com.pichs.filepicker

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import com.pichs.filepicker.databinding.ActivityFilepickerMainBinding
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.utils.FilePickerLog
import com.pichs.filepicker.utils.FilePickerPadUtils
import com.pichs.xwidget.utils.XNavigationBarUtils
import com.pichs.xwidget.utils.XStatusBarHelper
import kotlinx.coroutines.flow.update

class FilePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilepickerMainBinding

    private val viewModel by viewModels<FilePickerViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {

        // 判断一下，如果是是手机，则直接锁定竖屏。
        if (FilePickerPadUtils.isTablet(this)) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        XStatusBarHelper.transparentStatusBar(window)
        super.onCreate(savedInstanceState)

        binding = ActivityFilepickerMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView, object : OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
                checkNavigationBar()
                return insets
            }

        })

        var maxSelectNumber = intent.getIntExtra("maxSelectNumber", 0)
        var maxFileSize = intent.getLongExtra("maxFileSize", 0L)
        var minFileSize = intent.getLongExtra("minFileSize", 0L)
        var selectType = intent.getStringExtra("selectType") ?: FilePickerSelectType.IMAGE_VIDEO
        var singleClickEnable = intent.getBooleanExtra("singleClickEnable", false)
        var slideChooseEnable = intent.getBooleanExtra("slideChooseEnable", true)

        // uiConfigData 是拍平的纯基本类型 Bundle（避免自定义 Parcelable 过 system_server
        // 反序列化报 ClassNotFoundException）；旧 key 保留兼容直接以 Parcelable extra
        // 启动本 Activity 的外部调用方
        val uiConfig = FilePickerUIConfig.fromTransportBundle(intent.getBundleExtra("uiConfigData"))
            ?: intent.getParcelableExtra<FilePickerUIConfig>("uiConfig")

        if (uiConfig != null) {
            viewModel.uiConfig = uiConfig
        }

        viewModel.originalCheckedFlow.update { viewModel.uiConfig.isOriginalChecked }

        if (maxFileSize < 0) {
            maxFileSize = 0L
        }

        if (minFileSize < 0) {
            minFileSize = 0L
        }

        FilePickerLog.d(
            """
            maxSelectNumber: $maxSelectNumber, 
            maxFileSize: $maxFileSize, 
            minFileSize: $minFileSize,
             selectType: $selectType, 
             selectDataList: ${FilePickerViewModel.userUseSelectDataList.size}
        """.trimIndent()
        )

        // 强行 纠正数据。
        if (maxSelectNumber < 0 || maxSelectNumber == Int.MAX_VALUE) {
            maxSelectNumber = 0
        }

        if (!SelectTypeUtil.isValidType(selectType)) {
            selectType = FilePickerSelectType.IMAGE_VIDEO
        }

        viewModel.maxFileSize.value = maxFileSize
        viewModel.minFileSize.value = minFileSize
        viewModel.selectType.value = selectType
        viewModel.maxSelectNumber.value = maxSelectNumber
        viewModel.singleClickEnable.value = singleClickEnable
        viewModel.slideChooseEnable.value = slideChooseEnable

        // 如果有传入已选数据，则添加到已选列表中
//        if (FilePickerViewModel.userUseSelectDataList.value.isNotEmpty()) {
//            viewModel.selectedData.addAll(FilePickerViewModel.userUseSelectDataList.value)
//        }

        // 这里可以直接使用viewModel.updateCurrentFolderDataList()方法来更新当前文件夹数据列表
        // 例如：viewModel.updateCurrentFolderDataList(selectDataList)
//        viewModel.addSelectedDataList(selectDataList)

        val fragment = FilePickerFragment.newInstance()

        supportFragmentManager.beginTransaction().add(binding.flContainer.id, fragment).show(fragment).commitAllowingStateLoss()

    }

    private fun checkNavigationBar() {
        if (XNavigationBarUtils.isGestureBarVisible(this)) {
            binding.flContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = XNavigationBarUtils.getNavigationBarHeight(this@FilePickerActivity)
            }
        } else {
            binding.flContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 0
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        checkNavigationBar()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        checkNavigationBar()
    }

    override fun onPause() {
        super.onPause()
        viewModel.sendActivityLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onResume() {
        super.onResume()
        viewModel.sendActivityLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}