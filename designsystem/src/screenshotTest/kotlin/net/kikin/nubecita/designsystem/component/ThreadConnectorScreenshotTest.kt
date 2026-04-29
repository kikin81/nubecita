package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [Modifier.threadConnector] across the three
 * meaningful flag combinations (above-only, below-only, both) in light
 * and dark themes — six baselines total. Each baseline renders a
 * single placeholder "post" (just an avatar circle + a body text line)
 * so the line geometry is the only thing that varies between cases.
 *
 * The "neither flag" (no-op) case is exercised by the ktdoc on the
 * modifier itself; no baseline since the rendered output is identical
 * to a post without the modifier applied.
 */

private const val WIDTH_DP: Int = 360
private const val HEIGHT_DP: Int = 96

@Composable
private fun ConnectorPreviewSurface(
    connectAbove: Boolean,
    connectBelow: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier =
                Modifier
                    .width(WIDTH_DP.dp)
                    .height(HEIGHT_DP.dp)
                    .threadConnector(
                        connectAbove = connectAbove,
                        connectBelow = connectBelow,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    ),
        ) {
            // Avatar placeholder at the gutter, sized + positioned to match
            // the modifier's default geometry (20.dp horizontal padding +
            // 12.dp top padding + 44.dp avatar).
            Box(
                modifier =
                    Modifier
                        .padding(start = 20.dp, top = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            Text(
                text = "post body",
                modifier = Modifier.padding(start = 76.dp, top = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PreviewTest
@Preview(name = "above-light", widthDp = WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun ThreadConnectorAboveLight() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = true, connectBelow = false)
    }
}

@PreviewTest
@Preview(name = "above-dark", widthDp = WIDTH_DP, heightDp = HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadConnectorAboveDark() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = true, connectBelow = false)
    }
}

@PreviewTest
@Preview(name = "below-light", widthDp = WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun ThreadConnectorBelowLight() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = false, connectBelow = true)
    }
}

@PreviewTest
@Preview(name = "below-dark", widthDp = WIDTH_DP, heightDp = HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadConnectorBelowDark() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = false, connectBelow = true)
    }
}

@PreviewTest
@Preview(name = "both-light", widthDp = WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun ThreadConnectorBothLight() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = true, connectBelow = true)
    }
}

@PreviewTest
@Preview(name = "both-dark", widthDp = WIDTH_DP, heightDp = HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadConnectorBothDark() {
    NubecitaTheme(dynamicColor = false) {
        ConnectorPreviewSurface(connectAbove = true, connectBelow = true)
    }
}
