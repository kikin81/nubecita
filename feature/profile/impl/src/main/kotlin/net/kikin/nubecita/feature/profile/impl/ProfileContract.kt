package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import kotlin.time.Instant

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
    /**
     * Whether the signed-in device holds the Nubecita Pro entitlement,
     * mirrored from `EntitlementRepository.isPro`. Independent of which
     * profile is being viewed — it tracks the *viewer's* Pro status, so
     * it lives as its own flat flag rather than being folded into the
     * actor-keyed header. The badge-visibility decision combines it with
     * [ownProfile]; see [showSupporterBadge].
     */
    val isProSupporter: Boolean = false,
    val viewerRelationship: ViewerRelationship = ViewerRelationship.None,
    val selectedTab: ProfileTab = ProfileTab.Posts,
    val postsStatus: TabLoadStatus = TabLoadStatus.Idle,
    val repliesStatus: TabLoadStatus = TabLoadStatus.Idle,
    val mediaStatus: TabLoadStatus = TabLoadStatus.Idle,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastLikeTapPostUri: String? = null,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastRepostTapPostUri: String? = null,
    /**
     * Verification explanation sheet sub-state. Independent flat flags (the
     * sheet can be visible while its verifier list is still loading, and an
     * error can coexist with an empty list). The verifier list is resolved
     * lazily on first open and then cached for the screen's lifetime.
     *
     * [resolvedVerifierRefs] is the cache key: the exact [VerifierRef] list the
     * current [verifiers] were resolved from. `null` before anything is resolved;
     * an **empty** list is a legitimate resolved state (the header carries no
     * verifier refs, so [verifiers] is correctly empty). A tap re-resolves only
     * when it differs from the header's current `verifierRefs`, so a header
     * refresh that changes the refs invalidates the cache while an unchanged one
     * (including an all-DIDs-unresolvable resolve that left [verifiers] empty) is
     * served without re-fetching.
     */
    val verificationSheetVisible: Boolean = false,
    val verifiersLoading: Boolean = false,
    val verifiers: ImmutableList<VerifierUi> = persistentListOf(),
    val resolvedVerifierRefs: ImmutableList<VerifierRef>? = null,
    val verifiersError: Boolean = false,
) : UiState {
    /**
     * Whether to render the Pro "Supporter" badge on the hero. True only
     * when the viewer is Pro AND viewing their OWN profile — Tier 1 is
     * self-visible only (no backend, no public/networked badge yet), so a
     * non-Pro user, and anyone viewing someone else's profile, sees
     * nothing. Derived (not stored) so the gate has one definition the
     * hero reads directly; the four-way `isPro × own/other` truth table is
     * pinned in `ProfileViewModelTest`.
     */
    val showSupporterBadge: Boolean get() = ownProfile && isProSupporter
}

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
 * Moderation-relevant viewer state for the rendered profile, sourced
 * from `app.bsky.actor.defs#viewerState`. Independent of
 * [ViewerRelationship] — the follow graph and the mute / block graph
 * are orthogonal (a viewer can mute someone they follow, or block
 * someone unrelated to their follow graph), so the two are exposed
 * as sibling fields on [ProfileHeaderUi] rather than nested.
 *
 * Defaults are the all-`false` / null "no moderation in play" baseline
 * so existing test fixtures and synthetic header instances keep
 * compiling and rendering unchanged.
 *
 * `@Immutable` because every field is a stable primitive (Boolean /
 * nullable String). Compose treats this as a fully stable input.
 *
 * - [isMutedByViewer]: viewer has muted this profile's author.
 *   Sourced from `viewer.muted == true`.
 * - [blockUri]: AT URI of the viewer's own `app.bsky.graph.block` record
 *   pointing at this profile. Non-null iff the viewer is currently
 *   blocking. Captured as a String (the runtime `AtUri.raw`) so the
 *   UI layer stays atproto-runtime-light, matching the [PostUi]
 *   layer's `likeUri`/`repostUri` shape.
 * - [isBlockingViewer]: this profile's author is blocking the viewer.
 *   Sourced from `viewer.blockedBy == true`. Independent of
 *   [blockUri] — the two directions can each hold independently.
 */
