package com.pichs.filepicker.camera

/**
 * 网格里"拍照 / 录像"入口的占位模型。
 *
 * 它走 BRV 的 header 通道（`BindingAdapter.headers`），**刻意不进 models**。
 *
 * 原因：models 必须保持纯 [com.pichs.filepicker.entity.MediaEntity]。库里有整套
 * "列表下标"体系都建立在 models == currentFolderDataList 之上：
 * - 预览弹窗的 ViewPager2 按 `getCurrentFolderDataByPosition(index)` 取页，
 *   下标规整见 [com.pichs.filepicker.dialog.PreviewIndexResolver]；
 * - 滑动连选按 startPosition/currentPosition 对 models 做 subList；
 * - 选中角的 indexOfSelected 也是按 models 里的 path 算的。
 *
 * 一旦往 models 头部掺一个非媒体项，这三套下标会整体错位一格 —— 表现就是"点的是这张、
 * 显示的是另一张"。走 header 就没有这个问题：header 只是 adapter 里多一个 position 0，
 * models 本身不变。
 *
 * 代价：adapter position = models 下标 + headerCount，所有"把 models 下标直接当
 * adapter position 传给 notifyItemXxx"的地方都要显式换算，且 BRV 的 `onBind` 是全局
 * 唯一的一个（不按 type 分发），多类型必须在 onBind 里按 `_data` 分流。
 *
 * ## 两种模式与"劈开"的显示规则
 * 同一个格子最多提供两种模式，按 [allowPhoto] / [allowVideo] 决定怎么画：
 * - 只允许一种 → 整个格子就是那一个按钮（图标居中，不劈）；
 * - 两种都允许 → 格子从中间劈开，**左半边录像、右半边拍照**，中间一条分隔线。
 *
 * 左右顺序由布局里子 view 的先后决定（见 file_picker_item_rv_camera.xml），
 * 与这里的字段无关 —— 要换左右改布局即可。
 *
 * 为什么用 data class 而不是 object：BRV 的 `addType` 是按**类**匹配的
 * （`TypeList.equalInstance` 比的是 `javaObjectType == other::class.java`），
 * 同一个类不同实例仍然命中同一个 viewType，所以模式差异放在字段里最省事 ——
 * 不用加 viewType、不用加布局。
 */
data class CameraEntryModel(
    /** 是否允许拍照（劈开时排在右半边） */
    val allowPhoto: Boolean,
    /** 是否允许录像（劈开时排在左半边） */
    val allowVideo: Boolean,
)
