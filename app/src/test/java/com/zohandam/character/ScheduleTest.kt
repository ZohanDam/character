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
}
