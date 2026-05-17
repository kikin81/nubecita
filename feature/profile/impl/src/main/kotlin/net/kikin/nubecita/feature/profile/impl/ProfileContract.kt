package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PostUi

/**
 * MVI state for the Profile screen.
 *
 * Flat fields hold values whose presence is independent (e.g. `header`
 * and `headerError` can coexist with any `TabLoadStatus` combination
 * across the three tabs). The three per-tab statuses are sealed sums
 * because each tab has a mutually-exclusive lifecycle — Idle / loading
 * / loaded / error — that the spec requires to be invalid-by-construction
 * (see `openspec/.../specs/feature-profile/spec.md` Requirement
 * "ProfileScreenViewState uses flat fields for independent flags and
 * per-tab sealed TabLoadStatus").
 *
 * Per-tab independent errors. The header carries its own `headerError`
 * flag because a failed `getProfile` blanks the hero — that's worth
 * surfacing as a Snackbar in addition to the in-state flag. Per-tab
 * `TabLoadStatus.InitialError` is the in-tab rendering signal (an
 * empty-with-retry composable inside the tab body) and is NOT
 * mirrored into a Snackbar — partial-load surfaces should be silent
 * about their per-section failures.
 */
data class ProfileScreenViewState(
    /** The current view's profile target — `null = own user, "..." = other`. Set once at composition. */
    val handle: String? = null,
    /** Loaded profile header. Null while the initial `getProfile` is in flight. */
    val header: ProfileHeaderUi? = null,
    /** Sticky-once header error; coexists with non-null `header` after a refresh fails. */
    val headerError: ProfileError? = null,
    val ownProfile: Boolean = false,
    val viewerRelationship: ViewerRelationship = ViewerRelationship.None,
    val selectedTab: ProfileTab = ProfileTab.Posts,
    val postsStatus: TabLoadStatus = TabLoadStatus.Idle,
    val repliesStatus: TabLoadStatus = TabLoadStatus.Idle,
    val mediaStatus: TabLoadStatus = TabLoadStatus.Idle,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastLikeTapPostUri: String? = null,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastRepostTapPostUri: String? = null,
) : UiState

/**
 * Which of the three tab bodies is currently rendering. The default
 * tab on profile open is Posts (per the wireframes).
 */
enum class ProfileTab { Posts, Replies, Media }

/**
 * The relationship between the authenticated viewer and the
 * currently-rendered profile. `Self` ↔ `state.ownProfile == true`;
 * the others apply only to other-user profiles.
 *
 * `viewerRelationship is NotFollowing` AND `state.ownProfile == true`
 * is invalid; that combination MUST NOT be representable in any
 * reducer-emitted state.
 *
 * [isPending] flips to `true` for [Following] / [NotFollowing] while
 * an in-flight `app.bsky.graph.follow` create / delete is pending the
 * server's confirmation. The host composable disables the action
 * button while pending so a second tap can't double-fire — the same
 * `isPending` flag is the ViewModel's single-flight guard.
 *
 * On [Following] the [Following.followUri] is null **only** while
 * pending the optimistic NotFollowing → Following flip; the wire
 * response's `uri` populates it on success. The `init { require(...) }`
 * on [Following] enforces this invariant — a committed `Following`
 * (`isPending == false`) MUST carry a non-null URI so the matching
 * `unfollow` path always has a record to delete.
 *
 * Marked `@Immutable` so Compose treats every variant — the data
 * objects and the data classes (whose only fields are stable
 * primitives) — as fully stable inputs to composables that take a
 * `ViewerRelationship` parameter.
 */
@Immutable
sealed interface ViewerRelationship {
    val isPending: Boolean

    data object None : ViewerRelationship {
        override val isPending: Boolean = false
    }

    data object Self : ViewerRelationship {
        override val isPending: Boolean = false
    }

    data class Following(
        val followUri: String?,
        override val isPending: Boolean = false,
    ) : ViewerRelationship {
        init {
            // Invalid by construction: a committed Following without a
            // followUri is unrecoverable (the unfollow write path can't
            // target a record it doesn't have a URI for). The pending
            // optimistic flip is the only legitimate `followUri == null`
            // case — wire success then commits the URI returned from
            // createRecord.
            require(followUri != null || isPending) {
                "Following.followUri may only be null while isPending == true"
            }
        }
    }

    data class NotFollowing(
        override val isPending: Boolean = false,
    ) : ViewerRelationship
}

/**
 * Header-row UI model. Derived from `app.bsky.actor.defs#profileViewDetailed`
 * via [net.kikin.nubecita.feature.profile.impl.data.AuthorProfileMapper].
 *
 * `banner` is the nullable banner URL **as a String** (the atproto
 * runtime's `Uri.raw` value) — matches the shape `BoldHeroGradient`
 * accepts in `:designsystem`. `avatarHue` is derived deterministically
 * from `did + first char of handle` so the bold-derived hero gradient
 * has a stable fallback when no banner is set.
 */
