package com.example.campuslife.data.plan

import com.example.campuslife.data.checkin.CheckInPhase
import com.example.campuslife.ui.checkin.isCheckInDayLocked
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「打卡完成后不能再取消勾选」这条规则的判据。
 *
 * 规则本身只有一个入参（打卡状态），所以可以完全靠单元测试钉住；
 * 真正拦住写入的那道锁在 `DailyPlanController` 里，用的是同一个纯函数，
 * 不会出现「界面禁用了、数据层还放行」这种两套口径。
 *
 * 这里同时钉住 `DailyPlan.toggle` 本身是**不做**锁判断的：它只负责翻转。
 * 谁都知道「已打卡的日子不该再被翻转」，但把这条规则塞进数据模型会让
 * 历史存档回看、未来可能的纠错入口一起被锁死；所以判断留在调用方（控制器）。
 */
class CheckInLockTest {

    private val epochDay = 20712L
    private val tasks = listOf(
        PlanTask("上肢力量训练", "", isCompleted = true),
        PlanTask("整理高数错题", "", isCompleted = true),
    )

    @Test
    fun onlyAFinishedCheckInLocksTheDay() {
        assertTrue(isCheckInDayLocked(CheckInPhase.Finished))
    }

    @Test
    fun theDayStaysEditableBeforeTheCheckInIsFinished() {
        // 未开始与进行中都放行：用户在点「结束打卡」之前随时可以改主意，
        // 勾错了、做漏了都要能改回来。
        assertFalse(isCheckInDayLocked(CheckInPhase.Idle))
        assertFalse(isCheckInDayLocked(CheckInPhase.Started))
    }

    @Test
    fun lockingIsDecidedByPhaseOnlySoItSurvivesTheMidnightRollover() {
        // 换日之后新的一天按钮复位成未开始 —— 锁自动解开，不需要额外记录。
        // 这一条也是「不去查打卡记录」的理由：换日自动补记的那天并没有写记录。
        assertFalse(isCheckInDayLocked(CheckInPhase.Idle))
    }

    @Test
    fun toggleItselfOnlyFlipsTheFlag() {
        // 数据模型不承担锁定职责：同一个操作在锁外必须照常生效，
        // 否则「未完成打卡前可以取消」这条要求会被数据层自己挡掉。
        val plan = DailyPlan(epochDay = epochDay, tasks = tasks)

        val toggled = plan.toggle("上肢力量训练")

        assertFalse(toggled.tasks.first { it.title == "上肢力量训练" }.isCompleted)
        // 另一条不受影响。
        assertTrue(toggled.tasks.first { it.title == "整理高数错题" }.isCompleted)
    }

    @Test
    fun toggleCanBeReversedBeforeTheCheckInIsFinished() {
        // 取消勾选之后还能再勾回来：只要没结束打卡，来回改都是允许的。
        val plan = DailyPlan(epochDay = epochDay, tasks = tasks)
            .toggle("上肢力量训练")
            .toggle("上肢力量训练")

        assertTrue(plan.isAllCompleted)
    }
}
