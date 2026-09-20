package com.example.campuslife.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 每日计划的编码与勾选。
 *
 * 编码用的是 ASCII 字段分隔符而不是逗号/竖线，就是为了让标题里的常见符号不会破坏存档；
 * 这里用带分隔符的标题把这条约定钉住。
 */
class DailyPlanTest {

    private val epochDay = LocalDate.of(2026, 9, 14).toEpochDay()

    @Test
    fun planDateLabelUsesChineseWeekday() {
        assertEquals("9月14日 · 周一", formatPlanDateLabel(LocalDate.of(2026, 9, 14)))
        assertEquals("9月20日 · 周日", formatPlanDateLabel(LocalDate.of(2026, 9, 20)))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val task = PlanTask(title = "整理高数第三章错题", supportingText = "高等数学 · 40 分钟", isCompleted = true)
        val decoded = decodePlanTask(encodePlanTask(epochDay, index = 2, task = task))

        assertEquals(epochDay, decoded?.epochDay ?: -1L)
        assertEquals(2, decoded?.index ?: -1)
        assertEquals(task, decoded?.task)
    }

    @Test
    fun expectedStartMinuteSurvivesRoundTrip() {
        val task = PlanTask(
            title = "上肢力量训练",
            supportingText = "学校健身房 · 35 分钟",
            expectedStartMinute = 19 * 60,
        )
        val decoded = decodePlanTask(encodePlanTask(epochDay, index = 0, task = task))

        assertEquals(19 * 60, decoded?.task?.expectedStartMinute)
        assertEquals("19:00", decoded?.task?.expectedStartLabel)
    }

    @Test
    fun manualOrderSurvivesRoundTrip() {
        val task = PlanTask(
            title = "手动排到第二项",
            supportingText = "",
            expectedStartMinute = 8 * 60,
            manualOrder = 1,
        )

        val decoded = decodePlanTask(encodePlanTask(epochDay, index = 0, task = task))

        assertEquals(1, decoded?.task?.manualOrder)
    }

    @Test
    fun legacyRecordsWithoutStartTimeStillDecode() {
        // 加「预期开始时间」之前存下的 5 段记录：时间读作未安排，不该整条丢弃。
        val legacy = listOf(epochDay.toString(), "0", "1", "旧任务", "旧说明")
            .joinToString("\u001F")
        val decoded = decodePlanTask(legacy)

        assertEquals("旧任务", decoded?.task?.title)
        assertTrue(decoded?.task?.isCompleted == true)
        assertNull(decoded?.task?.expectedStartMinute)
        assertNull(decoded?.task?.expectedStartLabel)
        assertNull(decoded?.task?.manualOrder)
    }

    @Test
    fun titlesContainingCommonSeparatorsSurvive() {
        val trickyTitle = "读《人类简史》; 记笔记, 顺便想想「效率|效果」"
        val task = PlanTask(title = trickyTitle, supportingText = "读书 · 30 分钟")
        val decoded = decodePlanTask(encodePlanTask(epochDay, index = 0, task = task))

        assertEquals(trickyTitle, decoded?.task?.title)
        assertEquals("读书 · 30 分钟", decoded?.task?.supportingText)
    }

    @Test
    fun malformedEntriesAreRejected() {
        assertNull(decodePlanTask(""))
        assertNull(decodePlanTask("不是数字\u001F0\u001F1\u001F标题\u001F说明"))
        assertNull(decodePlanTask("${epochDay}\u001F0\u001F1"))
    }

    @Test
    fun groupPlansRestoresOrderAndSortsByDay() {
        val stored = listOf(
            StoredPlanTask(epochDay + 1, 0, PlanTask("B1", "")),
            StoredPlanTask(epochDay, 1, PlanTask("A2", "")),
            StoredPlanTask(epochDay, 0, PlanTask("A1", "")),
        )

        val plans = groupPlans(stored)

        assertEquals(2, plans.size)
        assertEquals(listOf("A1", "A2"), plans[0].tasks.map { it.title })
        assertEquals(listOf("B1"), plans[1].tasks.map { it.title })
        assertEquals(epochDay, plans[0].epochDay)
    }

    @Test
    fun toggleFlipsOnlyTheMatchingTask() {
        val plan = DailyPlan(
            epochDay = epochDay,
            tasks = listOf(
                PlanTask("A", "a", isCompleted = false),
                PlanTask("B", "b", isCompleted = true),
            ),
        )

        val checked = plan.toggle("A")

        // A 被勾上，B 不受影响。
        assertTrue(checked.tasks[0].isCompleted)
        assertTrue(checked.tasks[1].isCompleted)
        assertEquals(2, checked.completedCount)

        // 再点一次应该翻转回去，而不是一路单向。
        val unchecked = checked.toggle("A")
        assertFalse(unchecked.tasks[0].isCompleted)
        assertEquals(1, unchecked.completedCount)
    }

