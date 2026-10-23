package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaLinkChip]: the standard (outlined) and
 * emphasized (success-filled) variants, each with and without a leading
 * icon, plus a long label that must ellipsize instead of growing the pill.
 */
@PreviewTest
@Preview(name = "link-chip-light", showBackground = true)
@Preview(name = "link-chip-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaLinkChipScreenshot() {
    Column(
        modifier = Modifier.width(320.dp).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NubecitaLinkChip(label = "example.com", onClick = {})
            NubecitaLinkChip(label = "Portfolio", onClick = {}, icon = { GlobeIcon() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NubecitaLinkChip(label = "Support", onClick = {}, emphasized = true)
            NubecitaLinkChip(label = "Support our work!", onClick = {}, emphasized = true, icon = { GlobeIcon() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NubecitaLinkChip(label = "+3", onClick = {})
            NubecitaLinkChip(
                label = "A really long link title that should ellipsize",
                onClick = {},
                icon = { GlobeIcon() },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun GlobeIcon() {
    NubecitaLinkFavicon(icon = null)
}
