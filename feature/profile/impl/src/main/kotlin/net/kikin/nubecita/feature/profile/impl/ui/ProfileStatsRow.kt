package net.kikin.nubecita.feature.profile.impl.ui

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R
import java.util.Locale

/**
 * Inline stats row rendered inside the hero: one [Text] containing three
 * locale-aware short-scale-abbreviated counts separated by `·`.
 *
 * Spec mandates the inline shape ("MUST NOT be present" for the chip
 * variant) — see openspec/changes/add-profile-feature/specs/
 * feature-profile/spec.md "Inline stats and meta row replace the
 * chip variant".
 */
@Composable
internal fun ProfileStatsRow(
    postsCount: Long,
    followersCount: Long,
    followsCount: Long,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val posts = formatCompact(postsCount, locale)
    val followers = formatCompact(followersCount, locale)
    val follows = formatCompact(followsCount, locale)
    val postsLabel = stringResource(R.string.profile_stats_posts_label)
    val followersLabel = stringResource(R.string.profile_stats_followers_label)
    val followsLabel = stringResource(R.string.profile_stats_follows_label)
    Row(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "$posts $postsLabel · $followers $followersLabel · $follows $followsLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Locale-aware short-scale abbreviation via [CompactDecimalFormat]. The
 * `android.icu` variant is available on API 24+ (project `minSdk` is 28),
 * so no fallback path is required. Compact formatting is described in the
 * spec as an aesthetic enhancement, not a correctness requirement.
 */
private fun formatCompact(
    value: Long,
    locale: Locale,
): String =
    CompactDecimalFormat
        .getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        .format(value)
