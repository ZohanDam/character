package com.zohandam.character

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val valid = BackupData(LocalDate.of(2026, 11, 1), true, 2, listOf(1080, 480),
        List(12) { Trait("Trait $it", "Definition $it") })

    private fun rejects(data: BackupData) {
        try { data.checked(); fail("Invalid backup was accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `valid backup preserves the complete cycle and sorts times`() {
        val result = valid.checked()
        assertEquals(valid.anchor, result.anchor)
        assertEquals(valid.traits, result.traits)
        assertEquals(listOf(480, 1080), result.times)
        assertTrue(result.enabled)
        assertEquals(2, result.theme)
    }

    @Test fun `reject a starting day other than Sunday`() { rejects(valid.copy(anchor = valid.anchor.plusDays(1))) }
    @Test fun `reject unsupported theme values`() { rejects(valid.copy(theme = 3)) }
    @Test fun `reject empty and duplicate reminder times`() {
        rejects(valid.copy(times = emptyList()))
        rejects(valid.copy(times = listOf(480, 480)))
    }
    @Test fun `reject invalid clock minutes and excessive reminders`() {
        rejects(valid.copy(times = listOf(-1)))
        rejects(valid.copy(times = listOf(1440)))
        rejects(valid.copy(times = (0..Store.MAX_REMINDERS).toList()))
    }
    @Test fun `reject incomplete trait collections`() { rejects(valid.copy(traits = valid.traits.dropLast(1))) }
    @Test fun `reject blank names and definitions`() {
        rejects(valid.copy(traits = valid.traits.toMutableList().apply { this[3] = Trait(" ", "Definition") }))
        rejects(valid.copy(traits = valid.traits.toMutableList().apply { this[3] = Trait("Name", "\n") }))
    }
    @Test fun `reject text exceeding editor limits`() {
        rejects(valid.copy(traits = valid.traits.toMutableList().apply { this[0] = Trait("x".repeat(101), "Definition") }))
        rejects(valid.copy(traits = valid.traits.toMutableList().apply { this[0] = Trait("Name", "x".repeat(4001)) }))
    }
}
