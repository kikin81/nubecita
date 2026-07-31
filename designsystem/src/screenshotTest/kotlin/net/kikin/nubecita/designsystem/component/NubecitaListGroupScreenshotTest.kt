package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaListGroup] — the M3 Expressive segmented
 * grouped list (separate rounded segments, 2dp gaps, position-aware outer
 * corners, raised `surfaceContainerHigh` fill). Captures the multi-row
 * (first/middle/last shapes) and single-row (fully rounded) cases in light +
 * dark. Regenerate after intentional visual changes with
 * `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

private data class DemoRow(
    val icon: NubecitaIconName,
    val label: String,
)

@PreviewTest
@Preview(name = "group-light", showBackground = true)
@Preview(name = "group-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaListGroupMultiRowScreenshot() {
    NubecitaListGroup(
        items =
            persistentListOf(
                DemoRow(NubecitaIconName.Notifications, "Notifications"),
                DemoRow(NubecitaIconName.ChatBubble, "Messages"),
                DemoRow(NubecitaIconName.Flag, "Moderation"),
            ),
        label = "Section",
    ) { item, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(item.label) },
            onClick = {},
            leadingContent = {
                NubecitaIcon(name = item.icon, contentDescription = null, opticalSize = 24.dp)
            },
        )
    }
}

@PreviewTest
@Preview(name = "single-light", showBackground = true)
@Preview(name = "single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaListGroupSingleRowScreenshot() {
    NubecitaListGroup(items = persistentListOf(DemoRow(NubecitaIconName.WorkspacePremium, "Only row"))) { item, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(item.label) },
            onClick = {},
            leadingContent = {
                NubecitaIcon(name = item.icon, contentDescription = null, opticalSize = 24.dp)
            },
        )
    }
}

/**
 * The single-select mode: a group of one-of-N rows with a display-only leading
 * radio. Covers both states in one fixture — the selected row and unselected
 * siblings — so a regression that stops painting selection is visible.
 */
@PreviewTest
@Preview(name = "single-select-light", showBackground = true)
@Preview(name = "single-select-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaListGroupSingleSelectScreenshot() {
    val options = persistentListOf("All", "People you follow", "None")
    NubecitaListGroup(items = options, label = "Replies", singleSelect = true) { option, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(option) },
            selected = option == "People you follow",
            onSelect = {},
            leadingContent = {
                // Display-only: the row owns the gesture and the semantics.
                RadioButton(selected = option == "People you follow", onClick = null)
            },
        )
    }
}
