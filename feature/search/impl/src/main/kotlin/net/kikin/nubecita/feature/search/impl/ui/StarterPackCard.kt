package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

private val AVATAR_ROW_HEIGHT = 32.dp

/**
 * Starter-pack card for Explore (the two-column grid on wide windows, the carousel on phones):
 * up to four member avatars, then the pack's name, "by @creator" and the member count when the
 * server gave one. The avatar row keeps its height with no avatars so cards in one row line up.
 *
 * The whole card is one click target that opens the pack; its accessible name is the pack's name
 * and creator. Width is the caller's: the grid fills its cell, the carousel passes a fixed width.
 * Surface token: [MaterialTheme.colorScheme.surfaceContainer] (item card).
 */
@Composable
internal fun StarterPackCard(
    pack: StarterPackUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = packContentDescription(pack)
    ElevatedCard(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.height(AVATAR_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
                if (pack.avatarUrls.isNotEmpty()) {
                    PackAvatarStack(
                        avatarUrls = pack.avatarUrls,
                        avatarSize = AVATAR_ROW_HEIGHT,
                        // The card is a surfaceContainer item card; ring in its own colour.
                        ringColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.search_pack_byline, pack.creatorHandle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                pack.memberCount?.let { count ->
                    Text(
                        text = pluralStringResource(R.plurals.search_pack_member_count, count, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "StarterPackCard — avatars + count", showBackground = true, widthDp = 240)
@Composable
private fun StarterPackCardFullPreview() {
    NubecitaTheme {
        StarterPackCard(pack = StarterPackUiFixtures.packWithAvatars(), onClick = {})
    }
}

@Preview(name = "StarterPackCard — bare, dark", showBackground = true, widthDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackCardBarePreview() {
    NubecitaTheme {
        StarterPackCard(pack = StarterPackUiFixtures.pack(memberCount = null), onClick = {})
    }
}
