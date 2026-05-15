package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.compose.material3.rememberBlueskyAnnotatedString
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.rememberRelativeTimeText
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Canonical Bluesky post-rendering composable for nubecita.
 *
 * Stateless — owns no `remember`-d state beyond what's needed for sub-component
 * memoization (`rememberBlueskyAnnotatedString`'s memo, the relative-time
 * ticker). Callers retain ownership of viewer state on their own [PostUi]
 * instances; toggling like/repost fires the callback only — PostCard does NOT
 * flip locally. The host VM produces a new [PostUi] with an updated
 * [ViewerStateUi] in response.
 *
 * **Loaded-state-only.** PostCard renders one post that has data. Loading is
 * the host's job (substitute `PostCardShimmer()`); errors and empty states
 * are list-level concerns owned by the screen, not parameters here. See the
 * `add-postcard-component` openspec change, design Decision 9.
 *
 * **Supported embed types.**
 * - `EmbedUi.Empty` — no embed slot rendered
 * - `EmbedUi.Images` — 1–4 images via [PostCardImageEmbed]
 * - `EmbedUi.Video` — host-supplied via [videoEmbedSlot]
 * - `EmbedUi.External` — native link-preview card via [PostCardExternalEmbed]
 * - `EmbedUi.Unsupported` — deliberate-degradation chip via [PostCardUnsupportedEmbed]
 *
 * **Deferred embeds** (each tracked under its own bd ticket):
 * - quoted posts (record) — nubecita-6vq
 * - record-with-media — nubecita-umn
 *
 * Until those land, PostCard renders the Unsupported chip for them.
 *
 * **Why `videoEmbedSlot` is a slot, not internal.** The video render
 * composable (`PostCardVideoEmbed`) lives in `:feature:feed:impl` because
 * it's screen-coordinator-aware (binds the FeedScreen-scoped
 * `FeedVideoPlayerCoordinator`'s shared `ExoPlayer`). Module dependency
 * direction is `:feature:feed:impl → :designsystem` and never the
 * reverse, so PostCard cannot import the feature-impl video composable
 * directly. Hosts that render video (the feed) supply a real slot;
 * hosts that don't (previews, design-system tests, the post-detail
 * screen which has its own player) leave [videoEmbedSlot] null and the
 * embed slot renders nothing — no spacer, no surface — so a video post
 * collapses cleanly to the post text + action row.
 */
