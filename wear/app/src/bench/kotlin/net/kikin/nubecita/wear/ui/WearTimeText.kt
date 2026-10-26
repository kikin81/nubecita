package net.kikin.nubecita.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText

/** A fixed time, so every Play Store screenshot shows the same clock. */
@Composable
internal fun WearTimeText() {
    TimeText(timeSource = FixedTimeSource)
}

private object FixedTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}
