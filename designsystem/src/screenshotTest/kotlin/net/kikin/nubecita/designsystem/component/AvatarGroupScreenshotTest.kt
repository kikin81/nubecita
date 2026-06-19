package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

private fun member(
    n: Int,
    withPhoto: Boolean,
) = AuthorUi(
    did = "did:plc:m$n",
    handle = "user$n.bsky.social",
    displayName = "User $n",
    avatarUrl = if (withPhoto) "https://example.test/$n.jpg" else null,
)

@Composable
private fun AvatarGroupFixtures() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarGroup(members = persistentListOf(member(1, false)), contentDescription = null)
        AvatarGroup(members = (1..3).map { member(it, false) }.toPersistentList(), contentDescription = null)
        AvatarGroup(members = (1..9).map { member(it, false) }.toPersistentList(), contentDescription = null)
        AvatarGroup(
            members = persistentListOf(member(1, true), member(2, false), member(3, true), member(4, false), member(5, false)),
            contentDescription = null,
        )
    }
}

@PreviewTest
@Preview(name = "avatar-group-light", showBackground = true)
@Preview(name = "avatar-group-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun AvatarGroupScreenshot() {
    AvatarGroupFixtures()
}
