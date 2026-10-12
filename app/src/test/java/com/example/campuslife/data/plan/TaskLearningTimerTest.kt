package com.example.campuslife.data.plan

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskLearningTimerTest {
    private val day = LocalDate.of(2026, 10, 12).toEpochDay()
    private val morning = LocalDate.ofEpochDay(day).atTime(9, 0)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun plans() = listOf(DailyPlan(day, listOf(PlanTask("数学", ""), PlanTask("英语", ""))))

    @Test fun pauseAndResumeAccumulatesOnlyActiveSegments() {
        var plans = plans().toggleTaskLearning(day, "数学", morning)
        assertEquals(TaskLearningPhase.Running, plans.single().tasks.first().learningPhase)
        plans = plans.toggleTaskLearning(day, "数学", morning + 60_000L)
        val paused = plans.single().tasks.first()
        assertEquals(TaskLearningPhase.Paused, paused.learningPhase)
        assertEquals(60_000L, paused.learningDurationMillis(day, morning + 600_000L))
        plans = plans.toggleTaskLearning(day, "数学", morning + 600_000L)
        plans = plans.toggleTaskLearning(day, "数学", morning + 720_000L)
        assertEquals(180_000L, plans.single().tasks.first().learningElapsedMillis)
    }

    @Test fun switchingTasksPausesPreviousAndLeavesExactlyOneActive() {
        val switched = plans().toggleTaskLearning(day, "数学", morning)
            .toggleTaskLearning(day, "英语", morning + 30_000L)
        assertEquals(30_000L, switched.single().tasks.first().learningElapsedMillis)
        assertEquals(TaskLearningPhase.Paused, switched.single().tasks.first().learningPhase)
        assertEquals(1, switched.flatMap { it.tasks }.count { it.learningPhase == TaskLearningPhase.Running })
        assertEquals(10_000L, switched.single().tasks.last().learningDurationMillis(day, morning + 40_000L))
    }

    @Test fun finishSettlesElapsedTimeAndCannotRestartOrUncomplete() {
        val running = plans().toggleTaskLearning(day, "数学", morning)
        val finished = running.single().finishTaskLearning("数学", morning + 90_000L)
        assertTrue(finished.tasks.first().isCompleted)
        assertNull(finished.tasks.first().learningStartedAtEpochMillis)
        assertEquals(90_000L, finished.tasks.first().learningElapsedMillis)
        assertEquals(listOf(finished), listOf(finished).toggleTaskLearning(day, "数学", morning + 100_000L))
        assertEquals(finished, finished.finishTaskLearning("数学", morning + 120_000L))
    }

    @Test fun finishingPausedOrUntimedTaskDoesNotInventLearningTime() {
        val paused = plans().toggleTaskLearning(day, "数学", morning)
            .toggleTaskLearning(day, "数学", morning + 5_000L).single()
        assertEquals(5_000L, paused.finishTaskLearning("数学", morning + 900_000L).tasks.first().learningElapsedMillis)
        val untimed = plans().single().finishTaskLearning("英语", morning)
        assertTrue(untimed.tasks.last().isCompleted)
        assertEquals(0L, untimed.tasks.last().learningElapsedMillis)
    }

    @Test fun persistedActiveSegmentContinuesAfterReloadWithoutPeriodicWrites() {
        val running = plans().toggleTaskLearning(day, "数学", morning).single().tasks.first()
        val restored = requireNotNull(decodePlanTask(encodePlanTask(day, 0, running))).task
        assertEquals(running, restored)
        assertEquals(120_000L, restored.learningDurationMillis(day, morning + 120_000L))
    }

    @Test fun sixAmCapsPreviousDayWithoutCompletingTaskOrLeakingIntoToday() {
        val boundary = learningDayEndMillis(day)
        val running = plans().toggleTaskLearning(day, "数学", boundary - 120_000L)
        assertEquals(120_000L, running.single().tasks.first().learningDurationMillis(day, boundary + 3_600_000L))
        val settled = running.settleExpiredLearning(boundary + 3_600_000L)
        assertEquals(TaskLearningPhase.Paused, settled.single().tasks.first().learningPhase)
        assertFalse(settled.single().tasks.first().isCompleted)
        assertEquals(settled, settled.toggleTaskLearning(day, "数学", boundary))
    }

    @Test fun clockMovingBackwardsCannotCreateNegativeDuration() {
        val running = plans().toggleTaskLearning(day, "数学", morning).single().tasks.first()
        assertEquals(0L, running.learningDurationMillis(day, morning - 1_000L))
    }

    @Test fun renameReorderAndLegacyTaskDecodePreserveTimingRules() {
        val active = plans().toggleTaskLearning(day, "数学", morning).single()
        val edited = active.updateTask("数学", "高数", "新说明", null).moveTask("高数", 1)
        assertEquals(morning, edited.orderedTasks.last().learningStartedAtEpochMillis)
        val legacy = decodePlanTask(listOf(day, 0, 1, "旧任务", "", "", "").joinToString("\u001F"))!!.task
        assertTrue(legacy.isCompleted)
        assertEquals(0L, legacy.learningElapsedMillis)
        assertEquals(TaskLearningPhase.Finished, legacy.learningPhase)
    }

    @Test fun backupPauseFreezesTheRecordedDurationWithoutMutatingTheLivePlan() {
        val live = plans().toggleTaskLearning(day, "数学", morning).single()
        val backup = live.pauseLearning(morning + 60_000L)
        assertEquals(TaskLearningPhase.Running, live.tasks.first().learningPhase)
        assertEquals(TaskLearningPhase.Paused, backup.tasks.first().learningPhase)
        assertEquals(60_000L, backup.tasks.first().learningDurationMillis(day, morning + 3_600_000L))
    }
}
