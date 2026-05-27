package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.notifications.impl.NotificationsTestTags
import net.kikin.nubecita.feature.notifications.impl.R

/**
 * Horizontal single-select FilterChip strip rendered above the
 * notifications [androidx.compose.foundation.lazy.LazyColumn].
 *
 * The chip order follows [NotificationFilter]'s declaration order:
 * `All`, `Mentions`, `Reposts`, `Follows`, `Likes` — matching the
 * 5-chip mobile-friendly collapse defined in design.md D7. Exactly
 * one chip is selected at any time; tapping a chip fires
 * [onFilterSelect]. The host VM is responsible for `activeFilter`
 * — this composable is stateless.
 *
 * Layout: `LazyRow` with horizontal contentPadding 16.dp + vertical
 * 8.dp, chip spacing 8.dp. The list is short (5 entries) so the
 * lazy-vs-eager choice is mostly stylistic; we use `LazyRow` to keep
 * the contract symmetric with future growth and to opt into the
 * `LazyListScope` content-type model if we ever need it.
 */
@Composable
internal fun FilterChipRow(
    activeFilter: NotificationFilter,
    onFilterSelect: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag(NotificationsTestTags.FILTER_CHIP_ROW),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(NotificationFilter.entries, key = { it.name }) { filter ->
            FilterChip(
                selected = filter == activeFilter,
                onClick = { onFilterSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
                // Explicit elevation + colors keep the chip visuals stable
                // across screenshot baselines. FilterChipDefaults already
                // matches the M3 brand spec; we restate the defaults here
                // so any future M3 BOM bump that changes default tonality
                // doesn't silently invalidate the committed baselines.
                elevation = FilterChipDefaults.filterChipElevation(),
            )
        }
    }
}

@StringRes
private fun NotificationFilter.labelRes(): Int =
    when (this) {
        NotificationFilter.All -> R.string.notifications_filter_all
        NotificationFilter.Mentions -> R.string.notifications_filter_mentions
        NotificationFilter.Reposts -> R.string.notifications_filter_reposts
        NotificationFilter.Follows -> R.string.notifications_filter_follows
        NotificationFilter.Likes -> R.string.notifications_filter_likes
    }

@Preview(name = "FilterChipRow — All (light)", showBackground = true)
@Composable
private fun FilterChipRowAllPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.All, onFilterSelect = {})
    }
}

@Preview(name = "FilterChipRow — Mentions (light)", showBackground = true)
@Composable
private fun FilterChipRowMentionsPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.Mentions, onFilterSelect = {})
    }
}

@Preview(name = "FilterChipRow — Reposts (light)", showBackground = true)
@Composable
private fun FilterChipRowRepostsPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.Reposts, onFilterSelect = {})
    }
}

@Preview(name = "FilterChipRow — Follows (light)", showBackground = true)
@Composable
private fun FilterChipRowFollowsPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.Follows, onFilterSelect = {})
    }
}

@Preview(name = "FilterChipRow — Likes (light)", showBackground = true)
@Composable
private fun FilterChipRowLikesPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.Likes, onFilterSelect = {})
    }
}

@Preview(
    name = "FilterChipRow — All (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FilterChipRowAllDarkPreview() {
    NubecitaTheme {
        FilterChipRow(activeFilter = NotificationFilter.All, onFilterSelect = {})
    }
}
