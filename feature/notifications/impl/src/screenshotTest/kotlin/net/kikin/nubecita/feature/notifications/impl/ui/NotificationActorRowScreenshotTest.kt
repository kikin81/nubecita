package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NotificationActorRow] — the single-actor
 * row used inside [ActorListSheet]. Covers handle-only vs displayName +
 * handle variants × light/dark.
 */

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "actor-row-display-name-light", showBackground = true)
@Preview(name = "actor-row-display-name-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationActorRowWithDisplayNameScreenshot() {
    NotificationActorRow(
        actor =
            AuthorUi(
                did = "did:plc:fixture-display",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
        onClick = {},
    )
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "actor-row-handle-only-light", showBackground = true)
@Composable
private fun NotificationActorRowHandleOnlyScreenshot() {
    NotificationActorRow(
        actor =
            AuthorUi(
                did = "did:plc:fixture-handle",
                handle = "anon.bsky.social",
                displayName = "",
                avatarUrl = null,
            ),
        onClick = {},
    )
}
