package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi

/**
 * Resolves a reply target's full thread context — both the
 * immediate parent (always the target itself) and the thread root
 * (the originating post, which equals the parent when the target is
 * itself a thread root, else inherited from `target.record.reply.root`).
 *
 * Returns the resolved [ParentPostUi] inside `Result.success`, or
 * a `Result.failure` carrying a [net.kikin.nubecita.core.posting.ComposerError]-typed
 * throwable on failure (network, blocked, NotFoundPost,
 * BlockedPost, etc.). Consumers map `result.exceptionOrNull() as?
 * ComposerError` exhaustively.
 *
 * Lives in `:feature:composer:impl` rather than `:core:posting`
 * because the call is composer-specific (the post-detail feature
 * has its own thread fetcher with different semantics — full
 * threads, not just root+parent refs).
 *
 * **wtq.3 ships only the interface plus a stub implementation; the
 * real `app.bsky.feed.getPostThread`-backed implementation lands in
 * wtq.6 alongside the parent-card UI.** The stub returns a
 * never-completing call so the VM's reply-mode tests can stub it
 * out; production code injects the real impl via Hilt once it
 * lands.
 */
interface ParentFetchSource {
    /**
     * @param uri The AT URI of the post being replied to (passed
     *   through from `ComposerRoute.replyToUri` after lifting from
     *   String to [AtUri]).
     */
    suspend fun fetchParent(uri: AtUri): Result<ParentPostUi>
}
