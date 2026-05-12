package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.designsystem.component.PostCardShimmer

/**
 * Tab-level shimmer skeleton. Renders 5 PostCardShimmers stacked
 * vertically — matches the Feed module's initial-loading visual.
 * Alternates the `showImagePlaceholder` flag to mimic Feed's
 * shimmer fixture.
 */
@Composable
internal fun ProfileLoadingState(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        repeat(SHIMMER_COUNT) { index ->
            PostCardShimmer(showImagePlaceholder = index % 2 == 0)
        }
    }
}

private const val SHIMMER_COUNT = 5
