package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetFeaturesUnion
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.app.bsky.richtext.FacetTag
import io.github.kikin81.atproto.compose.appendBlueskyText
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.rememberRelativeTimeText
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.data.models.VerifiedBadge
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
    videoEmbedSlot: (@Composable (EmbedUi.Video, cover: MediaCover?) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    animateLikeTap: Boolean = false,
    animateRepostTap: Boolean = false,
    bodyMatch: String? = null,
    isMediaRevealed: Boolean = false,
    onRevealMedia: () -> Unit = {},
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
                    // The whole author identity row (name · @handle · time) opens the
                    // author's profile, same target as the avatar. Nested inside the
                    // card's onTap clickable, so this consumes the tap first.
                    AuthorLine(
                        post = post,
                        modifier = Modifier.clickable { callbacks.onAuthorTap(post.author) },
                    )
                    Spacer(Modifier.height(4.dp))
                    BodyText(
                        text = post.text,
                        facets = post.facets,
                        onFacetTap = callbacks.onFacetTap,
                        bodyMatch = bodyMatch,
                    )
                    EmbedSlot(
                        embed = post.embed,
                        callbacks = callbacks,
                        videoEmbedSlot = videoEmbedSlot,
                        quotedVideoEmbedSlot = quotedVideoEmbedSlot,
                        onImageClick = onImageClick,
                        isMediaRevealed = isMediaRevealed,
                        onRevealMedia = onRevealMedia,
                    )
                    Spacer(Modifier.height(8.dp))
                    ActionRow(
                        post = post,
                        callbacks = callbacks,
                        animateLikeTap = animateLikeTap,
                        animateRepostTap = animateRepostTap,
                    )
                }
            }
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
private fun AuthorLine(
    post: PostUi,
    modifier: Modifier = Modifier,
) {
    val timestamp by rememberRelativeTimeText(then = post.createdAt)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Unverified (the vast majority) keeps the original flat layout so every
        // existing PostCard baseline stays byte-identical: the display-name Text is
        // a direct, unweighted child. The verified case groups [name + badge] in an
        // UNWEIGHTED inner Row: like the unverified name it's measured up to the
        // full available width, and the weighted handle then takes whatever remains
        // — so a short name leaves the handle its full slot (timestamp pinned) and a
        // long name ellipsizes and squeezes the handle, exactly the unverified
        // name-priority behavior. Inside the group the name has weight(1f, fill =
        // false) so it ellipsizes to fit while the fixed badge keeps its size,
        // staying visible ahead of the handle. (nubecita-vw45.5, resolving the .2
        // clip.) The badge is non-interactive here: a tap falls through to the card.
        if (post.author.verifiedBadge == VerifiedBadge.None) {
            Text(
                text = post.author.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = post.author.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                VerificationBadge(badge = post.author.verifiedBadge)
            }
        }
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
    onFacetTap: (FacetTarget) -> Unit,
    bodyMatch: String? = null,
) {
    val annotated = rememberTappableBlueskyAnnotatedString(text = text, facets = facets, onFacetTap = onFacetTap)
    val withHighlight =
        annotated.withMatchHighlight(
            match = bodyMatch,
            highlightStyle =
                SpanStyle(
                    background = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        )
    Text(text = withHighlight, style = MaterialTheme.typography.bodyLarge)
}

/**
 * Bluesky post body as a tappable [AnnotatedString]. Reuses the atproto SDK's
 * [appendBlueskyText] primitive (which owns the UTF-8-byte → char index math) and
 * layers a Compose [LinkAnnotation.Clickable] over each mention/link facet so
 * `Text(...)` makes them tappable natively — each facet is its own span, so
 * multiple mentions in one post each route to their own target with no manual
 * hit-testing. `#tag` facets stay styled but inert until tag search exists.
 *
 * Memoized on `(text, facets, linkStyle)`; the tap listener reads the latest
 * [onFacetTap] via [rememberUpdatedState] so the string isn't rebuilt when the
 * host's lambda identity changes.
 */
@Composable
private fun rememberTappableBlueskyAnnotatedString(
    text: String,
    facets: kotlinx.collections.immutable.ImmutableList<Facet>,
    onFacetTap: (FacetTarget) -> Unit,
    linkStyle: SpanStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
): AnnotatedString {
    // The listener reads the latest lambda so the string (memoized on the keys
    // below) isn't rebuilt when the host's onFacetTap identity changes.
    val latestOnFacetTap = rememberUpdatedState(onFacetTap)
    return remember(text, facets, linkStyle) {
        buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { latestOnFacetTap.value(it) }
    }
}

/**
 * Pure builder behind [rememberTappableBlueskyAnnotatedString] — no Compose
 * runtime, so it's unit-testable: build with a capturing `onFacetTap`, pull the
 * link annotations off the result, and invoke each listener to assert the
 * target it routes to. Kept `internal` for the designsystem's own tests only.
 */
internal fun buildTappableBlueskyAnnotatedString(
    text: String,
    facets: kotlinx.collections.immutable.ImmutableList<Facet>,
    linkStyle: SpanStyle,
    onFacetTap: (FacetTarget) -> Unit,
): AnnotatedString {
    // Preserve the established rendering exactly — only tappability is new:
    //  - a @mention is color-only (no underline), matching the post header handle;
    //  - an inline link keeps the default link underline.
    // A bare LinkAnnotation underlines its span, so mentions need an explicit
    // textDecoration = None; links pass no styles and inherit the default underline
    // (in the link color) — pixel-identical to the previous non-tappable rendering.
    val mentionStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.None))
    return buildAnnotatedString {
        appendBlueskyText(text, facets) { feature, startChar, endChar, _ ->
            when (feature) {
                is FacetMention -> {
                    addStyle(linkStyle, startChar, endChar)
                    val did = feature.did.raw
                    addLink(
                        LinkAnnotation.Clickable(tag = "mention", styles = mentionStyles) {
                            onFacetTap(FacetTarget.Mention(did))
                        },
                        startChar,
                        endChar,
                    )
                }
                is FacetLink -> {
                    addStyle(linkStyle, startChar, endChar)
                    val uri = feature.uri.raw
                    addLink(
                        LinkAnnotation.Clickable(tag = "link") {
                            onFacetTap(FacetTarget.Link(uri))
                        },
                        startChar,
                        endChar,
                    )
                }
                // Tags are styled but not yet tappable (tag search needs a query route).
                is FacetTag -> addStyle(linkStyle, startChar, endChar)
                // Forward-compatible: an unknown facet feature stays plain text.
                is FacetFeaturesUnion.Unknown -> Unit
            }
        }
    }
}

