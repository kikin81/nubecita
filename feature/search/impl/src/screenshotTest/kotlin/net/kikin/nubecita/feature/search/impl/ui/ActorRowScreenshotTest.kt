package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "actor-row-with-displayname-no-match-light", showBackground = true)
@Preview(
    name = "actor-row-with-displayname-no-match-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ActorRowWithDisplayNameNoMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

@PreviewTest
@Preview(name = "actor-row-with-match-light", showBackground = true)
@Preview(name = "actor-row-with-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowWithMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

@PreviewTest
@Preview(name = "actor-row-no-displayname-light", showBackground = true)
@Preview(name = "actor-row-no-displayname-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowNoDisplayNameScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

@PreviewTest
@Preview(name = "actor-row-verified-light", showBackground = true)
@Preview(name = "actor-row-verified-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowVerifiedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

@PreviewTest
@Preview(name = "actor-row-trusted-verifier-light", showBackground = true)
@Preview(name = "actor-row-trusted-verifier-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowTrustedVerifierScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

@PreviewTest
@Preview(name = "actor-row-verified-long-name-light", showBackground = true)
@Preview(name = "actor-row-verified-long-name-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowVerifiedLongNameScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
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
}

private val SCIENCE_DIGEST =
    ActorUi(
        did = "did:plc:science",
        handle = "science.bsky.social",
        displayName = "Science Digest",
        avatarUrl = null,
        bio = "Daily papers, plainly explained.",
    )

@PreviewTest
@Preview(name = "actor-row-bio-follow-light", showBackground = true)
@Preview(name = "actor-row-bio-follow-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowBioFollowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(actor = SCIENCE_DIGEST, query = "sci", onClick = {}, onFollowClick = {})
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-following-light", showBackground = true)
@Preview(name = "actor-row-following-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowFollowingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(
                actor = SCIENCE_DIGEST.copy(isFollowing = true, followUri = "at://did:plc:me/app.bsky.graph.follow/1"),
                query = "sci",
                onClick = {},
                onFollowClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-no-bio-follow-light", showBackground = true)
@Preview(name = "actor-row-no-bio-follow-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowNoBioFollowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            // No bio: the row shows no empty line under the handle.
            ActorRow(actor = SCIENCE_DIGEST.copy(bio = null), query = "sci", onClick = {}, onFollowClick = {})
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-long-name-long-bio-light", showBackground = true)
@Preview(name = "actor-row-long-name-long-bio-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowLongNameLongBioScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(
                actor =
                    SCIENCE_DIGEST.copy(
                        displayName = "The Society For Slow And Careful Science Communication Worldwide",
                        bio =
                            "Daily papers, plainly explained, with a long enough bio that it cannot " +
                                "fit on one line and has to ellipsize before the follow button.",
                    ),
                query = "",
                onClick = {},
                onFollowClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-bio-follow-es419", showBackground = true, locale = "b+es+419")
@Preview(name = "actor-row-following-es419", showBackground = true, locale = "b+es+419")
@Composable
private fun ActorRowFollowEs419Screenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(actor = SCIENCE_DIGEST, query = "sci", onClick = {}, onFollowClick = {})
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-suffix-not-highlighted-light", showBackground = true)
@Composable
private fun ActorRowSuffixNotHighlightedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            // "al" is inside "social" as well as "alice": only the name part is highlighted.
            ActorRow(
                actor = SCIENCE_DIGEST.copy(handle = "alice.bsky.social", displayName = null, bio = null),
                query = "al",
                onClick = {},
            )
        }
    }
}
