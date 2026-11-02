package com.zohandam.character

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

/** Pure date calculations: opening the app is never needed to advance the cycle. */
object Schedule {
    fun sunday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun traitIndex(anchor: LocalDate, today: LocalDate, count: Int = 12): Int {
        require(count > 0)
        val weeks = ChronoUnit.WEEKS.between(sunday(anchor), sunday(today))
        return Math.floorMod(weeks, count.toLong()).toInt()
    }

    fun nextReminder(minutes: Int, now: ZonedDateTime): ZonedDateTime {
        require(minutes in 0..1439)
        val time = java.time.LocalTime.of(minutes / 60, minutes % 60)
        var next = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        return next
    }
}
