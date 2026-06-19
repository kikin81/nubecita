package net.kikin.nubecita.feature.moderation.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.data.models.BlockedAccount
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [BlockedAccountsContent]: the loaded list, the empty
 * state, and the initial-error state. Light + dark. Driven through the stateless
 * body with deterministic fixtures.
 */
private fun fixture(
    id: String,
    displayName: String?,
): BlockedAccount =
    BlockedAccount(
        did = "did:plc:$id",
        handle = "$id.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        avatarHue = avatarHueFor(did = "did:plc:$id", handle = "$id.bsky.social"),
        blockUri = "at://did:plc:$id/app.bsky.graph.block/$id",
    )

private val LOADED =
    BlockedAccountsState(
        status =
            BlockedAccountsStatus.Loaded(
                persistentListOf(
                    fixture("spammer", "Spam Account"),
                    fixture("troll", null),
                ),
            ),
    )
private val EMPTY = BlockedAccountsState(status = BlockedAccountsStatus.Loaded(persistentListOf()))
private val ERROR = BlockedAccountsState(status = BlockedAccountsStatus.Error)

@PreviewTest
@Preview(name = "blocked-loaded-light", showBackground = true, heightDp = 500)
@Preview(name = "blocked-loaded-dark", showBackground = true, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlockedAccountsLoadedScreenshot() {
    NubecitaCanvasPreviewTheme {
        BlockedAccountsContent(state = LOADED, snackbarHostState = remember { SnackbarHostState() }, onBack = {}, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "blocked-empty-light", showBackground = true, heightDp = 500)
@Preview(name = "blocked-empty-dark", showBackground = true, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlockedAccountsEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        BlockedAccountsContent(state = EMPTY, snackbarHostState = remember { SnackbarHostState() }, onBack = {}, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "blocked-error-light", showBackground = true, heightDp = 500)
@Preview(name = "blocked-error-dark", showBackground = true, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlockedAccountsErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        BlockedAccountsContent(state = ERROR, snackbarHostState = remember { SnackbarHostState() }, onBack = {}, onEvent = {})
    }
}
