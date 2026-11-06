package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Inline stats row rendered inside the hero: three locale-aware
 * short-scale-abbreviated counts.
 *
 * Followers and following open the lists, so their numbers stand out against
 * muted labels. The posts count isn't tappable and stays muted throughout.
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
    onFollowersClick: () -> Unit = {},
    onFollowsClick: () -> Unit = {},
) {
    // Locale-aware abbreviation ("1.2K" in en, "1,2 mil" in es). The
    // underlying CompactDecimalFormat instance is remember-keyed on the
    // locale inside the helper, so the three calls share work and
    // recompose only on a Settings-app language switch.
    val posts = rememberCompactCount(postsCount)
    val followers = rememberCompactCount(followersCount)
    val follows = rememberCompactCount(followsCount)
    val countStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    // FlowRow, because a long locale with large counts must wrap. Each count's
    // padding is its gap from its neighbour and, on the tappable ones, a 48dp
    // touch target.
    FlowRow(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        StatText(AnnotatedString(pluralStringResource(R.plurals.profile_stats_posts, postsCount.asQuantity(), posts)))
        StatText(
            text =
                emphasizeCount(
                    text = pluralStringResource(R.plurals.profile_stats_followers, followersCount.asQuantity(), followers),
                    count = followers,
                    style = countStyle,
                ),
            modifier =
                Modifier.clip(MaterialTheme.shapes.small).clickable(
                    onClickLabel = stringResource(R.string.profile_stats_followers_click_label),
                    role = Role.Button,
                    onClick = onFollowersClick,
                ),
        )
        StatText(
            text =
                emphasizeCount(
                    text = stringResource(R.string.profile_stats_following, follows),
                    count = follows,
                    style = countStyle,
                ),
            modifier =
                Modifier.clip(MaterialTheme.shapes.small).clickable(
                    onClickLabel = stringResource(R.string.profile_stats_following_click_label),
                    role = Role.Button,
                    onClick = onFollowsClick,
                ),
        )
    }
}

@Composable
private fun StatText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

/** Finds [count] in the text, because a translation decides where it sits. */
internal fun emphasizeCount(
    text: String,
    count: String,
    style: SpanStyle,
): AnnotatedString {
    val start = text.indexOf(count)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) addStyle(style, start, start + count.length)
    }
}

/** Plural rules only care about small numbers, so a huge count can safely clamp. */
private fun Long.asQuantity(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
