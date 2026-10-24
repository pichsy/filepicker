package com.pichs.filepicker.select

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 提示文案映射单测。
 *
 * 核心诉求（用户反馈）：配了分类型上限时，命中图片上限不该报"已达到最大选择数量"
 * —— 因为视频可能还有额度，笼统文案会让用户误以为整体已满。
 */
class SelectMessagesTest {

    private val typeMaxTemplate = "%1\u0024s最多选择%2\u0024d个"
    private val conflictTemplate = "当前只能选择%1\u0024s"
    private val genericTemplate = "已达到最大选择数量"

    private fun message(
        reason: SelectCheck.Rejected.Reason,
        quotaType: String,
        typeMax: Int,
    ): String = SelectMessages.forRejected(
        rejected = SelectCheck.Rejected(reason, quotaType),
        typeMaxTemplate = typeMaxTemplate,
        conflictTemplate = conflictTemplate,
        genericMaxTemplate = genericTemplate,
        typeMax = typeMax,
    )

    @Test
    fun typeMax_reportsConcreteTypeAndQuota() {
        assertEquals(
            "图片最多选择3个",
            message(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.IMAGE, 3),
        )
        assertEquals(
            "视频最多选择5个",
            message(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.VIDEO, 5),
        )
    }

    @Test
    fun typeMax_fallsBackToGenericWhenQuotaUnlimited() {
        assertEquals(
            "已达到最大选择数量",
            message(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.IMAGE, Int.MAX_VALUE),
        )
        assertEquals(
            "已达到最大选择数量",
            message(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.IMAGE, 0),
        )
    }

    @Test
    fun totalMax_usesGenericMessage() {
        assertEquals(
            "已达到最大选择数量",
            message(SelectCheck.Rejected.Reason.TOTAL_MAX, "", 3),
        )
    }

    @Test
    fun typeConflict_reportsLockedType() {
        assertEquals(
            "当前只能选择视频",
            message(SelectCheck.Rejected.Reason.TYPE_CONFLICT, QuotaType.VIDEO, 0),
        )
        assertEquals(
            "当前只能选择图片",
            message(SelectCheck.Rejected.Reason.TYPE_CONFLICT, QuotaType.IMAGE, 0),
        )
    }

    // ---------------------------------------------------------------- 必选未满足

    @Test
    fun notSatisfied_reportsRequiredCount_notRemaining() {
        // 选了 1 个视频、要求 3 个：要报"必须选3个"（完整要求），而不是"还差2个"
        assertEquals(
            "视频必须选3个",
            SelectMessages.forNotSatisfied(
                missing = listOf(QuotaType.VIDEO to 2),
                requiredMin = mapOf(QuotaType.VIDEO to 3),
                template = "%1\u0024s必须选%2\u0024d个",
            ),
        )
    }

    @Test
    fun notSatisfied_joinsMultipleTypes() {
        assertEquals(
            "图片必须选9个，视频必须选3个",
            SelectMessages.forNotSatisfied(
                missing = listOf(QuotaType.IMAGE to 8, QuotaType.VIDEO to 2),
                requiredMin = mapOf(QuotaType.IMAGE to 9, QuotaType.VIDEO to 3),
                template = "%1\u0024s必须选%2\u0024d个",
            ),
        )
    }

    @Test
    fun notSatisfied_returnsNullWhenNothingMissing() {
        assertNull(
            SelectMessages.forNotSatisfied(
                missing = emptyList(),
                requiredMin = emptyMap(),
                template = "%1\u0024s必须选%2\u0024d个",
            )
        )
    }

    @Test
    fun allowed_hasNoMessage() {
        val allowed: SelectCheck = SelectCheck.Allowed
        assertNull(allowed as? SelectCheck.Rejected)
    }
}
