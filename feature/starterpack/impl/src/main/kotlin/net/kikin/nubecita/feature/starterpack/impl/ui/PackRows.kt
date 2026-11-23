package net.kikin.nubecita.feature.starterpack.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.component.NubecitaActorRow
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.starterpack.impl.R
import net.kikin.nubecita.feature.starterpack.impl.data.PackFeedUi
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackDetail

/**
 * The pack's name, "by @creator" and description. The name here is the page's own title; the
 * top bar repeats it so it stays visible while scrolling.
 */
@Composable
internal fun PackHeader(
    pack: StarterPackDetail,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.s5, vertical = MaterialTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s1),
    ) {
        Text(
            text = pack.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.starterpack_byline, pack.creatorHandle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        pack.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = MaterialTheme.spacing.s2),
            )
        }
    }
}

/** A section title ("Feeds", "People"), with the member count after "People" when known. */
@Composable
internal fun PackSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.s5, vertical = MaterialTheme.spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        if (count != null) {
            Text(
                text = pluralStringResource(R.plurals.starterpack_member_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One of the pack's feeds: avatar tile, name and "by @creator". The whole row opens the feed. */
@Composable
internal fun PackFeedRow(
    feed: PackFeedUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaAsyncImage(
            model = feed.avatarUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.starterpack_byline, feed.creatorHandle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A pack member: the shared [NubecitaActorRow] with the account's one-line bio and, unless
 * [onFollowClick] is null, a Follow / Following button. The button is its own click target;
 * the rest of the row opens the profile. The signed-in account's own row passes no
 * [onFollowClick].
 */
@Composable
internal fun PackMemberRow(
    actor: ActorUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFollowClick: (() -> Unit)? = null,
) {
    val bio = actor.bio
    NubecitaActorRow(
        actor = actor,
        onClick = onClick,
        modifier = modifier,
        trailingContent =
            if (onFollowClick != null) {
                { PackFollowButton(actor = actor, onClick = onFollowClick) }
            } else {
                null
            },
        supportingContent =
            if (bio != null) {
                {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                null
            },
    )
}

@Composable
private fun PackFollowButton(
    actor: ActorUi,
    onClick: () -> Unit,
) {
    val name = actor.displayName?.takeIf { it.isNotBlank() } ?: actor.handle
    if (actor.isFollowing) {
        val description = stringResource(R.string.starterpack_unfollow_content_desc, name)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.starterpack_following_button))
        }
    } else {
        val description = stringResource(R.string.starterpack_follow_content_desc, name)
        Button(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.starterpack_follow_button))
        }
    }
}
