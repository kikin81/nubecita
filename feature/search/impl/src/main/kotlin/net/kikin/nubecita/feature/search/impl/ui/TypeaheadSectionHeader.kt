package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Compact section divider label used by the typeahead screen for
 * the "Top match" and "People" group headers. Just a styled [Text];
 * extracted so the screen body's `when (status)` branches stay
 * legible and so previews / screenshot tests can lock the heading
 * baseline independently.
 */
@Composable
internal fun TypeaheadSectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Preview(name = "TypeaheadSectionHeader", showBackground = true)
@Composable
private fun TypeaheadSectionHeaderPreview() {
    NubecitaTheme {
        TypeaheadSectionHeader(label = "Top match")
    }
}
