package net.kikin.nubecita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * The notification small icon at the size the status bar actually draws it.
 *
 * Android reduces a small icon to its ALPHA channel and tints it, so this
 * renders white-on-dark at 24dp — what the system shows, not what the asset
 * looks like in a file browser. The launcher foreground is drawn beside it
 * because the difference is the whole point: it carries an adaptive-icon safe
 * margin (artwork at 0.7 scale in a 108dp canvas, ~54% coverage), which is why
 * it appeared shrunken against apps shipping a real notification asset.
 */
@Composable
private fun NotificationIconComparison() {
    // The dark box and white tint are hard-coded rather than taken from the
    // theme on purpose: a small icon is drawn by SystemUI on the status bar,
    // not on an app surface, so a themed background would show something the
    // user never sees. The wrapper is here for consistency with the module's
    // other fixtures and for stable baseline sizing.
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(Color(0xFF1B1B1F)).padding(20.dp),
    ) {
        listOf(
            "before\nic_launcher_foreground" to R.drawable.ic_launcher_foreground,
            "after\nic_stat_nubecita" to R.drawable.ic_stat_nubecita,
        ).forEach { (label, res) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 24dp is the status-bar size; 48dp shows the shape detail.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(res), null, Modifier.size(24.dp), tint = Color.White)
                    Icon(painterResource(res), null, Modifier.size(48.dp), tint = Color.White)
                }
                Text(label, color = Color.White, fontSize = 9.sp)
            }
        }
    }
}

@PreviewTest
@Preview(name = "notification-icon", widthDp = 320, heightDp = 150)
@Composable
private fun NotificationIconPreview() {
    NubecitaCanvasPreviewTheme {
        NotificationIconComparison()
    }
}
