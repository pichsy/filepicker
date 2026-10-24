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
import com.pichs.filepicker.select.SelectRule
import com.pichs.filepicker.select.SelectRulesEngine
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
        // 默认 false：不开启时不会挂相机 header，也不会碰相机权限
        val showCameraEntry = intent.getBooleanExtra("showCameraEntry", false)

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
        viewModel.showCameraEntry.value = showCameraEntry
        viewModel.cropAspect.value = intent.getFloatExtra("cropAspect", 0f)
        viewModel.cropOutputMaxSize.value = intent.getIntExtra("cropOutputMaxSize", 2048)
        viewModel.cropQuality.value = intent.getIntExtra("cropQuality", 90)
        viewModel.editInPreview.value = intent.getBooleanExtra("editInPreview", false)
        // 编辑缓存目录：宿主传了就用自己的，没传用默认 Android/data/<包名>/cache/filepicker
        com.pichs.filepicker.crop.FilePickerEditCache.customDir =
            intent.getStringExtra("editCacheDir")?.takeIf { it.isNotEmpty() }?.let { java.io.File(it) }
        // 兜底清理：删掉超过 24h 的编辑缓存（崩溃/被杀进程的残留）。
        // 注意：**不在返回结果后清理**，宿主可能还要把文件另存到自己的位置。
        kotlin.concurrent.thread { com.pichs.filepicker.crop.CropEngine.pruneCache(this) }

        // 按类型限量模型：合计上限由规则自动叠加，maxSelectNumber 在该模式下不生效。
        // 注意：没配任何规则时这里什么都不做，仍然走 setMaxSelectNumber（setExclusive 不生效）。
        if (intent.getBooleanExtra("useSelectRules", false)) run {
            val rules = intent.getStringArrayListExtra("selectRules")
                ?.mapNotNull { SelectRule.decode(it) }
                ?: emptyList()
            val engine = SelectRulesEngine.createOrNull(
                rules = rules,
                exclusive = intent.getBooleanExtra("selectExclusive", false),
                gifCountsAsImage = intent.getBooleanExtra("gifCountsAsImage", true),
            )
            if (engine == null) {
                FilePickerLog.w(
                    "FilePickerActivity",
                    "没有配置任何规则：setExclusive / setGifCountsAsImage 不生效，" +
                            "数量限制仍由 setMaxSelectNumber($maxSelectNumber) 决定"
                )
                return@run
            }
            viewModel.setSelectRulesEngine(engine)
            FilePickerLog.d("FilePickerActivity", "启用按类型限量规则：${engine.describe()}")
            if (maxSelectNumber != 0) {
                FilePickerLog.w(
                    "FilePickerActivity",
                    "setMaxSelectNumber($maxSelectNumber) 在规则模式下不生效：合计上限由规则自动叠加得出"
                )
            }
            if (singleClickEnable && engine.totalCapacity() != 1) {
                FilePickerLog.w(
                    "FilePickerActivity",
                    "singleClickEnable=true 但总容量=${engine.totalCapacity()}（0=不限），单击即返回不会生效"
                )
            }
        }

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