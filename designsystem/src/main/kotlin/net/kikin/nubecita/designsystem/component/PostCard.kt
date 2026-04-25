package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
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
 * **Supported embed types (v1).**
 * - `EmbedUi.Empty` — no embed slot rendered
 * - `EmbedUi.Images` — 1–4 images via [PostCardImageEmbed]
 * - `EmbedUi.Unsupported` — deliberate-degradation chip via [PostCardUnsupportedEmbed]
 *
 * **Deferred embeds** (each tracked under its own bd ticket — see the embed
 * scope decision in `docs/superpowers/specs/2026-04-25-postcard-embed-scope-v1.md`):
 * - external link cards — nubecita-aku
 * - quoted posts (record) — nubecita-6vq
 * - video — nubecita-xsu
 * - record-with-media — nubecita-umn
 *
 * Until those land, PostCard renders the Unsupported chip for them.
 */
@Composable
fun PostCard(
    post: PostUi,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    AuthorLine(post = post)
                    Spacer(Modifier.height(4.dp))
                    BodyText(text = post.text, facets = post.facets)
                    EmbedSlot(embed = post.embed)
                    Spacer(Modifier.height(8.dp))
                    ActionRow(post = post, callbacks = callbacks)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        Icon(
            imageVector = Icons.Outlined.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "$name reposted",
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
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "@${post.author.handle} · $timestamp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun EmbedSlot(embed: EmbedUi) {
    when (embed) {
        EmbedUi.Empty -> Unit
        is EmbedUi.Images -> {
            Spacer(Modifier.height(10.dp))
            PostCardImageEmbed(items = embed.items)
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        PostStat(
            icon = Icons.Outlined.ChatBubbleOutline,
            count = post.stats.replyCount.toString(),
            onClick = { callbacks.onReply(post) },
        )
        PostStat(
            icon = Icons.Outlined.Repeat,
            count = post.stats.repostCount.toString(),
            active = post.viewer.isRepostedByViewer,
            activeColor = MaterialTheme.colorScheme.tertiary,
            onClick = { callbacks.onRepost(post) },
        )
        PostStat(
            icon = if (post.viewer.isLikedByViewer) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            count = post.stats.likeCount.toString(),
            active = post.viewer.isLikedByViewer,
            activeColor = MaterialTheme.colorScheme.secondary,
            onClick = { callbacks.onLike(post) },
        )
        PostStat(
            icon = Icons.Outlined.IosShare,
            count = "",
            onClick = { callbacks.onShare(post) },
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

@Preview(name = "PostCard — with unsupported embed (video)", showBackground = true)
@Composable
private fun PostCardUnsupportedEmbedPreview() {
    NubecitaTheme {
        PostCard(post = previewPost(embed = EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")))
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
