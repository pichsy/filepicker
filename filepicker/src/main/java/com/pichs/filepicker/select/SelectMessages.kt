package com.pichs.filepicker.select

/**
 * 把"不能选"的原因翻译成给用户的提示文案（纯函数，不依赖 Android，便于单测）。
 *
 * 文案策略：
 *  - **分类型上限**：说清楚是哪个类型、额度多少（"图片最多选择 3 个"）。
 *    因为此时视频可能还有额度，笼统的"已达到最大选择数量"会让用户以为整体已满。
 *  - **合计上限 / 旧模型**：用笼统文案（"已达到最大选择数量"）——那时它确实是准的。
 *  - **互斥冲突**：说清楚当前只能选哪一类（"当前只能选择视频"）。
 */
object SelectMessages {

    /**
     * @param rejected 判定结果
     * @param typeMaxTemplate 分类型上限文案模板，含两个占位符：类型名、上限
     * @param conflictTemplate 互斥冲突文案模板，含一个占位符：当前被锁定的类型名
     * @param genericMaxTemplate 合计上限/旧模型的笼统文案
     * @param typeMax 该类型的上限；`<= 0` 或 `Int.MAX_VALUE` 表示不限，退回笼统文案
     */
    fun forRejected(
        rejected: SelectCheck.Rejected,
        typeMaxTemplate: String,
        conflictTemplate: String,
        genericMaxTemplate: String,
        typeMax: Int,
    ): String = when (rejected.reason) {
        SelectCheck.Rejected.Reason.TYPE_CONFLICT -> String.format(
            conflictTemplate,
            QuotaType.displayName(rejected.quotaType),
        )

        SelectCheck.Rejected.Reason.TYPE_MAX -> {
            if (typeMax > 0 && typeMax != Int.MAX_VALUE) {
                String.format(typeMaxTemplate, QuotaType.displayName(rejected.quotaType), typeMax)
            } else {
                genericMaxTemplate
            }
        }

        SelectCheck.Rejected.Reason.TOTAL_MAX -> genericMaxTemplate
    }

    /**
     * 「必选」未满足时的提示文案；没有未满足项时返回 null。
     *
     * 报的是**要求的数量**（"视频必须选3个"）而不是"还差几个" —— 用户点确定时
     * 更需要知道完整要求。
     *
     * @param missing 未满足的类型（类型 → 还差几个）
     * @param requiredMin 各类型要求的数量（`rule.min`）
     * @param template 文案模板，含两个占位符：类型名、要求数量
     */
    fun forNotSatisfied(
        missing: List<Pair<String, Int>>,
        requiredMin: Map<String, Int>,
        template: String,
    ): String? {
        if (missing.isEmpty()) return null
        return missing.joinToString("，") { (type, _) ->
            String.format(template, QuotaType.displayName(type), requiredMin[type] ?: 0)
        }
    }
}