@Composable
fun PostCard(
    post: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
) {
    // PostCard uses NubecitaAvatar (40dp) with 20dp horizontal + 14dp vertical
    // padding, so the avatar center is at x = 20 + 20 = 40dp, NOT the
    // threadConnector default of 42dp. Override the gutter geometry here so
    // the connector lines visually pass through the avatar center.
    //
    // avatarTop / avatarBottom intentionally leave a ~6dp gap on each side
    // of the avatar so the line doesn't touch the avatar circle — matches
    // TikTok's threaded-reply look (cleaner than bsky-style flush-to-avatar).
    // Avatar occupies y=14 to y=54; the line above ends at y=8, the line
    // below starts at y=60.
    val connectorModifier =
        if (connectAbove || connectBelow) {
            Modifier.threadConnector(
                connectAbove = connectAbove,
                connectBelow = connectBelow,
                color = MaterialTheme.colorScheme.outlineVariant,
                gutterX = 40.dp,
                avatarTop = 8.dp,
                avatarBottom = 60.dp,
            )
        } else {
            Modifier
        }
    Column(modifier = modifier.then(connectorModifier).fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { callbacks.onTap(post) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            post.repostedBy?.let { name ->
                RepostedByLine(name = name, modifier = Modifier.padding(start = 56.dp, bottom = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NubecitaAvatar(
                    model = post.author.avatarUrl,
                    contentDescription = post.author.displayName,
                    modifier = Modifier.clickable { callbacks.onAuthorTap(post.author) },
                )
                // weight(1f) — claim the remaining width after the avatar. Was
                // fillMaxWidth() which overflows past the avatar on narrow screens.
                Column(modifier = Modifier.weight(1f)) {
                    AuthorLine(post = post)
                    Spacer(Modifier.height(4.dp))
                    BodyText(text = post.text, facets = post.facets)
                    EmbedSlot(
                        embed = post.embed,
                        callbacks = callbacks,
                        videoEmbedSlot = videoEmbedSlot,
                        quotedVideoEmbedSlot = quotedVideoEmbedSlot,
                        onImageClick = onImageClick,
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(post = post, callbacks = callbacks)
                }
            }
        }
        // Suppress the divider when this PostCard sits inside a thread cluster
        // and is followed by another cluster post (i.e., connectBelow=true).
        // The thread connector line crossing through a horizontal divider
        // creates visual noise; the cluster's bottom card (leaf, with
        // connectBelow=false) keeps its divider so the cluster boundary is
        // still visible against the next feed item.
        if (!connectBelow) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RepostedByLine(
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = 14.dp,
        )
        Text(
            text = stringResource(R.string.postcard_reposted_by, name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AuthorLine(post: PostUi) {
    val timestamp by rememberRelativeTimeText(then = post.createdAt)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = post.author.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // weight(1f) — handle claims ALL remaining space after displayName +
        // timestamp take their intrinsic widths. Text aligns left within its
        // slot, so a short handle hugs the displayName and the timestamp
        // stays right-pinned. A long handle ellipsizes only when its content
        // would overflow the full available slot — never earlier. The prior
        // weight(1f, fill = false) + Spacer(weight(1f)) split forced a 50/50
        // contention that ellipsized handles much earlier than necessary
        // (kikin81/nubecita#54 review feedback).
        Text(
            text = stringResource(R.string.postcard_handle, post.author.handle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.postcard_relative_time, timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BodyText(
    text: String,
    facets: kotlinx.collections.immutable.ImmutableList<Facet>,
) {
    val annotated = rememberBlueskyAnnotatedString(text = text, facets = facets)
    Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun EmbedSlot(
    embed: EmbedUi,
    callbacks: PostCallbacks,
    videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)?,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)?,
    onImageClick: ((imageIndex: Int) -> Unit)?,
) {
    when (embed) {
        EmbedUi.Empty -> Unit
        is EmbedUi.Images -> {
            Spacer(Modifier.height(10.dp))
            PostCardImageEmbed(items = embed.items, onImageClick = onImageClick)
        }
        is EmbedUi.Video -> {
            // No spacer when the host hasn't supplied a slot — the
            // embed renders nothing rather than reserving 10dp of
            // whitespace for a non-existent surface.
            if (videoEmbedSlot != null) {
                Spacer(Modifier.height(10.dp))
                videoEmbedSlot(embed)
            }
        }
        is EmbedUi.External -> {
            Spacer(Modifier.height(10.dp))
            PostCardExternalEmbed(
                uri = embed.uri,
                domain = embed.domain,
                title = embed.title,
                description = embed.description,
                thumbUrl = embed.thumbUrl,
                onTap = callbacks.onExternalEmbedTap,
            )
        }
        is EmbedUi.Record -> {
            Spacer(Modifier.height(10.dp))
            PostCardQuotedPost(
                quotedPost = embed.quotedPost,
                // Forward only when the host wired a real handler. Otherwise
                // the inner clickable would consume the gesture and do
                // nothing — the tap should fall through to the outer parent
                // onTap instead.
                onTap = callbacks.onQuotedPostTap?.let { tap -> { tap(embed.quotedPost) } },
                quotedVideoEmbedSlot = quotedVideoEmbedSlot,
            )
        }
        is EmbedUi.RecordUnavailable -> {
            Spacer(Modifier.height(10.dp))
            PostCardRecordUnavailable(reason = embed.reason)
        }
        is EmbedUi.RecordWithMedia -> {
            Spacer(Modifier.height(10.dp))
            PostCardRecordWithMediaEmbed(
                record = embed.record,
                media = embed.media,
                // Only forward a tap target when the quoted record actually
                // resolved AND the host wired a real handler. RecordUnavailable
                // and the no-handler case both stay inert.
                onQuotedPostTap =
                    when (val r = embed.record) {
                        is EmbedUi.Record ->
                            callbacks.onQuotedPostTap?.let { tap -> { tap(r.quotedPost) } }
                        is EmbedUi.RecordUnavailable -> null
                    },
                onExternalMediaTap = callbacks.onExternalEmbedTap,
                videoEmbedSlot = videoEmbedSlot,
                quotedVideoEmbedSlot = quotedVideoEmbedSlot,
            )
        }
        is EmbedUi.Unsupported -> {
            Spacer(Modifier.height(10.dp))
            PostCardUnsupportedEmbed(typeUri = embed.typeUri)
        }
    }
}

@Composable
private fun ActionRow(
    post: PostUi,
    callbacks: PostCallbacks,
) {
    // Reply / share are one-shot actions (Role.Button); like / repost are
    // toggles (Role.Switch). The toggle path announces on/off state via
    // PostStat's Modifier.toggleable, so the label here is the noun being
    // toggled ("Like", "Repost") — not the inverse-action verb ("Unlike").
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        PostStat(
            name = NubecitaIconName.ChatBubble,
            count = post.stats.replyCount.toString(),
            accessibilityLabel = stringResource(R.string.postcard_action_reply),
            onClick = { callbacks.onReply(post) },
        )
        PostStat(
            name = NubecitaIconName.Repeat,
            count = post.stats.repostCount.toString(),
            accessibilityLabel = stringResource(R.string.postcard_action_repost),
            active = post.viewer.isRepostedByViewer,
            toggleable = true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            onClick = { callbacks.onRepost(post) },
        )
        PostStat(
            name = NubecitaIconName.Favorite,
            filled = post.viewer.isLikedByViewer,
            count = post.stats.likeCount.toString(),
            accessibilityLabel = stringResource(R.string.postcard_action_like),
            active = post.viewer.isLikedByViewer,
            toggleable = true,
            activeColor = MaterialTheme.colorScheme.secondary,
            onClick = { callbacks.onLike(post) },
        )
        PostStat(
            name = NubecitaIconName.IosShare,
            count = "",
            accessibilityLabel = stringResource(R.string.postcard_action_share),
            onClick = { callbacks.onShare(post) },
            // Only opt into combinedClickable's long-press path when the
            // host actually supplied a handler — keeps PostCallbacks.None
            // call sites (previews, non-feed hosts) on the plain clickable
            // path so TalkBack doesn't advertise a no-op long-press.
            onLongClick = callbacks.onShareLongPress?.let { handler -> { handler(post) } },
            onLongClickLabel = stringResource(R.string.postcard_action_copy_link),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews — exercise every visual branch PostCard supports. These are
// compositional smoke tests; they do not fetch network resources (the
// rememberBlueskyAnnotatedString helper noops without facets).
// ---------------------------------------------------------------------------

private fun previewAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakedid000000000000000",
        handle = "alice.bsky.social",
        displayName = "Alice Chen",
        avatarUrl = null,
    )

private fun previewPost(
    text: String = "The thing about building a Bluesky client in 2026 is you realize how much of the web we gave up trying to fix.",
    facets: kotlinx.collections.immutable.ImmutableList<Facet> = persistentListOf(),
    embed: EmbedUi = EmbedUi.Empty,
    stats: PostStatsUi = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
    viewer: ViewerStateUi = ViewerStateUi(isLikedByViewer = true),
    repostedBy: String? = null,
): PostUi =
    PostUi(
        id = "preview",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = previewAuthor(),
        createdAt = Clock.System.now() - 3.minutes,
        text = text,
        facets = facets,
        embed = embed,
        stats = stats,
        viewer = viewer,
        repostedBy = repostedBy,
    )

@Preview(name = "PostCard — empty body, no embed", showBackground = true)
@Composable
private fun PostCardEmptyBodyPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    text = "",
                    stats = PostStatsUi(),
                    viewer = ViewerStateUi(),
                ),
        )
    }
}

@Preview(name = "PostCard — typical post", showBackground = true)
@Composable
private fun PostCardTypicalPreview() {
    NubecitaTheme {
        PostCard(post = previewPost())
    }
}

@Preview(name = "PostCard — with single image", showBackground = true)
@Composable
private fun PostCardWithImagePreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.Images(
                            items =
                                persistentListOf(
                                    ImageUi(
                                        fullsizeUrl = "https://example.com/preview.jpg",
                                        thumbUrl = "https://example.com/preview.jpg",
                                        altText = "Preview image",
                                        aspectRatio = 1.5f,
                                    ),
                                ),
                        ),
                ),
        )
    }
}

@Preview(name = "PostCard — with external link card", showBackground = true)
@Composable
private fun PostCardWithExternalEmbedPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.External(
                            uri = "https://www.theverge.com/tech/elon-altman-court-battle",
                            domain = "theverge.com",
                            title = "Elon Musk and Sam Altman's court battle over the future of OpenAI",
                            description = "The billionaire battle goes to court.",
                            thumbUrl = "https://example.com/preview-external-thumb.jpg",
                        ),
                ),
        )
    }
}

