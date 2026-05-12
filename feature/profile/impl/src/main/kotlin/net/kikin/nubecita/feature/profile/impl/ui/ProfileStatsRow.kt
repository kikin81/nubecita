package net.kikin.nubecita.feature.profile.impl.ui

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.R

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
    // Allocate the formatter once per locale; reused across the three
    // counts and across recompositions (e.g. the hero re-rendering as
    // it scrolls). `CompactDecimalFormat.getInstance` does non-trivial
    // ICU table lookups, so the saved work matters on hot paths.
    val formatter =
        remember(locale) {
            CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        }
    val posts = formatter.format(postsCount)
    val followers = formatter.format(followersCount)
    val follows = formatter.format(followsCount)
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
