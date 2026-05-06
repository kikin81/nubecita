package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadRequest
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPost
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.decodeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Production [ParentFetchSource] backed by the atproto-kotlin SDK.
 *
 * Issues a single `app.bsky.feed.getPostThread` call with `depth = 0`
 * and `parentHeight = 0` — we don't need any replies (composer is
 * authoring a NEW reply) and we don't need to walk the ancestor
 * chain. Instead, the root ref is derived directly from the target
 * post's serialized [Post] record:
 *
 * - If `record.reply == AtField.Missing` → target IS the thread
 *   root, so `rootRef = parentRef = StrongRef(target.uri, target.cid)`.
 * - If `record.reply == AtField.Defined(replyRef)` → target is
 *   itself a reply, so `rootRef = replyRef.root` (already a
 *   `StrongRef`, no further fetching needed).
 *
 * This approach matches what the official Bluesky web client does:
 * the root ref a reply needs is *recoverable from the target post's
 * own record without traversal*, because every reply post in the
 * lexicon must already carry the root ref. Skipping the parent-chain
 * walk keeps the call cheap (zero ancestors in the response payload)
 * and avoids the "how many parents do I need to ask for" guessing
 * game.
 *
 * Failure mapping:
 *
 * - [NotFoundPost] / [BlockedPost] → [ComposerError.ParentNotFound]
 *   (UI shows "this post is no longer available").
 * - [IOException] → [ComposerError.Network] (UI shows "no
 *   connection").
 * - [NoSessionException] → [ComposerError.Unauthorized] (UI routes
 *   to sign-in, mirrors `DefaultPostingRepository`'s mapping).
 * - Anything else (open-union `Unknown` thread variant, decoder
 *   failure, server 500, etc.) → [ComposerError.RecordCreationFailed]
 *   wrapping the cause. The `RecordCreationFailed` variant is
 *   admittedly named for the submit path; the composer screen maps
 *   it to a generic error string here.
 *
 * Cancellation propagates unchanged — the composer's reply-mode
 * fetch runs inside `viewModelScope` and a back-out before completion
 * must tear the call down cleanly.
 */
internal class DefaultParentFetchSource
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ParentFetchSource {
        override suspend fun fetchParent(uri: AtUri): Result<ParentPostUi> =
            withContext(dispatcher) {
                Timber.tag(TAG).d(
                    "fetchParent() — rkey=%s",
                    uri.raw.substringAfterLast('/'),
                )
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPostThread(
                            GetPostThreadRequest(
                                uri = uri,
                                depth = 0L,
                                parentHeight = 0L,
                            ),
                        )

                    when (val thread = response.thread) {
                        is ThreadViewPost -> {
                            // Decode the target's record exactly once.
                            // Failure here propagates to the outer catch
                            // → mapped to ComposerError.RecordCreationFailed.
                            // Falling back to "target is root" on decode
                            // failure would silently corrupt the reply ref
                            // when the target was actually a reply (root
                            // would be wrong) — better to fail loud than
                            // construct a misthreaded post.
                            val targetRecord = thread.post.record.decodeRecord(Post.serializer())
                            Result.success(thread.post.toParentPostUi(targetRecord))
                        }
                        is NotFoundPost, is BlockedPost -> {
                            Timber.tag(TAG).d(
                                "fetchParent() — parent unavailable: %s",
                                thread::class.simpleName,
                            )
                            Result.failure(ComposerError.ParentNotFound)
                        }
                        else -> {
                            Timber.tag(TAG).w(
                                "fetchParent() — unexpected thread variant: %s",
                                thread::class.simpleName,
                            )
                            Result.failure(
                                ComposerError.RecordCreationFailed(
                                    IllegalStateException("Unexpected thread variant: ${thread::class.simpleName}"),
                                ),
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).e(throwable, "fetchParent() failed")
                    Result.failure(mapToComposerError(throwable))
                }
            }

        private fun PostView.toParentPostUi(record: Post): ParentPostUi {
            val parentRef = StrongRef(uri = uri, cid = cid)
            val rootRef: StrongRef =
                when (val reply = record.reply) {
                    is AtField.Defined -> reply.value.root
                    // Both Missing (field absent) and Null (field
                    // present but explicitly null on the wire) mean
                    // "this is a top-level post" — target IS the
                    // root, parentRef doubles as rootRef.
                    AtField.Missing, AtField.Null -> parentRef
                }
            return ParentPostUi(
                parentRef = parentRef,
                rootRef = rootRef,
                authorHandle = author.handle.raw,
                authorDisplayName = author.displayName?.takeIf { it.isNotBlank() },
                text = record.text,
            )
        }

        private fun mapToComposerError(throwable: Throwable): ComposerError =
            when (throwable) {
                is ComposerError -> throwable
                is NoSessionException -> ComposerError.Unauthorized
                is IOException -> ComposerError.Network(throwable)
                else -> ComposerError.RecordCreationFailed(throwable)
            }

        private companion object {
            private const val TAG = "ParentFetchSource"
        }
    }