    @Test
    fun allCompletedNeedsAtLeastOneTask() {
        assertFalse(DailyPlan(epochDay, emptyList()).isAllCompleted)
        assertTrue(DailyPlan(epochDay, listOf(PlanTask("A", "", isCompleted = true))).isAllCompleted)
        assertFalse(
            DailyPlan(
                epochDay,
                listOf(PlanTask("A", "", true), PlanTask("B", "", false)),
            ).isAllCompleted,
        )
    }

    @Test
    fun addTaskNormalizesInputAndRejectsDuplicates() {
        val plan = DailyPlan(epochDay, listOf(PlanTask("已有任务", "说明")))

        val added = plan.addTask(PlanTask("  新任务  ", "  30 分钟  "))
        val duplicate = added.addTask(PlanTask("新任务", "另一段说明"))

        assertEquals(listOf("已有任务", "新任务"), added.tasks.map { it.title })
        assertEquals("30 分钟", added.tasks.last().supportingText)
        assertEquals(added, duplicate)
        assertEquals(plan, plan.addTask(PlanTask("   ", "无效")))
    }

    @Test
    fun addTaskHasNoThreeItemLimit() {
        val plan = (1..8).fold(DailyPlan(epochDay, emptyList())) { current, index ->
            current.addTask(PlanTask("任务 $index", ""))
        }

        assertEquals(8, plan.tasks.size)
        assertEquals("任务 8", plan.tasks.last().title)
    }

    @Test
    fun updateTaskKeepsCompletionAndCanRename() {
        val plan = DailyPlan(
            epochDay,
            listOf(
                PlanTask("旧标题", "旧说明", isCompleted = true),
                PlanTask("另一项", "", isCompleted = false),
            ),
        )

        val updated = plan.updateTask("旧标题", "  新标题  ", "  新说明  ", expectedStartMinute = 8 * 60)

        assertEquals("新标题", updated.tasks.first().title)
        assertEquals("新说明", updated.tasks.first().supportingText)
        assertTrue(updated.tasks.first().isCompleted)
        assertEquals(8 * 60, updated.tasks.first().expectedStartMinute)
        assertEquals(plan, plan.updateTask("旧标题", "另一项", "重复", null))
    }

    @Test
    fun updateTaskCanClearExpectedStartTime() {
        val plan = DailyPlan(
            epochDay,
            listOf(PlanTask("任务", "", expectedStartMinute = 9 * 60)),
        )

        val cleared = plan.updateTask("任务", "任务", "", expectedStartMinute = null)

        assertNull(cleared.tasks.first().expectedStartMinute)
    }

    @Test
    fun removeTaskCanProduceAnEmptyPlan() {
        val plan = DailyPlan(epochDay, listOf(PlanTask("唯一任务", "")))

        val empty = plan.removeTask("唯一任务")

        assertTrue(empty.tasks.isEmpty())
        assertFalse(empty.isAllCompleted)
    }

    @Test
    fun automaticOrderUsesTimeAndKeepsUnscheduledTasksLast() {
        val plan = DailyPlan(
            epochDay,
            listOf(
                PlanTask("未排时间", ""),
                PlanTask("晚上", "", expectedStartMinute = 20 * 60),
                PlanTask("早上", "", expectedStartMinute = 8 * 60),
            ),
        )

        assertEquals(listOf("早上", "晚上", "未排时间"), plan.orderedTasks.map { it.title })
        assertFalse(plan.isManuallyOrdered)
    }

    @Test
    fun movingATaskCreatesPersistentManualOrder() {
        val plan = DailyPlan(
            epochDay,
            listOf(
                PlanTask("早上", "", expectedStartMinute = 8 * 60),
                PlanTask("中午", "", expectedStartMinute = 12 * 60),
                PlanTask("晚上", "", expectedStartMinute = 20 * 60),
            ),
        )

        val reordered = plan.moveTask("晚上", 0)

        assertTrue(reordered.isManuallyOrdered)
        assertEquals(listOf("晚上", "早上", "中午"), reordered.orderedTasks.map { it.title })
        assertEquals(listOf(0, 1, 2), reordered.orderedTasks.map { it.manualOrder })
    }

    @Test
    fun automaticOrderCanBeRestoredAfterManualMove() {
        val restored = DailyPlan(
            epochDay,
            listOf(
                PlanTask("晚上", "", expectedStartMinute = 20 * 60, manualOrder = 0),
                PlanTask("早上", "", expectedStartMinute = 8 * 60, manualOrder = 1),
            ),
        ).useAutomaticOrder()

        assertFalse(restored.isManuallyOrdered)
        assertEquals(listOf("早上", "晚上"), restored.orderedTasks.map { it.title })
        assertTrue(restored.tasks.all { it.manualOrder == null })
    }

    @Test
    fun addAndRemoveKeepManualPositionsContiguous() {
        val changed = DailyPlan(
            epochDay,
            listOf(
                PlanTask("A", "", manualOrder = 0),
                PlanTask("B", "", manualOrder = 1),
            ),
        ).addTask(PlanTask("C", "")).removeTask("B")

        assertEquals(listOf("A", "C"), changed.orderedTasks.map { it.title })
        assertEquals(listOf(0, 1), changed.orderedTasks.map { it.manualOrder })
    }
}
