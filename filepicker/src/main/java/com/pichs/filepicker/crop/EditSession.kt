package com.pichs.filepicker.crop

import java.io.File

/**
 * 一次编辑的会话（非破坏模型）。
 *
 * 关键约束：
 * - **不改原图实体**：选中身份、选中顺序、数量规则、去重全部按原图 `MediaEntity.path` 算；
 * - 编辑产物只是临时文件，挂在原图 path 上作为"覆盖"；
 * - 本次选择内一直有效（取消选中不回退），关闭选择器后随临时文件一起清理。
 */
/**
 * 编辑缓存的公开入口。
 *
 * 默认目录：`Android/data/<包名>/cache/filepicker/`（外部缓存不可用时退回内部 cache）。
 * 接入方可通过 `FilePicker.Builder.setEditCacheDir(path)` 换成自己的目录，
 * 也可以随时调用 [clear] 清空、[size] 查看占用。
 */
object FilePickerEditCache {

    /** 接入方自定义的缓存目录；null 表示用默认目录 */
    @Volatile
    var customDir: File? = null

    /** 实际使用的缓存目录（不存在时返回应创建的位置） */
    fun dir(context: android.content.Context): File {
        customDir?.let { return it }
        return CropEngine.defaultCacheRoot(context)
    }

    /** 当前缓存大小（字节） */
    fun size(context: android.content.Context): Long = CropEngine.cacheSize(context)

    /** 清理全部编辑缓存；返回是否成功 */
    fun clear(context: android.content.Context): Boolean = CropEngine.clearCache(context)
}

data class EditSession(
    /** 原图身份 key：`MediaEntity.path` */
    val originalKey: String,
    /** 编辑产物（临时文件，位于 cacheDir/filepicker/edit/） */
    val editedFile: File,
    /** 每次变更 +1，界面按它判断是否需要刷新 */
    val revision: Int = 1,
)
