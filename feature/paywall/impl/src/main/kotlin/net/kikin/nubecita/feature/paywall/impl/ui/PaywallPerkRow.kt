package net.kikin.nubecita.feature.paywall.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing

/**
 * One perk in the paywall's "What you get" list: a tinted circular icon
 * disc beside a title + supporting body. The [icon] is decorative
 * (`contentDescription = null`) — the [title]/[body] text carries the
 * meaning for screen readers, so announcing the glyph too would be
 * redundant noise.
 */
@Composable
internal fun PaywallPerkRow(
    icon: NubecitaIconName,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(MaterialTheme.spacing.s10),
        ) {
            Box(contentAlignment = Alignment.Center) {
                NubecitaIcon(
                    name = icon,
                    contentDescription = null,
                    filled = true,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s1)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
