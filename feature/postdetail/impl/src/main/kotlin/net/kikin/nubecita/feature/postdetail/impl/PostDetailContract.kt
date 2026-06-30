package net.kikin.nubecita.feature.postdetail.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * One frame's worth of UI state for the post-detail screen.
 *
 * `items` is a flat field per the MVI convention's "independent flags
 * stay flat" rule — once a thread loads, refresh lifecycles toggle
 * `loadStatus` while the items list stays put for the user to keep
 * reading. `loadStatus` is a sealed sum (per the "mutually-exclusive
 * view modes" carve-out in CLAUDE.md) so the type system makes invalid
 * combinations like `InitialLoading && Refreshing` unrepresentable.
 *
 * Each [ThreadItem] is one row in the flattened thread (root-most
 * ancestor → focus → replies depth-first); the screen's LazyColumn
 * keys on `ThreadItem.key`.
 */
@Immutable
internal data class PostDetailState(
    val items: ImmutableList<ThreadItem> = persistentListOf(),
    val loadStatus: PostDetailLoadStatus = PostDetailLoadStatus.Idle,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastLikeTapPostUri: String? = null,
    /** Same-shape user-delta flag as FeedState. See FeedState KDoc. */
    val lastRepostTapPostUri: String? = null,
) : UiState

/**
 * The focused post once the thread has loaded, or `null` before a focus row
 * resolves (the default `Idle` state, `InitialLoading`, or `InitialError`).
 */
internal val PostDetailState.focusPost: PostUi?
    get() = items.firstNotNullOfOrNull { it as? ThreadItem.Focus }?.post

/**
 * Whether the floating reply FAB should show. `true` only once a [focusPost] has
 * resolved AND the appview permits this viewer to reply to it
 * ([net.kikin.nubecita.data.models.ViewerStateUi.canViewerReply] — the inverse of
 * the post's threadgate-derived `replyDisabled`). A reply-gated thread hides the
 * FAB entirely rather than letting the user tap into a composer that would reject
 * the reply. `false` while no focus is loaded — the same pre-load gate the FAB had
 * before (it never showed against zero items), now also closed for reply-gated
 * focuses.
 */
internal val PostDetailState.showReplyFab: Boolean
    get() = focusPost?.viewer?.canViewerReply == true

/**
 * Mutually-exclusive load lifecycle for the post-detail screen.
 * Mirrors `:feature:feed:impl`'s `FeedLoadStatus` shape but without
 * `Appending` — getPostThread returns the entire visible thread in
 * one response (the lexicon's `parentHeight` / `depth` parameters
 * cap the tree on the server side, no client-side pagination).
 */
internal sealed interface PostDetailLoadStatus {
    /** No load is in flight. */
    @Immutable
    data object Idle : PostDetailLoadStatus

    /** First load (the screen has no items yet). */
    @Immutable
    data object InitialLoading : PostDetailLoadStatus

    /** Pull-to-refresh in progress; existing items are still rendered. */
    @Immutable
    data object Refreshing : PostDetailLoadStatus

    /**
     * Initial load failed. Sticky — the screen renders a full-screen
     * retry layout against this state. Refresh failures preserve the
     * existing items and emit [PostDetailEffect.ShowError] instead of
     * flipping the load status.
     */
    @Immutable
    data class InitialError(
        val error: PostDetailError,
    ) : PostDetailLoadStatus
}

/**
 * UI-resolvable error categories surfaced by the post-detail screen.
 * The screen maps each variant to a `stringResource` call when
 * rendering — the VM stays Android-resource-free.
 */
internal sealed interface PostDetailError {
    /** Underlying network or transport failure. */
    @Immutable
    data object Network : PostDetailError

    /**
     * No authenticated session. The screen routes to login (same
     * recovery as the feed surface).
     */
    @Immutable
    data object Unauthenticated : PostDetailError

    /**
     * Post is gone — either the lexicon's declared `NotFound` XRPC
     * error (404 from the server), OR the response surfaced
     * `app.bsky.feed.defs#notFoundPost` at the focus position. The
     * screen renders a "Post not found" empty state.
     */
    @Immutable
    data object NotFound : PostDetailError

    /** Anything else (5xx, decode failure, unexpected throwable). */
    @Immutable
    data class Unknown(
        val cause: String?,
    ) : PostDetailError
}

internal sealed interface PostDetailEvent : UiEvent {
    /** First load on screen entry. Idempotent — repeated `Load` while loading is a no-op. */
    data object Load : PostDetailEvent

    /** Pull-to-refresh; re-fetches the thread for the same focus URI. */
    data object Refresh : PostDetailEvent

    /** Re-run the initial load after a [PostDetailLoadStatus.InitialError]. */
    data object Retry : PostDetailEvent

    /**
     * Tap on an ancestor, focus, or reply post body — pushes another
     * post-detail screen onto the stack with that post as the new
     * focus. Tapping the focused post re-enters the same screen
     * (harmless; the state holder picks up at the same scroll position).
     */
    data class OnPostTapped(
        val postUri: String,
    ) : PostDetailEvent

    data class OnAuthorTapped(
        val authorDid: String,
    ) : PostDetailEvent

    /**
     * Tap on the floating reply composer FAB. Resolves the focus URI
     * out of state at the VM and emits [PostDetailEffect.NavigateToComposer]
     * which the screen routes to a `ComposerRoute` push. Per-post
     * reply taps on a PostCard's action row don't pass through the VM
     * at all — the screen invokes `onReplyClick` directly, same shape
     * as `FeedScreen`. Idempotent against the load lifecycle: if the
     * VM hasn't resolved a focus yet (still in `InitialLoading` /
     * `InitialError`), the event is dropped silently.
     */
    data object OnReplyClicked : PostDetailEvent

    /**
     * Tap on a focus-post image. The screen routes this to the
     * fullscreen media viewer via [PostDetailEffect.NavigateToMediaViewer].
     */
    data class OnFocusImageClicked(
        val imageIndex: Int,
    ) : PostDetailEvent

    /**
     * Tap on the inner quoted-post region of any rendered PostCard
     * (ancestor, focus, or reply). Pushes another post-detail screen
     * with the quoted post as the new focus — same destination as
     * [OnPostTapped] but distinct on the event surface so analytics
     * and tests can tell the inner click target from the outer one.
     */
    data class OnQuotedPostTapped(
        val quotedPostUri: String,
    ) : PostDetailEvent

    /**
     * Tap on a video embed rendered inside a thread PostCard (ancestor,
     * focus, or reply). Routes to the fullscreen video player on the
     * outer NavDisplay (escaping MainShell chrome), matching the feed
     * → fullscreen entry path. [postUri] is the URI of the post whose
     * embed carries the video — or the quoted post's URI when the tap
     * lands inside a quoted-record video — so the player's resolver
     * fetches the right `app.bsky.embed.video#view`.
     */
    data class OnVideoTapped(
        val postUri: String,
    ) : PostDetailEvent

    /**
     * User selected an overflow-menu entry on a PostCard inside the
     * post-detail thread (focus, ancestor, or reply). Routed through the
     * VM's [PostDetailViewModel.onOverflowAction] override which handles
     * MuteAuthor / UnmuteAuthor locally (optimistic flip + rollback) and
     * delegates all other actions to the injected [PostInteractionHandler].
     */
    data class OnOverflowAction(
        val post: PostUi,
        val action: PostOverflowAction,
    ) : PostDetailEvent
}

internal sealed interface PostDetailEffect : UiEffect {
    /** Surface-able, non-sticky error (snackbar). */
    @Immutable
    data class ShowError(
        val error: PostDetailError,
    ) : PostDetailEffect

    /** Push another post-detail screen for the tapped post URI. */
    @Immutable
    data class NavigateToPost(
        val postUri: String,
    ) : PostDetailEffect

    /** Push the author's profile screen. */
    @Immutable
    data class NavigateToAuthor(
        val authorDid: String,
    ) : PostDetailEffect

    /**
     * Open the reply composer keyed to the given parent post URI. The
     * screen's `onReplyClick` pushes a `ComposerRoute(replyToUri)`; its
     * entry is tagged `adaptiveDialog()`, so it's full-screen at Compact
     * width and a centered Dialog at Medium / Expanded.
     */
    @Immutable
    data class NavigateToComposer(
        val parentPostUri: String,
    ) : PostDetailEffect

    /**
     * Push the fullscreen media viewer for the given focus post +
     * image index. The screen forwards this to the outer
     * `LocalAppNavigator` (wired in `PostDetailNavigationModule`) so
     * the viewer escapes `MainShell`'s NavigationSuiteScaffold chrome.
     * String-typed `postUri` matches the rest of the effect surface
     * (`NavigateToPost`, `NavigateToAuthor`); `AtUri(...)` construction
     * is deferred to the XRPC boundary if downstream code needs it.
     */
    @Immutable
    data class NavigateToMediaViewer(
        val postUri: String,
        val imageIndex: Int,
    ) : PostDetailEffect

    /**
     * Push the fullscreen video player route for the given post. Same
     * instance-transfer contract as the feed → fullscreen path —
     * `SharedVideoPlayer` is process-scoped, so a transition from this
     * screen never restarts a video already buffered elsewhere. The
     * route is `@OuterShell`-qualified in `VideoPlayerNavigationModule`,
     * so the entry composable routes this via the outer Navigator
     * rather than `LocalMainShellNavState`.
     */
    @Immutable
    data class NavigateToVideoPlayer(
        val postUri: String,
    ) : PostDetailEffect

    /**
     * Push a sub-route NavKey onto `MainShell`'s inner back stack via
     * the screen's `onNavigateTo` callback (which the host wires to
     * `LocalMainShellNavState.current.add(key)`). The VM never reads
     * the navigation state holder — that lives in `CompositionLocal`,
     * which is unreachable from a ViewModel. Report / Block now travel
     * via [net.kikin.nubecita.core.postinteractions.InteractionEffect]
     * emitted on [PostInteractionHandler.interactionEffects] and consumed
     * by [net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions];
     * this effect remains for any future screen-specific sub-route pushes.
     */
    @Immutable
    data class NavigateTo(
        val key: NavKey,
    ) : PostDetailEffect
}
