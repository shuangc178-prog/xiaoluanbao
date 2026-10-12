package com.example.campuslife.ui.stats

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import java.time.LocalDate
import java.time.ZoneId

/** 固定预览数据仅在 Preview 使用。运行时不生成示例学习时间。 */
object StatsPreviewData {
    private val today = LocalDate.of(2026, 9, 14).toEpochDay()
    val nowEpochMillis: Long = LocalDate.ofEpochDay(today).atTime(13, 15)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun sampleRecords(todayEpochDay: Long, days: Int = 7): List<CheckInRecord> =
        (0 until days).map { CheckInRecord(todayEpochDay - it, 9 * 60, 17 * 60) }

    fun samplePlans(todayEpochDay: Long, days: Int = 5): List<DailyPlan> =
        (0 until days).map { offset ->
            DailyPlan(todayEpochDay - offset, listOf(
                PlanTask("整理高数第三章错题", "高等数学", isCompleted = true,
                    learningElapsedMillis = 40 * 60_000L, hasLearningStarted = true),
                PlanTask("四级单词复习", "28 个单词", learningElapsedMillis = 15 * 60_000L,
                    hasLearningStarted = true),
                PlanTask("阅读网络基础笔记", "网络基础", learningElapsedMillis = 25 * 60_000L,
                    learningStartedAtEpochMillis = if (offset == 0) nowEpochMillis - 5 * 60_000L else null,
                    hasLearningStarted = true),
            ))
        }
    val populated = buildStatsUiState(sampleRecords(today), today, plans = samplePlans(today),
        nowEpochMillis = nowEpochMillis)
    val empty = buildStatsUiState(emptyList(), today, nowEpochMillis = nowEpochMillis)
    val history = buildStatsUiState(sampleRecords(today), today, plans = samplePlans(today),
        selectedEpochDay = today - 2, nowEpochMillis = nowEpochMillis)
    val withCards = buildStatsUiState(sampleRecords(today), today, plans = samplePlans(today),
        nowEpochMillis = nowEpochMillis,
        ownedCards = CardCollection(mapOf(GachaCard.Unicorn.id to 2, GachaCard.Hero.id to 1)))
    val withCompleteSet = buildStatsUiState(sampleRecords(today), today, plans = samplePlans(today),
        nowEpochMillis = nowEpochMillis,
        ownedCards = CardCollection(CardSeries.Isekai.cards.associate { it.id to 1 }))
    val withRestCards = buildStatsUiState(
        records = sampleRecords(today - 1, 5) + CheckInRecord(today - 6, 540, 1020, CheckInRecordKind.RestCard),
        todayEpochDay = today,
        selectedEpochDay = today - 6,
        nowEpochMillis = nowEpochMillis,
    )
}
