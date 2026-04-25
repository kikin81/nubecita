package net.kikin.nubecita.core.common.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.common.R
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Resolve [RelativeTimeStrings] from the host's Android string + plural
 * resources. Reading [LocalConfiguration] makes this composable invalidate
 * on locale change, so the strings track the active locale without a
 * process restart.
 */
@Composable
fun rememberRelativeTimeStrings(): RelativeTimeStrings {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return remember(context, configuration) {
        RelativeTimeStrings(
            now = context.getString(R.string.relative_time_now),
            minutes = { count -> context.resources.getQuantityString(R.plurals.relative_time_minutes, count, count) },
            hours = { count -> context.resources.getQuantityString(R.plurals.relative_time_hours, count, count) },
            days = { count -> context.resources.getQuantityString(R.plurals.relative_time_days, count, count) },
        )
    }
}

/**
 * Composable wrapper around [formatRelativeTime] that auto-updates the text
 * as time passes — a "now" label ticks to "1m", "1m" to "2m", and so on
 * without any scroll or surrounding-content recomposition.
 *
 * The producer coroutine is bound to the composition lifecycle: when the
 * composable leaves composition (e.g. its row scrolls out of a LazyColumn)
 * the loop is cancelled. One coroutine per visible card; per-card overhead
 * is negligible compared to a shared ticker, and the API surface stays simple.
 *
 * [strings] defaults to the resource-backed [rememberRelativeTimeStrings];
 * call sites that need to inject a fixture (tests, previews) pass their own.
 * [clock] is parameterized for tests; production call sites pass nothing.
 */
@Composable
fun rememberRelativeTimeText(
    then: Instant,
    strings: RelativeTimeStrings = rememberRelativeTimeStrings(),
    clock: Clock = Clock.System,
): State<String> =
    produceState(
        initialValue = formatRelativeTime(clock.now(), then, strings),
        then,
        strings,
    ) {
        while (true) {
            val now = clock.now()
            value = formatRelativeTime(now, then, strings)
            delay(tickInterval(now - then))
        }
    }

/**
 * Pick a tick interval matching the current bucket — tighter than the bucket
 * boundary so the displayed string never lags by more than ~half the
 * resolution it shows. Past 7 days the format is an absolute date that
 * doesn't change without a calendar flip, so a 1h tick is just a safety net.
 */
private fun tickInterval(age: Duration): Duration =
    when {
        age.isNegative() -> 30.seconds
        age < 1.minutes -> 30.seconds
        age < 1.hours -> 30.seconds
        age < 1.days -> 5.minutes
        else -> 1.hours
    }
