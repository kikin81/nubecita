package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.ActorUi
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
