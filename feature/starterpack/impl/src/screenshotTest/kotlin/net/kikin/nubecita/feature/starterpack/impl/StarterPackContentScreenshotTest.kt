package net.kikin.nubecita.feature.starterpack.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.starterpack.impl.data.PackFeedUi
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackDetail
import net.kikin.nubecita.feature.starterpack.impl.ui.StarterPackContent

private val FEEDS =
    persistentListOf(
        PackFeedUi("at://did:plc:f1/app.bsky.feed.generator/android", "Android", "labnotes.bsky.social", null),
        PackFeedUi("at://did:plc:f2/app.bsky.feed.generator/compose", "Jetpack Compose", "studio.bsky.social", null),
    )

private fun pack(withFeeds: Boolean) =
    StarterPackDetail(
        uri = "at://did:plc:creator/app.bsky.graph.starterpack/android",
        name = "Android developers",
        creatorHandle = "labnotes.bsky.social",
        description = "People building, designing and writing about Android.",
        memberCount = 42,
        listUri = "at://did:plc:creator/app.bsky.graph.list/android",
        feeds = if (withFeeds) FEEDS else persistentListOf(),
    )

private val MEMBERS =
    listOf(
        ActorUi(did = "did:plc:a", handle = "alice.bsky.social", displayName = "Alice Chen", avatarUrl = null, bio = "Compose and coroutines."),
        ActorUi(did = "did:plc:me", handle = "me.bsky.social", displayName = "Me", avatarUrl = null, bio = "The signed-in account: no button."),
        ActorUi(
            did = "did:plc:b",
            handle = "bob.bsky.social",
            displayName = "Bob",
            avatarUrl = null,
            bio = null,
            isFollowing = true,
            followUri = "at://did:plc:me/app.bsky.graph.follow/1",
        ),
    ).toImmutableList()

private fun loaded(
    withFeeds: Boolean,
    isAppending: Boolean = false,
) = StarterPackState(
    viewerDid = "did:plc:me",
    status =
        StarterPackLoadStatus.Loaded(
            pack = pack(withFeeds),
            members = MEMBERS,
            nextCursor = "c2",
            endReached = false,
            isAppending = isAppending,
        ),
)

@PreviewTest
@Preview(name = "starterpack-loaded-with-feeds-light", showBackground = true)
@Preview(name = "starterpack-loaded-with-feeds-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackLoadedWithFeedsScreenshot() {
    NubecitaTheme(dynamicColor = false) { Surface { StarterPackContent(state = loaded(withFeeds = true), onEvent = {}, onBack = {}) } }
}

@PreviewTest
@Preview(name = "starterpack-loaded-no-feeds-light", showBackground = true)
@Preview(name = "starterpack-loaded-no-feeds-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackLoadedNoFeedsScreenshot() {
    NubecitaTheme(dynamicColor = false) { Surface { StarterPackContent(state = loaded(withFeeds = false), onEvent = {}, onBack = {}) } }
}

@PreviewTest
@Preview(name = "starterpack-appending-light", showBackground = true)
@Composable
private fun StarterPackAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) { Surface { StarterPackContent(state = loaded(withFeeds = false, isAppending = true), onEvent = {}, onBack = {}) } }
}

@PreviewTest
@Preview(name = "starterpack-loading-light", showBackground = true)
@Preview(name = "starterpack-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) { Surface { StarterPackContent(state = StarterPackState(), onEvent = {}, onBack = {}) } }
}

@PreviewTest
@Preview(name = "starterpack-error-network-light", showBackground = true)
@Preview(name = "starterpack-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            StarterPackContent(
                state = StarterPackState(status = StarterPackLoadStatus.InitialError(StarterPackError.Network)),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "starterpack-empty-light", showBackground = true)
@Preview(name = "starterpack-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            StarterPackContent(
                state = StarterPackState(status = StarterPackLoadStatus.Empty(pack(withFeeds = false).copy(memberCount = 0))),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
