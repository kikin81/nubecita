package net.kikin.nubecita.feature.profile.impl

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
 * `viewerRelationship == NotFollowing` AND `state.ownProfile == true`
 * is invalid; that combination MUST NOT be representable in any
 * reducer-emitted state.
 */
enum class ViewerRelationship { None, Self, Following, NotFollowing }

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
     * image in the post's embed (or the video thumbnail for video
     * posts).
     */
    data class MediaCell(
        override val postUri: String,
        val thumbUrl: String?,
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

    /** User tapped a `PostCard` or media grid cell. */
    data class PostTapped(
        val postUri: String,
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

    /** User tapped the Follow / Unfollow action — stubbed in this epic. */
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

    /** Push another user's profile onto the active tab's back stack. Self-tap is consumed silently in the VM. */
    data class NavigateToProfile(
        val handle: String,
    ) : ProfileEffect

    /** Push the Settings sub-route onto the active tab's back stack. */
    data object NavigateToSettings : ProfileEffect
}

/**
 * Which stubbed write action triggered a "Coming soon" snackbar.
 * Lets the screen pick per-action copy (`Follow coming soon` vs
 * `Edit profile coming soon`) without coupling the VM to UI strings.
 *
 * Bead F adds Block / Mute / Report to cover the other-user overflow-menu
 * stubs. The real moderation writes ship under follow-up bd 7.7.
 */
enum class StubbedAction { Follow, Edit, Message, Block, Mute, Report }
