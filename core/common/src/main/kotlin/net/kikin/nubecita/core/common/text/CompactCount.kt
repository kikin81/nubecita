package net.kikin.nubecita.core.common.text

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLocale
import java.util.Locale

/**
 * Locale-aware short-scale abbreviated count (e.g. `86`, `999`,
 * `1.2K`, `1.2M`) for use in tight UI affordances like a post's
 * action-row stats or a profile header's followers count.
 *
 * Uses Android's ICU [CompactDecimalFormat] so the abbreviation
 * matches the user's device locale: `1.2K` in en-US, `1,2 mil` in
 * es, locale-specific grouping in Hindi (Lakh/Crore), etc.
 *
 * Keyed on `LocalLocale.current.platformLocale` so a Settings-app
 * language change re-formats on the next recomposition instead of
 * leaving stale strings on screen. Use this over
 * `CompactDecimalFormat.getInstance(Locale.getDefault(), ...)` —
 * `Locale.getDefault()` is captured at the first call and won't
 * react to runtime locale switches.
 *
 * The formatter is `remember`-ed per locale (not per count) because
 * `getInstance` does non-trivial ICU table lookups; one allocation
 * per locale change is the right cadence for a hot scroll path.
 */
@Composable
public fun rememberCompactCount(count: Long): String {
    val locale = LocalLocale.current.platformLocale
    val formatter =
        remember(locale) {
            CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        }
    return remember(formatter, count) { formatter.format(count) }
}

/**
 * Non-composable backdoor for unit tests and call sites that have
 * already resolved a locale (e.g. a top-level extension on a string
 * formatter chain). Production composable call sites should prefer
 * [rememberCompactCount] so they pick up Compose-side locale
 * propagation.
 */
public fun formatCompactCount(
    count: Long,
    locale: Locale,
): String =
    CompactDecimalFormat
        .getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        .format(count)
