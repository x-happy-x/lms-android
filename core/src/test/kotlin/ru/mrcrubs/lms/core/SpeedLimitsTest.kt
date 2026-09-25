package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedLimitsTest {
    @Test
    fun `labels`() {
        assertEquals("Без лимита", SpeedLimits.label(null))
        assertEquals("Без лимита", SpeedLimits.label(0))
        assertEquals("5.0 МБ/с", SpeedLimits.label(5L * 1024 * 1024))
    }

    @Test
    fun `parses megabytes`() {
        assertEquals(2_621_440L, SpeedLimits.parseMegabytes("2,5").getOrThrow())
        assertEquals(null, SpeedLimits.parseMegabytes(" ").getOrThrow())
        assertEquals(null, SpeedLimits.parseMegabytes("0").getOrThrow())
        assertTrue(SpeedLimits.parseMegabytes("abc").isFailure)
        assertTrue(SpeedLimits.parseMegabytes("-1").isFailure)
    }

    @Test
    fun `formats megabytes for editing`() {
        assertEquals("", SpeedLimits.toMegabytesText(null))
        assertEquals("10", SpeedLimits.toMegabytesText(10L * 1024 * 1024))
        assertEquals("2.5", SpeedLimits.toMegabytesText(2_621_440L))
    }
}
