package com.zohandam.character

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {
    private val anchor = LocalDate.of(2026, 11, 1)

    @Test fun `Saturday stays on the same trait and Sunday advances`() {
        assertEquals(0, Schedule.traitIndex(anchor, LocalDate.of(2026, 11, 7)))
        assertEquals(1, Schedule.traitIndex(anchor, LocalDate.of(2026, 11, 8)))
    }

    @Test fun `cycle wraps and does not depend on opening the app`() {
        assertEquals(11, Schedule.traitIndex(anchor, anchor.plusWeeks(11)))
        assertEquals(0, Schedule.traitIndex(anchor, anchor.plusWeeks(12)))
        assertEquals(3, Schedule.traitIndex(anchor, anchor.plusWeeks(123)))
    }

    @Test fun `a date before the anchor stays valid`() {
        assertEquals(11, Schedule.traitIndex(anchor, anchor.minusDays(1)))
    }

    @Test fun `rotation crosses the year boundary`() {
        assertEquals(9, Schedule.traitIndex(anchor, LocalDate.of(2027, 1, 3)))
    }

    @Test fun `past reminder moves to tomorrow rather than firing immediately`() {
        val now = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        val next = Schedule.nextReminder(480, now)
        assertEquals(now.toLocalDate().plusDays(1), next.toLocalDate())
        assertEquals(8, next.hour)
    }

    @Test fun `future reminder remains today and equality moves tomorrow`() {
        val now = ZonedDateTime.of(2026, 11, 2, 8, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        assertEquals(now.toLocalDate(), Schedule.nextReminder(481, now).toLocalDate())
        assertEquals(now.toLocalDate().plusDays(1), Schedule.nextReminder(480, now).toLocalDate())
    }

    @Test fun `spring daylight saving gap resolves to a valid future time`() {
        val now = ZonedDateTime.of(2027, 3, 13, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        val next = Schedule.nextReminder(150, now)
        assertTrue(next.isAfter(now))
        assertEquals(LocalDate.of(2027, 3, 14), next.toLocalDate())
        assertEquals(3, next.hour)
    }

    @Test fun `autumn daylight saving still schedules the next local day`() {
        val now = ZonedDateTime.of(2026, 10, 31, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        val next = Schedule.nextReminder(480, now)
        assertEquals(LocalDate.of(2026, 11, 1), next.toLocalDate())
        assertEquals(8, next.hour)
    }

    @Test fun `local timezone changes preserve chosen wall clock time`() {
        for (zone in listOf("Asia/Ho_Chi_Minh", "Europe/London", "Pacific/Auckland")) {
            val now = ZonedDateTime.of(2026, 11, 2, 7, 0, 0, 0, ZoneId.of(zone))
            assertEquals(8, Schedule.nextReminder(480, now).hour)
        }
    }

    @Test fun `version comparison uses numbers rather than alphabetical order`() {
        assertTrue(Versions.newer("v1.10.0", "1.9.0"))
        assertFalse(Versions.newer("1.0.0", "1.0.0"))
        assertFalse(Versions.newer("0.9.9", "1.0.0"))
        assertFalse(Versions.newer("1.1.0-beta", "1.0.0"))
        assertFalse(Versions.newer("invalid", "1.0.0"))
    }

    @Test fun `opening after reminder time preserves a pending late alarm`() {
        val now = ZonedDateTime.of(2026, 11, 3, 8, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        val pending = now.withHour(8).withMinute(0).toInstant().toEpochMilli()
        assertEquals(pending, Schedule.reminderTrigger(480, pending, true, now))
    }

    @Test fun `opening repeatedly does not move an alarm awaiting delivery`() {
        val start = ZonedDateTime.of(2026, 11, 3, 8, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        val pending = start.toInstant().toEpochMilli()
        for (delay in listOf(1L, 5L, 45L, 90L))
            assertEquals(pending, Schedule.reminderTrigger(480, pending, true, start.plusMinutes(delay)))
    }

    @Test fun `a missing pending intent is rebuilt rather than retained`() {
        val now = ZonedDateTime.of(2026, 11, 3, 8, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        val old = now.minusMinutes(5).toInstant().toEpochMilli()
        val expected = Schedule.nextReminder(480, now).toInstant().toEpochMilli()
        assertEquals(expected, Schedule.reminderTrigger(480, old, false, now))
    }

    @Test fun `yesterday's stale alarm is not retained forever`() {
        val now = ZonedDateTime.of(2026, 11, 3, 8, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        val old = now.minusDays(1).withMinute(0).toInstant().toEpochMilli()
        assertEquals(Schedule.nextReminder(480, now).toInstant().toEpochMilli(), Schedule.reminderTrigger(480, old, true, now))
    }

    @Test fun `clock changes explicitly rebuild the chosen local time`() {
        val now = ZonedDateTime.of(2026, 11, 3, 8, 5, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        assertEquals(Schedule.nextReminder(480, now).toInstant().toEpochMilli(),
            Schedule.reminderTrigger(480, now.minusMinutes(5).toInstant().toEpochMilli(), true, now, rebuild = true))
    }

    @Test fun `Saturday notification expires at the coming Sunday midnight`() {
        val now = ZonedDateTime.of(2026, 11, 7, 23, 59, 59, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        assertEquals(1L, java.time.Duration.between(now, Schedule.nextSunday(now)).seconds)
    }

    @Test fun `Sunday notification lasts until the next Sunday`() {
        val now = ZonedDateTime.of(2026, 11, 8, 0, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"))
        assertEquals(now.plusWeeks(1), Schedule.nextSunday(now))
    }

    @Test fun `weekly expiration follows local midnight through daylight saving`() {
        val now = ZonedDateTime.of(2027, 3, 13, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        val expiry = Schedule.nextSunday(now)
        assertEquals(LocalDate.of(2027, 3, 14), expiry.toLocalDate())
        assertEquals(0, expiry.hour)
    }
}
