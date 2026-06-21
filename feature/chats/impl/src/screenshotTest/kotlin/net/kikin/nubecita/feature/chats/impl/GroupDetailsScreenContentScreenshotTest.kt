package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [GroupDetailsScreenContent] — the full-screen
 * group-roster presentation. The loaded roster exercises every per-member visual
 * variant (Owner admin chip, viewer-self with hidden Follow button, the three
 * [FollowState] button shapes, and addedBy / display-name presence); loading and
 * error are the two non-roster states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "group-details-loaded-light", showBackground = true, heightDp = 720)
@Preview(name = "group-details-loaded-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupDetailsLoadedScreenshot() {
    val members =
        persistentListOf(
            GroupMemberUi(
                did = "did:plc:owner",
                handle = "ada.bsky.social",
                displayName = "Ada Lovelace",
                avatarUrl = null,
                role = GroupRole.Owner,
                addedByName = null,
                isViewer = false,
                followState = FollowState.Following,
                followUri = "at://did:plc:owner/app.bsky.graph.follow/1",
            ),
            GroupMemberUi(
                did = "did:plc:viewer",
                handle = "me.bsky.social",
                displayName = "Me",
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = "Ada Lovelace",
                isViewer = true,
                followState = FollowState.NotFollowing,
                followUri = null,
            ),
            GroupMemberUi(
                did = "did:plc:grace",
                handle = "grace.bsky.social",
                displayName = "Grace Hopper",
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = "Ada Lovelace",
                isViewer = false,
                followState = FollowState.NotFollowing,
                followUri = null,
            ),
            GroupMemberUi(
                did = "did:plc:katherine",
                handle = "katherine.bsky.social",
                displayName = "Katherine Johnson",
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = null,
                isViewer = false,
                followState = FollowState.Following,
                followUri = "at://did:plc:katherine/app.bsky.graph.follow/2",
            ),
            GroupMemberUi(
                did = "did:plc:alan",
                handle = "alan.bsky.social",
                displayName = null,
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = null,
                isViewer = false,
                followState = FollowState.InFlight,
                followUri = null,
            ),
        )
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    status =
                        GroupDetailsLoadStatus.Loaded(
                            members = members,
                            memberCount = members.size,
                        ),
                ),
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "group-details-owner-light", showBackground = true, heightDp = 720)
@Preview(name = "group-details-owner-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupDetailsOwnerScreenshot() {
    val members =
        persistentListOf(
            GroupMemberUi(
                did = "did:plc:viewer",
                handle = "me.bsky.social",
                displayName = "Me",
                avatarUrl = null,
                role = GroupRole.Owner,
                addedByName = null,
                isViewer = true,
                followState = FollowState.NotFollowing,
                followUri = null,
            ),
            GroupMemberUi(
                did = "did:plc:grace",
                handle = "grace.bsky.social",
                displayName = "Grace Hopper",
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = "Me",
                isViewer = false,
                followState = FollowState.Following,
                followUri = "at://did:plc:grace/app.bsky.graph.follow/1",
            ),
            GroupMemberUi(
                did = "did:plc:katherine",
                handle = "katherine.bsky.social",
                displayName = "Katherine Johnson",
                avatarUrl = null,
                role = GroupRole.Member,
                addedByName = "Me",
                isViewer = false,
                followState = FollowState.NotFollowing,
                followUri = null,
            ),
        )
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    viewerRole = GroupRole.Owner,
                    status =
                        GroupDetailsLoadStatus.Loaded(
                            members = members,
                            memberCount = members.size,
                        ),
                ),
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "group-details-loading-light", showBackground = true, heightDp = 480)
@Preview(name = "group-details-loading-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupDetailsLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state = GroupDetailsViewState(name = "Mathematicians", status = GroupDetailsLoadStatus.Loading),
            onEvent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "group-details-error-light", showBackground = true, heightDp = 480)
@Preview(name = "group-details-error-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupDetailsErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        GroupDetailsScreenContent(
            state =
                GroupDetailsViewState(
                    name = "Mathematicians",
                    status = GroupDetailsLoadStatus.InitialError(ChatError.Network),
                ),
            onEvent = {},
        )
    }
}
