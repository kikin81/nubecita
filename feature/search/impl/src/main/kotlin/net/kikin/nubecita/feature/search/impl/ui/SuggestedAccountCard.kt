package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SuggestedAccountUi

private val CARD_WIDTH = 192.dp
private val CARD_AVATAR_SIZE = 56.dp
private val MUTUAL_AVATAR_SIZE = 20.dp
private const val MUTUAL_AVATAR_OVERLAP_FRACTION = 0.30f

/**
 * Threads-style suggested-account card for the Discover tab's accounts carousel.
 *
 * Layout (centered column inside an [ElevatedCard]):
 * - Large avatar ([CARD_AVATAR_SIZE]) at center.
 * - Display name (bold) + @handle.
 * - When [SuggestedAccountUi.mutualsCount] > 0: a small overlapping avatar
 *   stack drawn from [SuggestedAccountUi.mutualAvatarUrls] followed by
 *   "N mutual(s)" text.
 * - Full-width **Follow** ([Button]) / **Following** ([OutlinedButton]) toggle.
 *
 * Click targets:
 * - Whole card tap → [onAccountClick] (→ `OnAccountTapped` event).
 * - Follow/Following button → [onFollowClick] (→ `OnFollowTapped` event).
 * - Dismiss × in top-right corner → [onDismissClick] (→ `OnAccountDismissed`).
 *   Neither button tap propagates to the card's outer click target — child
 *   clicks consume the event in Compose.
 *
 * Surface token: [MaterialTheme.colorScheme.surfaceContainer] (item card per
 * the surface-roles doc). Elevation shadow from [ElevatedCard] provides depth
 * cue without tonal elevation lift (cards live in a layout, not a windowed
 * surface).
 */
@Composable
internal fun SuggestedAccountCard(
    account: SuggestedAccountUi,
    onAccountClick: () -> Unit,
    onFollowClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardContentDesc =
        stringResource(
            R.string.discover_account_card_content_desc,
            account.displayName ?: account.handle,
        )
    ElevatedCard(
        modifier =
            modifier
                .width(CARD_WIDTH)
                .clickable(onClick = onAccountClick)
                .semantics { contentDescription = cardContentDesc },
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 40.dp, // leave room for the dismiss button row
                            bottom = 16.dp,
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NubecitaAvatar(
                    model = account.avatarUrl,
                    contentDescription = null,
                    size = CARD_AVATAR_SIZE,
                    fallback =
                        avatarFallbackFor(
                            did = account.did,
                            handle = account.handle,
                            displayName = account.displayName,
                        ),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = account.displayName ?: account.handle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (account.displayName != null) {
                        Text(
                            text = "@${account.handle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (account.mutualsCount > 0) {
                    val mutualsText =
                        pluralStringResource(
                            id = R.plurals.discover_mutuals_count,
                            count = account.mutualsCount,
                            account.mutualsCount,
                        )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (account.mutualAvatarUrls.isNotEmpty()) {
                            MutualAvatarStack(avatarUrls = account.mutualAvatarUrls)
                        }
                        Text(
                            text = mutualsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Reserve height when there are no mutuals so all cards in the
                    // carousel align their Follow buttons on the same baseline.
                    Spacer(modifier = Modifier.height(20.dp))
                }
                if (account.isFollowing) {
                    OutlinedButton(
                        onClick = onFollowClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.discover_following_button))
                    }
                } else {
                    Button(
                        onClick = onFollowClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.discover_follow_button))
                    }
                }
            }
            // Dismiss × — top-right corner, above the avatar so it doesn't
            // interfere with the card tap target.
            IconButton(
                onClick = onDismissClick,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.discover_dismiss_content_desc),
                    opticalSize = 18.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Small overlapping circular avatar stack from a list of image URLs.
 *
 * Mirrors [net.kikin.nubecita.designsystem.component.AvatarGroup]'s overlap
 * geometry (30 % of the avatar diameter) but works with raw URL strings rather
 * than [net.kikin.nubecita.data.models.AuthorUi] objects, so it avoids
 * constructing fake domain models. Up to 3 URLs are rendered; extras are
 * silently dropped (callers should trim before passing).
 */
