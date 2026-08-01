package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    withPhoto: Boolean = false,
) = AuthorUi(
    did = "did:plc:m$n",
    handle = "user$n.bsky.social",
    displayName = "User $n",
    avatarUrl = if (withPhoto) "https://example.test/$n.jpg" else null,
)

/**
 * Every count in one column, each tile followed by a label. The tiles are
 * stacked so their left AND right edges line up in the image — that alignment
 * IS the component's contract, and a regression that let width vary with member
 * count would be visible as a ragged edge rather than needing to be measured.
 */
@Composable
private fun GroupAvatarTileFixtures() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1, 2, 3, 4, 5, 9).forEach { count ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupAvatarTile(
                    members = (1..count).map { member(it) }.toPersistentList(),
                    contentDescription = null,
                )
                Text("$count member(s)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A direct avatar beside a group tile: both must occupy the same footprint. */
@Composable
private fun TileMatchesSingleAvatarFixture() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        NubecitaAvatar(
            model = null,
            contentDescription = null,
            size = DEFAULT_GROUP_TILE_SIZE,
            fallback = avatarFallbackFor(did = "did:plc:solo", handle = "solo.bsky.social", displayName = "Solo"),
        )
        GroupAvatarTile(
            members = persistentListOf(member(1), member(2), member(3), member(4)),
            contentDescription = null,
        )
        Text("same width", style = MaterialTheme.typography.bodySmall)
    }
}

@PreviewTest
@Preview(name = "group-avatar-tile-light", showBackground = true)
@Preview(name = "group-avatar-tile-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun GroupAvatarTilePreview() {
    GroupAvatarTileFixtures()
}

@PreviewTest
@Preview(name = "group-avatar-tile-matches-avatar-light", showBackground = true)
@Preview(name = "group-avatar-tile-matches-avatar-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun GroupAvatarTileMatchesAvatarPreview() {
    TileMatchesSingleAvatarFixture()
}
