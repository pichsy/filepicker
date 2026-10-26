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

        /**
         * 清理裁剪/编辑产生的临时缓存（`Android/data/<包名>/cache/filepicker/`）。
         *
         * 编辑产物只在本次选择内使用，宿主另存到自己的位置后可以随时调用本方法清理；
         * 选择器启动时也会自动清理超过 24 小时的残留。
         */
        fun clearEditCache(context: android.content.Context): Boolean =
            com.pichs.filepicker.crop.FilePickerEditCache.clear(context)

        /** 当前编辑缓存占用大小（字节），方便宿主做容量控制 */
        fun editCacheSize(context: android.content.Context): Long =
            com.pichs.filepicker.crop.FilePickerEditCache.size(context)

        /** 编辑缓存目录（`Android/data/<包名>/cache/filepicker/`） */
        fun editCacheDir(context: android.content.Context): java.io.File =
            com.pichs.filepicker.crop.FilePickerEditCache.dir(context)

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

        /**
         * 裁剪模式右侧"还原"文案（多语言适配用；默认"还原"）。
         *
         * 等价于设置 [FilePickerUIConfig.cropResetText]；想做完整多语言时，
         * 建议直接用 `FilePickerUIConfig(cropResetText = ...)` 统一配置。
         */
        fun setAvatarCropResetText(text: String): Builder {
            this.mUiConfig.cropResetText = text
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

        /** 固定裁剪比例：> 0 生效（1f = 头像 1:1）；<= 0 表示不启用或者自由裁剪 */
        var mCropAspect: Float = 0f
            private set

        /** 裁剪输出最长边（0 = 不缩放） */
        var mCropOutputMaxSize: Int = 2048
            private set

        /** 裁剪输出 JPEG 质量（60~100） */
        var mCropQuality: Int = 90
            private set

        /** 预览页是否显示「编辑」入口（自由裁剪） */
        var mEditInPreview: Boolean = false
            private set

        /** 编辑产物缓存目录；空 = 默认 Android/data/<包名>/cache/filepicker */
        var mEditCacheDir: String = ""
            private set

        /**
         * 自定义编辑产物的缓存目录。
         *
         * 默认（不传）是 `Android/data/<包名>/cache/filepicker/`。
         * 编辑产物只是临时文件，宿主另存到自己的位置后可调用 `FilePicker.clearEditCache(context)` 清理。
         */
        fun setEditCacheDir(path: String): Builder {
            this.mEditCacheDir = path
            return this
        }

        /**
         * 头像场景：固定比例裁剪 + 单选单击。
         *
         * 配合 `setMaxSelectNumber(1)` + `setSingleClickEnable(true)` 使用：
         * 点击图片不再直接返回，而是先进入裁剪页（固定比例、只能拖动/缩放图片），
         * 裁剪确认后把结果挂到原图实体上返回（`entity.editedPath`），相册原图不动。
         *
         * @param aspect 1f = 1:1；其他比例传对应浮点值；<= 0 表示关闭固定比例
         */
        @Deprecated(
            "8.0.0 起裁剪相关 API 统一加 Avatar 前缀",
            ReplaceWith("setAvatarCropAspectRatio(aspect)"),
        )
        fun setCropAspectRatio(aspect: Float): Builder = setAvatarCropAspectRatio(aspect)

        @Deprecated(
            "8.0.0 起裁剪相关 API 统一加 Avatar 前缀",
            ReplaceWith("setAvatarCropOutputMaxSize(size)"),
        )
        fun setCropOutputMaxSize(size: Int): Builder = setAvatarCropOutputMaxSize(size)

        @Deprecated(
            "8.0.0 起裁剪相关 API 统一加 Avatar 前缀",
            ReplaceWith("setAvatarCropQuality(quality)"),
        )
        fun setCropQuality(quality: Int): Builder = setAvatarCropQuality(quality)

        @Deprecated(
            "8.0.0 起裁剪相关 API 统一加 Avatar 前缀",
            ReplaceWith("setAvatarCropResetText(text)"),
        )
        fun setCropResetText(text: String): Builder = setAvatarCropResetText(text)

        fun setAvatarCropAspectRatio(aspect: Float): Builder {
            this.mCropAspect = aspect
            return this
        }

        /** 裁剪输出最长边，默认 2048；传 0 表示不缩放（原图级） */
        fun setAvatarCropOutputMaxSize(size: Int): Builder {
            this.mCropOutputMaxSize = size
            return this
        }

        /** 裁剪输出 JPEG 质量，默认 90 */
        fun setAvatarCropQuality(quality: Int): Builder {
            this.mCropQuality = quality.coerceIn(60, 100)
            return this
        }

        /**
         * 预览页显示「编辑」入口（自由裁剪）。
         * 默认 false；开启后预览大图页顶部出现「编辑」，只对图片生效。
         */
        /** 头像裁剪模式开关（默认 false）；开启后 setCropAspectRatio 等裁剪参数才生效 */
        var mAvatarCropMode: Boolean = false

        /**
         * 头像裁剪模式总开关（默认 false）。
         *
         * 开启后：
         * - 强制单选 + 单击图片直接进固定比例裁剪页（数量/规则参数不生效，会打 warn 日志）
         * - [setCropAspectRatio] 设置裁剪比例（不设置默认 1:1），[setCropOutputMaxSize] / [setCropQuality] 生效
         *
         * 关闭时（默认）：[setCropAspectRatio] 等裁剪参数**全部不生效**（打 warn 日志）。
         */
        fun setAvatarCropMode(enable: Boolean): Builder {
            this.mAvatarCropMode = enable
            return this
        }

        /** 头像模式实际使用的裁剪比例：开关关闭返回 0（不裁剪），开启且未设比例时默认 1:1 */
        internal fun effectiveCropAspect(): Float {
            if (!mAvatarCropMode) return 0f
            return if (mCropAspect > 0f) mCropAspect else 1f
        }

        fun setEditInPreviewEnable(enable: Boolean): Builder {
            this.mEditInPreview = enable
            return this
        }

        fun setShowCameraEntry(enable: Boolean): Builder {
            this.mShowCameraEntry = enable
            return this
        }

        var mMaxSelectNumber = 0
            private set

        /**
         * **合计**最大选择数量（所有类型加起来最多几个），`0` / `Int.MAX_VALUE` 表示不限。
         *
         * 与新模型的分类型规则（[setSelectRules]）**同时生效**：可再选数 =
         * `min(该类型剩余额度, 合计剩余额度)`。不调用 [setSelectRules] 时，
         * 这就是唯一的口径，行为与老版本一致。
         */
        fun setMaxSelectNumber(maxSelectNumber: Int): Builder {
            this.mMaxSelectNumber = maxSelectNumber
            return this
        }

        // ---------------------------------------------------------------- 按类型限量（新模型）

        private var mSelectRules: MutableList<SelectRule> = mutableListOf()
        private var mSelectExclusive: Boolean = false
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
         *
         * **数量限制只有一个来源**：一旦使用规则，合计上限就由各类型的 `max` 自动叠加得出
         * （互斥时取单类型的最大 `max`），调用方不需要、也不应该再写第二个数字 ——
         * 因此 [setMaxSelectNumber] 在规则模式下**不生效**（会打一条 warn 日志）。
         * 不调用规则时，[setMaxSelectNumber] 就是唯一口径，行为与老版本完全一致。
         *
         * 这样不存在"合计上限与规则冲突"的配置，也就不需要运行时抉择。
         */
        /**
         * 头像裁剪是「霸道」模式：只要 [setCropAspectRatio] 传了大于 0 的比例，
         * 就固定为**单选 + 单击直接进裁剪页**。
         *
         * 数量、单击开关、选择规则都由库接管（并打 warn 日志），
         * 避免接入方"设了一堆参数却全部无效"。
         */
        internal fun normalizeForAvatarCrop(): SelectRulesEngine? {
            if (!mAvatarCropMode) {
                if (mCropAspect > 0f) {
                    FilePickerLog.w(
                        "FilePicker",
                        "未开启 setAvatarCropMode(true)：setCropAspectRatio(${mCropAspect}) 等裁剪参数不生效",
                    )
                }
                mCropAspect = 0f
                return buildSelectRulesEngine()
            }
            // 头像模式：默认 1:1；强制单选 + 单击即裁剪；规则模式让位
            if (mCropAspect <= 0f) mCropAspect = 1f
            if (mMaxSelectNumber != 1) {
                FilePickerLog.w(
                    "FilePicker",
                    "头像裁剪强制单选：忽略 setMaxSelectNumber(${mMaxSelectNumber})，实际按 1 处理",
                )
            }
            if (!mSingleClickEnable) {
                FilePickerLog.w(
                    "FilePicker",
                    "头像裁剪强制单击即裁剪：setSingleClickEnable 已按 true 处理",
                )
            }
            if (mSelectRulesUsed) {
                FilePickerLog.w(
                    "FilePicker",
                    "头像裁剪忽略 setSelectRules：头像模式固定单选、点图直接进裁剪页，规则配置不生效",
                )
                mSelectRules = mutableListOf()
                mSelectRulesUsed = false
            }
            mMaxSelectNumber = 1
            mSingleClickEnable = true
            return null
        }

        internal fun buildSelectRulesEngine(): SelectRulesEngine? {
            if (!mSelectRulesUsed) return null
            val engine = SelectRulesEngine.createOrNull(
                rules = mSelectRules,
                exclusive = mSelectExclusive,
                gifCountsAsImage = mGifCountsAsImage,
            )
            if (engine == null) {
                FilePickerLog.w(
                    "FilePicker",
                    "没有调用 setSelectRules 配置任何规则：setExclusive / setGifCountsAsImage 不生效，" +
                            "数量限制仍由 setMaxSelectNumber(${mMaxSelectNumber}) 决定"
                )
                return null
            }
            engine.checkSatisfiable("FilePicker.Builder")
            if (mMaxSelectNumber != 0) {
                FilePickerLog.w(
                    "FilePicker",
                    "setMaxSelectNumber(${mMaxSelectNumber}) 在规则模式下不生效：合计上限由规则自动叠加得出。" +
                            "规则=${engine.rules.joinToString { it.describe() }}，capacity=${engine.totalCapacity()}"
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
            // 配置自检：头像裁剪会强制单选 + 单击裁剪；其余情况走规则自检
            val selectRulesEngine = bd.normalizeForAvatarCrop()
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
                            intent.putExtra("cropAspect", bd.effectiveCropAspect())
                            intent.putExtra("cropOutputMaxSize", bd.mCropOutputMaxSize)
                            intent.putExtra("cropQuality", bd.mCropQuality)
                            intent.putExtra("editInPreview", bd.mEditInPreview)
                            intent.putExtra("editCacheDir", bd.mEditCacheDir)
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
                            intent.putExtra("cropAspect", bd.effectiveCropAspect())
                            intent.putExtra("cropOutputMaxSize", bd.mCropOutputMaxSize)
                            intent.putExtra("cropQuality", bd.mCropQuality)
                            intent.putExtra("editInPreview", bd.mEditInPreview)
                            intent.putExtra("editCacheDir", bd.mEditCacheDir)
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