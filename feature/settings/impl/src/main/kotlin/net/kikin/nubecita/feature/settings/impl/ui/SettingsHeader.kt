package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.feature.settings.impl.R

/**
 * Identity header rendered at the top of the Settings home screen.
 * Mirrors the Google Play settings sheet:
 *
 * - Handle (e.g. `kikin.bsky.social`) as the topmost text in the
 *   centered column.
 * - Large circular avatar (88dp) rendered via [NubecitaAvatar] —
 *   loads the async-image when [avatarUrl] is non-null, otherwise
 *   shows the deterministic initials-disc fallback derived from
 *   [did]+[handle] (hue) and [displayName]/[handle] (initial). An
 *   earlier draft overlaid a camera-icon edit badge here; it was
 *   dropped when the badge had no tap behavior in v1 and rendering it
 *   inside an otherwise-empty avatar made the header read as
 *   unfinished. Restore the overlay inside whichever follow-on task
 *   wires avatar upload.
 * - Display-name greeting ("Hi, $name!" when a display name is
 *   present, "Hi!" when absent or blank).
 * - "Manage your Bluesky account" pill-shaped [OutlinedButton] —
 *   tapping invokes [onManageAccountClick], which the screen
 *   translates into a `LaunchUri("https://bsky.app/settings")`
 *   effect that opens a Chrome Custom Tab.
 *
 * Stateless: callers pass the projected profile values from
 * `SettingsViewState`.
 */
@Composable
internal fun SettingsHeader(
    handle: String,
    did: String,
    displayName: String?,
    avatarUrl: String?,
    onManageAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting =
        if (displayName.isNullOrBlank()) {
            stringResource(R.string.settings_header_greeting_no_name)
        } else {
            stringResource(R.string.settings_header_greeting_with_name, displayName)
        }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = handle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        NubecitaAvatar(
            model = avatarUrl,
            contentDescription = stringResource(R.string.settings_header_avatar_content_description),
            size = 88.dp,
            fallback = avatarFallbackFor(did = did, handle = handle, displayName = displayName),
            initialTextStyle = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedButton(
            onClick = onManageAccountClick,
            shape = CircleShape,
        ) {
            Text(
                text = stringResource(R.string.settings_header_manage_account_cta),
            )
        }
    }
}
