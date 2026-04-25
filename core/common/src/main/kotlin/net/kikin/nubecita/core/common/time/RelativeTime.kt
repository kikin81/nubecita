package net.kikin.nubecita.core.common.time

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Format [then] as a short relative-time string anchored to [now].
 *
 * Buckets (matching common social-client convention):
 *  - `< 1m`  → "now"
 *  - `< 1h`  → "Nm"  (e.g. "5m")
 *  - `< 1d`  → "Nh"  (e.g. "3h")
 *  - `< 7d`  → "Nd"  (e.g. "5d")
 *  - `≥ 7d`  → "MMM d" if same year as [now], else "MMM d, yyyy"
 *
 * Future timestamps (clock skew) are clamped to "now" — never throws.
 *
 * Pure: no clock reads, no I/O. Trivially unit-testable. The
 * auto-recomposing Composable wrapper lives in [rememberRelativeTimeText].
 */
fun formatRelativeTime(
    now: Instant,
    then: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val delta = now - then
    if (delta.isNegative() || delta < ONE_MINUTE) return NOW_LABEL
    if (delta < ONE_HOUR) return "${delta.inWholeMinutes}m"
    if (delta < ONE_DAY) return "${delta.inWholeHours}h"
    if (delta < ONE_WEEK) return "${delta.inWholeDays}d"

    val nowDate = now.toLocalDateTime(timeZone).date
    val thenDate = then.toLocalDateTime(timeZone).date
    val pattern = if (nowDate.year == thenDate.year) PATTERN_SAME_YEAR else PATTERN_DIFFERENT_YEAR
    return DateTimeFormatter.ofPattern(pattern, locale).format(thenDate.toJavaLocalDate())
}

private const val NOW_LABEL = "now"
private const val PATTERN_SAME_YEAR = "MMM d"
private const val PATTERN_DIFFERENT_YEAR = "MMM d, yyyy"
private val ONE_MINUTE: Duration = 1.minutes
private val ONE_HOUR: Duration = 1.hours
private val ONE_DAY: Duration = 1.days
private val ONE_WEEK: Duration = 7.days
