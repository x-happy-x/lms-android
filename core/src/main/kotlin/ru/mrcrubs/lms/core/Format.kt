package ru.mrcrubs.lms.core

import java.util.Locale
import kotlin.math.abs

/** Russian-language formatting of sizes, speeds and durations for the UI. */
object Format {
    private val UNITS = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "—"
        var size = value.toDouble()
        var unit = 0
        while (abs(size) >= 1024 && unit < UNITS.lastIndex) {
            size /= 1024
            unit++
        }
        return if (unit == 0) "$value ${UNITS[0]}"
        else String.format(Locale.ROOT, if (size >= 100) "%.0f %s" else "%.1f %s", size, UNITS[unit])
    }

    fun speed(bytesPerSecond: Long?): String? =
        bytesPerSecond?.takeIf { it > 0 }?.let { "${bytes(it)}/с" }

    fun eta(seconds: Long?): String? {
        if (seconds == null || seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "${h} ч ${m} мин"
            m > 0 -> "${m} мин ${s} с"
            else -> "${s} с"
        }
    }

    fun percent(value: Double?): String? =
        value?.let { String.format(Locale.ROOT, if (it >= 10 || it == 0.0) "%.0f%%" else "%.1f%%", it) }

    fun status(status: JobStatus): String = when (status) {
        JobStatus.QUEUED -> "В очереди"
        JobStatus.RUNNING -> "Загружается"
        JobStatus.PAUSED -> "Пауза"
        JobStatus.DONE -> "Готово"
        JobStatus.ERROR -> "Ошибка"
        JobStatus.CANCELED -> "Отменено"
        JobStatus.UNKNOWN -> "Неизвестно"
    }
}