@Composable
private fun EmbedSlot(
    embed: EmbedUi,
    callbacks: PostCallbacks,
    videoEmbedSlot: (@Composable (EmbedUi.Video, cover: MediaCover?) -> Unit)?,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)?,
    onImageClick: ((imageIndex: Int) -> Unit)?,
    isMediaRevealed: Boolean,
    onRevealMedia: () -> Unit,
) {
    // Build a cover from a media embed's precomputed warning + the per-post
    // reveal state. Null → render the media (no warning, or already revealed).
    // Top-level image / link / gif / video and a RecordWithMedia's media half
    // are all covered here; the video cover is handed to the host video slot so
    // the poster never fetches while covered. The quoted record's OWN media is
    // not covered yet — quoted-post labels aren't moderated (deferred follow-up).
    val coverFor = { warning: MediaContentWarning? ->
        warning.takeIf { !isMediaRevealed }?.let { MediaCover(it, onRevealMedia) }
    }
    when (embed) {
        EmbedUi.Empty -> Unit
        is EmbedUi.ImageContainerEmbed -> {
            // Images (≤4) and Gallery (≤10) share the same carousel render path.
            Spacer(Modifier.height(10.dp))
            PostCardImageEmbed(items = embed.items, onImageClick = onImageClick, cover = coverFor(embed.contentWarning))
        }
        is EmbedUi.Video -> {
            // No spacer when the host hasn't supplied a slot — the
            // embed renders nothing rather than reserving 10dp of
            // whitespace for a non-existent surface.
            if (videoEmbedSlot != null) {
                Spacer(Modifier.height(10.dp))
                videoEmbedSlot(embed, coverFor(embed.contentWarning))
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
                cover = coverFor(embed.contentWarning),
            )
        }
        is EmbedUi.Gif -> {
            Spacer(Modifier.height(10.dp))
            PostCardGifEmbed(
                gifUrl = embed.gifUrl,
                aspectRatio = embed.aspectRatio,
                alt = embed.alt,
                cover = coverFor(embed.contentWarning),
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
                // The media half (this post's own attachment) is covered per the
                // precomputed warning; the quoted record's own media is not (its
                // labels aren't moderated yet — deferred follow-up).
                mediaCover = coverFor(embed.media.contentWarning),
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
    animateLikeTap: Boolean,
    animateRepostTap: Boolean,
) {
    // Reply / share are one-shot actions (Role.Button); like / repost are
    // toggles (Role.Switch). The toggle path announces on/off state via
    // PostStat's Modifier.toggleable, so the label here is the noun being
    // toggled ("Like", "Repost") — not the inverse-action verb ("Unlike").
    //
    // Counts pass through to PostStat as Long; PostStat composes the
    // locale-aware compact formatter and the digit-roll animation
    // (gated by `animateUserDelta` per the careful rule in
    // AnimatedCompactCount).
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        PostStat(
            name = NubecitaIconName.ChatBubble,
            count = post.stats.replyCount.toLong(),
            accessibilityLabel =
                stringResource(
                    if (post.viewer.canViewerReply) {
                        R.string.postcard_action_reply
                    } else {
                        R.string.postcard_action_reply_disabled
                    },
                ),
            // Threadgate: the appview says this viewer can't reply, so the CTA is
            // inert + dimmed and the onReply callback never fires — no doomed
            // composer launch.
            enabled = post.viewer.canViewerReply,
            onClick = { callbacks.onReply(post) },
        )
        RepostAction(
            post = post,
            callbacks = callbacks,
            animateRepostTap = animateRepostTap,
        )
        PostStat(
            name = NubecitaIconName.Favorite,
            filled = post.viewer.isLikedByViewer,
            count = post.stats.likeCount.toLong(),
            accessibilityLabel = stringResource(R.string.postcard_action_like),
            active = post.viewer.isLikedByViewer,
            toggleable = true,
            activeColor = MaterialTheme.colorScheme.secondary,
            onClick = { callbacks.onLike(post) },
            iconAnimation = PostStatIconAnimation.Pop,
            animateUserDelta = animateLikeTap,
        )
        PostStat(
            name = NubecitaIconName.IosShare,
            count = null,
            accessibilityLabel = stringResource(R.string.postcard_action_share),
            onClick = { callbacks.onShare(post) },
            // Only opt into combinedClickable's long-press path when the
            // host actually supplied a handler — keeps PostCallbacks.None
            // call sites (previews, non-feed hosts) on the plain clickable
            // path so TalkBack doesn't advertise a no-op long-press.
            onLongClick = callbacks.onShareLongPress?.let { handler -> { handler(post) } },
            onLongClickLabel = stringResource(R.string.postcard_action_copy_link),
        )
        // 5th action-row cell: overflow menu. Suppressed entirely when
        // the host hasn't wired `onOverflowAction` — mirrors the
        // `onQuotedPostTap == null` and `onShareLongPress == null`
        // suppression patterns. Hosts opt in by passing a non-null
        // handler.
        callbacks.onOverflowAction?.let { handler ->
            PostOverflowAffordance(post = post, onAction = { handler(post, it) })
        }
    }
}

/**
 * The repost action-row cell. Two interaction modes:
 *
 * - **Plain toggle** (default) — when [PostCallbacks.onQuote] is unwired, or the
 *   post has quoting disabled (`viewer.canViewerQuote == false`). A single tap
 *   toggles repost via [PostCallbacks.onRepost]. Identical to the pre-quote
 *   behavior, including the `Role.Switch` on/off a11y semantics.
 * - **Menu** — when quoting is wired AND permitted. A single tap opens a
 *   [DropdownMenu] offering "Repost"/"Undo repost" + "Quote post"; a long-press
 *   performs the repost toggle directly (the power-user shortcut). Every action
 *   stays reachable via the menu, so long-press is purely additive.
 *
 * The icon tints to `tertiary` while [PostUi.viewer]`.isRepostedByViewer` in both
 * modes, and spins on activate.
 */
@Composable
private fun RepostAction(
    post: PostUi,
    callbacks: PostCallbacks,
    animateRepostTap: Boolean,
) {
    val isReposted = post.viewer.isRepostedByViewer
    val repostLabel =
        stringResource(
            if (isReposted) R.string.postcard_action_undo_repost else R.string.postcard_action_repost,
        )
    val onQuote = callbacks.onQuote
    // Menu only when there's actually a Quote option to offer — wired AND the
    // post permits quoting (read-side postgate). Otherwise a single-item menu
    // would be pointless, so fall back to the direct toggle.
    val showMenu = onQuote != null && post.viewer.canViewerQuote
    if (!showMenu) {
        PostStat(
            name = NubecitaIconName.Repeat,
            count = post.stats.repostCount.toLong(),
            accessibilityLabel = stringResource(R.string.postcard_action_repost),
            active = isReposted,
            toggleable = true,
            activeColor = MaterialTheme.colorScheme.tertiary,
            onClick = { callbacks.onRepost(post) },
            iconAnimation = PostStatIconAnimation.Spin,
            animateUserDelta = animateRepostTap,
        )
        return
    }
    // Keyed by post.id so a recycled cell rebound to a different post resets the
    // menu to closed rather than leaking the prior post's open state.
    var expanded by remember(post.id) { mutableStateOf(false) }
    Box {
        PostStat(
            name = NubecitaIconName.Repeat,
            count = post.stats.repostCount.toLong(),
            // Tap opens the menu (an "options" button); long-press is the
            // instant toggle. Not toggleable — the primary tap action is "open".
            accessibilityLabel = stringResource(R.string.postcard_action_repost_options),
            active = isReposted,
            activeColor = MaterialTheme.colorScheme.tertiary,
            onClick = { expanded = true },
            onLongClick = { callbacks.onRepost(post) },
            onLongClickLabel = repostLabel,
            iconAnimation = PostStatIconAnimation.Spin,
            animateUserDelta = animateRepostTap,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(repostLabel) },
                onClick = {
                    expanded = false
                    callbacks.onRepost(post)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.postcard_action_quote)) },
                onClick = {
                    expanded = false
                    onQuote(post)
                },
            )
        }
    }
}

