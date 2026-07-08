package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.GetProfileRequest
import io.github.kikin81.atproto.app.bsky.feed.GetAuthorFeedRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.SharedFlow
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.VerifierRef

/**
 * `app.bsky.actor.getProfile` + `app.bsky.feed.getAuthorFeed` fetch
 * surface scoped to `:feature:profile:impl`.
 *
 * No other module imports this interface today. If a second
 * consumer needs `getProfile` later (e.g. a hover card or
 * notification author preview), the change that adds the consumer
 * also promotes this to a `:core:profile` or `:core:actor` module.
 *
 * Takes a non-null `actor: String` from every entry point — the
 * ViewModel resolves `Profile(handle = null)` to the authenticated
 * user's DID upstream via `:core:auth`'s `SessionStateProvider`. This
 * keeps the repository auth-aware-free; future multi-account support
 * swaps the upstream `SessionStateProvider` impl, and this code
 * remains unchanged. See `openspec/.../design.md` Decision 4 +
 * Bead-C brainstorming note on multi-account readiness.
 */
interface ProfileRepository {
    /**
     * Emits once after every successful [updateProfile] write. A live
     * own-profile [net.kikin.nubecita.feature.profile.impl.ProfileViewModel]
     * collects this and refetches its header so the saved display name /
     * bio / avatar / banner appear without a manual pull-to-refresh — in
     * particular the authoritative avatar/banner CDN URLs, which the
     * editor (holding only the uploaded bytes) cannot predict.
     *
     * Hot and replay-free: only ViewModels collecting at emit time react.
     * A profile screen not yet composed relies on its own initial load,
     * so it already shows fresh data; this signal only matters for the
     * retained-but-covered own-profile screen sitting under the editor.
     */
    val ownProfileUpdates: SharedFlow<Unit>

    suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer>

    /**
     * Resolve a profile's verifier DIDs to display-ready [VerifierUi]s via
     * `app.bsky.actor.getProfiles`, pairing each resolved profile with its
     * ref's verification date. DIDs the appview doesn't return (deleted /
     * unresolvable accounts) are skipped. Empty [refs] short-circuits to an
     * empty list without a network call. Called lazily when the verification
     * sheet opens, not on profile load.
     */
    suspend fun resolveVerifiers(refs: ImmutableList<VerifierRef>): Result<ImmutableList<VerifierUi>>

    suspend fun fetchTab(
        actor: String,
        tab: ProfileTab,
        cursor: String? = null,
        limit: Int = PROFILE_TAB_PAGE_LIMIT,
    ): Result<ProfileTabPage>

    /**
     * Writes an `app.bsky.graph.follow` record from the authenticated
     * viewer to [subjectDid]. Returns the AT-URI of the new follow
     * record on success — the caller stores it on
     * [net.kikin.nubecita.feature.profile.impl.ViewerRelationship.Following.followUri]
     * so the matching [unfollow] call can target it directly.
     */
    suspend fun follow(subjectDid: String): Result<String>

    /**
     * Deletes the `app.bsky.graph.follow` record at [followUri]. The
     * URI is the value previously returned by [follow] (or surfaced
     * by the appview on `getProfile.viewer.following`).
     */
    suspend fun unfollow(followUri: String): Result<Unit>

    /**
     * Writes the authenticated user's own `app.bsky.actor.profile`
     * record (rkey `self`), editing only the four managed fields and
     * preserving every other field on the record.
     *
     * Flow (design "Write path"):
     * 1. `getRecord(app.bsky.actor.profile/self)` → `(value, cid)`.
     *    A brand-new account has no record yet — `RecordNotFound`
     *    starts from an empty record and writes WITHOUT a swap.
     * 2. Upload only **changed** images ([ImageChange.Replaced]); reuse
     *    the fetched blob ref for [ImageChange.Unchanged]; drop the key
     *    for [ImageChange.Removed].
     * 3. Merge [displayName] / [description] / avatar / banner onto the
     *    fetched [kotlinx.serialization.json.JsonObject], preserving all
     *    other keys (pinnedPost, labels, createdAt, pronouns, …).
     * 4. `putRecord(..., swapRecord = cid)` — optimistic concurrency. A
     *    stale CID surfaces distinctly as
     *    [ProfileUpdateError.SwapConflict] (no silent overwrite).
     *
     * [displayName] / [description]: a non-blank string sets the key; a
     * `null`-or-blank value drops it.
     *
     * On failure, returns a [ProfileUpdateError] through `Result`'s
     * exception channel.
     */
    suspend fun updateProfile(
        displayName: String?,
        description: String?,
        avatar: ImageChange,
        banner: ImageChange,
    ): Result<Unit>
}

/**
 * One paginated page of [TabItemUi] entries for a single profile tab.
 * `nextCursor == null` signals end-of-feed.
 */
data class ProfileTabPage(
    val items: ImmutableList<TabItemUi> = persistentListOf(),
    val nextCursor: String? = null,
)

/**
 * Default page size for author-feed requests. The lexicon allows
 * 1–100; 30 matches `:feature:feed:impl`'s `TIMELINE_PAGE_LIMIT`.
 */
internal const val PROFILE_TAB_PAGE_LIMIT: Int = 30

/**
 * Wire-level filter string for each [ProfileTab]. Lexicon accepts
 * `posts_with_replies` / `posts_no_replies` / `posts_with_media` /
 * `posts_and_author_threads` / `posts_with_video` — we use the
 * three values that map to the three tabs in the design.
 */
internal fun ProfileTab.toAuthorFeedFilter(): String =
    when (this) {
        ProfileTab.Posts -> "posts_no_replies"
        ProfileTab.Replies -> "posts_with_replies"
        ProfileTab.Media -> "posts_with_media"
    }

/**
 * Internal helper to build [GetAuthorFeedRequest]s. Pulled out so
 * tests can assert the filter string without booting `ActorService`.
 */
internal fun buildAuthorFeedRequest(
    actor: String,
    tab: ProfileTab,
    cursor: String?,
    limit: Int,
): GetAuthorFeedRequest =
    GetAuthorFeedRequest(
        actor = AtIdentifier(actor),
        filter = tab.toAuthorFeedFilter(),
        cursor = cursor,
        limit = limit.toLong(),
    )

internal fun buildGetProfileRequest(actor: String): GetProfileRequest = GetProfileRequest(actor = AtIdentifier(actor))
