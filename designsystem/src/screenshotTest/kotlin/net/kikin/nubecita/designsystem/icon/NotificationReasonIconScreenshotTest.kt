package net.kikin.nubecita.designsystem.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Visual baselines for every [NotificationReason] mapped by
 * [NotificationReasonIcon]. Iterates the enum's `entries` so any new
 * reason added in `:data:models` lands here without a manual edit —
 * the consumer site (the exhaustive `when` in `NotificationReasonIcon`)
 * is the one that surfaces the missing branch at compile time.
 *
 * Eyeball the generated baseline once: the like row should be magenta-
 * pink, the repost row should be green, follow/verified should match
 * the brand's `primary` blue, and the unknown row should mirror the
 * outlined bell tint.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun NotificationReasonIconShowcasePreviews() {
    NubecitaTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationReason.entries.forEach { reason ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NotificationReasonIcon(reason = reason)
                    Text(text = reason.name)
                }
            }
        }
    }
}