/**
 * 5th action-row affordance — an icon-button that opens a DropdownMenu
 * with the moderation / utility entries listed in [PostOverflowAction].
 *
 * **DropdownMenu (not ModalBottomSheet).** Matches the existing
 * ProfileHero pattern, cheaper to render at 120Hz scroll (no scrim, no
 * sheet animations to budget around), and matches social-app's
 * PostMenu shape on web. Owns its own `expanded` state — the parent
 * passes a single dispatch lambda, not visibility.
 *
 * Visibility rules (per the oftc.2 spec):
 * - "Report post" — always visible.
 * - "Mute @handle" / "Unmute @handle" — exactly one of the pair,
 *   keyed on `post.viewer.isAuthorMutedByViewer`.
 * - "Block @handle" / "Unblock @handle" — exactly one of the pair,
 *   keyed on `post.viewer.isAuthorBlockedByViewer`.
 * - "Mute thread" — always visible in oftc.2; per-thread mute-state
 *   lookup ships under oftc.5.
 * - "Copy post text" — always visible.
 */
@Composable
private fun PostOverflowAffordance(
    post: PostUi,
    onAction: (PostOverflowAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PostStat(
            name = NubecitaIconName.MoreHoriz,
            count = null,
            accessibilityLabel = stringResource(R.string.postcard_action_more),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.moderation_action_report_post)) },
                onClick = {
                    expanded = false
                    onAction(PostOverflowAction.ReportPost)
                },
            )
            // Mute / Unmute pair — exactly one renders. Keyed on the
            // post's viewer projection so oftc.1's mapper population is
            // load-bearing.
            if (post.viewer.isAuthorMutedByViewer) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.moderation_action_unmute_author,
                                post.author.handle,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(PostOverflowAction.UnmuteAuthor)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.moderation_action_mute_author,
                                post.author.handle,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(PostOverflowAction.MuteAuthor)
                    },
                )
            }
            if (post.viewer.isAuthorBlockedByViewer) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.moderation_action_unblock_author,
                                post.author.handle,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(PostOverflowAction.UnblockAuthor)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.moderation_action_block_author,
                                post.author.handle,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(PostOverflowAction.BlockAuthor)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.moderation_action_mute_thread)) },
                onClick = {
                    expanded = false
                    onAction(PostOverflowAction.MuteThread)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.moderation_action_copy_post_text)) },
                onClick = {
                    expanded = false
                    onAction(PostOverflowAction.CopyPostText)
                },
            )
        }
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

@Preview(name = "PostCard — with body match highlight", showBackground = true)
@Composable
private fun PostCardWithBodyMatchPreview() {
    NubecitaTheme {
        PostCard(
            post =
                previewPost(
                    text = "Kotlin is great. We use Kotlin every day at this Bluesky client.",
                ),
            bodyMatch = "kotlin",
        )
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
            videoEmbedSlot = { _, _ ->
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
