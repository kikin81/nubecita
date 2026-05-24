package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardRecordWithMediaEmbed
import kotlin.time.Instant

/**
 * Screenshot baselines for [PostCardRecordWithMediaEmbed] when the
 * media slot is a video — exercises the `videoEmbedSlot` lambda
 * with the phase-B `PostCardVideoEmbed(video = ...)` overload. The
 * screenshot harness runs in `LocalInspectionMode`, where layoutlib
 * can't construct `PlayerSurface` — the autoplay overload falls
 * through to phase B anyway. Lives in `:feature:feed:impl`
 * because the slot's body imports the phase-B `PostCardVideoEmbed`
 * which is internal to this module.
 *
 * 2 baselines (light + dark) — the non-video shapes (resolved /
 * unavailable × images / external) live in `:designsystem`'s
 * `PostCardRecordWithMediaEmbedScreenshotTest`.
 */

private val FIXED_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val FIXED_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:fixed",
        handle = "acyn.bsky.social",
        displayName = "Acyn",
        avatarUrl = null,
    )

private val FIXED_QUOTED: QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:fixed/app.bsky.feed.post/q",
        cid = "bafyreifixedquotedcid000000000000000000000000000",
        author = FIXED_AUTHOR,
        createdAt = FIXED_CREATED_AT,
        text =
            "Quoted post inside a recordWithMedia — the parent's video " +
                "renders above this card.",
        facets = persistentListOf(),
        embed = QuotedEmbedUi.Empty,
    )

private val FIXED_VIDEO_MEDIA: EmbedUi.Video =
    EmbedUi.Video(
        posterUrl = "https://example.com/preview-poster.jpg",
        playlistUrl = "https://video.bsky.app/parent/playlist.m3u8",
        aspectRatio = 16f / 9f,
        durationSeconds = null,
        altText = null,
    )

@PreviewTest
@Preview(name = "with-video-light", showBackground = true)
@Preview(name = "with-video-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordWithMediaWithVideoScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordWithMediaEmbed(
            record = EmbedUi.Record(quotedPost = FIXED_QUOTED),
            media = FIXED_VIDEO_MEDIA,
            videoEmbedSlot = { video -> PostCardVideoEmbed(video = video) },
        )
    }
}
