package com.pichs.filepicker.select

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 规则引擎单测：覆盖"共存 / 互斥 / 必选 / 上限 / GIF 归并 / 批量筛选 / 矛盾配置"。
 */
class SelectRulesEngineTest {

    // ---------------------------------------------------------------- 共存模式

    /** 共存：图片最多 9、视频最多 1，互不影响 */
    @Test
    fun coexist_perTypeMax_isIndependent() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(0, 9), SelectRule.ofVideo(0, 1)),
        )
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.IMAGE, counts(QuotaType.IMAGE to 8), 8))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.IMAGE),
            engine.canSelect(QuotaType.IMAGE, counts(QuotaType.IMAGE to 9), 9),
        )
        // 图片选满 9 张后，视频仍然可选（共存）
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.VIDEO, counts(QuotaType.IMAGE to 9), 9))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.VIDEO),
            engine.canSelect(QuotaType.VIDEO, counts(QuotaType.VIDEO to 1), 1),
        )
    }

    @Test
    fun coexist_totalMaxCount_blocksEverything() {
        val engine = SelectRulesEngine(rules = emptyList(), totalMaxCount = 5)
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.IMAGE, counts(QuotaType.IMAGE to 4), 4))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TOTAL_MAX, QuotaType.IMAGE),
            engine.canSelect(QuotaType.IMAGE, counts(QuotaType.IMAGE to 5), 5),
        )
    }

    /** 共存：所有类型的 min 都要满足才允许提交 */
    @Test
    fun coexist_satisfied_requiresAllMins() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(2, 3), SelectRule.ofVideo(1, 1)),
        )
        assertFalse(engine.satisfied(counts(QuotaType.IMAGE to 1, QuotaType.VIDEO to 1), 2))
        assertTrue(engine.satisfied(counts(QuotaType.IMAGE to 2, QuotaType.VIDEO to 1), 3))
        assertFalse(engine.satisfied(emptyMap(), 0))
    }

    // ---------------------------------------------------------------- 互斥模式

    /** 互斥 + 只有视频必选 → 只能选视频，图片直接拦掉 */
    @Test
    fun exclusive_singleRequiredType_locksThatTypeOnly() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofVideo(1, 1), SelectRule.ofImage(0, 9)),
            exclusive = true,
        )
        assertEquals(QuotaType.VIDEO, engine.lockedType(emptyMap()))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_CONFLICT, QuotaType.VIDEO),
            engine.canSelect(QuotaType.IMAGE, emptyMap(), 0),
        )
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.VIDEO, emptyMap(), 0))
    }

    /** 互斥 + 两个都有必选 → 先点谁锁谁，另一类的必选作废 */
    @Test
    fun exclusive_bothRequired_firstClickWins_andOtherMinIsDropped() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(3, 9), SelectRule.ofVideo(1, 1)),
            exclusive = true,
        )
        // 一个都没选时两边都能点
        assertNull(engine.lockedType(emptyMap()))
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.IMAGE, emptyMap(), 0))
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.VIDEO, emptyMap(), 0))

        // 先点了图片 → 锁定图片，视频不可再选
        val selectedImage = counts(QuotaType.IMAGE to 1)
        assertEquals(QuotaType.IMAGE, engine.lockedType(selectedImage))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_CONFLICT, QuotaType.IMAGE),
            engine.canSelect(QuotaType.VIDEO, selectedImage, 1),
        )
        // 只校验图片自己的 min，视频 min(=1) 已作废
        assertFalse(engine.satisfied(selectedImage, 1))
        assertTrue(engine.satisfied(counts(QuotaType.IMAGE to 3), 3))
    }

    /** 互斥：锁定类型清空后重新解锁，可以改选另一类 */
    @Test
    fun exclusive_clearedSelection_unlocks() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(3, 9), SelectRule.ofVideo(1, 1)),
            exclusive = true,
        )
        assertEquals(QuotaType.IMAGE, engine.lockedType(counts(QuotaType.IMAGE to 2)))
        assertNull(engine.lockedType(emptyMap()))
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.VIDEO, emptyMap(), 0))
    }

    /** 互斥但没有必选：先点谁锁谁 */
    @Test
    fun exclusive_withoutMin_firstSelectionLocks() {
        val engine = SelectRulesEngine(rules = emptyList(), exclusive = true)
        assertNull(engine.lockedType(emptyMap()))
        assertEquals(SelectCheck.Allowed, engine.canSelect(QuotaType.VIDEO, emptyMap(), 0))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_CONFLICT, QuotaType.VIDEO),
            engine.canSelect(QuotaType.IMAGE, counts(QuotaType.VIDEO to 1), 1),
        )
    }

    // ---------------------------------------------------------------- 提示辅助

    @Test
    fun missing_reportsRemainingRequired() {
        val engine = SelectRulesEngine(rules = listOf(SelectRule.ofImage(3, 9), SelectRule.ofVideo(1, 1)))
        assertEquals(
            listOf(QuotaType.IMAGE to 2, QuotaType.VIDEO to 1),
            engine.missing(counts(QuotaType.IMAGE to 1), 1),
        )
        assertTrue(engine.missing(counts(QuotaType.IMAGE to 3, QuotaType.VIDEO to 1), 4).isEmpty())
    }

    // ---------------------------------------------------------------- 总容量

    @Test
    fun totalCapacity_derivesFromRulesAndExplicitTotal() {
        assertEquals(10, SelectRulesEngine(listOf(SelectRule.ofImage(0, 9), SelectRule.ofVideo(0, 1))).totalCapacity())
        assertEquals(5, SelectRulesEngine(listOf(SelectRule.ofImage(0, 9)), totalMaxCount = 5).totalCapacity())
        // 有类型不限 → 合计不限 → 0
        assertEquals(0, SelectRulesEngine(listOf(SelectRule.ofImage(0, 9), SelectRule.ofVideo())).totalCapacity())
        assertEquals(0, SelectRulesEngine(emptyList()).totalCapacity())
    }

    /** 互斥下只能选其中一类：容量取单类型最大 max，而不是相加 */
    @Test
    fun totalCapacity_exclusiveUsesMaxSingleType() {
        val ruleEngine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(0, 9), SelectRule.ofVideo(0, 1)),
            exclusive = true,
        )
        assertEquals(9, ruleEngine.totalCapacity())
        // 只有一个类型且 max=1 → 容量 1，可用于触发"点一下就返回"
        assertEquals(1, SelectRulesEngine(listOf(SelectRule.ofVideo(1, 1)), exclusive = true).totalCapacity())
    }

    // ---------------------------------------------------------------- 批量筛选（滑动连选）

    @Test
    fun filterSelectable_stopsAtQuota() {
        val engine = SelectRulesEngine(rules = listOf(SelectRule.ofImage(0, 2)))
        val types = List(5) { QuotaType.IMAGE }
        assertEquals(listOf(0, 1), engine.filterSelectable(types, emptyMap(), 0))
    }

    @Test
    fun filterSelectable_inExclusiveMode_keepsOnlyFirstType() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofVideo(1, 1)),
            exclusive = true,
        )
        val types = listOf(QuotaType.IMAGE, QuotaType.VIDEO, QuotaType.VIDEO)
        // 视频被锁定 → 只有视频那两个下标可被选中，且 max=1
        assertEquals(listOf(1), engine.filterSelectable(types, emptyMap(), 0))
    }

    // ---------------------------------------------------------------- GIF 归并

    @Test
    fun gif_countsAsImage_byDefault() {
        val engine = SelectRulesEngine(rules = listOf(SelectRule.ofImage(0, 2)))
        assertEquals(QuotaType.IMAGE, engine.normalize(QuotaType.GIF))
        // GIF 占图片配额：已选 2 张图片后 GIF 也不能选
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_MAX, QuotaType.IMAGE),
            engine.canSelect(QuotaType.GIF, counts(QuotaType.IMAGE to 2), 2),
        )
    }

    @Test
    fun gif_keepsOwnQuota_whenGifRuleProvidedOrSwitchOff() {
        val withGifRule = SelectRulesEngine(rules = listOf(SelectRule.ofImage(0, 2), SelectRule.ofGif(0, 5)))
        assertEquals(QuotaType.GIF, withGifRule.normalize(QuotaType.GIF))

        val switchOff = SelectRulesEngine(rules = listOf(SelectRule.ofImage(0, 2)), gifCountsAsImage = false)
        assertEquals(QuotaType.GIF, switchOff.normalize(QuotaType.GIF))
    }

    @Test
    fun nonMediaType_onlyAffectedByTotal() {
        val engine = SelectRulesEngine(rules = listOf(SelectRule.ofImage(0, 1)), totalMaxCount = 2)
        assertNull(engine.normalize(null))
        assertEquals(SelectCheck.Allowed, engine.canSelect(null, counts(QuotaType.IMAGE to 1), 1))
        assertEquals(
            SelectCheck.Rejected(SelectCheck.Rejected.Reason.TOTAL_MAX, ""),
            engine.canSelect(null, counts(QuotaType.IMAGE to 2), 2),
        )
    }

    // ---------------------------------------------------------------- 配置自检

    @Test
    fun checkSatisfiable_throwsWhenCapacityCannotHoldRequired() {
        val engine = SelectRulesEngine(rules = listOf(SelectRule.ofImage(9, 9)), totalMaxCount = 1)
        try {
            engine.checkSatisfiable()
            fail("合计容量 1 装不下必选 9，应该抛异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("配置矛盾"))
        }
    }

    /** 你问的那个场景：合计上限 5，但规则要求图片必须 9 个 → 矛盾，start() 抛异常 */
    @Test
    fun checkSatisfiable_throwsWhenTotalCapCannotHoldRequired() {
        val engine = SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(9, 9)),
            totalMaxCount = 5,
        )
        val error = try {
            engine.checkSatisfiable("FilePicker.Builder")
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue("应该抛 IllegalArgumentException", error != null)
        val msg = error!!.message!!
        // 报错里要能直接看出资源与需求
        assertTrue("报错应含合计容量: $msg", msg.contains("合计容量(5)"))
        assertTrue("报错应含必选数量: $msg", msg.contains("必选数量(9)"))
        println("[config-error] $msg")
    }

    @Test
    fun checkSatisfiable_okForExclusiveSingleType() {
        // 互斥 + 图片必选 9、视频必选 1：只要能装下其中一个就可行
        SelectRulesEngine(
            rules = listOf(SelectRule.ofImage(9, 9), SelectRule.ofVideo(1, 1)),
            exclusive = true,
            totalMaxCount = 9,
        ).checkSatisfiable()
    }

    @Test
    fun checkSatisfiable_okWhenUnlimited() {
        SelectRulesEngine(rules = listOf(SelectRule.ofImage(9, 9))).checkSatisfiable()
    }

    // ---------------------------------------------------------------- 序列化

    @Test
    fun rule_encodeDecode_roundTrip() {
        val rule = SelectRule.ofImage(2, 9)
        assertEquals(rule, SelectRule.decode(SelectRule.encode(rule)))
        assertEquals(SelectRule.ofVideo(1, 1), SelectRule.decode("video:1:1"))
        assertNull(SelectRule.decode("bad"))
        assertNull(SelectRule.decode(""))
        assertNull(SelectRule.decode(null))
        // min > max 的非法串不崩，返回 null
        assertNull(SelectRule.decode("image:9:1"))
    }

    // ---------------------------------------------------------------- 工具

    private fun counts(vararg pairs: Pair<String, Int>): Map<String, Int> = pairs.toMap()
}
