package com.pichs.filepicker

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.pichs.filepicker.empty.CallbackFragment
import com.pichs.filepicker.entity.MediaEntity
import kotlinx.coroutines.flow.update
import kotlin.collections.toMutableList

/**
 * FilePicker入口API
 * ofImage()，ofVideo()， ofAll()，ofAllWithGif()，ofGif(), ofAudio() //这几个获取方式必须申请 读写权限。可不用文件管理权限。
 * 但是剩下的全部 方式，都必须申请 文件管理权限。否则拿不到数据。
 * 使用时请先自行申请权限。方便个人定制，不会写到库里，定制起来太麻烦。
 */
fun interface OnSelectCallback {
    fun onSelectedCallback(isUseOriginal: Boolean, list: MutableList<MediaEntity>)
    fun onCancel() {
        // 取消选择时的回调
    }
}

class FilePicker {

    private var builder: Builder? = null

    fun setBuilder(builder: Builder): FilePicker {
        this.builder = builder
        return this
    }

    companion object {

        private val _instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            FilePicker()
        }

        fun get(): FilePicker {
            return _instance
        }

        fun with(activity: FragmentActivity): Builder {
            return Builder(activity).apply {
                get().setBuilder(this)
            }
        }

        fun with(fragment: Fragment): Builder {
            return Builder(fragment).apply {
                get().setBuilder(this)
            }
        }

        // ----------------------------------------------------------------
        // 已废弃：类型入口 API 统一收敛到 FilePickerSelectType（语义更贴合）。
        // 以下方法保留转发、保证老代码可用，请尽快迁移到 FilePickerSelectType.ofXxx()。
        // tar/gz/bz2/iso/br/lz4/zstd/xz 等压缩格式用 FilePickerSelectType.ofExtensions(...) 自定义后缀过滤。
        // ----------------------------------------------------------------

        @Deprecated("换用 FilePickerSelectType.ofAll()", replaceWith = ReplaceWith("FilePickerSelectType.ofAll()"))
        fun ofAll(): String {
            return FilePickerSelectType.IMAGE_VIDEO
        }

        @Deprecated("换用 FilePickerSelectType.ofAllWithGif()", replaceWith = ReplaceWith("FilePickerSelectType.ofAllWithGif()"))
        fun ofAllWithGif(): String {
            return FilePickerSelectType.IMAGE_VIDEO_GIF
        }

        @Deprecated("换用 FilePickerSelectType.ofImage()", replaceWith = ReplaceWith("FilePickerSelectType.ofImage()"))
        fun ofImage(): String {
            return FilePickerSelectType.IMAGE
        }

        @Deprecated("换用 FilePickerSelectType.ofVideo()", replaceWith = ReplaceWith("FilePickerSelectType.ofVideo()"))
        fun ofVideo(): String {
            return FilePickerSelectType.VIDEO
        }

        @Deprecated("换用 FilePickerSelectType.ofGif()", replaceWith = ReplaceWith("FilePickerSelectType.ofGif()"))
        fun ofGif(): String {
            return FilePickerSelectType.GIF
        }

        @Deprecated("换用 FilePickerSelectType.ofAudio()", replaceWith = ReplaceWith("FilePickerSelectType.ofAudio()"))
        fun ofAudio(): String {
            return FilePickerSelectType.AUDIO
        }

        @Deprecated("换用 FilePickerSelectType.ofDocument()", replaceWith = ReplaceWith("FilePickerSelectType.ofDocument()"))
        fun ofDocument(): String {
            return FilePickerSelectType.DOCUMENT
        }

        @Deprecated("换用 FilePickerSelectType.ofPdf()", replaceWith = ReplaceWith("FilePickerSelectType.ofPdf()"))
        fun ofPdf(): String {
            return FilePickerSelectType.PDF
        }

        @Deprecated("换用 FilePickerSelectType.ofDoc()", replaceWith = ReplaceWith("FilePickerSelectType.ofDoc()"))
        fun ofDoc(): String {
            return FilePickerSelectType.DOC
        }

        @Deprecated("换用 FilePickerSelectType.ofPpt()", replaceWith = ReplaceWith("FilePickerSelectType.ofPpt()"))
        fun ofPpt(): String {
            return FilePickerSelectType.PPT
        }

        @Deprecated("换用 FilePickerSelectType.ofExcel()", replaceWith = ReplaceWith("FilePickerSelectType.ofExcel()"))
        fun ofExcel(): String {
            return FilePickerSelectType.EXCEL
        }

