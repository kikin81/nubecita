package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardQuotedPost
import kotlin.time.Instant

/**
 * Screenshot baselines for [PostCardQuotedPost] when the inner
 * embed is a video — exercises the `quotedVideoEmbedSlot` lambda
 * with the phase-B `PostCardVideoEmbed(quotedVideo = ...)` overload
 * (the screenshot harness runs in `LocalInspectionMode`, where
 * layoutlib can't construct `PlayerSurface` — the autoplay overload
 * falls through to phase B anyway). Lives in `:feature:feed:impl`
 * because the slot's body imports the phase-B `PostCardVideoEmbed`
 * which is internal to this module.
 *
 * 2 baselines (light + dark) — the with-image, with-external,
 * text-only, and view-thread variants live in `:designsystem`'s
 * `PostCardQuotedPostScreenshotTest` because they don't need
 * Media3.
 */

private val FIXED_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val FIXED_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:fixed",
        handle = "acyn.bsky.social",
        displayName = "Acyn",
        avatarUrl = null,
    )

private val FIXED_VIDEO: QuotedEmbedUi.Video =
    QuotedEmbedUi.Video(
        posterUrl = "https://example.com/preview-poster.jpg",
        playlistUrl = "https://video.bsky.app/quoted/playlist.m3u8",
        aspectRatio = 16f / 9f,
        durationSeconds = null,
        altText = null,
    )

private val FIXED_QUOTED: QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:fixed/app.bsky.feed.post/q",
        cid = "bafyreifixedquotedcid000000000000000000000000000",
        author = FIXED_AUTHOR,
        createdAt = FIXED_CREATED_AT,
        text = "Quoted post with an inline video — the coordinator binds the player to the quoted URI.",
        facets = persistentListOf(),
        embed = FIXED_VIDEO,
    )

@PreviewTest
@Preview(name = "with-video-light", showBackground = true)
@Preview(name = "with-video-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostWithVideoScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(
            quotedPost = FIXED_QUOTED,
            quotedVideoEmbedSlot = { qVideo -> PostCardVideoEmbed(quotedVideo = qVideo) },
        )
    }
}
