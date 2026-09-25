package ru.mrcrubs.lms.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormatTest {
    @Test
    fun formatsBytesSpeedEtaPercent() {
        assertEquals("512 Б", Format.bytes(512))
        assertEquals("1.5 КБ", Format.bytes(1536))
        assertEquals("700 МБ", Format.bytes(700L * 1024 * 1024))
        assertEquals("2.0 МБ/с", Format.speed(2L * 1024 * 1024))
        assertNull(Format.speed(0))
        assertEquals("1 ч 2 мин", Format.eta(3720))
        assertEquals("45 с", Format.eta(45))
        assertEquals("42%", Format.percent(42.4))
        assertEquals("3.5%", Format.percent(3.5))
    }
}
