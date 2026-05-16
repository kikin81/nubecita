package net.kikin.nubecita.feature.search.impl.data

import androidx.compose.runtime.Immutable

/**
 * UI-ready projection of an `app.bsky.feed.defs#generatorView`. Drives the
 * Feeds tab row in `:feature:search:impl/ui/FeedRow`.
 *
 * Kept local to `:feature:search:impl/data` for V1 — same rationale as
 * `DefaultSearchActorsRepository`'s local `ActorUi` mapper before it was
 * promoted to `:data:models`: one consumer, no premature sharing.
 * Promote to `:data:models` when a second consumer surfaces (e.g. a
 * future "feeds you follow" list or a feed-detail header).
 *
 * `likeCount` defaults to 0 when the upstream view omits it. Bluesky
 * uses likes as the de-facto "subscriber count" surface for feeds; the
 * row's "%d likes" line maps directly to this.
 *
 * `avatarUrl` is nullable because feeds without a custom icon fall
 * back to `NubecitaAsyncImage`'s standard flat placeholder tile — the
 * same shape every other AsyncImage in the app degrades to.
 */
@Immutable
internal data class FeedGeneratorUi(
    val uri: String,
    val displayName: String,
    val creatorHandle: String,
    val creatorDisplayName: String?,
    val description: String?,
    val avatarUrl: String?,
    val likeCount: Long,
)
