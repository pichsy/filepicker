package com.pichs.filepicker

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.pichs.filepicker.empty.CallbackFragment
import com.pichs.filepicker.entity.MediaEntity
import com.pichs.filepicker.select.SelectRule
import com.pichs.filepicker.select.SelectRulesEngine
import com.pichs.filepicker.utils.FilePickerLog
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

        /**
         * 是否显示网格第一格的"拍照 / 录像"入口，点击调起系统相机。
         *
         * **默认 false**：不开启时整条链路完全不参与 —— 不挂 header、不注册 viewType、
         * 不声明权限、不请求权限，对宿主等同于不存在。
         *
         * 开启后按"这个类型下相机能产出什么"决定画法：
         * - 只允许一种 → 整个格子就是那一个按钮（ofImage() 整格拍照、ofVideo() 整格录像）；
         * - 两种都允许 → 格子从中间劈开，左半边录像、右半边拍照（ofAll() / ofAllWithGif()）。
         *
         * 有 tab 的类型（ofAll / ofAllWithGif）里，入口**跟着当前 tab 走**：在"视频"页只给
         * 录像、在"图片"页只给拍照、在"GIF"页不给 —— 否则录出来的东西不会出现在当前页里。
         * GIF / 音频 / 文档类型不显示入口。
         */
        var mShowCameraEntry: Boolean = false
            private set

        fun setShowCameraEntry(enable: Boolean): Builder {
            this.mShowCameraEntry = enable
            return this
        }

        var mMaxSelectNumber = 0
            private set

        fun setMaxSelectNumber(maxSelectNumber: Int): Builder {
            this.mMaxSelectNumber = maxSelectNumber
            return this
        }

        // ---------------------------------------------------------------- 按类型限量（新模型）

        private var mSelectRules: MutableList<SelectRule> = mutableListOf()
        private var mSelectExclusive: Boolean = false
        private var mSelectTotalMaxCount: Int = -1
        private var mGifCountsAsImage: Boolean = true
        private var mSelectRulesUsed: Boolean = false

        /**
         * 按类型分别限量（新模型）。一旦调用，[setMaxSelectNumber] 传入的合计上限会被**忽略** ——
         * 数量限制只保留一个数据源，避免两个数字互相打架。
         *
         * ```
         * .setSelectRules(SelectRule.ofImage(0, 9), SelectRule.ofVideo(0, 1))  // 图片≤9、视频≤1，可混选
         * .setSelectRules(SelectRule.ofVideo(1, 1))                            // 必须正好 1 个视频
         * ```
         *
         * 不调用则完全不启用新模型，行为与旧版本 1:1 一致。
         */
        fun setSelectRules(vararg rules: SelectRule): Builder {
            this.mSelectRules = rules.toMutableList()
            this.mSelectRulesUsed = true
            return this
        }

        /**
         * 互斥选择：图片和视频只能选其中一类。
         *
         * - 只有一类配了 `min > 0` → 直接锁定那一类，另一类点了会被拦下（等于"只能选图片 / 只能选视频"）；
         * - 两类都配了 `min > 0` → 先点哪类就锁定哪类，另一类的 `min` 本次作废；
         * - 已选类型全部取消后自动解锁，可以改选另一类。
         */
        fun setExclusive(exclusive: Boolean): Builder {
            this.mSelectExclusive = exclusive
            this.mSelectRulesUsed = true
            return this
        }

        /** 合计上限（属于新模型，0 = 不限）。不传时按各类型 max 之和自动推导。 */
        fun setTotalMaxCount(count: Int): Builder {
            this.mSelectTotalMaxCount = count
            this.mSelectRulesUsed = true
            return this
        }

        /**
         * GIF 是否占用"图片"的额度，默认 true（用户视角里 GIF 就是图片）。
         * 单独给 GIF 配了 [SelectRule.ofGif] 时，GIF 用自己的规则，此项不生效。
         */
        fun setGifCountsAsImage(countsAsImage: Boolean): Builder {
            this.mGifCountsAsImage = countsAsImage
            this.mSelectRulesUsed = true
            return this
        }

        /** 是否启用了新模型 */
        internal fun isSelectRulesUsed(): Boolean = mSelectRulesUsed

        /**
         * 构造规则引擎并做配置自检。
         * 矛盾配置（合计容量装不下必选数量）在这里直接抛异常：开发期就炸，别留到用户点不动。
         */
        internal fun buildSelectRulesEngine(): SelectRulesEngine? {
            if (!mSelectRulesUsed) return null
            val engine = SelectRulesEngine(
                rules = mSelectRules,
                exclusive = mSelectExclusive,
                totalMaxCount = if (mSelectTotalMaxCount < 0) 0 else mSelectTotalMaxCount,
                gifCountsAsImage = mGifCountsAsImage,
            )
            engine.checkSatisfiable("FilePicker.Builder")
            if (mMaxSelectNumber != 0) {
                FilePickerLog.w(
                    "FilePicker",
                    "setMaxSelectNumber(${mMaxSelectNumber}) 已失效：调用了 setSelectRules/setExclusive/setTotalMaxCount 后，" +
                            "数量限制只由规则决定。${engine.describe()}"
                )
            }
            if (mSingleClickEnable && engine.totalCapacity() != 1) {
                FilePickerLog.w(
                    "FilePicker",
                    "setSingleClickEnable(true) 需要总容量为 1，当前总容量=${engine.totalCapacity()}（0=不限），单击即返回不会生效。"
                )
            }
            return engine
        }

        /** 把新模型参数写进 intent；engine 为 null 时什么都不做（旧路径完全不变） */
        internal fun applySelectRuleExtras(intent: Intent, engine: SelectRulesEngine?) {
            if (engine == null) return
            intent.putExtra("useSelectRules", true)
            intent.putStringArrayListExtra("selectRules", ArrayList(engine.rules.map { SelectRule.encode(it) }))
            intent.putExtra("selectExclusive", engine.exclusive)
            intent.putExtra("selectTotalMaxCount", engine.totalMaxCount)
            intent.putExtra("gifCountsAsImage", engine.gifCountsAsImage)
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
            // 新模型：配置自检在这里做，矛盾配置当场抛异常
            val selectRulesEngine = bd.buildSelectRulesEngine()
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
                            intent.putExtra("showCameraEntry", bd.mShowCameraEntry)
                            bd.applySelectRuleExtras(intent, selectRulesEngine)
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
                        intent.putExtra("showCameraEntry", bd.mShowCameraEntry)
                        bd.applySelectRuleExtras(intent, selectRulesEngine)
                        FilePickerViewModel.userUseSelectDataList.clear()
                        FilePickerViewModel.userUseSelectDataList.addAll(bd.mSelectedList)
                        launch(intent)
                    }
                }
            }
        }
    }
}