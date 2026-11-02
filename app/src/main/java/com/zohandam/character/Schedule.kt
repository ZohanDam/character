package com.zohandam.character

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.Instant
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

    /** Keep an alarm awaiting Android delivery today, even after its selected time. */
    fun reminderTrigger(minutes: Int, recorded: Long?, pendingExists: Boolean,
                        now: ZonedDateTime, rebuild: Boolean = false): Long {
        if (!rebuild && pendingExists && recorded != null && recorded > 0 &&
            !Instant.ofEpochMilli(recorded).atZone(now.zone).toLocalDate().isBefore(now.toLocalDate())) {
            return recorded
        }
        return nextReminder(minutes, now).toInstant().toEpochMilli()
    }

    fun nextSunday(now: ZonedDateTime): ZonedDateTime =
        sunday(now.toLocalDate()).plusWeeks(1).atStartOfDay(now.zone)
}
