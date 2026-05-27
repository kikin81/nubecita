package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/**
 * Public preview / test fixtures for [NotificationItemUi]. Lives in
 * `src/main` rather than `src/test` (as [PostUiFixtures] does) because
 * the notifications surface fans out across `:designsystem` previews,
 * `:feature:notifications:impl` previews and unit tests, plus the
 * screenshot-test source set — and Gradle's `java-test-fixtures` isn't
 * applied here. Public-in-main is the simplest "usable from any module
 * that depends on `:data:models`" surface.
 *
 * Production code SHOULD NOT call these factories; they're tagged
 * `Fixture` for grep-ability and tree-shaking heuristics. R8 strips
 * unused factories from release builds.
 *
 * Each factory accepts defaults for every parameter so callers can
 * tweak just the dimension under test (e.g. an aggregated likes
 * fixture with 8 actors to exercise the avatar-stack `+N` overflow).
 */
public object NotificationItemUiFixtures {
    private val DEFAULT_INDEXED_AT: Instant = Instant.parse("2026-05-26T12:00:00Z")

    private val DEFAULT_AUTHORS: List<AuthorUi> =
        listOf(
            AuthorUi(
                did = "did:plc:fixture0alice00000000000",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
            AuthorUi(
                did = "did:plc:fixture0bob0000000000000",
                handle = "bob.bsky.social",
                displayName = "Bob Diaz",
                avatarUrl = null,
            ),
            AuthorUi(
                did = "did:plc:fixture0carol000000000000",
                handle = "carol.bsky.social",
                displayName = "Carol Estrada",
                avatarUrl = null,
            ),
            AuthorUi(
                did = "did:plc:fixture0dave00000000000000",
                handle = "dave.bsky.social",
                displayName = "Dave Fuentes",
                avatarUrl = null,
            ),
        )

    private val DEFAULT_SUBJECT_POST: PostUi =
        PostUi(
            id = "at://did:plc:fixturese1f000000000000/app.bsky.feed.post/fixture-subject",
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author =
                AuthorUi(
                    did = "did:plc:fixturese1f000000000000",
                    handle = "you.bsky.social",
                    displayName = "You",
                    avatarUrl = null,
                ),
            createdAt = DEFAULT_INDEXED_AT,
            text = "Hello from the notifications fixture.",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    /** Pull `count` distinct authors, repeating the default roster as needed. */
    private fun authors(count: Int): List<AuthorUi> {
        require(count >= 1) { "actor count must be >= 1, got $count" }
        return List(count) { i -> DEFAULT_AUTHORS[i % DEFAULT_AUTHORS.size] }
    }

    public fun singleLike(
        itemKey: String = "like-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Like,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun aggregatedLikes(
        actorCount: Int = 3,
        itemKey: String = "like-fixture-agg",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Aggregated =
        NotificationItemUi.Aggregated(
            itemKey = itemKey,
            reason = NotificationReason.Like,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = authors(actorCount).toImmutableList(),
            subjectPost = subjectPost,
        )

    public fun singleRepost(
        itemKey: String = "repost-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Repost,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun aggregatedReposts(
        actorCount: Int = 3,
        itemKey: String = "repost-fixture-agg",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Aggregated =
        NotificationItemUi.Aggregated(
            itemKey = itemKey,
            reason = NotificationReason.Repost,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = authors(actorCount).toImmutableList(),
            subjectPost = subjectPost,
        )

    public fun singleFollow(
        itemKey: String = "follow-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Follow,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = null,
        )

    public fun aggregatedFollows(
        actorCount: Int = 3,
        itemKey: String = "follow-fixture-agg",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
    ): NotificationItemUi.Aggregated =
        NotificationItemUi.Aggregated(
            itemKey = itemKey,
            reason = NotificationReason.Follow,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = authors(actorCount).toImmutableList(),
            subjectPost = null,
        )

    public fun singleMention(
        itemKey: String = "mention-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Mention,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleReply(
        itemKey: String = "reply-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Reply,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleQuote(
        itemKey: String = "quote-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Quote,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleStarterpackJoined(
        itemKey: String = "starterpack-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.StarterpackJoined,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = null,
        )

    public fun singleVerified(
        itemKey: String = "verified-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Verified,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = null,
        )

    public fun singleUnverified(
        itemKey: String = "unverified-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.Unverified,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = null,
        )

    public fun singleLikeViaRepost(
        itemKey: String = "like-via-repost-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.LikeViaRepost,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleRepostViaRepost(
        itemKey: String = "repost-via-repost-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.RepostViaRepost,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleSubscribedPost(
        itemKey: String = "subscribed-post-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
        subjectPost: PostUi? = DEFAULT_SUBJECT_POST,
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.SubscribedPost,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = subjectPost,
        )

    public fun singleContactMatch(
        itemKey: String = "contact-match-fixture-1",
        isRead: Boolean = false,
        indexedAt: Instant = DEFAULT_INDEXED_AT,
        actor: AuthorUi = DEFAULT_AUTHORS[0],
    ): NotificationItemUi.Single =
        NotificationItemUi.Single(
            itemKey = itemKey,
            reason = NotificationReason.ContactMatch,
            indexedAt = indexedAt,
            isRead = isRead,
            actors = persistentListOf(actor),
            subjectPost = null,
        )
}
