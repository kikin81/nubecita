package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Screenshot baselines for [profileMediaTabBody]. Renders the LazyListScope
 * extension inside a host LazyColumn so the layout reflects the production
 * call site (the extension only makes sense inside a parent LazyColumn).
 *
 * Per-fixture coverage: media-loaded (3×N grid + a short last row),
 * media-empty (Loaded(items=[])), media-error (InitialError(Network)).
 * Light + dark per fixture = 6 baselines.
 *
 * Coil is no-op in Layoutlib so MediaCellThumb baselines render the
 * NubecitaAsyncImage placeholder ColorPainter; visual delta with real
 * thumbs is verified on-device.
 */
private fun sampleCells(count: Int): List<TabItemUi> =
    (0 until count).map { idx ->
        TabItemUi.MediaCell(
            postUri = "at://did:plc:alice/post/media-$idx",
            thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$idx@jpeg",
        )
    }

@Composable
private fun MediaBodyHost(status: TabLoadStatus) {
    NubecitaTheme(dynamicColor = false) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            profileMediaTabBody(
                status = status,
                onMediaTap = {},
                onRetry = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "media-loaded-light", showBackground = true, heightDp = 800)
@Preview(name = "media-loaded-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyLoadedScreenshot() {
    // 7 cells → 2 full rows of 3 + 1 short row of 1 (exercises the
    // Spacer-padding code path for incomplete last rows).
    MediaBodyHost(
        status =
            TabLoadStatus.Loaded(
                items = sampleCells(count = 7).toImmutableList(),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
    )
}

@PreviewTest
@Preview(name = "media-empty-light", showBackground = true, heightDp = 400)
@Preview(name = "media-empty-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyEmptyScreenshot() {
    MediaBodyHost(
        status =
            TabLoadStatus.Loaded(
                items = persistentListOf(),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
    )
}

@PreviewTest
@Preview(name = "media-error-light", showBackground = true, heightDp = 400)
@Preview(name = "media-error-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyErrorScreenshot() {
    MediaBodyHost(status = TabLoadStatus.InitialError(ProfileError.Network))
}
