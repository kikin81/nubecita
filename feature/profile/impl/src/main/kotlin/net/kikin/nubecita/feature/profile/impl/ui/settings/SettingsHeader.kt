package net.kikin.nubecita.feature.profile.impl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Identity header rendered at the top of the Settings home screen.
 * Mirrors the Google Play settings sheet:
 *
 * - Handle (e.g. `kikin.bsky.social`) as the topmost text in the
 *   centered column.
 * - Large circular avatar (80dp) with a small camera-icon badge in
 *   the bottom-trailing slot. The badge is a non-interactive overlay
 *   in v1; tappable avatar editing is deferred to a follow-on task.
 * - Display-name greeting ("Hi, $name!" when a display name is
 *   present, "Hi!" when absent or blank).
 * - "Manage your Bluesky account" pill-shaped [OutlinedButton] —
 *   tapping invokes [onManageAccountClick], which the screen
 *   translates into a `LaunchUri("https://bsky.app/settings")`
 *   effect.
 *
 * Stateless: callers pass the projected profile values. Header data
 * is hoisted into `SettingsViewModel` and flows down via
 * `SettingsViewState` (wiring tracked under task 2.8).
 */
@Composable
internal fun SettingsHeader(
    handle: String,
    displayName: String?,
    avatarUrl: String?,
    onManageAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greeting =
        if (displayName.isNullOrBlank()) {
            stringResource(R.string.profile_settings_header_greeting_no_name)
        } else {
            stringResource(R.string.profile_settings_header_greeting_with_name, displayName)
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
        SettingsAvatar(
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
            modifier = Modifier.size(88.dp),
            contentDescription =
                stringResource(R.string.profile_settings_header_avatar_content_description),
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
                text = stringResource(R.string.profile_settings_header_manage_account_cta),
            )
        }
    }
}
