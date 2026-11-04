package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.component.AvatarGroup
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.feed.impl.R

/**
 * "There are new posts above you" — a few faces and an upward chevron,
 * floating over the timeline.
 *
 * No count, deliberately. The number of posts that arrived is not something a
 * reader acts on, and showing it turns the feed into an inbox with a backlog
 * to clear — the feeling this whole change exists to avoid. Faces answer the
 * question a reader actually has, which is *who* has been posting.
 *
 * Tapping means "take me there and stop telling me": the caller scrolls to the
 * top and records the dismissal, so the same entries never raise it again.
 *
 * Visibility is driven entirely by [authors] being non-empty — the pill owns
 * no state of its own, because what makes it appear is rows in the database
 * above the reader's place, which outlive any composition (D20).
 */
@Composable
internal fun NewPostsPill(
    authors: ImmutableList<AuthorUi>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.feed_new_posts)
    AnimatedVisibility(
        visible = authors.isNotEmpty(),
        // Decelerate in, accelerate out, per M3's motion contract: the pill
        // arrives under its own weight and leaves briskly once acted on.
        enter =
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ) { height -> -height },
        exit =
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                ) { height -> -height },
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            // A floating affordance over the list, so it carries elevation
            // rather than a surfaceContainer token: it is above the content,
            // not a depth layer within it.
            shadowElevation = PILL_ELEVATION,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                modifier = Modifier.padding(horizontal = PILL_PADDING_HORIZONTAL, vertical = PILL_PADDING_VERTICAL),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.ExpandMore,
                    contentDescription = null,
                    // Rotated rather than a new glyph: the subset has no
                    // upward chevron, and half a turn of this one is exactly
                    // the shape the design asks for — cheaper than
                    // regenerating the icon font for a single codepoint.
                    modifier = Modifier.size(PILL_ICON_SIZE).rotate(HALF_TURN),
                )
                AvatarGroup(
                    members = authors,
                    // The pill as a whole already describes itself; the faces
                    // would otherwise read out three handles the reader did
                    // not ask for.
                    contentDescription = null,
                    maxVisible = PILL_MAX_FACES,
                    avatarSize = PILL_AVATAR_SIZE,
                )
            }
        }
    }
}

private val PILL_ELEVATION = 6.dp
private val PILL_GAP = 6.dp
private val PILL_PADDING_HORIZONTAL = 12.dp
private val PILL_PADDING_VERTICAL = 8.dp
private val PILL_ICON_SIZE = 18.dp
private val PILL_AVATAR_SIZE = 24.dp
private const val PILL_MAX_FACES = 3
private const val HALF_TURN = 180f
