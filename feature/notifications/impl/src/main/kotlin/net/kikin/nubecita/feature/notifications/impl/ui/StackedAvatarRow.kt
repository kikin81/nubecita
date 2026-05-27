package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.feature.notifications.impl.R

/**
 * Overlapping-avatar row used on aggregated notification rows.
 *
 * Renders up to [maxVisible] avatars with each subsequent avatar
 * shifted left by [OVERLAP_OFFSET] dp so the row reads as a stacked
 * thumbnail strip. When `actors.size > maxVisible` the (`maxVisible`)th
 * slot becomes a "+N" pill instead of an avatar so the user still sees
 * the overflow count.
 *
 * Each avatar gets a small surface-colored ring so the overlap reads
 * as discrete circles rather than a smear when actors have similar
 * avatar colors. The ring color tracks `MaterialTheme.colorScheme.surface`
 * so dark mode and tonal-tinted unread rows both render correctly.
 */
@Composable
internal fun StackedAvatarRow(
    actors: ImmutableList<AuthorUi>,
    modifier: Modifier = Modifier,
    maxVisible: Int = DEFAULT_MAX_VISIBLE,
    avatarSize: androidx.compose.ui.unit.Dp = DEFAULT_AVATAR_SIZE,
) {
    // Cap visible bubbles at maxVisible: when there are MORE actors than fit,
    // the LAST visible slot becomes a "+N" pill (NOT an extra bubble appended
    // after maxVisible avatars). So with maxVisible=5 and actors.size=8, we
    // render 4 avatars + 1 "+4" pill = 5 visible bubbles total. With
    // actors.size <= maxVisible no pill renders. Matches the KDoc at line 35.
    val overflows = actors.size > maxVisible
    val avatarSlots =
        if (overflows) maxVisible - 1 else actors.size
    val overflowCount = actors.size - avatarSlots
    val ringColor = MaterialTheme.colorScheme.surface
    val description =
        androidx.compose.ui.res.pluralStringResource(
            R.plurals.notifications_avatar_stack_description,
            actors.size,
            actors.size,
        )
    Row(
        modifier =
            modifier.semantics(mergeDescendants = true) {
                contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(-OVERLAP_OFFSET),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (actor in actors.take(avatarSlots)) {
            NubecitaAvatar(
                model = actor.avatarUrl,
                contentDescription = null,
                size = avatarSize,
                modifier =
                    Modifier
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape),
            )
        }
        if (overflows) {
            Box(
                modifier =
                    Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.notifications_avatar_overflow, overflowCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val OVERLAP_OFFSET = 10.dp
private val DEFAULT_AVATAR_SIZE = 32.dp
private const val DEFAULT_MAX_VISIBLE = 5

// ---------- Previews -------------------------------------------------------

private fun fakeAuthors(count: Int): ImmutableList<AuthorUi> =
    (0 until count)
        .map { i ->
            AuthorUi(
                did = "did:plc:preview-stack-$i",
                handle = "user$i.bsky.social",
                displayName = "User $i",
                avatarUrl = null,
            )
        }.toImmutableList()

@Preview(name = "StackedAvatarRow — 1 actor (light)", showBackground = true)
@Composable
private fun StackedAvatarRowSinglePreview() {
    NubecitaTheme {
        StackedAvatarRow(actors = fakeAuthors(1))
    }
}

@Preview(name = "StackedAvatarRow — 2 actors (light)", showBackground = true)
@Composable
private fun StackedAvatarRowTwoPreview() {
    NubecitaTheme {
        StackedAvatarRow(actors = fakeAuthors(2))
    }
}

@Preview(name = "StackedAvatarRow — 5 actors (at cap)", showBackground = true)
@Composable
private fun StackedAvatarRowFivePreview() {
    NubecitaTheme {
        StackedAvatarRow(actors = fakeAuthors(5))
    }
}

@Preview(name = "StackedAvatarRow — 8 actors (+4 overflow)", showBackground = true)
@Composable
private fun StackedAvatarRowEightPreview() {
    // 8 actors, maxVisible=5 → 4 avatars + a "+4" pill (the pill consumes
    // the 5th visible slot per the overflow contract).
    NubecitaTheme {
        StackedAvatarRow(actors = fakeAuthors(8))
    }
}

@Preview(
    name = "StackedAvatarRow — 8 actors (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun StackedAvatarRowEightDarkPreview() {
    NubecitaTheme {
        StackedAvatarRow(actors = fakeAuthors(8))
    }
}
