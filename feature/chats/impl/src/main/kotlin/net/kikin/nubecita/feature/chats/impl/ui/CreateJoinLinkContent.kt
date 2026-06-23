package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Create-state body: a large tonal glyph over title/explainer, the two settings controls (seeded
 * with permissive-but-vetted defaults Anyone + requireApproval = true), and a Create button that
 * shows an in-button spinner while [creating].
 */
@Composable
internal fun CreateJoinLinkContent(
    creating: Boolean,
    onCreate: (JoinRule, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var joinRule by rememberSaveable { mutableStateOf(JoinRule.Anyone) }
    var requireApproval by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NubecitaIcon(
            name = NubecitaIconName.PersonAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(20.dp)
                    .size(40.dp),
        )
        Text(
            text = stringResource(R.string.join_link_create_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.join_link_create_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        JoinRuleSelector(selected = joinRule, enabled = !creating, onSelect = { joinRule = it })
        RequireApprovalRow(
            checked = requireApproval,
            enabled = !creating,
            onCheckedChange = { requireApproval = it },
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = { onCreate(joinRule, requireApproval) },
            enabled = !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (creating) {
                // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_link_create_button))
            }
        }
    }
}

/** Shared label + Switch row for `requireApproval`, with the helper text. */
@Composable
internal fun RequireApprovalRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.join_link_require_approval),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(
            text = stringResource(R.string.join_link_require_approval_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.wrapContentSize(Alignment.CenterStart),
        )
    }
}
