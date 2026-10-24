package com.pichs.filepicker.select

/**
 * 一次"能否选择"判定的结果。
 *
 * 只表达"能不能选 + 为什么"，具体提示文案由 UI 层决定（便于换语言/改文案）。
 */
sealed class SelectCheck {

    /** 允许选择 */
    object Allowed : SelectCheck() {
        override fun toString(): String = "Allowed"
    }

    /**
     * 不允许选择。
     *
     * @param reason 原因
     * @param quotaType 相关类型：TYPE_CONFLICT 时是"当前被锁定的类型"（即现在只能选的那一类），
     *                  其他情况是触发限制的类型（可能为空，表示实体不参与分类型配额）
     */
    data class Rejected(val reason: Reason, val quotaType: String) : SelectCheck() {

        enum class Reason {
            /** 互斥模式下，与已锁定的类型冲突 */
            TYPE_CONFLICT,

            /** 该类型已达上限 */
            TYPE_MAX,

            /** 合计已达上限 */
            TOTAL_MAX,
        }

        fun describe(): String = "Rejected(reason=$reason, quotaType=$quotaType)"
    }
}
