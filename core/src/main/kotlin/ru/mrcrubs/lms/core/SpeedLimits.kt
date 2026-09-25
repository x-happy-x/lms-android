package ru.mrcrubs.lms.core

/** Download speed limit presets and labels (bytes/s; null = unlimited). */
object SpeedLimits {
    private const val MB = 1024L * 1024

    val PRESETS: List<Long?> = listOf(null, 1 * MB, 5 * MB, 10 * MB, 25 * MB, 50 * MB)

    fun label(limit: Long?): String = limit?.takeIf { it > 0 }?.let { Format.speed(it) } ?: "Без лимита"

    /** Parses a custom limit typed in MB/s ("2.5" or "2,5"); blank or 0 → unlimited, null when invalid. */
    fun parseMegabytes(text: String): Result<Long?> {
        val value = text.trim().replace(',', '.')
        if (value.isEmpty()) return Result.success(null)
        val mb = value.toDoubleOrNull() ?: return Result.failure(IllegalArgumentException("Введите число"))
        if (mb < 0) return Result.failure(IllegalArgumentException("Лимит не может быть отрицательным"))
        val bytes = (mb * MB).toLong()
        return Result.success(bytes.takeIf { it > 0 })
    }

    /** MB/s value for an edit field (empty for unlimited). */
    fun toMegabytesText(limit: Long?): String {
        if (limit == null || limit <= 0) return ""
        val mb = limit.toDouble() / MB
        return if (mb == Math.floor(mb)) mb.toLong().toString() else String.format(java.util.Locale.ROOT, "%.2f", mb).trimEnd('0')
    }
}