@androidx.compose.runtime.Immutable
data class ViewerModerationState(
    val isMutedByViewer: Boolean = false,
    val blockUri: String? = null,
    val isBlockingViewer: Boolean = false,
)

/**
 * Header-row UI model. Derived from `app.bsky.actor.defs#profileViewDetailed`
 * via [net.kikin.nubecita.feature.profile.impl.data.AuthorProfileMapper].
 *
 * `banner` is the nullable banner URL **as a String** (the atproto
 * runtime's `Uri.raw` value).
 */
data class ProfileHeaderUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bannerUrl: String?,
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
     * Defaults to `true` for callers that don't construct the field
     * (older test fixtures, future synthetic header instances); the
     * mapper always computes the real value via `canViewerMessage`, so
     * this default only affects fixtures. Note the computed value is NOT
     * fail-open: an absent `associated.chat` is treated as `"following"`
     * (Bluesky's default), so it's gated on `viewer.followedBy`.
     */
    val canMessage: Boolean = true,
    /**
     * Mute / block-direction flags for the viewer's relationship to
     * this profile. Sourced from `app.bsky.actor.defs#viewerState`
     * via [net.kikin.nubecita.feature.profile.impl.data.AuthorProfileMapper].
     * Defaults to the all-false / null baseline so existing test
     * fixtures keep compiling.
     */
    val viewerModeration: ViewerModerationState = ViewerModerationState(),
    /**
     * Verification tier derived from the wire `verificationState`. Drives the
     * (tappable) badge in the profile hero. `VerifiedBadge.None` (default)
     * renders nothing. See `:data:models`'s `toVerifiedBadge`.
     */
    val verifiedBadge: VerifiedBadge = VerifiedBadge.None,
    /**
     * Unresolved references to the accounts that issued this profile's valid
     * verifications — issuer DID + date only. The explanation sheet resolves
     * these DIDs to names lazily on open (via `getProfiles`); until then only
     * the count/dates are known. Empty when the profile is unverified.
     */
    val verifierRefs: ImmutableList<VerifierRef> = persistentListOf(),
)

/**
 * An unresolved verifier: the issuer DID of a valid verification plus the date
 * it was issued. The VM resolves the DID to a [VerifierUi] (with handle /
 * display name) lazily when the verification sheet opens; carrying only
 * (DID, date) here keeps profile load free of the extra `getProfiles` round-trip.
 */
