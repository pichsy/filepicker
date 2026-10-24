package com.pichs.filepicker.select

/**
 * 选择规则引擎（纯 Kotlin，不依赖 Android，可直接单测）。
 *
 * 两类约束同时成立：
 *  - **分项**：每种类型各自的 [SelectRule.min] / [SelectRule.max]；
 *  - **合计**：[totalMaxCount]，0 表示不限。
 *
 * [exclusive] = 互斥模式：任意时刻只允许一个类型有选中项，类型由 [lockedType] 决定：
 *  ① 已经选了哪个类型，就锁定哪个；
 *  ② 一个都还没选时，如果**只有一个类型配了 min > 0**，直接锁定它
 *     —— 效果就是"只能选图片"或"只能选视频"；
 *  ③ 两个类型都配了 min > 0：不锁定，谁先被点锁定谁，另一类的必选条件在本次选择中作废。
 *
 * 所有 `counts` 参数都是"已选数量快照"，key 必须是 [normalize] 之后的类型，value > 0 的才算已选。
 */
class SelectRulesEngine(
    rules: List<SelectRule>,
    val exclusive: Boolean = false,
    val totalMaxCount: Int = 0,
    val gifCountsAsImage: Boolean = true,
) {

    private val ruleMap: Map<String, SelectRule> = rules.associateBy { it.type }

    /** 配置里的规则（按类型去重后） */
    val rules: List<SelectRule> get() = ruleMap.values.toList()

    /** 有没有配置任何约束 */
    val hasRules: Boolean get() = ruleMap.isNotEmpty() || totalMaxCount > 0 || exclusive

    /**
     * GIF 归并到图片配额：仅当**没有单独给 GIF 配规则**、且 [gifCountsAsImage] 时生效。
     * 返回 null 表示该实体不参与分类型配额（音频/文档等）。
     */
    fun normalize(quotaType: String?): String? {
        if (quotaType == null) return null
        if (quotaType == QuotaType.GIF && gifCountsAsImage && !ruleMap.containsKey(QuotaType.GIF)) {
            return QuotaType.IMAGE
        }
        return quotaType
    }

    fun ruleOf(normalizedType: String?): SelectRule? = normalizedType?.let { ruleMap[it] }

    /**
     * 当前锁定类型（仅互斥模式有意义）。
     * @param counts 已选数量快照
     */
    fun lockedType(counts: Map<String, Int>): String? {
        val selectedTypes = counts.filter { it.value > 0 }.keys
        if (selectedTypes.size == 1) return selectedTypes.first()
        if (selectedTypes.size > 1) return null // 互斥下正常不会出现；出现了就不锁，避免死锁
        return ruleMap.values.filter { it.min > 0 }.map { it.type }.singleOrNull()
    }

    /**
     * 判定一个实体能否被选中。
     *
     * @param quotaType 实体的配额类型（未归一化，内部会调 [normalize]）
     * @param counts 已选数量快照（归一化后的类型）
     * @param totalCount 已选总数（含"临时选择"）
     * @param lockedType 已知的锁定类型；传 null 时按 [lockedType] 自行计算
     */
    fun canSelect(
        quotaType: String?,
        counts: Map<String, Int>,
        totalCount: Int,
        lockedType: String? = null,
    ): SelectCheck {
        val type = normalize(quotaType)

        // 1) 互斥：类型冲突优先于数量判断
        if (exclusive) {
            val locked = lockedType ?: this.lockedType(counts)
            if (locked != null && type != null && locked != type) {
                return SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_CONFLICT, locked)
            }
        }

        // 2) 合计上限
        if (totalMaxCount > 0 && totalCount >= totalMaxCount) {
            return SelectCheck.Rejected(SelectCheck.Rejected.Reason.TOTAL_MAX, type ?: "")
        }

        // 3) 分项上限
        if (type != null) {
            val rule = ruleMap[type]
            if (rule != null && (counts[type] ?: 0) >= rule.max) {
                return SelectCheck.Rejected(SelectCheck.Rejected.Reason.TYPE_MAX, type)
            }
        }

        return SelectCheck.Allowed
    }

    /**
     * 当前已选是否满足全部"必选"条件（用于确定按钮）。
     * 一个都没选时恒为 false（保持原有"没选不能确定"的行为）。
     */
    fun satisfied(counts: Map<String, Int>, totalCount: Int): Boolean {
        if (totalCount <= 0) return false
        if (exclusive) {
            // 互斥：只校验被锁定的那一类；另一类的 min 本次作废
            val locked = lockedType(counts) ?: return true
            val rule = ruleMap[locked] ?: return true
            return (counts[locked] ?: 0) in rule.min..rule.max
        }
        // 共存：每个类型的数量都必须落在自己的 min..max 内
        return ruleMap.values.all { rule -> (counts[rule.type] ?: 0) in rule.min..rule.max }
    }

    /**
     * 还差多少才满足必选，用于提示文案。已满足返回空列表。
     * 每项为 `类型 to 还差的数量`。
     */
    fun missing(counts: Map<String, Int>, totalCount: Int): List<Pair<String, Int>> {
        if (exclusive) {
            val locked = lockedType(counts) ?: return emptyList()
            val rule = ruleMap[locked] ?: return emptyList()
            val lack = rule.min - (counts[locked] ?: 0)
            return if (lack > 0) listOf(locked to lack) else emptyList()
        }
        return ruleMap.values.mapNotNull { rule ->
            val lack = rule.min - (counts[rule.type] ?: 0)
            if (lack > 0) rule.type to lack else null
        }
    }

    /**
     * 总容量：显式 [totalMaxCount] 优先；一般由各类型 max 推导（规则模式下调用方不再手写合计上限）。
     * 返回 0 表示不限。用于决定"单击即选即返回"这类交互模式。
     *
     * - 共存：各类型 max 之和；
     * - 互斥：只能选其中一类，所以取单类型里最大的那个 max。
     */
    fun totalCapacity(): Int {
        if (totalMaxCount > 0) return totalMaxCount
        if (ruleMap.isEmpty()) return 0
        if (exclusive) {
            var maxOfRules = 0
            for (rule in ruleMap.values) {
                if (rule.max == Int.MAX_VALUE) return 0
                if (rule.max > maxOfRules) maxOfRules = rule.max
            }
            return maxOfRules
        }
        var sum = 0L
        for (rule in ruleMap.values) {
            if (rule.max == Int.MAX_VALUE) return 0
            sum += rule.max
            if (sum >= Int.MAX_VALUE) return 0
        }
        return sum.toInt()
    }

    /**
     * 批量（滑动连选）按顺序贪心筛选：返回**可被选中**的下标列表。
     * 过程中用模拟快照推进，保证"选中前 N 个刚好填满配额"。
     */
    fun filterSelectable(
        quotaTypes: List<String?>,
        counts: Map<String, Int>,
        totalCount: Int,
    ): List<Int> {
        val simulated = counts.toMutableMap()
        var total = totalCount
        var locked = if (exclusive) lockedType(simulated) else null
        val accepted = ArrayList<Int>(quotaTypes.size)
        quotaTypes.forEachIndexed { index, raw ->
            val check = canSelect(raw, simulated, total, locked)
            if (check is SelectCheck.Allowed) {
                accepted.add(index)
                val type = normalize(raw)
                if (type != null) {
                    simulated[type] = (simulated[type] ?: 0) + 1
                }
                total++
                if (exclusive && locked == null) {
                    locked = type
                }
            }
        }
        return accepted
    }

    /**
     * 配置自检：合计容量连"必选"都装不下时，是永远无法满足的矛盾配置，直接抛异常。
     *
     * - 共存：容量必须 >= 各类型 min 之和；
     * - 互斥：只要求容量能装下**某一个**类型的 min（选它即可满足）。
     *
     * @param displayName 用于报错信息的前缀
     */
    fun checkSatisfiable(displayName: String = "FilePicker") {
        val capacity = totalCapacity()
        if (capacity <= 0) return // 不限，永远能满足
        val minPairs = ruleMap.values.filter { it.min > 0 }.map { it.type to it.min }
        if (minPairs.isEmpty()) return
        val required = if (exclusive) {
            minPairs.minOf { it.second }
        } else {
            minPairs.sumOf { it.second }
        }
        require(capacity >= required) {
            "$displayName 配置矛盾：合计容量($capacity) 装不下必选数量($required)。" +
                    "规则=${ruleMap.values.joinToString { it.describe() }}，" +
                    "exclusive=$exclusive，totalMaxCount=$totalMaxCount"
        }
    }

    companion object {

        /**
         * 规则表为空时返回 **null**：说明调用方没有配置"按类型限量"，
         * 此时新模型（互斥 / GIF 归并）一律不启用，数量限制回退到 `setMaxSelectNumber`。
         *
         * 也就是说：**只有配置了规则，`setExclusive` / `setGifCountsAsImage` 才生效。**
         */
        fun createOrNull(
            rules: List<SelectRule>,
            exclusive: Boolean = false,
            gifCountsAsImage: Boolean = true,
        ): SelectRulesEngine? {
            if (rules.isEmpty()) return null
            return SelectRulesEngine(
                rules = rules,
                exclusive = exclusive,
                // 合计上限不外部设置：规则模式下由各类型 max 自动叠加
                totalMaxCount = 0,
                gifCountsAsImage = gifCountsAsImage,
            )
        }
    }

    fun describe(): String = "SelectRulesEngine(" +
            "rules=${ruleMap.values.joinToString { it.describe() }}, " +
            "exclusive=$exclusive, totalMaxCount=$totalMaxCount, " +
            "gifCountsAsImage=$gifCountsAsImage, capacity=${totalCapacity()})"

    override fun toString(): String = describe()
}
