package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship

/**
 * Screenshot baselines for [OtherUserActionsRow]. Two relationships
 * (NotFollowing → filled Follow CTA; Following → outlined Following)
 * × two themes = 4 baselines. Overflow open-state is verified by
 * instrumentation only (DropdownMenu overlay doesn't compose
 * deterministically in Layoutlib).
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
            viewerRelationship = ViewerRelationship.NotFollowing,
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
            viewerRelationship = ViewerRelationship.Following,
            onFollow = {},
            onMessage = {},
            onOverflowAction = {},
        )
    }
}
