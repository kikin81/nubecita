package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme

@PreviewTest
@Preview(name = "day-sep-today-light", showBackground = true)
@Preview(name = "day-sep-today-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Today() {
    NubecitaScreenPreviewTheme {
        Surface { DaySeparatorChip("Today") }
    }
}

@PreviewTest
@Preview(name = "day-sep-yesterday-light", showBackground = true)
@Preview(name = "day-sep-yesterday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Yesterday() {
    NubecitaScreenPreviewTheme {
        Surface { DaySeparatorChip("Yesterday") }
    }
}

@PreviewTest
@Preview(name = "day-sep-weekday-light", showBackground = true)
@Preview(name = "day-sep-weekday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Weekday() {
    NubecitaScreenPreviewTheme {
        Surface { DaySeparatorChip("Mon") }
    }
}

@PreviewTest
@Preview(name = "day-sep-monthday-light", showBackground = true)
@Preview(name = "day-sep-monthday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthDay() {
    NubecitaScreenPreviewTheme {
        Surface { DaySeparatorChip("Apr 25") }
    }
}
