package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi

private fun info(requireApproval: Boolean) =
    GroupPublicInfoUi(
        name = "Book Club",
        memberCount = 7,
        ownerDisplayName = "Alice",
        ownerHandle = "alice.bsky.social",
        ownerAvatarUrl = null,
        requireApproval = requireApproval,
    )

@Composable
private fun Fixture(requireApproval: Boolean) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            GroupJoinPreviewCard(info = info(requireApproval), joinInFlight = false, onJoin = {})
        }
    }
}

@PreviewTest
@Preview(name = "join-light", showBackground = true)
@Preview(name = "join-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewJoin() = Fixture(requireApproval = false)

@PreviewTest
@Preview(name = "request-light", showBackground = true)
@Preview(name = "request-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewRequest() = Fixture(requireApproval = true)
