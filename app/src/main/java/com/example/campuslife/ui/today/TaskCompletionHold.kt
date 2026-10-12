package com.example.campuslife.ui.today

internal const val TASK_COMPLETION_HOLD_MILLIS = 3_000L

internal enum class TaskHoldAction { None, Tap, Finish }

/** 每次按下独立计时；不足 3 秒的长按取消，不能退化为开始/暂停。 */
internal class TaskCompletionHold(
    private val pressedAtMillis: Long,
    private val tapTimeoutMillis: Long = 500L,
) {
    private var cancelled = false
    private var completed = false

    private fun elapsed(nowMillis: Long): Long = (nowMillis - pressedAtMillis).coerceAtLeast(0L)

    fun progress(nowMillis: Long): Float = if (cancelled) 0f else
        (elapsed(nowMillis).toFloat() / TASK_COMPLETION_HOLD_MILLIS).coerceIn(0f, 1f)

    fun tick(nowMillis: Long): TaskHoldAction {
        if (cancelled || completed || elapsed(nowMillis) < TASK_COMPLETION_HOLD_MILLIS) {
            return TaskHoldAction.None
        }
        completed = true
        return TaskHoldAction.Finish
    }

    fun release(nowMillis: Long): TaskHoldAction {
        if (cancelled || completed) return TaskHoldAction.None
        if (tick(nowMillis) == TaskHoldAction.Finish) return TaskHoldAction.Finish
        val isTap = elapsed(nowMillis) < tapTimeoutMillis
        cancel()
        return if (isTap) TaskHoldAction.Tap else TaskHoldAction.None
    }

    fun cancel() { cancelled = true }
}
