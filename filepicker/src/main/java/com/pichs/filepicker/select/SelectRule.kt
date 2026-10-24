package com.pichs.filepicker.select

import com.pichs.filepicker.FilePickerSelectType

/**
 * 配额类型：参与数量限制的三种媒体类型。
 *
 * 直接复用 [FilePickerSelectType] 的常量值（`image` / `video` / `gif`），
 * 这样调用方写 `SelectRule.ofImage(...)` 和 `setSelectType(ofImage())` 用的是同一套字符串，不会漂移。
 */
object QuotaType {

    const val IMAGE: String = FilePickerSelectType.IMAGE
    const val VIDEO: String = FilePickerSelectType.VIDEO
    const val GIF: String = FilePickerSelectType.GIF

    /** 用于提示文案的类型名 */
    fun displayName(type: String?): String = when (type) {
        IMAGE -> "图片"
        VIDEO -> "视频"
        GIF -> "GIF"
        else -> ""
    }
}

/**
 * 单个类型的选择数量约束。
 *
 *  - [min] > 0 表示"必须选"：提交时该类型数量必须落在 `min..max` 内；
 *  - [max] 是硬上限：到达后不能再勾；
 *  - 没配 [SelectRule] 的类型 = 不限制。
 *
 * 例：
 * ```
 * SelectRule.ofImage(0, 9)    // 图片最多 9 张
 * SelectRule.ofVideo(1, 1)    // 视频必须正好 1 个
 * ```
 */
class SelectRule(
    val type: String,
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
) {

    init {
        require(type.isNotBlank()) { "SelectRule.type 不能为空" }
        require(min >= 0) { "SelectRule.min 不能为负数：$min（type=$type）" }
        require(max >= min) { "SelectRule.max($max) 不能小于 min($min)（type=$type）" }
    }

    fun describe(): String = "SelectRule(type=$type, min=$min, max=${if (max == Int.MAX_VALUE) "无限" else max})"

    override fun toString(): String = describe()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectRule) return false
        return type == other.type && min == other.min && max == other.max
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + min
        result = 31 * result + max
        return result
    }

    companion object {

        fun ofImage(min: Int = 0, max: Int = Int.MAX_VALUE): SelectRule = SelectRule(QuotaType.IMAGE, min, max)

        fun ofVideo(min: Int = 0, max: Int = Int.MAX_VALUE): SelectRule = SelectRule(QuotaType.VIDEO, min, max)

        fun ofGif(min: Int = 0, max: Int = Int.MAX_VALUE): SelectRule = SelectRule(QuotaType.GIF, min, max)

        /**
         * 序列化成 `type:min:max`，用于 intent 传递。
         * 用冒号而不是逗号：adb `am start --esal` 是按**逗号**拆分数组的，
         * 用逗号会让规则在命令行里被拆碎（`video,1,1` → ["video","1","1"]）。
         */
        fun encode(rule: SelectRule): String = "${rule.type}:${rule.min}:${rule.max}"

        /** 反序列化，格式不对返回 null（容错，不抛） */
        fun decode(raw: String?): SelectRule? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(':')
            if (parts.size != 3) return null
            val type = parts[0].trim()
            if (type.isEmpty()) return null
            val min = parts[1].trim().toIntOrNull() ?: return null
            val max = parts[2].trim().toIntOrNull() ?: return null
            return runCatching { SelectRule(type, min, max) }.getOrNull()
        }
    }
}
