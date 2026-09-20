package com.example.campuslife.data.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 打卡的时间换算与换日规则。
 *
 * 凌晨 6 点换日是这里最容易被写错、又最难在界面上发现的部分，所以单独钉一遍。
 */
class CheckInModelsTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone)

    @Test
    fun beforeSixAmBelongsToPreviousCheckInDay() {
        val expected = LocalDate.of(2026, 9, 13).toEpochDay()
        assertEquals(expected, at(2026, 9, 14, 0, 0).checkInEpochDay())
        assertEquals(expected, at(2026, 9, 14, 5, 59).checkInEpochDay())
    }

    @Test
    fun atSixAmTheCheckInDayRollsOver() {
        val expected = LocalDate.of(2026, 9, 14).toEpochDay()
        assertEquals(expected, at(2026, 9, 14, 6, 0).checkInEpochDay())
        assertEquals(expected, at(2026, 9, 14, 23, 30).checkInEpochDay())
    }

    @Test
    fun millisUntilNextCheckInDayCountsToSixAm() {
        assertEquals(60L * 60 * 1000, at(2026, 9, 14, 5, 0).millisUntilNextCheckInDay())
    }

    @Test
    fun millisUntilNextCheckInDayIsAFullDayRightAfterRollover() {
        assertEquals(24L * 60 * 60 * 1000, at(2026, 9, 14, 6, 0).millisUntilNextCheckInDay())
    }

    @Test
    fun minuteOfDayIsHoursTimesSixty() {
        assertEquals(0, at(2026, 9, 14, 0, 0).toMinuteOfDay())
        assertEquals(452, at(2026, 9, 14, 7, 32).toMinuteOfDay())
        assertEquals(1439, at(2026, 9, 14, 23, 59).toMinuteOfDay())
    }

    @Test
    fun formatMinuteOfDayPadsToTwoDigits() {
        assertEquals("00:00", formatMinuteOfDay(0))
        assertEquals("07:05", formatMinuteOfDay(425))
        assertEquals("23:59", formatMinuteOfDay(1439))
    }

    @Test
    fun formatDurationLabelSplitsHoursAndMinutes() {
        assertEquals("0 分", formatDurationLabel(0))
        assertEquals("45 分", formatDurationLabel(45))
        assertEquals("2 小时", formatDurationLabel(120))
        assertEquals("3 小时 25 分", formatDurationLabel(205))
    }

    @Test
    fun durationIsEndMinusStart() {
        val record = CheckInRecord(epochDay = 20_000, startMinuteOfDay = 440, endMinuteOfDay = 645)
        assertEquals(205, record.durationMinutes ?: -1)
        assertTrue(record.isCompleted)
    }

    @Test
    fun durationCountsAcrossMidnightBeforeTheSixAmBoundary() {
        val record = CheckInRecord(epochDay = 20_000, startMinuteOfDay = 23 * 60, endMinuteOfDay = 60)
        assertEquals(2 * 60, record.durationMinutes ?: -1)
    }

    @Test
    fun unfinishedRecordHasNoDuration() {
        val record = CheckInRecord(epochDay = 20_000, startMinuteOfDay = 440)
        assertNull(record.durationMinutes)
        assertFalse(record.isCompleted)
    }

    @Test
    fun recordDateComesFromEpochDay() {
        val epochDay = LocalDate.of(2026, 9, 14).toEpochDay()
        assertEquals(LocalDate.of(2026, 9, 14), CheckInRecord(epochDay, 400).date)
    }
}
