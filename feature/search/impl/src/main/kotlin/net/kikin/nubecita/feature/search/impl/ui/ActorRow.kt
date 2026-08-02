package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaActorRow

/**
 * Single-actor row for the People tab.
 *
 * A thin adapter over [NubecitaActorRow], which is where the rendering lives
 * since nubecita-qt71.3 — the login typeahead was the third consumer the
 * original "promote when a third surfaces" note was waiting for. Kept as a
 * named wrapper so the People tab's call sites and previews read in terms of
 * the tab's own vocabulary, and so `query` stays required here (a People row
 * always has a query to highlight) while it is optional on the shared row.
 */
@Composable
internal fun ActorRow(
    actor: ActorUi,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NubecitaActorRow(
        actor = actor,
        onClick = onClick,
        modifier = modifier,
        query = query,
    )
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
