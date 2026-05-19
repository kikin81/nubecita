package net.kikin.nubecita.core.common.time

import androidx.compose.runtime.Stable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Localized labels consumed by [formatRelativeTime].
 *
 * Decoupling labels from the formatting logic keeps the pure helper
 * unit-testable without an Android [android.content.Context] — tests
 * pass [English] (or a custom fixture) directly. Production call sites
 * should prefer [rememberRelativeTimeStrings] which resolves these from
 * Android string + plural resources and tracks locale changes.
 *
 * `@Stable`: a `data class` with function-typed fields is otherwise
 * marked unstable by the Compose compiler (it can't prove lambda
 * equality), which would force [rememberRelativeTimeText]'s
 * `produceState` to restart its producer coroutine on every parent
 * recomposition. Promised contract: `equals` reflects observable
 * behavior — call sites that hold a `RelativeTimeStrings` across
 * recompositions must keep the same instance (which
 * [rememberRelativeTimeStrings] does via `remember`).
 */
@Stable
data class RelativeTimeStrings(
    val now: String,
    val minutes: (count: Int) -> String,
    val hours: (count: Int) -> String,
    val days: (count: Int) -> String,
    /**
     * Used by [formatChatRelativeTime] for the "previous calendar day"
     * bucket. [formatRelativeTime] (Feed-style compact form) doesn't read
     * this — it stays on `Nd` plurals through 6 days. Default "Yesterday"
     * for the [English] fallback keeps unit tests of either formatter
     * trivial.
     */
    val yesterday: String,
) {
    public companion object {
        /**
         * Hard-coded English fallback. Useful for unit tests of the pure
         * helper and as a defensive default in non-Android contexts. Not
         * shown to users on Android — those go through resources.
         */
        public val English: RelativeTimeStrings =
            RelativeTimeStrings(
                now = "now",
                minutes = { "${it}m" },
                hours = { "${it}h" },
                days = { "${it}d" },
                yesterday = "Yesterday",
            )
    }
}

/**
 * Format [then] as a short relative-time string anchored to [now].
 *
 * Buckets (matching common social-client convention):
 *  - `< 1m`  → [strings.now]
 *  - `< 1h`  → [strings.minutes] (e.g. "5m")
 *  - `< 1d`  → [strings.hours] (e.g. "3h")
 *  - `< 7d`  → [strings.days] (e.g. "5d")
 *  - `≥ 7d`  → "MMM d" if same year as [now], else "MMM d, yyyy"
 *
 * Future timestamps (clock skew) are clamped to [strings.now] — never throws.
 *
 * Pure: no clock reads, no I/O, no Android Context. Trivially unit-testable
 * with [RelativeTimeStrings.English] or a custom fixture. The auto-recomposing
 * Composable wrapper lives in [rememberRelativeTimeText].
 */
fun formatRelativeTime(
    now: Instant,
    then: Instant,
    strings: RelativeTimeStrings,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val delta = now - then
    if (delta.isNegative() || delta < ONE_MINUTE) return strings.now
    if (delta < ONE_HOUR) return strings.minutes(delta.inWholeMinutes.toInt())
    if (delta < ONE_DAY) return strings.hours(delta.inWholeHours.toInt())
    if (delta < ONE_WEEK) return strings.days(delta.inWholeDays.toInt())

    val nowDate = now.toLocalDateTime(timeZone).date
    val thenDate = then.toLocalDateTime(timeZone).date
    val pattern = if (nowDate.year == thenDate.year) PATTERN_SAME_YEAR else PATTERN_DIFFERENT_YEAR
    return DateTimeFormatter.ofPattern(pattern, locale).format(thenDate.toJavaLocalDate())
}

/**
 * Format [then] as a relative-time string for the **Chats** convo list,
 * anchored to [now]. Buckets follow Google Chat / Messages convention
 * (calendar-aware), distinct from the Twitter-style compact form in
 * [formatRelativeTime]:
 *
 *  - `< 1m` → [strings.now]
 *  - `< 1h` → [strings.minutes] (e.g. "5m")
 *  - same calendar day (in [timeZone]) → [strings.hours] (e.g. "3h")
 *  - previous calendar day → [strings.yesterday]
 *  - 2–6 calendar days ago → short localized weekday (e.g. "Sun")
 *  - same year → "MMM d" (localized via [locale])
 *  - prior year → "MMM d, yyyy"
 *
 * Calendar comparisons use [timeZone] so the "same day" / "yesterday"
 * boundaries match the user's wall clock, not UTC. Future timestamps
 * (clock skew) clamp to [strings.now] — never throws.
 *
 * Pure: no clock reads, no I/O, no Android Context. The auto-recomposing
 * Composable wrapper lives in [rememberChatRelativeTimeText].
 */
fun formatChatRelativeTime(
    now: Instant,
    then: Instant,
    strings: RelativeTimeStrings,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val delta = now - then
    if (delta.isNegative() || delta < ONE_MINUTE) return strings.now
    if (delta < ONE_HOUR) return strings.minutes(delta.inWholeMinutes.toInt())

    val nowDate = now.toLocalDateTime(timeZone).date.toJavaLocalDate()
    val thenDate = then.toLocalDateTime(timeZone).date.toJavaLocalDate()
    val daysBetween = ChronoUnit.DAYS.between(thenDate, nowDate)

    if (daysBetween == 0L) return strings.hours(delta.inWholeHours.toInt())
    if (daysBetween == 1L) return strings.yesterday
    if (daysBetween in 2L..6L) return DateTimeFormatter.ofPattern(PATTERN_WEEKDAY, locale).format(thenDate)

    val pattern = if (nowDate.year == thenDate.year) PATTERN_SAME_YEAR else PATTERN_DIFFERENT_YEAR
    return DateTimeFormatter.ofPattern(pattern, locale).format(thenDate)
}

private const val PATTERN_SAME_YEAR = "MMM d"
private const val PATTERN_DIFFERENT_YEAR = "MMM d, yyyy"
private const val PATTERN_WEEKDAY = "EEE"
private val ONE_MINUTE: Duration = 1.minutes
private val ONE_HOUR: Duration = 1.hours
private val ONE_DAY: Duration = 1.days
private val ONE_WEEK: Duration = 7.days
