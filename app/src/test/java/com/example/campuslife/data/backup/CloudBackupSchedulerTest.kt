package com.example.campuslife.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class CloudBackupSchedulerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun beforeSixSchedulesSameMorning() {
        val now = ZonedDateTime.of(2026, 9, 20, 5, 30, 0, 0, zone)
        assertEquals(
            Duration.ofMinutes(30).toMillis(),
            CloudBackupScheduler.millisUntilNextSix(now),
        )
    }

    @Test
    fun atOrAfterSixSchedulesNextMorning() {
        val now = ZonedDateTime.of(2026, 9, 20, 6, 0, 0, 0, zone)
        assertEquals(
            Duration.ofDays(1).toMillis(),
            CloudBackupScheduler.millisUntilNextSix(now),
        )
    }
}
