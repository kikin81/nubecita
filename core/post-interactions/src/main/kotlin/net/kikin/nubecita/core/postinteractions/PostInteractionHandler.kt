package net.kikin.nubecita.core.postinteractions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.postinteractions.sharing.PostShareIntent
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

/**
 * Tracks the most-recently-tapped post URI for like and repost, so the
 * screen can animate the count's ±1 transition without re-running the
 * cache merge. Sticky until the next tap or screen relaunch.
 */
data class PostTapMarkers(
    val lastLikeTapPostUri: String? = null,
    val lastRepostTapPostUri: String? = null,
)

/**
 * Bucketed error category for post-interaction failures. Maps the raw
 * [Throwable] at the repository boundary to a UI-resolvable variant so
 * the consuming ViewModel stays Android-resource-free and the screen can
 * render localized copy.
 */
enum class InteractionError {
    /** Network / socket / transport failure. */
    Network,

    /**
     * No authenticated session — access token refresh failed or the user
     * signed out on another device.
     */
    Unauthenticated,

    /** Anything else (server 5xx, decode failure, unexpected throw). */
    Unknown,
}

/**
 * One-shot effects emitted by [PostInteractionHandler]. Consumed by the
 * host screen/ViewModel exactly once via a single collector.
 *
 * Navigation effects carry *data* only — no `:feature:*:api` NavKey
 * types. The consuming ViewModel constructs the appropriate NavKey from
 * the payload, keeping `:core:post-interactions` feature-module-free.
 */
sealed interface InteractionEffect {
    /**
     * A post-interaction network call failed. Surface as a non-sticky
     * snackbar; the optimistic flip in [PostInteractionsCache] has already
     * rolled back.
     */
    data class ShowError(
        val error: InteractionError,
    ) : InteractionEffect

    /**
     * Hand a pre-computed share payload to the screen so it can fire
     * `Intent.ACTION_SEND` via the system share sheet.
     */
    data class SharePost(
        val intent: PostShareIntent,
    ) : InteractionEffect

    /**
     * Copy a post permalink to the system clipboard. Fired on long-press of
     * the share button.
     */
    data class CopyPermalink(
        val permalink: String,
    ) : InteractionEffect

    /**
     * Open the composer pre-filled for a reply (when [replyToUri] is
     * non-null) or a quote-post (when [quoteUri] is non-null). Exactly one
     * of the two is non-null per emission.
     */
    data class NavigateToComposer(
        val replyToUri: String?,
        val quoteUri: String?,
    ) : InteractionEffect

    /**
     * Navigate to the Report sub-route for [post]. The consuming screen
     * constructs `Report.forPost(post)` (`:feature:moderation:api`) and
     * pushes it onto `MainShell`'s inner back stack.
     */
    data class NavigateToReport(
        val post: PostUi,
    ) : InteractionEffect

    /**
     * Navigate to the Block sub-route for the author identified by [did]
     * and [handle]. The consuming screen constructs
     * `Block.forAccount(did, handle)` and pushes it onto the inner stack.
     */
    data class NavigateToBlock(
        val did: String,
        val handle: String,
    ) : InteractionEffect

    /**
     * Surface a "coming soon" snackbar for an overflow-menu action that is
     * not yet implemented. Transient — lives on the effect surface, not
     * sticky state.
     */
    data class ShowComingSoon(
        val action: PostOverflowAction,
    ) : InteractionEffect
}

/**
 * Delegate that handles all post-interaction logic shared across multiple
 * feed-like surfaces (Following feed, Profile timeline, Search results,
 * PostDetail thread, etc.).
 *
 * **Lifecycle:** inject one instance per ViewModel (unscoped in Hilt),
 * then call [bind] once from the VM's `init` block. The handler uses the
 * supplied [CoroutineScope] to launch network calls; the VM's
 * `viewModelScope` is the natural choice.
 *
 * **Per-URI double-tap guard:** a second [onLike] / [onRepost] for the
 * same post URI while one is already in flight is silently ignored — no
 * duplicate analytics, no redundant network call.
 *
 * **Effect delivery:** [interactionEffects] is a hot, buffered stream.
 * The consuming VM MUST collect it from exactly one place, forwarding or
 * mapping each [InteractionEffect] into its own `UiEffect` channel.
 *
 * **Analytics:** fired optimistically at the call site (before the
 * network resolves) for like / repost / share, matching the existing
 * `FeedViewModel` pattern. The per-URI guard ensures analytics fire at
 * most once per user tap.
 */
interface PostInteractionHandler {
    /**
     * Bind this handler to a surface and coroutine scope. MUST be called
     * once from the owning VM's `init` block before any interaction
     * method is invoked. Calling [bind] more than once overwrites the
     * previous binding.
     */
    fun bind(
        surface: PostSurface,
        scope: CoroutineScope,
    )

    /**
     * The most-recently-tapped post URIs for like / repost, updated
     * synchronously on every tap (before the network call resolves).
     */
    val tapMarkers: StateFlow<PostTapMarkers>

    /**
     * One-shot effects stream. Each element is delivered exactly once to
     * the first active collector. The consuming VM should forward these
     * into its own `UiEffect` channel.
     */
    val interactionEffects: Flow<InteractionEffect>

    /** Toggle like on [post]. Ignored if a like/unlike for this URI is in flight. */
    fun onLike(post: PostUi)

    /** Toggle repost on [post]. Ignored if a repost/unrepost for this URI is in flight. */
    fun onRepost(post: PostUi)

    /** Open the composer pre-filled as a reply to [post]. */
    fun onReply(post: PostUi)

    /** Open the composer pre-filled as a quote of [post]. */
    fun onQuote(post: PostUi)

    /** Share [post] via the system share sheet. */
    fun onShare(post: PostUi)

    /** Copy [post]'s permalink to the clipboard (long-press on share). */
    fun onShareLongPress(post: PostUi)

    /**
     * Handle a PostCard overflow-menu selection. Routes each
     * [PostOverflowAction] variant to the appropriate network call or
     * navigation effect.
     */
    fun onOverflowAction(
        post: PostUi,
        action: PostOverflowAction,
    )
}
