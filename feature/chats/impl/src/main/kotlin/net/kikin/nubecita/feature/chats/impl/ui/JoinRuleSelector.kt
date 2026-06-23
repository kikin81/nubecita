package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Two-option segmented control for the editable [JoinRule]s (Anyone / Followed by owner).
 * [JoinRule.Unsupported] is never passed here — callers disable the control for unsupported links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JoinRuleSelector(
    selected: JoinRule,
    enabled: Boolean,
    onSelect: (JoinRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(JoinRule.Anyone, JoinRule.FollowedByOwner)
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, rule ->
            SegmentedButton(
                selected = selected == rule,
                onClick = { onSelect(rule) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(
                    text =
                        stringResource(
                            if (rule == JoinRule.Anyone) {
                                R.string.join_link_rule_anyone
                            } else {
                                R.string.join_link_rule_followed
                            },
                        ),
                )
            }
        }
    }
}
