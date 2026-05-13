package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Screenshot baselines for [OtherUserActionsRow]. Two relationships
 * × two pending states × two themes = 8 baselines:
 *
 * - NotFollowing (filled `Follow` CTA, enabled).
 * - NotFollowing pending (filled `Follow`, disabled — the optimistic
 *   unfollow flip already painted the future label).
 * - Following (outlined `Following`, enabled).
 * - Following pending (outlined `Following`, disabled — the optimistic
 *   follow flip already painted the future label).
 *
 * Overflow open-state is verified by instrumentation only (DropdownMenu
 * overlay doesn't compose deterministically in Layoutlib).
 */
@PreviewTest
@Preview(name = "other-follow-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-follow-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            viewerRelationship = ViewerRelationship.NotFollowing(),
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}

@PreviewTest
@Preview(name = "other-follow-pending-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-follow-pending-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowPendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            // Mid-unfollow optimistic state: label flipped to `Follow`,
            // button disabled while the deleteRecord call is in flight.
            viewerRelationship = ViewerRelationship.NotFollowing(isPending = true),
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}

@PreviewTest
@Preview(name = "other-following-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-following-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            viewerRelationship =
                ViewerRelationship.Following(
                    followUri = "at://did:plc:viewer/app.bsky.graph.follow/sample",
                ),
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}

@PreviewTest
@Preview(name = "other-following-pending-light", showBackground = true, heightDp = 80)
@Preview(
    name = "other-following-pending-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OtherUserActionsRowFollowingPendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OtherUserActionsRow(
            // Mid-follow optimistic state: label flipped to `Following`
            // before the wire call returns, button disabled until commit.
            viewerRelationship =
                ViewerRelationship.Following(
                    followUri = null,
                    isPending = true,
                ),
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}