data class ProfileHeaderUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val avatarHue: Int,
    val bio: String?,
    val location: String?,
    val website: String?,
    val joinedDisplay: String?,
    val postsCount: Long,
    val followersCount: Long,
    val followsCount: Long,
    /**
     * Whether the signed-in viewer is allowed to send this user a DM.
     * Derived in the mapper from `associated.chat.allowIncoming` and
     * `viewer.followedBy`. When `false`, the Message button is hidden
     * — mirrors the official Bluesky Android client's behavior and
     * avoids the dead-end "MessagesDisabled" error after tap.
     *
     * Defaults to `true` so callers that don't construct the field
     * (older test fixtures, future synthetic header instances) keep
     * the action visible — same fail-open posture as the wire mapping
     * (an absent `allowIncoming` is treated as `"all"`).
     */
    val canMessage: Boolean = true,
)

/**
 * One row of any tab body. Posts and Replies render `Post` variants in
 * a `LazyColumn` of `:designsystem.PostCard`s; Media renders
 * `MediaCell` variants in a `LazyVerticalGrid(GridCells.Fixed(3))`.
 *
 * The single sealed sum keeps `TabLoadStatus` structurally identical
 * across the three tabs — the screen branches on the variant when
 * rendering, not on the tab itself.
 */
sealed interface TabItemUi {
    val postUri: String

    /** Standalone post or reply — rendered by `:designsystem.PostCard`. */
    data class Post(
        val post: PostUi,
    ) : TabItemUi {
        override val postUri: String get() = post.id
    }

    /**
     * Media grid cell — one thumb per post. [thumbUrl] is the first
     * image in the post's embed (or the video poster for video posts).
     *
     * [isVideo] differentiates a video post from an image post so the
     * grid can overlay a small play-badge on video cells and the VM
     * can route the tap to the fullscreen video player (the MediaViewer
     * route would crash with "post has no images" for a video).
     */
    data class MediaCell(
        override val postUri: String,
        val thumbUrl: String?,
        val isVideo: Boolean = false,
    ) : TabItemUi
}

/**
 * Per-tab paginated load lifecycle. Each tab tracks its own status
 * independently; refreshing one tab does NOT reset the others. Per
 * `CLAUDE.md` MVI conventions, this is a sealed sum (not a flat
 * boolean tuple) because the variants are mutually exclusive.
 */
sealed interface TabLoadStatus {
    /** Pre-fetch. Profile just composed; the load hasn't been kicked off yet (this is rare — the VM kicks all four loads from `init`). */
    data object Idle : TabLoadStatus

    /** First load in flight; the body renders a loading composable. */
    data object InitialLoading : TabLoadStatus

    /** Page(s) loaded. Pagination state lives here. */
    data class Loaded(
        val items: ImmutableList<TabItemUi> = persistentListOf(),
        val isAppending: Boolean = false,
        val isRefreshing: Boolean = false,
        val hasMore: Boolean = true,
        val cursor: String? = null,
    ) : TabLoadStatus

    /** Initial load failed. The body renders an error composable with a Retry affordance. */
    data class InitialError(
        val error: ProfileError,
    ) : TabLoadStatus
}

/**
 * Closed sum of error variants the profile screen distinguishes for
 * rendering. Network and unknown failures both fall back to "try
 * again" — the variants exist so future work can surface
 * variant-specific copy without a Contract.kt change.
 */
sealed interface ProfileError {
    data object Network : ProfileError

    data class Unknown(
        val cause: String?,
    ) : ProfileError
}

/**
 * MVI events the screen sends to the ViewModel.
 */
sealed interface ProfileEvent : UiEvent {
    /** User tapped a different pill tab. */
    data class TabSelected(
        val tab: ProfileTab,
    ) : ProfileEvent

    /**
     * User tapped a `PostCard` body in the Posts or Replies tab (the
     * outer card tap, not an inner image). Routes to PostDetail.
     */
    data class PostTapped(
        val postUri: String,
    ) : ProfileEvent

    /**
     * User tapped a specific image inside a `PostCard`'s image embed
     * in the Posts or Replies tab. Routes directly to the fullscreen
     * MediaViewer with [imageIndex] as the carousel's starting page —
     * skipping the PostDetail detour the outer-card tap takes.
     */
    data class OnImageTapped(
        val post: PostUi,
        val imageIndex: Int,
    ) : ProfileEvent

    /**
     * User tapped the inner quoted-post region of a PostCard's record
     * embed in the Posts or Replies tab. Routes to PostDetail for the
     * quoted post — distinct from [PostTapped] which routes to the
     * outer (parent) post.
     */
    data class OnQuotedPostTapped(
        val quotedPostUri: String,
    ) : ProfileEvent

    /**
     * User tapped a media-grid cell in the Media tab. Image cells open
     * the MediaViewer at index 0; video cells (marked by [isVideo])
     * open the fullscreen video player instead, since the MediaViewer
     * can't render a video embed and would dead-end on "post has no
     * images".
     */
    data class OnMediaCellTapped(
        val postUri: String,
        val isVideo: Boolean = false,
    ) : ProfileEvent

    /** User tapped an author handle inside one of the rendered posts. */
    data class HandleTapped(
        val handle: String,
    ) : ProfileEvent

