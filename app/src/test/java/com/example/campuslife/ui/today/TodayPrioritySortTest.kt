package com.example.campuslife.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayPrioritySortTest {

    @Test
    fun scheduledTasksAreSortedFromEarlyToLate() {
        val priorities = listOf(
            priority("晚上", 20 * 60),
            priority("早上", 7 * 60 + 30),
            priority("下午", 15 * 60),
        )

        val sorted = priorities.sortedByExpectedStartTime()

        assertEquals(listOf("早上", "下午", "晚上"), sorted.map { it.title })
    }

    @Test
    fun unscheduledTasksComeLastAndKeepTheirOriginalOrder() {
        val priorities = listOf(
            priority("未排时 A", null),
            priority("下午", 16 * 60),
            priority("未排时 B", null),
            priority("上午", 9 * 60),
        )

        val sorted = priorities.sortedByExpectedStartTime()

        assertEquals(
            listOf("上午", "下午", "未排时 A", "未排时 B"),
            sorted.map { it.title },
        )
    }

    @Test
    fun tasksAtTheSameTimeKeepTheirOriginalOrder() {
        val priorities = listOf(
            priority("同一时刻 A", 10 * 60),
            priority("同一时刻 B", 10 * 60),
            priority("更早", 8 * 60),
        )

        val sorted = priorities.sortedByExpectedStartTime()

        assertEquals(listOf("更早", "同一时刻 A", "同一时刻 B"), sorted.map { it.title })
    }

    @Test
    fun manualPositionsOverrideExpectedStartTimes() {
        val priorities = listOf(
            priority("早上", 8 * 60).copy(manualOrder = 1),
            priority("晚上", 20 * 60).copy(manualOrder = 0),
        )

        val sorted = priorities.sortedForTaskBar()

        assertEquals(listOf("晚上", "早上"), sorted.map { it.title })
    }

    private fun priority(title: String, expectedStartMinute: Int?): TodayPriorityUiModel =
        TodayPriorityUiModel(
            id = title,
            title = title,
            supportingText = "",
            category = TodayCategory.Personal,
            expectedStartMinute = expectedStartMinute,
        )
}
