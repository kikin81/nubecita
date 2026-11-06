package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import net.kikin.nubecita.feature.profile.api.ProfileConnectionsTab
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi

/**
 * Baselines for the screen's parts. A `LazyPagingItems` can't advance past
 * loading in the screenshot host, so a whole-screen frame would only ever
 * capture the spinner.
 */
private fun row(
    name: String?,
    handle: String,
    followUri: String? = null,
    canFollow: Boolean = true,
    badge: VerifiedBadge = VerifiedBadge.None,
) = ConnectionRowUi(
    actor = ActorUi(did = "did:plc:$handle", handle = handle, displayName = name, avatarUrl = null, verifiedBadge = badge),
    followUri = followUri,
    canFollow = canFollow,
)

@Composable
private fun Row(row: ConnectionRowUi) {
    ConnectionRow(row = row, display = row.followDisplay(null), onClick = {}, onFollowClick = {})
}

@PreviewTest
@Preview(name = "rows-light", showBackground = true)
@Preview(name = "rows-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun ConnectionRowsScreenshot() {
    Column {
        Row(row("Alice Chen", "alice.bsky.social"))
        Row(row("Bob Iglesias", "bob.dev", followUri = "at://follow", badge = VerifiedBadge.Verified))
        Row(row("You", "me.bsky.social", canFollow = false))
        Row(row(null, "handle-only.bsky.social"))
        Row(row("A display name long enough that it has to give way to the button", "long.bsky.social"))
    }
}

@PreviewTest
@Preview(name = "tabs-followers-light", showBackground = true)
@Preview(name = "tabs-followers-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun ConnectionTabsFollowersScreenshot() {
    ConnectionTabs(selectedTab = ProfileConnectionsTab.Followers, followersCount = 1, followsCount = 283, onSelect = {})
}

@PreviewTest
@Preview(name = "tabs-following-es-large", showBackground = true, locale = "es")
@Preview(name = "tabs-following-pt-large", showBackground = true, locale = "pt-rBR", fontScale = 1.3f)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun ConnectionTabsLongLabelScreenshot() {
    ConnectionTabs(selectedTab = ProfileConnectionsTab.Following, followersCount = 2_400_000, followsCount = 12_300, onSelect = {})
}
