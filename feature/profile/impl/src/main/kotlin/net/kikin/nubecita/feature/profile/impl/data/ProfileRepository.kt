package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.GetProfileRequest
import io.github.kikin81.atproto.app.bsky.feed.GetAuthorFeedRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi

/**
 * `app.bsky.actor.getProfile` + `app.bsky.feed.getAuthorFeed` fetch
 * surface scoped to `:feature:profile:impl`.
 *
 * Internal: no other module imports this interface. If a second
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
internal interface ProfileRepository {
    suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer>

    suspend fun fetchTab(
        actor: String,
        tab: ProfileTab,
        cursor: String? = null,
        limit: Int = PROFILE_TAB_PAGE_LIMIT,
    ): Result<ProfileTabPage>
}

/**
 * One paginated page of [TabItemUi] entries for a single profile tab.
 * `nextCursor == null` signals end-of-feed.
 */
internal data class ProfileTabPage(
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
