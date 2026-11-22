package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaActorRow
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.highlightRanges

/**
 * Single-actor row for the People tab and the typeahead.
 *
 * A thin adapter over [NubecitaActorRow], which is where the rendering lives
 * since nubecita-qt71.3 — the login typeahead was the third consumer the
 * original "promote when a third surfaces" note was waiting for. Kept as a
 * named wrapper so the People tab's call sites and previews read in terms of
 * the tab's own vocabulary, and so `query` stays required here (a People row
 * always has a query to highlight) while it is optional on the shared row.
 *
 * Adds what search results show beyond the shared row: the account's bio on
 * one ellipsized line (nothing when there is none, so no empty line), and —
 * when [onFollowClick] is given — a Follow / Following button at the end. The
 * button is its own click target (at least 48dp); the rest of the row still
 * opens the profile through [onClick]. Typeahead rows pass no [onFollowClick]
 * and so show no button. The handle's shared `.bsky.social` suffix is never
 * highlighted, see [highlightRanges].
 *
 * Stateless: takes plain data and lambdas, so the wide-window summary pane can
 * reuse it unchanged.
 */
@Composable
internal fun ActorRow(
    actor: ActorUi,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFollowClick: (() -> Unit)? = null,
) {
    val bio = actor.bio
    NubecitaActorRow(
        actor = actor,
        onClick = onClick,
        modifier = modifier,
        query = query,
        handleHighlightRanges = highlightRanges(text = actor.handle, query = query, isHandle = true),
        trailingContent =
            if (onFollowClick != null) {
                { FollowButton(actor = actor, onClick = onFollowClick) }
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

/**
 * Follow when the viewer does not follow [actor], the outlined Following state
 * when they do — the same Button / OutlinedButton pair the Discover cards use.
 * The accessible name carries the account so a screen reader does not announce
 * a row of identical "Follow" buttons.
 */
@Composable
private fun FollowButton(
    actor: ActorUi,
    onClick: () -> Unit,
) {
    val name = actor.displayName?.takeIf { it.isNotBlank() } ?: actor.handle
    if (actor.isFollowing) {
        val description = stringResource(R.string.search_actor_unfollow_content_desc, name)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.discover_following_button))
        }
    } else {
        val description = stringResource(R.string.search_actor_follow_content_desc, name)
        Button(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.discover_follow_button))
        }
    }
}

@Preview(name = "ActorRow — with displayName, no match", showBackground = true)
@Composable
private fun ActorRowWithDisplayNameNoMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — with displayName + match", showBackground = true)
@Composable
private fun ActorRowWithMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            query = "ali",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — no displayName, match on handle", showBackground = true)
@Composable
private fun ActorRowNoDisplayNamePreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:nodisplay",
                    handle = "anon42.bsky.social",
                    displayName = null,
                    avatarUrl = null,
                ),
            query = "anon",
            onClick = {},
        )
    }
}

@Preview(
    name = "ActorRow — dark, with avatar URL",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ActorRowDarkPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:withavatar",
                    handle = "avatar.bsky.social",
                    displayName = "With Avatar",
                    avatarUrl = "https://example.com/avatar.jpg",
                ),
            query = "avatar",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — verified badge", showBackground = true)
@Composable
private fun ActorRowVerifiedPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:verified",
                    handle = "verified.bsky.social",
                    displayName = "Verified Vera",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.Verified,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — trusted verifier badge", showBackground = true)
@Composable
private fun ActorRowTrustedVerifierPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:trusted",
                    handle = "trusted.bsky.social",
                    displayName = "Trusted Tomas",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.TrustedVerifier,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — verified, long name keeps badge visible", showBackground = true)
@Composable
private fun ActorRowVerifiedLongNamePreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:longname",
                    handle = "longname.bsky.social",
                    displayName = "A Very Long Display Name That Should Ellipsize Before The Badge",
                    avatarUrl = null,
                    verifiedBadge = VerifiedBadge.Verified,
                ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — bio, follow", showBackground = true)
@Composable
private fun ActorRowBioFollowPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:bio",
                    handle = "science.bsky.social",
                    displayName = "Science Digest",
                    avatarUrl = null,
                    bio = "Daily papers, plainly explained.",
                ),
            query = "sci",
            onClick = {},
            onFollowClick = {},
        )
    }
}

@Preview(name = "ActorRow — following, no bio", showBackground = true)
@Composable
private fun ActorRowFollowingPreview() {
    NubecitaTheme {
        ActorRow(
            actor =
                ActorUi(
                    did = "did:plc:fol",
                    handle = "science.bsky.social",
                    displayName = "Science Digest",
                    avatarUrl = null,
                    isFollowing = true,
                ),
            query = "sci",
            onClick = {},
            onFollowClick = {},
        )
    }
}