        @Deprecated("换用 FilePickerSelectType.ofTxt()", replaceWith = ReplaceWith("FilePickerSelectType.ofTxt()"))
        fun ofTxt(): String {
            return FilePickerSelectType.TXT
        }

        @Deprecated("换用 FilePickerSelectType.ofApk()", replaceWith = ReplaceWith("FilePickerSelectType.ofApk()"))
        fun ofApk(): String {
            return FilePickerSelectType.APK
        }

        @Deprecated("换用 FilePickerSelectType.ofZipAll()", replaceWith = ReplaceWith("FilePickerSelectType.ofZipAll()"))
        fun ofZipAll(): String {
            return FilePickerSelectType.ZIP_ALL
        }

        @Deprecated("换用 FilePickerSelectType.ofZip()", replaceWith = ReplaceWith("FilePickerSelectType.ofZip()"))
        fun ofZip(): String {
            return FilePickerSelectType.ZIP
        }

        @Deprecated("换用 FilePickerSelectType.ofRar()", replaceWith = ReplaceWith("FilePickerSelectType.ofRar()"))
        fun ofRar(): String {
            return FilePickerSelectType.RAR
        }

        @Deprecated("换用 FilePickerSelectType.of7Z()", replaceWith = ReplaceWith("FilePickerSelectType.of7Z()"))
        fun of7Z(): String {
            return FilePickerSelectType.SEVEN_Z
        }

        /**
         * 将 MediaEntity 列表转换为路径列表
         */
        fun convertToPathList(list: MutableList<MediaEntity>): MutableList<String> {
            return list.map { it ->
                it.path ?: ""
            }.toMutableList()
        }

