package com.example.campuslife.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskCompletionHoldTest {
    @Test fun shortTapStillStartsOrPausesAndDoesNotFinish() {
        val hold = TaskCompletionHold(1_000L)
        assertEquals(TaskHoldAction.Tap, hold.release(1_200L))
        assertEquals(TaskHoldAction.None, hold.tick(9_000L))
    }

    @Test fun releasingAnIncompleteHoldCancelsInsteadOfTogglingTheTimer() {
        val hold = TaskCompletionHold(1_000L)
        assertEquals(TaskHoldAction.None, hold.tick(3_999L))
        assertEquals(TaskHoldAction.None, hold.release(3_999L))
        assertEquals(TaskHoldAction.None, hold.tick(4_000L))
        assertEquals(0f, hold.progress(4_000L), 0f)
    }

    @Test fun holdingThreeSecondsFinishesExactlyOnceEvenAfterRelease() {
        val hold = TaskCompletionHold(1_000L)
        assertEquals(TaskHoldAction.None, hold.tick(3_999L))
        assertEquals(TaskHoldAction.Finish, hold.tick(4_000L))
        assertEquals(TaskHoldAction.None, hold.tick(4_500L))
        assertEquals(TaskHoldAction.None, hold.release(5_000L))
    }

    @Test fun releasingAtTheThresholdWorksWhenTheLastAnimationFrameWasLate() {
        val hold = TaskCompletionHold(1_000L)
        assertEquals(TaskHoldAction.None, hold.tick(3_990L))
        assertEquals(TaskHoldAction.Finish, hold.release(4_000L))
        assertEquals(TaskHoldAction.None, hold.tick(4_100L))
    }

    @Test fun slidingOutOrScrollingCancelsAndTheNextPressStartsFromZero() {
        val hold = TaskCompletionHold(1_000L)
        hold.cancel()
        assertEquals(TaskHoldAction.None, hold.release(5_000L))
        val next = TaskCompletionHold(5_000L)
        assertEquals(0f, next.progress(5_000L), 0f)
        assertEquals(TaskHoldAction.None, next.tick(7_999L))
    }

    @Test fun progressIsClampedAndTracksTheActualHoldDuration() {
        val hold = TaskCompletionHold(1_000L)
        assertEquals(0f, hold.progress(900L), 0f)
        assertEquals(0.5f, hold.progress(2_500L), 0.0001f)
        assertEquals(1f, hold.progress(8_000L), 0f)
    }
}