@Immutable
data class VerifierRef(
    val did: String,
    val verifiedAt: Instant,
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
    /**
     * The AT URI of the underlying post. Used for navigation, the
     * post-interaction cache, and the media viewer. NOT unique across
     * feed entries: an author's original post and their own repost of it
     * share one [postUri]. Use [key] for the `LazyColumn` slot key.
     */
    val postUri: String

    /**
     * Unique per *feed entry* — the `LazyColumn` slot key.
     *
     * `getAuthorFeed` can return the same post twice (the original plus
     * the author's own repost of it), and Compose throws
     * `IllegalArgumentException: Key … was already used` on duplicate slot
     * keys — crashing the profile mid-scroll. The repost entry folds the
     * reposter DID into the key, so both entries get distinct slots and
     * render side by side (matching bsky.app) instead of one being
     * dropped.
     */
    val key: String

    /** Standalone post or reply — rendered by `:designsystem.PostCard`. */
    data class Post(
        val post: PostUi,
        /** DID of the reposter when this entry is a repost (`reason is ReasonRepost`); null for an original post. */
        val reposterDid: String? = null,
    ) : TabItemUi {
        override val postUri: String get() = post.id
        override val key: String get() = reposterDid?.let { "repost:$it:${post.id}" } ?: post.id
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
        /** DID of the reposter when this entry is a repost (`reason is ReasonRepost`); null for an original post. */
        val reposterDid: String? = null,
    ) : TabItemUi {
        override val key: String get() = reposterDid?.let { "repost:$it:$postUri" } ?: postUri
    }
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
     * User tapped an image of a quoted post embedded in a PostCard in the
     * Posts or Replies tab. Routes to the MediaViewer for the QUOTED post
     * — distinct from [OnQuotedPostTapped], which opens the quoted post's
     * detail. [quotedPostUri] is the quoted post's uri (from the embed).
     */
    data class OnQuotedImageTapped(
        val quotedPostUri: String,
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

    /**
     * User tapped a video embed rendered inside a `PostCard` in the
     * Posts or Replies tab (a `VideoPosterEmbed`'s tap surface). Routes
     * to the fullscreen player, bypassing the PostDetail detour that
     * an outer-card tap takes. [postUri] is the URI of the parent post
     * whose embed carries the video — also the URI of the quoted post
     * when the video was tapped inside a quoted-record embed, so the
     * resolver fetches the right `app.bsky.embed.video` view either
     * way.
     */
    data class OnVideoTapped(
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
     * User tapped one of the still-stubbed overflow entries (Block)
     * on the other-user actions row. Mirrors the
     * [ProfileEffect.ShowComingSoon] shape on the effect side — VM
     * dispatch stays a one-liner. The Report row graduated out of this
     * event in `oftc.3` and now flows through
     * [OnReportAccountRequested]. The Mute row graduated in `oftc.5`
     * and now flows through [HeroMuteTapped].
     */
    data class StubActionTapped(
        val action: StubbedAction,
    ) : ProfileEvent

    /**
     * User tapped the Mute / Unmute entry in the other-user hero
     * overflow menu. The VM reads
     * [ProfileHeaderUi.viewerModeration.isMutedByViewer] to determine
     * the current mute state and optimistically flips it before issuing
     * the network call.
     */
    data object HeroMuteTapped : ProfileEvent

    /**
     * User tapped the "Report account" overflow entry on the other-user
     * actions row. The VM reduces this to a
     * [ProfileEffect.NavigateTo] carrying a
     * `Report(subject = ReportSubject.Account(did))` NavKey — the
     * screen's effect collector pushes the key onto MainShell's inner
     * back stack, where the `:feature:moderation:impl` `@MainShell`
     * entry provider resolves it to a Modal Bottom Sheet hosting the
     * report dialog.
     */
    data object OnReportAccountRequested : ProfileEvent

    /** User tapped an overflow-menu entry that pushes the Settings sub-route. */
    data object SettingsTapped : ProfileEvent

    /**
     * User tapped the verification badge in the hero. Opens the explanation
     * sheet and triggers a one-time lazy resolution of the verifier DIDs →
     * names (via `getProfiles`). Re-tapping after a successful load re-opens
     * the sheet without re-fetching.
     */
    data object VerificationBadgeTapped : ProfileEvent

    /** User dismissed the verification explanation sheet. */
    data object VerificationSheetDismissed : ProfileEvent

    /**
     * User selected an overflow-menu entry on a PostCard inside one of
     * the tab bodies (Posts / Replies / Media). Distinct from the
     * profile-level overflow which is handled by [StubActionTapped]; this
     * event is per-post moderation while [StubActionTapped] is per-author.
     */
    data class OnPostOverflowAction(
        val post: PostUi,
        val action: PostOverflowAction,
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

    /**
     * Push a sub-route NavKey onto `MainShell`'s inner back stack via
     * the screen's `onNavigateTo` callback (which the host wires to
     * `LocalMainShellNavState.current.add(key)`). The VM never reads
     * the navigation state holder — that lives in `CompositionLocal`,
     * which is unreachable from a ViewModel. Currently emitted only
     * for the Report dialog sub-route (`Report` NavKey from
     * `:feature:moderation:api`); future moderation children
     * (Block / Mute confirmation sub-routes) travel the same effect.
     */
    data class NavigateTo(
        val key: NavKey,
    ) : ProfileEffect
}

/**
 * Which stubbed write action triggered a "Coming soon" snackbar.
 * Lets the screen pick per-action copy (`Edit profile coming soon` vs
 * `Block — coming soon`) without coupling the VM to UI strings.
 *
 * `Report` graduated out of this enum in `oftc.3` once the report
 * dialog landed — Report taps now flow through
 * [ProfileEvent.OnReportAccountRequested] → [ProfileEffect.NavigateTo]
 * with a `Report(...)` NavKey from `:feature:moderation:api`.
 * `Mute` graduated in `oftc.5` — hero mute taps now flow through
 * [ProfileEvent.HeroMuteTapped]. `Block` remains stubbed until its own
 * moderation-epic child lands (`oftc.4`); `Edit` ships in a separate
 * profile-edit bd issue.
 */
enum class StubbedAction { Edit, Block }
