package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Up to three optional meta rows rendered as `NubecitaIcon` (14 dp) +
 * Text pairs. Rows whose data is `null` are not rendered (no
 * placeholder, no spacing). If all three are null the Column renders
 * nothing (zero height) — the caller never sees a wasted 0 dp slot.
 *
 * Icon glyphs: the design calls for Link / Location / Calendar but the
 * current `NubecitaIconName` catalog does not vendor those exact
 * entries. The closest available glyphs are substituted —
 * [NubecitaIconName.Public] for the website affordance,
 * [NubecitaIconName.Language] for location (globe semantics), and
 * [NubecitaIconName.Article] for the joined-date entry. Swap these for
 * `Link` / `Place` / `CalendarToday` once those entries are added to
 * the catalog and the subset font is regenerated via
 * `scripts/update_material_symbols.sh`. The meta row's intent is
 * communicated by the icon's affordance plus the row's content
 * description, not the specific glyph name.
 */
@Composable
internal fun ProfileMetaRow(
    website: String?,
    location: String?,
    joinedDisplay: String?,
    modifier: Modifier = Modifier,
) {
    if (website == null && location == null && joinedDisplay == null) return
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (website != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Public,
                contentDescription = stringResource(R.string.profile_meta_link_content_description),
                text = website,
            )
        }
        if (location != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Language,
                contentDescription = stringResource(R.string.profile_meta_location_content_description),
                text = location,
            )
        }
        if (joinedDisplay != null) {
            MetaRowEntry(
                iconName = NubecitaIconName.Article,
                contentDescription = stringResource(R.string.profile_meta_joined_content_description),
                text = joinedDisplay,
            )
        }
    }
}

@Composable
private fun MetaRowEntry(
    iconName: NubecitaIconName,
    contentDescription: String,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NubecitaIcon(
            name = iconName,
            contentDescription = contentDescription,
            opticalSize = 14.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