@Preview(name = "PostCard — with quoted post (text-only quote)", showBackground = true)
@Composable
private fun PostCardWithQuotedPostPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.Record(
                            quotedPost =
                                QuotedPostUi(
                                    uri = "at://did:plc:quoted/app.bsky.feed.post/q",
                                    cid = "bafyreifakequotedcid000000000000000000000000000",
                                    author =
                                        net.kikin.nubecita.data.models.AuthorUi(
                                            did = "did:plc:quoted",
                                            handle = "acyn.bsky.social",
                                            displayName = "Acyn",
                                            avatarUrl = null,
                                        ),
                                    createdAt = Clock.System.now() - 60.minutes,
                                    text =
                                        "Bluesky's quoted-post rendering needs to match the official " +
                                            "client — full text, single-line author, no action row, " +
                                            "embed dispatch including video.",
                                    facets = persistentListOf(),
                                    embed = QuotedEmbedUi.Empty,
                                ),
                        ),
                ),
        )
    }
}

@Preview(name = "PostCard — with quoted post unavailable", showBackground = true)
@Composable
private fun PostCardWithRecordUnavailablePreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                ),
        )
    }
}

@Preview(name = "PostCard — with recordWithMedia (resolved + Images)", showBackground = true)
@Composable
private fun PostCardWithRecordWithMediaImagesPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.RecordWithMedia(
                            record =
                                EmbedUi.Record(
                                    quotedPost =
                                        QuotedPostUi(
                                            uri = "at://did:plc:quoted/app.bsky.feed.post/q",
                                            cid = "bafyreifakequotedcid000000000000000000000000000",
                                            author =
                                                AuthorUi(
                                                    did = "did:plc:quoted",
                                                    handle = "acyn.bsky.social",
                                                    displayName = "Acyn",
                                                    avatarUrl = null,
                                                ),
                                            createdAt = Clock.System.now() - 60.minutes,
                                            text =
                                                "Bluesky post being quoted by the parent — " +
                                                    "the parent's media renders above this card.",
                                            facets = persistentListOf(),
                                            embed = QuotedEmbedUi.Empty,
                                        ),
                                ),
                            media =
                                EmbedUi.Images(
                                    items =
                                        persistentListOf(
                                            ImageUi(
                                                fullsizeUrl = "https://example.com/preview.jpg",
                                                thumbUrl = "https://example.com/preview.jpg",
                                                altText = null,
                                                aspectRatio = 16f / 9f,
                                            ),
                                        ),
                                ),
                        ),
                ),
        )
    }
}

