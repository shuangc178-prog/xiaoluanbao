package com.example.campuslife.ui.today

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 使用单调时钟，刷新进度不会改变持续按住 3 秒的判据。 */
internal suspend fun animateTaskCompletionHold(
    hold: TaskCompletionHold,
    onProgress: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    while (currentCoroutineContext().isActive) {
        val now = SystemClock.uptimeMillis()
        onProgress(hold.progress(now))
        if (hold.tick(now) == TaskHoldAction.Finish) {
            onFinish()
            return
        }
        withFrameNanos { }
    }
}

internal fun Modifier.taskCompletionGesture(
    taskKey: String,
    enabled: Boolean,
    scope: CoroutineScope,
    onPressStart: () -> Unit,
    onProgress: (Float) -> Unit,
    onTap: () -> Unit,
    onFinish: () -> Unit,
): Modifier = pointerInput(taskKey, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPressStart()
        val hold = TaskCompletionHold(down.uptimeMillis, viewConfiguration.longPressTimeoutMillis.coerceAtMost(500L))
        // Compose 的 scope 提供帧时钟；手势取消时必须同时停止帧动画。
        val animation = scope.launch { animateTaskCompletionHold(hold, onProgress, onFinish) }
        try {
            val up = waitForUpOrCancellation()
            animation.cancel()
            if (up == null) {
                hold.cancel()
            } else {
                up.consume()
                when (hold.release(up.uptimeMillis)) {
                    TaskHoldAction.Tap -> onTap()
                    TaskHoldAction.Finish -> onFinish()
                    TaskHoldAction.None -> Unit
                }
            }
        } finally {
            hold.cancel()
            animation.cancel()
            onProgress(0f)
        }
    }
}
