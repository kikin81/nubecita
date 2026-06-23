package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * The existing-link body. A disabled link is de-emphasized (alpha 0.6, onSurfaceVariant URL,
 * no copy/share); an [JoinRule.Unsupported] link shows the update banner and locks every control.
 * A merely *disabled* link keeps its Enable toggle interactive so re-enabling is always reachable;
 * an *Unsupported* link (a rule a newer client created) locks every control, including Enable/Disable.
 */
@Composable
internal fun JoinLinkCard(
    link: JoinLinkUi,
    mutationInFlight: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onJoinRuleChange: (JoinRule) -> Unit,
    onRequireApprovalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unsupported = link.joinRule == JoinRule.Unsupported
    val controlsEnabled = !mutationInFlight && !unsupported
    val contentAlpha = if (link.enabled) 1f else 0.6f

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (unsupported) {
            UnsupportedBanner()
        }
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).alpha(contentAlpha),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (link.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCopy, enabled = link.enabled) {
                        Text(stringResource(R.string.join_link_copy))
                    }
                    FilledTonalButton(onClick = onShare, enabled = link.enabled) {
                        NubecitaIcon(name = NubecitaIconName.IosShare, contentDescription = null)
                        Text(
                            stringResource(R.string.join_link_share),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }

        // Enable/Disable — stays interactive even when the link is disabled (but not when unsupported).
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.join_link_enabled_toggle),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = link.enabled,
                onCheckedChange = { onEnabledChange(it) },
                enabled = !mutationInFlight && !unsupported,
            )
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.join_link_who_can_join),
            style = MaterialTheme.typography.titleSmall,
        )
        JoinRuleSelector(
            selected = if (unsupported) JoinRule.Anyone else link.joinRule,
            enabled = controlsEnabled,
            onSelect = onJoinRuleChange,
        )
        RequireApprovalRow(
            checked = link.requireApproval,
            enabled = controlsEnabled,
            onCheckedChange = onRequireApprovalChange,
        )
    }
}

@Composable
private fun UnsupportedBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NubecitaIcon(
                name = NubecitaIconName.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.join_link_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}