        fun convertToEntityList(list: List<String>): MutableList<MediaEntity> {
            return list.map { it ->
                MediaEntity.fromPath(it)
            }.toMutableList()
        }
    }


    class Builder {
        private var mActivity: FragmentActivity? = null
        private var mFragment: Fragment? = null

        var mUiConfig: FilePickerUIConfig = FilePickerUIConfig()
            private set

        constructor(activity: FragmentActivity) {
            this.mActivity = activity
        }

        constructor(fragment: Fragment) {
            this.mFragment = fragment
        }

        fun getActivity(): FragmentActivity? {
            return mActivity
        }

        fun getFragment(): Fragment? {
            return mFragment
        }

        fun setUiConfig(uiConfig: FilePickerUIConfig): Builder {
            this.mUiConfig = uiConfig
            return this
        }

        var mSelectedList: MutableList<MediaEntity> = mutableListOf()
            private set

        fun setSelectedList(selectedList: MutableList<MediaEntity>): Builder {
            this.mSelectedList = selectedList
            return this
        }

        fun setSelectedPathList(selectedList: List<String>): Builder {
            this.mSelectedList = selectedList.map { it ->
                MediaEntity.fromPath(it)
            }.toMutableList()
            return this
        }

        var mSelectType: String = FilePickerSelectType.IMAGE_VIDEO
            private set

        fun setSelectType(selectType: String): Builder {
            this.mSelectType = selectType
            return this
        }

        /**
         * 是否启用 单点击选择模式，
         */
        var mSingleClickEnable: Boolean = false
            private set

        /**
         * 单点 图片或视频 就立刻 返回选择，无需再点确定按钮。仅针对 单选，即选择数量为1 时生效。
         * maxSelectNumber权重大于此参数。maxSelectNumber!=1 时，此参数无效。请配合使用。
         */
        fun setSingleClickEnable(enable: Boolean): Builder {
            this.mSingleClickEnable = enable
            return this
        }

        /**
         * 是否启用 滑动选择模式，
         */
        var mSlideChooseEnable: Boolean = true
            private set

        /**
         * 是否启用 滑动选择模式，
         */
        fun setSlideChooseEnable(enable: Boolean): Builder {
            this.mSlideChooseEnable = enable
            return this
        }

        var mMaxSelectNumber = 0
            private set

        fun setMaxSelectNumber(maxSelectNumber: Int): Builder {
            this.mMaxSelectNumber = maxSelectNumber
            return this
        }

        var mMaxFileSize = Long.MAX_VALUE
            private set

        fun setMaxFileSize(maxFileSize: Long): Builder {
            this.mMaxFileSize = maxFileSize
            return this
        }

        var mMinFileSize = 1L
            private set

        fun setMinFileSize(minFileSize: Long): Builder {
            this.mMinFileSize = minFileSize
            return this
        }

        var mOnSelectCallback: OnSelectCallback? = null
            private set

        fun setOnSelectCallback(onSelectCallback: OnSelectCallback): Builder {
            this.mOnSelectCallback = onSelectCallback
            return this
        }

        fun start() {
            FilePicker.get().start()
        }
    }

    private var existingFragment: CallbackFragment? = null

    private fun start() {
        doStart(FilePickerActivity::class.java)
    }

    private fun doStart(clazz: Class<out FragmentActivity>) {
        builder?.let { bd ->
            if (bd.getFragment() != null) {
                bd.getFragment()?.apply {
                    context?.let { ctx ->
                        val fm = childFragmentManager
                        val tag = "CallbackFragment"
                        existingFragment = fm.findFragmentByTag(tag) as? CallbackFragment
                        if (existingFragment == null) {
                            existingFragment = CallbackFragment()
                        }
                        existingFragment?.apply {
                            onResult = { resultCode: Int, data: Intent? ->
                                if (resultCode == RESULT_OK) {
                                    val resultData = FilePickerViewModel.finalSelectedDataList.toMutableList()
//                                    val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()

                                    val isUseOriginal = data?.getBooleanExtra("isUseOriginal", false) == true

                                    builder?.mOnSelectCallback?.onSelectedCallback(isUseOriginal, resultData)
                                    FilePickerViewModel.clearAll()
                                } else {
                                    builder?.mOnSelectCallback?.onCancel()
                                    FilePickerViewModel.clearAll()
                                }
                            }
                            fm.beginTransaction().add(this, tag).commitNowAllowingStateLoss()
                            val intent = Intent(ctx, clazz)
                            intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                            intent.putExtra("selectType", bd.mSelectType)
                            intent.putExtra("maxFileSize", bd.mMaxFileSize)
                            intent.putExtra("minFileSize", bd.mMinFileSize)
                            intent.putExtra("uiConfigData", bd.mUiConfig.toTransportBundle())
                            intent.putExtra("singleClickEnable", bd.mSingleClickEnable)
                            intent.putExtra("slideChooseEnable", bd.mSlideChooseEnable)
                            FilePickerViewModel.userUseSelectDataList.clear()
                            FilePickerViewModel.userUseSelectDataList.addAll(bd.mSelectedList)
                            launch(intent)
                        }
                    }
                }

            } else {
                bd.getActivity()?.let { act ->

                    val fm = act.supportFragmentManager
                    val tag = "CallbackFragment"
                    existingFragment = fm.findFragmentByTag(tag) as? CallbackFragment
                    if (existingFragment == null) {
                        existingFragment = CallbackFragment()
                    }
                    existingFragment?.apply {
                        onResult = { resultCode: Int, data: Intent? ->
                            if (resultCode == RESULT_OK) {
                                val resultData = FilePickerViewModel.finalSelectedDataList.toMutableList()
//                                val resultData = data?.getParcelableArrayListExtra<MediaEntity>("selectedDataList")?.toMutableList()
                                val isUseOriginal = data?.getBooleanExtra("isUseOriginal", false) == true
                                builder?.mOnSelectCallback?.onSelectedCallback(isUseOriginal, resultData)
                                FilePickerViewModel.clearAll()
                            } else {
                                builder?.mOnSelectCallback?.onCancel()
                                FilePickerViewModel.clearAll()
                            }
                        }
                        fm.beginTransaction().add(this, tag).commitNowAllowingStateLoss()
                        val intent = Intent(act, clazz)
                        intent.putExtra("maxSelectNumber", bd.mMaxSelectNumber)
                        intent.putExtra("selectType", bd.mSelectType)
                        intent.putExtra("maxFileSize", bd.mMaxFileSize)
                        intent.putExtra("minFileSize", bd.mMinFileSize)
                        intent.putExtra("uiConfigData", bd.mUiConfig.toTransportBundle())
                        intent.putExtra("singleClickEnable", bd.mSingleClickEnable)
                        intent.putExtra("slideChooseEnable", bd.mSlideChooseEnable)
                        FilePickerViewModel.userUseSelectDataList.clear()
                        FilePickerViewModel.userUseSelectDataList.addAll(bd.mSelectedList)
                        launch(intent)
                    }
                }
            }
        }
    }
}