package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [PostCard]'s state matrix — six visual
 * branches × light/dark. Action row's PostStat is exercised transitively
 * (no standalone PostStat test). Timestamps are pinned to a constant
 * `createdAt` so the relative-time text is deterministic across runs.
 */

private fun screenshotAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakedid000000000000000",
        handle = "alice.bsky.social",
        displayName = "Alice Chen",
        avatarUrl = null,
    )

private fun screenshotPost(
    text: String = "The thing about building a Bluesky client in 2026 is you realize how much of the web we gave up trying to fix.",
    facets: ImmutableList<Facet> = persistentListOf(),
    embed: EmbedUi = EmbedUi.Empty,
    stats: PostStatsUi = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
    viewer: ViewerStateUi = ViewerStateUi(isLikedByViewer = true),
    repostedBy: String? = null,
): PostUi =
    PostUi(
        id = "screenshot",
        author = screenshotAuthor(),
        // Pinned to a fixed Instant deep enough in the past that
        // rememberRelativeTimeText resolves to the absolute-date bucket
        // (> 7 days). The rendered string is "Oct 15, 2025" regardless of
        // when the test runs — fully deterministic across CI re-runs and
        // local generations. Earlier draft used `Clock.System.now() - 3.minutes`
        // which would render "3m" today but could drift to "4m" / "Apr 25" /
        // etc. depending on minute-boundary or hours-elapsed-since-baseline.
        createdAt = SCREENSHOT_CREATED_AT,
        text = text,
        facets = facets,
        embed = embed,
        stats = stats,
        viewer = viewer,
        repostedBy = repostedBy,
    )

private val SCREENSHOT_CREATED_AT: Instant = Instant.parse("2025-10-15T12:00:00Z")

@PreviewTest
@Preview(name = "empty-body-light", showBackground = true)
@Preview(name = "empty-body-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardEmptyBodyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    text = "",
                    stats = PostStatsUi(),
                    viewer = ViewerStateUi(),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "typical-light", showBackground = true)
@Preview(name = "typical-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardTypicalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(post = screenshotPost())
    }
}

@PreviewTest
@Preview(name = "with-image-light", showBackground = true)
@Preview(name = "with-image-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardWithImageScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    embed =
                        EmbedUi.Images(
                            items =
                                persistentListOf(
                                    ImageUi(
                                        url = "https://example.com/preview.jpg",
                                        altText = "Preview image",
                                        aspectRatio = 1.5f,
                                    ),
                                ),
                        ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "unsupported-embed-light", showBackground = true)
@Preview(name = "unsupported-embed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardUnsupportedEmbedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post = screenshotPost(embed = EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")),
        )
    }
}

@PreviewTest
@Preview(name = "reposted-by-light", showBackground = true)
@Preview(name = "reposted-by-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRepostedByScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(post = screenshotPost(repostedBy = "Alice Chen"))
    }
}

@PreviewTest
@Preview(name = "long-handle-short-name-light", showBackground = true)
@Preview(name = "long-handle-short-name-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardLongHandleShortNameScreenshot() {
    // Locks the AuthorLine truncation contract: a 30+ char handle MUST shrink
    // with ellipsis on a short display name, while the timestamp stays
    // right-pinned. Pre-fix, the timestamp wrapped to a second visual line.
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost().copy(
                    author =
                        AuthorUi(
                            did = "did:plc:fakedid000000000000000",
                            handle = "someverylonghandle.bsky.social",
                            displayName = "Bob",
                            avatarUrl = null,
                        ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "long-name-and-handle-light", showBackground = true)
@Preview(name = "long-name-and-handle-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardLongNameAndHandleScreenshot() {
    // Both displayName and handle are long. Handle shrinks first (weighted),
    // displayName takes its intrinsic width up to remaining space, timestamp
    // stays right-pinned by the absorbing Spacer.
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost().copy(
                    author =
                        AuthorUi(
                            did = "did:plc:fakedid000000000000000",
                            handle = "someverylonghandle.bsky.social",
                            displayName = "Alexandra Christopherson-Williamson",
                            avatarUrl = null,
                        ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "with-facets-light", showBackground = true)
@Preview(name = "with-facets-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardWithFacetsScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        val text = "Hello @alice.bsky.social, check out https://nubecita.app"
        val mention =
            Facet(
                features = listOf(FacetMention(did = Did("did:plc:fakedid000000000000000"))),
                index = FacetByteSlice(byteStart = 6, byteEnd = 24),
            )
        val link =
            Facet(
                features = listOf(FacetLink(uri = Uri("https://nubecita.app"))),
                index = FacetByteSlice(byteStart = 36, byteEnd = 56),
            )
        PostCard(
            post = screenshotPost(text = text, facets = persistentListOf(mention, link)),
        )
    }
}
