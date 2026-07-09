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