    /** User pulled to refresh. Refreshes the active tab and the header. */
    data object Refresh : ProfileEvent

    /** Scroll reached the prefetch threshold for the currently-active tab. */
    data class LoadMore(
        val tab: ProfileTab,
    ) : ProfileEvent

    /** User tapped an inline Retry affordance in a tab's InitialError state. */
    data class RetryTab(
        val tab: ProfileTab,
    ) : ProfileEvent

    /**
     * User tapped the Follow / Unfollow action. Drives an optimistic
     * [ViewerRelationship.Following] / [ViewerRelationship.NotFollowing]
     * flip, then issues `app.bsky.graph.follow` create or delete.
     */
    data object FollowTapped : ProfileEvent

    /** User tapped the Edit action — stubbed in this epic. */
    data object EditTapped : ProfileEvent

    /** User tapped the Message action — stubbed in this epic. */
    data object MessageTapped : ProfileEvent

    /**
     * User tapped one of the stubbed overflow entries (Block / Mute / Report)
     * on the other-user actions row. Consolidates the three into a single
     * parameterized event — mirrors the [ProfileEffect.ShowComingSoon] shape
     * on the effect side, keeping the VM dispatch a one-liner.
     */
    data class StubActionTapped(
        val action: StubbedAction,
    ) : ProfileEvent

    /** User tapped an overflow-menu entry that pushes the Settings sub-route. */
    data object SettingsTapped : ProfileEvent

    /** User tapped the like icon on a PostCard rendered inside one of the tab bodies. */
    data class OnLikeClicked(
        val post: PostUi,
    ) : ProfileEvent

    /** User tapped the repost icon on a PostCard rendered inside one of the tab bodies. */
    data class OnRepostClicked(
        val post: PostUi,
    ) : ProfileEvent
}

/**
 * MVI one-shot effects routed through the screen's effect collector.
 *
 * Navigation effects are pushed onto `LocalMainShellNavState.current`
 * by the screen Composable — the ViewModel never sees the nav state
 * holder. Same pattern as `:feature:postdetail:impl`.
 */
sealed interface ProfileEffect : UiEffect {
    /**
     * Header load failed; surface a transient Snackbar. The screen
     * composable maps [error] to a string resource for the SnackbarHost
     * — same pattern as [PostDetail] (typed error in the effect,
     * stringResource resolved at render time).
     */
    data class ShowError(
        val error: ProfileError,
    ) : ProfileEffect

    /**
     * One of the stubbed write actions was tapped; surface the
     * "Coming soon" Snackbar for that action. The screen maps the
     * action to its own string resource so the copy can differ per
     * action (Follow vs Edit vs Message) without touching the
     * ViewModel when copy changes.
     */
    data class ShowComingSoon(
        val action: StubbedAction,
    ) : ProfileEffect

    /** Push the post-detail route onto the active tab's back stack. */
    data class NavigateToPost(
        val postUri: String,
    ) : ProfileEffect

    /**
     * Push the fullscreen MediaViewer route onto the active tab's back
     * stack — skipping the PostDetail screen the outer-card tap routes
     * to. [imageIndex] is the zero-based start page for the carousel.
     */
    data class NavigateToMediaViewer(
        val postUri: String,
        val imageIndex: Int,
    ) : ProfileEffect

    /**
     * Push the fullscreen video player route — fired when a video
     * media-grid cell is tapped. Same instance-transfer contract as
     * the feed → fullscreen path: SharedVideoPlayer is process-scoped
     * so no playback restart at the transition.
     */
    data class NavigateToVideoPlayer(
        val postUri: String,
    ) : ProfileEffect

    /** Push another user's profile onto the active tab's back stack. Self-tap is consumed silently in the VM. */
    data class NavigateToProfile(
        val handle: String,
    ) : ProfileEffect

    /** Push the Settings sub-route onto the active tab's back stack. */
    data object NavigateToSettings : ProfileEffect

    /**
     * Switch to the Chats top-level tab and push the per-conversation
     * thread for [otherUserDid] onto that tab's back stack. Emitted on
     * [ProfileEvent.MessageTapped] for an other-user profile whose
     * header has finished loading (the header carries the DID — the
     * Chats thread resolves DID→convoId itself via
     * `chat.bsky.convo.getConvoForMembers`).
     */
    data class NavigateToMessage(
        val otherUserDid: String,
    ) : ProfileEffect
}

/**
 * Which stubbed write action triggered a "Coming soon" snackbar.
 * Lets the screen pick per-action copy (`Edit profile coming soon` vs
 * `Block — coming soon`) without coupling the VM to UI strings.
 *
 * Bead F adds Block / Mute / Report to cover the other-user overflow-menu
 * stubs. The real moderation writes ship under follow-up bd 7.7. The
 * Follow stub was removed in nubecita-39l once `app.bsky.graph.follow`
 * writes landed; `Message` was removed in nubecita-a7a once real
 * cross-tab routing into the Chats thread landed.
 */
enum class StubbedAction { Edit, Block, Mute, Report }
