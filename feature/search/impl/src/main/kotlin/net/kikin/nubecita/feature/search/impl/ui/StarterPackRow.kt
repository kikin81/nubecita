package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.feature.search.impl.R

/**
 * Single starter-pack row for the Packs tab and the Summary: the pack's name, "by @creator", the
 * member count when the server gave one, and up to four member avatars at the end when the
 * response carried a sample. The whole row is one click target that opens the pack, and its
 * accessible name is the pack's name, creator and member count (see [packContentDescription]).
 *
 * Stateless: plain data and a lambda, so the wide-window summary pane reuses it unchanged.
 */
@Composable
internal fun StarterPackRow(
    pack: StarterPackUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = packContentDescription(pack)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (pack.avatarUrls.isNotEmpty()) {
            PackAvatarStack(avatarUrls = pack.avatarUrls, avatarSize = 32.dp)
        }
    }
}

/**
 * The merged accessible name of a pack row or card: its name, "by @creator", and the member
 * count when the server gave one (the visible text shows it, so a screen reader must too).
 */
@Composable
internal fun packContentDescription(pack: StarterPackUi): String {
    val count = pack.memberCount
    return if (count == null) {
        stringResource(R.string.search_pack_content_desc, pack.name, pack.creatorHandle)
    } else {
        stringResource(
            R.string.search_pack_content_desc_with_members,
            pack.name,
            pack.creatorHandle,
            pluralStringResource(R.plurals.search_pack_member_count, count, count),
        )
    }
}

/**
 * Overlapping member avatars, the first on top, each ringed in [ringColor] (the colour of what
 * they sit on, `surface` by default) so the circles stay distinct. Decorative: the pack's own
 * name carries the accessible description.
 */
@Composable
internal fun PackAvatarStack(
    avatarUrls: ImmutableList<String>,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.surface,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(-avatarSize * 0.3f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatarUrls.forEachIndexed { index, url ->
            NubecitaAvatar(
                model = url,
                contentDescription = null,
                size = avatarSize,
                modifier =
                    Modifier
                        .zIndex((avatarUrls.size - index).toFloat())
                        .border(width = 1.5.dp, color = ringColor, shape = CircleShape),
            )
        }
    }
}

@Preview(name = "StarterPackRow — with avatars", showBackground = true)
@Composable
private fun StarterPackRowAvatarsPreview() {
    NubecitaTheme {
        StarterPackRow(pack = StarterPackUiFixtures.packWithAvatars(), onClick = {})
    }
}

@Preview(name = "StarterPackRow — no avatars, no count, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackRowBarePreview() {
    NubecitaTheme {
        StarterPackRow(pack = StarterPackUiFixtures.pack(memberCount = null), onClick = {})
    }
}
