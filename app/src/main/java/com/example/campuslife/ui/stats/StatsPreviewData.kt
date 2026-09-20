package com.example.campuslife.ui.stats

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.data.card.MAX_COPIES_PER_CARD
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import java.time.LocalDate

/**
 * 仅供 Android Studio Preview 使用的固定统计数据。
 * 运行时由 [rememberStatsController] 直接消费本地真实记录，不会调用这里的内容。
 */
object StatsPreviewData {

    private val sampleTaskPool = listOf(
        "整理高数第三章错题" to "高等数学 · 40 分钟",
        "四级单词到期复习" to "28 个待复习 · 约 15 分钟",
        "上肢力量训练" to "学校健身房 · 35 分钟",
        "阅读网络基础笔记" to "网络基础 · 30 分钟",
    )

    private val previewToday: Long = LocalDate.of(2026, 9, 14).toEpochDay()

    /** 固定种子的预览打卡记录，让图表预览稳定且不会随时间变化。 */
    fun sampleRecords(todayEpochDay: Long, days: Int = DefaultStatsRange.days): List<CheckInRecord> {
        var seed = 20260914L
        fun next(bound: Int): Int {
            seed = (seed * 1103515245L + 12345L) % 2147483647L
            return (seed % bound).toInt()
        }

        return (0 until days).map { offset ->
            val epochDay = todayEpochDay - (days - 1 - offset)
            val start = 400 + next(120)
            val duration = 90 + next(230)
            CheckInRecord(
                epochDay = epochDay,
                startMinuteOfDay = start,
                endMinuteOfDay = start + duration,
            )
        }
    }

    /** 预览用的每日计划快照。 */
    fun samplePlans(todayEpochDay: Long, days: Int = 5): List<DailyPlan> {
        val completionCounts = listOf(2, 3, 1, 3, 2)
        return (0 until days).map { offset ->
            val tasks = (0 until 3).map { index ->
                val (title, supportingText) = sampleTaskPool[(offset + index) % sampleTaskPool.size]
                PlanTask(
                    title = title,
                    supportingText = supportingText,
                    isCompleted = index < completionCounts[offset % completionCounts.size],
                )
            }
            DailyPlan(epochDay = todayEpochDay - offset, tasks = tasks)
        }
    }

    val populated: StatsUiState = buildStatsUiState(
        records = sampleRecords(previewToday),
        todayEpochDay = previewToday,
        plans = samplePlans(previewToday),
    )

    val week: StatsUiState = buildStatsUiState(
        records = sampleRecords(previewToday, StatsRange.Week.days),
        todayEpochDay = previewToday,
        range = StatsRange.Week,
        plans = samplePlans(previewToday),
    )

    val month: StatsUiState = buildStatsUiState(
        records = sampleRecords(previewToday, StatsRange.Month.days),
        todayEpochDay = previewToday,
        range = StatsRange.Month,
        plans = samplePlans(previewToday),
    )

    val sparse: StatsUiState = buildStatsUiState(
        records = listOf(
            CheckInRecord(epochDay = previewToday - 4, startMinuteOfDay = 470, endMinuteOfDay = 690),
            CheckInRecord(epochDay = previewToday - 2, startMinuteOfDay = 505, endMinuteOfDay = 640),
            CheckInRecord(epochDay = previewToday, startMinuteOfDay = 440, endMinuteOfDay = null),
        ),
        todayEpochDay = previewToday,
        plans = samplePlans(previewToday),
        // 固定一个「此刻」，让最后那天的进行中虚线在 Preview 里总是一样的长度。
        nowMinuteOfDay = 21 * 60,
    )

    val plansOnly: StatsUiState = buildStatsUiState(
        records = emptyList(),
        todayEpochDay = previewToday,
        plans = samplePlans(previewToday),
    )

    val staleWindow: StatsUiState = buildStatsUiState(
        records = listOf(
            CheckInRecord(epochDay = previewToday - 40, startMinuteOfDay = 450, endMinuteOfDay = 700),
        ),
        todayEpochDay = previewToday,
        plans = samplePlans(previewToday),
    )

    val empty: StatsUiState = EmptyStatsUiState

    val selectedDay: StatsDayUiModel = StatsDayUiModel(
        epochDay = previewToday,
        axisLabel = "9/14",
        dateLabel = "9月14日 · 周一",
        isToday = true,
        startMinutes = 435f,
        endMinutes = 660f,
        durationMinutes = 225,
        plannedCount = 3,
        completedCount = 3,
        tasks = listOf(
            StatsTaskUiModel("整理高数第三章错题", "高等数学 · 40 分钟", true),
            StatsTaskUiModel("四级单词到期复习", "28 个待复习 · 约 15 分钟", true),
            StatsTaskUiModel("上肢力量训练", "学校健身房 · 35 分钟", true),
        ),
    )

    val emptyDay: StatsDayUiModel = StatsDayUiModel(
        epochDay = previewToday - 1,
        axisLabel = "9/13",
        dateLabel = "9月13日 · 周日",
        isToday = false,
    )

    /** 一个还没打卡、可以用休息卡补上的过去日子。 */
    val restCardTargetDay: StatsDayUiModel = StatsDayUiModel(
        epochDay = previewToday - 2,
        axisLabel = "9/12",
        dateLabel = "9月12日 · 周六",
        isToday = false,
        canUseRestCard = true,
    )

    /** 已经被休息卡补记过的一天。 */
    val restCardDay: StatsDayUiModel = StatsDayUiModel(
        epochDay = previewToday - 3,
        axisLabel = "9/11",
        dateLabel = "9月11日 · 周五",
        isToday = false,
        startMinutes = 465f,
        endMinutes = 645f,
        durationMinutes = 180,
        isRestCardDay = true,
    )

    /** 预览用：一段真实连续打卡（满 10 天，因此有两张卡，其中一张已用）。 */
    val withRestCards: StatsUiState = buildStatsUiState(
        records = (0 until 12).map { offset ->
            CheckInRecord(
                epochDay = previewToday - offset,
                startMinuteOfDay = 470,
                endMinuteOfDay = 470 + 180,
                kind = if (offset == 4) {
                    CheckInRecordKind.RestCard
                } else {
                    CheckInRecordKind.Real
                },
            )
        },
        todayEpochDay = previewToday,
        plans = samplePlans(previewToday),
    )

    /**
     * 预览用的固定卡册：三张到手、其中一张重复，还差两张——用来渲染「未获得」的灰格。
     * 与打卡记录一样，这只是 Preview 的固定数据，运行时不走这里。
     */
    private val previewCards: CardCollection = CardCollection(
        counts = mapOf(
            GachaCard.Unicorn.id to 2,
            GachaCard.ElfKing.id to 1,
            GachaCard.BlackMage.id to 1,
            GachaCard.SanguoTaoyuan.id to 1,
            GachaCard.SanguoChangban.id to 1,
        ),
    )

    /** 预览用：整套集齐（每种 2 张），可以兑换。 */
    private val previewCompleteCards: CardCollection = CardCollection(
        counts = GachaCard.entries.associate { it.id to MAX_COPIES_PER_CARD },
    )

    /** 预览用：收藏进行中（只改收集分区，其它数字沿用 [populated]）。 */
    val withCards: StatsUiState = populated.copy(
        collections = buildCollectionUiStates(previewCards),
    )

    /** 预览用：整套集齐、可以兑换。 */
    val withCompleteCards: StatsUiState = populated.copy(
        collections = buildCollectionUiStates(previewCompleteCards),
    )
}
