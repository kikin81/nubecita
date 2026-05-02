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
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
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

// Multi-image carousel coverage. Two fixtures lock the carousel branch
// behavior post-m28.5.2:
//
// - `with-3-images`: uniform 1.5 aspect ratio across 3 slides — exercises
//   the carousel keyline math against equally-sized items so the
//   per-slide width comes purely from the M3 default `preferredItemWidth`
//   token. Single-image fixtures (`with-image-*`) MUST stay byte-for-byte
//   unchanged through this addition; the conditional `images.size > 1`
//   branch is the only path that touched.
// - `with-3-images-mixed-aspect`: one portrait (0.5) + two landscape (1.78)
//   slides. Locks the carousel's behavior under non-uniform per-slide
//   sizing — the spec's design.md Risks section flags this as the place
//   uneven slide heights surface. Don't try to size to tallest;
//   variance is the contract.

@PreviewTest
@Preview(name = "with-3-images-light", showBackground = true)
@Preview(name = "with-3-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardWith3ImagesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    embed =
                        EmbedUi.Images(
                            items =
                                persistentListOf(
                                    ImageUi(
                                        url = "https://example.com/preview-0.jpg",
                                        altText = "Preview image 0",
                                        aspectRatio = 1.5f,
                                    ),
                                    ImageUi(
                                        url = "https://example.com/preview-1.jpg",
                                        altText = "Preview image 1",
                                        aspectRatio = 1.5f,
                                    ),
                                    ImageUi(
                                        url = "https://example.com/preview-2.jpg",
                                        altText = "Preview image 2",
                                        aspectRatio = 1.5f,
                                    ),
                                ),
                        ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "with-3-images-mixed-aspect-light", showBackground = true)
@Preview(name = "with-3-images-mixed-aspect-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardWith3ImagesMixedAspectScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    embed =
                        EmbedUi.Images(
                            items =
                                persistentListOf(
                                    ImageUi(
                                        url = "https://example.com/preview-portrait.jpg",
                                        altText = "Portrait image",
                                        aspectRatio = 0.5f,
                                    ),
                                    ImageUi(
                                        url = "https://example.com/preview-landscape-0.jpg",
                                        altText = "Landscape image 0",
                                        aspectRatio = 16f / 9f,
                                    ),
                                    ImageUi(
                                        url = "https://example.com/preview-landscape-1.jpg",
                                        altText = "Landscape image 1",
                                        aspectRatio = 16f / 9f,
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
@Preview(name = "thread-cluster-parent-light", showBackground = true)
@Preview(name = "thread-cluster-parent-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardThreadClusterParentScreenshot() {
    // Locks the connector-bearing visual: connectAbove + connectBelow both
    // true matches the "parent" position inside a ReplyCluster — line draws
    // through the avatar gutter from y=0 to avatarTop, and from avatarBottom
    // to size.height. Geometry overrides (gutterX=40, avatarTop=14,
    // avatarBottom=54) compensate for PostCard's actual 40dp avatar with
    // 14dp vertical padding (the threadConnector defaults assume 44dp/12dp).
    NubecitaTheme(dynamicColor = false) {
        PostCard(post = screenshotPost(), connectAbove = true, connectBelow = true)
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

// Like × repost permutation matrix for the action row. Pinned counts
// surface every cell of the 2 × 2 matrix; the corresponding `viewer`
// flags drive PostStat's icon swap (filled vs outlined) and active-color
// tint. nubecita-8f6.2 acceptance: previews for liked/unliked × reposted/
// not-reposted permutations + screenshot baselines for the action row.

@PreviewTest
@Preview(name = "viewer-neutral-light", showBackground = true)
@Preview(name = "viewer-neutral-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardViewerNeutralScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
                    viewer = ViewerStateUi(isLikedByViewer = false, isRepostedByViewer = false),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "viewer-liked-light", showBackground = true)
@Preview(name = "viewer-liked-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardViewerLikedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 87),
                    viewer = ViewerStateUi(isLikedByViewer = true, isRepostedByViewer = false),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "viewer-reposted-light", showBackground = true)
@Preview(name = "viewer-reposted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardViewerRepostedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 5, likeCount = 86),
                    viewer = ViewerStateUi(isLikedByViewer = false, isRepostedByViewer = true),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "viewer-liked-and-reposted-light", showBackground = true)
@Preview(name = "viewer-liked-and-reposted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardViewerLikedAndRepostedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                screenshotPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 5, likeCount = 87),
                    viewer = ViewerStateUi(isLikedByViewer = true, isRepostedByViewer = true),
                ),
        )
    }
}