@Preview(name = "PostCard — with unsupported embed (something new)", showBackground = true)
@Composable
private fun PostCardUnsupportedEmbedPreview() {
    NubecitaTheme {
        PostCard(post = previewPost(embed = EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")))
    }
}

@Preview(name = "PostCard — with video embed (slot stub)", showBackground = true)
@Composable
private fun PostCardWithVideoEmbedPreview() {
    NubecitaTheme {
        // `:designsystem` cannot import PostCardVideoEmbed (lives in
        // `:feature:feed:impl`), so the preview's slot renders a stub Box.
        // The runtime slot is supplied by FeedScreen.
        PostCard(
            post =
                previewPost(
                    embed =
                        EmbedUi.Video(
                            posterUrl = "https://example.com/preview-video.jpg",
                            playlistUrl = "https://video.bsky.app/preview/playlist.m3u8",
                            aspectRatio = 16f / 9f,
                            durationSeconds = null,
                            altText = null,
                        ),
                ),
            videoEmbedSlot = { _ ->
                androidx.compose.foundation.layout.Box(
                    modifier =
                        androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(0.dp),
                )
            },
        )
    }
}

@Preview(name = "PostCard — reposted by Alice Chen", showBackground = true)
@Composable
private fun PostCardRepostedByPreview() {
    NubecitaTheme {
        PostCard(post = previewPost(repostedBy = "Alice Chen"))
    }
}

@Preview(name = "PostCard — with mention + link facets", showBackground = true)
@Composable
private fun PostCardWithFacetsPreview() {
    NubecitaTheme {
        // "Hello @alice.bsky.social, check out https://nubecita.app — built on @bluesky"
        // Two facets: one mention near the start, one link in the middle. Byte ranges
        // are computed against UTF-8; for ASCII text the byte and char offsets line up.
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
            post = previewPost(text = text, facets = persistentListOf(mention, link)),
        )
    }
}

// Like × repost permutation matrix for the action row. Counts are
// pinned per cell so the like / repost count diff between the
// neutral / liked / reposted / both branches is visible at a glance
// in the IDE preview pane.

@Preview(name = "PostCard — viewer neutral", showBackground = true)
@Composable
private fun PostCardViewerNeutralPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
                    viewer = ViewerStateUi(isLikedByViewer = false, isRepostedByViewer = false),
                ),
        )
    }
}

@Preview(name = "PostCard — viewer liked", showBackground = true)
@Composable
private fun PostCardViewerLikedPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 87),
                    viewer = ViewerStateUi(isLikedByViewer = true, isRepostedByViewer = false),
                ),
        )
    }
}

@Preview(name = "PostCard — viewer reposted", showBackground = true)
@Composable
private fun PostCardViewerRepostedPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 5, likeCount = 86),
                    viewer = ViewerStateUi(isLikedByViewer = false, isRepostedByViewer = true),
                ),
        )
    }
}

@Preview(name = "PostCard — viewer liked + reposted", showBackground = true)
@Composable
private fun PostCardViewerLikedAndRepostedPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    stats = PostStatsUi(replyCount = 12, repostCount = 5, likeCount = 87),
                    viewer = ViewerStateUi(isLikedByViewer = true, isRepostedByViewer = true),
                ),
        )
    }
}
