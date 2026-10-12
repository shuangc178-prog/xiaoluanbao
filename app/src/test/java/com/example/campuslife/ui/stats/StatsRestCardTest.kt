package com.example.campuslife.ui.stats

import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 统计页上的休息卡分区口径：库存、进度、可用性与"哪天能补"。
 *
 * 奖励规则本身在 `RestCardRewardTest`；这里只管它怎么落到统计页的 UI State 上。
 */
class StatsRestCardTest {

    private val today: Long = LocalDate.of(2026, 9, 18).toEpochDay()

    private fun real(day: Long) = CheckInRecord(
        epochDay = day,
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 17 * 60,
    )

    private fun rest(day: Long) = CheckInRecord(
        epochDay = day,
        startMinuteOfDay = 9 * 60,
        endMinuteOfDay = 17 * 60,
        kind = CheckInRecordKind.RestCard,
    )

    private fun state(records: List<CheckInRecord>, selected: Long = today) = buildStatsUiState(
        records = records,
        todayEpochDay = today,
        selectedEpochDay = selected,
    )

    @Test
    fun fiveRealDaysShowOneAvailableCard() {
        val records = (0 until 5).map { real(today - it) }
        val state = state(records)
        assertEquals(1, state.restCards.earned)
        assertEquals(0, state.restCards.used)
        assertEquals(1, state.restCards.available)
        assertEquals(today, state.restCards.latestEarnedEpochDay)
    }

    @Test
    fun usingACardSpendsTheStockButNotTheEarnedCount() {
        // 5 天真实 + 用卡补的第 6 天：卡发出来了，也被用掉了，库存归零。
        val records = (0 until 5).map { real(today - it) } + rest(today - 5)
        val state = state(records)
        assertEquals(1, state.restCards.earned)
        assertEquals(1, state.restCards.used)
        assertEquals(0, state.restCards.available)
        // 已获得的卡仍然列出来，并标出去向。
        assertEquals(1, state.restCardGrants.size)
        assertTrue(state.restCardGrants.single().isUsed)
        assertEquals(today - 5, state.restCardGrants.single().usedEpochDay)
    }

    @Test
    fun restCardDayIsMarkedAndCountedButNotRewarded() {
        val records = (0 until 5).map { real(today - it) } + rest(today - 5)
        val state = state(records)
        val filled = state(records, today - 5).day
        assertTrue(filled.isRestCardDay)
        // 补记的天进「已打卡」累计。
        assertEquals(6, state.completedDays)
        // 但不进奖励进度：真实连续是 5 天，进度按 5 计（一张卡已发）。
        assertEquals(5, state.restCards.progressDays)
    }

    @Test
    fun onlyPastUncheckedDaysCanBeFilled() {
        val records = (0 until 3).map { real(today - it) }
        val state = state(records)

        val todayCell = state.day
        assertFalse("今天不该能用卡补", todayCell.canUseRestCard)

        val checkedDay = state(records, today - 1).day
        assertFalse("已经打过的卡不能再补", checkedDay.canUseRestCard)

        val gapDay = state(records, today - 4).day
        assertTrue("过去没打卡的那天可以补", gapDay.canUseRestCard)
    }

    @Test
    fun aDayAlreadyFilledByRestCardCannotBeFilledAgain() {
        val records = (0 until 3).map { real(today - it) } + rest(today - 4)
        val state = state(records)
        val filled = state(records, today - 4).day
        assertTrue(filled.isRestCardDay)
        assertFalse("补过一次就不再提供入口", filled.canUseRestCard)
    }

    @Test
    fun previewStateHasOneCardAndOneUsed() {
        // Preview 数据必须真的走一遍推导，不能只摆样子。
        // 真实连续 5 天（今天起往前数）→ 一张卡；卡片用在了更早的一天 → 库存归零。
        val preview = StatsPreviewData.withRestCards
        assertEquals(1, preview.restCards.earned)
        assertEquals(1, preview.restCards.used)
        assertEquals(0, preview.restCards.available)
        assertEquals(1, preview.restCardGrants.size)
        assertTrue(preview.restCardGrants.single().isUsed)
        // 预览里今天尚未打卡，昨天起连续 5 天，因此下一张还需 5 天。
        assertEquals(5, preview.restCards.progressDays)
        assertEquals(5, preview.restCards.daysToNext)
        // 被补的那天在界面数据里带上「休息卡补记」标记。
        assertTrue(preview.day.isRestCardDay)
    }
}
