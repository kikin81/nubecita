package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [FilterChipRow] — one per chip-active variant
 * × light/dark. The chip strip is short and its layout is stable, so the
 * preview matrix below is deliberately narrow.
 */

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "filter-chip-row-all-light", showBackground = true)
@Preview(name = "filter-chip-row-all-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FilterChipRowAllScreenshot() {
    FilterChipRow(activeFilter = NotificationFilter.All, onFilterSelect = {})
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "filter-chip-row-mentions-light", showBackground = true)
@Composable
private fun FilterChipRowMentionsScreenshot() {
    FilterChipRow(activeFilter = NotificationFilter.Mentions, onFilterSelect = {})
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "filter-chip-row-reposts-light", showBackground = true)
@Composable
private fun FilterChipRowRepostsScreenshot() {
    FilterChipRow(activeFilter = NotificationFilter.Reposts, onFilterSelect = {})
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "filter-chip-row-follows-light", showBackground = true)
@Composable
private fun FilterChipRowFollowsScreenshot() {
    FilterChipRow(activeFilter = NotificationFilter.Follows, onFilterSelect = {})
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "filter-chip-row-likes-light", showBackground = true)
@Preview(name = "filter-chip-row-likes-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FilterChipRowLikesScreenshot() {
    FilterChipRow(activeFilter = NotificationFilter.Likes, onFilterSelect = {})
}
