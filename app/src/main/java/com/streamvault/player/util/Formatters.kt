package com.streamvault.player.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatDuration(seconds: Double?): String {
    val total = (seconds ?: 0.0).toLong().coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.US, h, m, s)
    else "%d:%02d".format(Locale.US, m, s)
}

fun formatDurationMs(milliseconds: Long): String = formatDuration(milliseconds / 1000.0)

fun formatBytes(bytes: Long?): String {
    val value = bytes ?: return ""
    if (value < 1024) return "$value B"
    if (value < 1024 * 1024) return "%.0f KB".format(Locale.US, value / 1024.0)
    if (value < 1024L * 1024L * 1024L) return "%.1f MB".format(Locale.US, value / 1024.0 / 1024.0)
    return "%.2f GB".format(Locale.US, value / 1024.0 / 1024.0 / 1024.0)
}

fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date: Date = input.parse(value) ?: return value.take(10)
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    } catch (_: Exception) { value.take(10) }
}