@Composable
private fun MutualAvatarStack(
    avatarUrls: ImmutableList<String>,
    modifier: Modifier = Modifier,
) {
    val ringColor = MaterialTheme.colorScheme.surfaceContainer
    val overlap = MUTUAL_AVATAR_SIZE * MUTUAL_AVATAR_OVERLAP_FRACTION
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-overlap),
    ) {
        avatarUrls.take(3).forEachIndexed { index, url ->
            NubecitaAvatar(
                model = url,
                contentDescription = null,
                size = MUTUAL_AVATAR_SIZE,
                modifier =
                    Modifier
                        .zIndex((avatarUrls.size - index).toFloat())
                        .border(width = 1.dp, color = ringColor, shape = CircleShape),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "AccountCard — not following, no mutuals", showBackground = true)
@Composable
private fun AccountCardNotFollowingNoMutualsPreview() {
    NubecitaTheme {
        SuggestedAccountCard(
            account = SAMPLE_ACCOUNT_NO_MUTUALS,
            onAccountClick = {},
            onFollowClick = {},
            onDismissClick = {},
        )
    }
}

@Preview(name = "AccountCard — not following, with mutuals", showBackground = true)
@Composable
private fun AccountCardNotFollowingWithMutualsPreview() {
    NubecitaTheme {
        SuggestedAccountCard(
            account = SAMPLE_ACCOUNT_WITH_MUTUALS,
            onAccountClick = {},
            onFollowClick = {},
            onDismissClick = {},
        )
    }
}

@Preview(name = "AccountCard — following", showBackground = true)
@Composable
private fun AccountCardFollowingPreview() {
    NubecitaTheme {
        SuggestedAccountCard(
            account = SAMPLE_ACCOUNT_FOLLOWING,
            onAccountClick = {},
            onFollowClick = {},
            onDismissClick = {},
        )
    }
}

@Preview(
    name = "AccountCard — dark, with mutuals",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AccountCardDarkPreview() {
    NubecitaTheme {
        SuggestedAccountCard(
            account = SAMPLE_ACCOUNT_WITH_MUTUALS,
            onAccountClick = {},
            onFollowClick = {},
            onDismissClick = {},
        )
    }
}

internal val SAMPLE_ACCOUNT_NO_MUTUALS =
    SuggestedAccountUi(
        did = "did:plc:test1",
        handle = "alice.bsky.social",
        displayName = "Alice Chen",
        avatarUrl = null,
        isFollowing = false,
        followUri = null,
        mutualsCount = 0,
        mutualAvatarUrls = persistentListOf(),
    )

internal val SAMPLE_ACCOUNT_WITH_MUTUALS =
    SuggestedAccountUi(
        did = "did:plc:test2",
        handle = "science.bsky.social",
        displayName = "Science Digest",
        avatarUrl = null,
        isFollowing = false,
        followUri = null,
        mutualsCount = 42,
        mutualAvatarUrls =
            persistentListOf(
                "https://cdn.bsky.app/img/avatar/plain/did:plc:mutual1/bafkrei@jpeg",
                "https://cdn.bsky.app/img/avatar/plain/did:plc:mutual2/bafkrei@jpeg",
                "https://cdn.bsky.app/img/avatar/plain/did:plc:mutual3/bafkrei@jpeg",
            ),
    )

internal val SAMPLE_ACCOUNT_FOLLOWING =
    SuggestedAccountUi(
        did = "did:plc:test3",
        handle = "techweekly.bsky.social",
        displayName = "Tech Weekly",
        avatarUrl = null,
        isFollowing = true,
        followUri = "at://did:plc:me/app.bsky.graph.follow/test3",
        mutualsCount = 8,
        mutualAvatarUrls = persistentListOf(),
    )
