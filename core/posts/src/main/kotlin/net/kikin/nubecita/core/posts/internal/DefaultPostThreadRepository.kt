package net.kikin.nubecita.core.posts.internal

import android.util.Log
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadRequest
import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadOtherV2Request
import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadV2Request
import io.github.kikin81.atproto.app.bsky.unspecced.UnspeccedService
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.data.models.ThreadItem
import timber.log.Timber
import javax.inject.Inject

internal class DefaultPostThreadRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val snapshotStore: ThreadSnapshotStore,
        private val orderedReadHealth: OrderedReadHealth,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostThreadRepository {
        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPostThread(
                            GetPostThreadRequest(uri = AtUri(uri)),
                        )
                    val raw = response.thread.toRawThreadEntries()
                    // Cover (never drop) warned media against the cached prefs +
                    // viewer DID — post-detail keeps every post in the thread.
                    val items = raw.toThreadItems(moderationPreferences.prefs.value, viewerDid())
                    // Write-through: a readable thread replaces its snapshot; one
                    // whose focus is now blocked / not found drops it, so a dead
                    // thread never renders from cache.
                    cacheSafely {
                        if (items.any { it is ThreadItem.Focus }) {
                            snapshotStore.write(uri, raw)
                        } else {
                            snapshotStore.remove(uri)
                        }
                    }
                    items
                }.onFailure { throwable ->
                    if (throwable.isPostGone()) cacheSafely { snapshotStore.remove(uri) }
                    // Mirrors the diagnostic logging convention in
                    // :feature:feed:impl/data/DefaultFeedRepository — emits the
                    // throwable's javaClass.name so the Unknown-error VM branch
                    // doesn't swallow which atproto-stack exception we hit.
                    //
                    // Logs the post's rkey (the segment after the final `/`)
                    // rather than the full AtUri because the URI's DID
                    // segment is third-party PII. The rkey alone identifies
                    // the post within a known dataset, matches the
                    // redaction policy used in :core:auth's
                    // DefaultXrpcClientProvider for cache-event logging,
                    // and keeps the diagnostic signal we actually need
                    // (which post failed, which exception type).
                    Timber.tag(TAG).w(
                        throwable,
                        "getPostThread(rkey=%s) failed: %s",
                        uri.substringAfterLast('/'),
                        throwable.javaClass.name,
                    )
                }
            }

        override suspend fun getOrderedThread(uri: String): Result<ImmutableList<ThreadItem>> =
            withContext(dispatcher) {
                val ordered =
                    runCatchingCancellable {
                        val client = xrpcClientProvider.authenticated()
                        val response =
                            UnspeccedService(client).getPostThreadV2(
                                GetPostThreadV2Request(
                                    anchor = AtUri(uri),
                                    above = true,
                                    below = ORDERED_BELOW,
                                    branchingFactor = ORDERED_BRANCHING_FACTOR,
                                    sort = ORDERED_SORT,
                                ),
                            )
                        val raw = response.toRawThreadEntries()
                        val items = raw.toThreadItems(moderationPreferences.prefs.value, viewerDid())
                        // Same write-through rule as the v1 read: a readable
                        // thread replaces its snapshot, a dead one drops it.
                        cacheSafely {
                            if (items.any { it is ThreadItem.Focus }) {
                                snapshotStore.write(uri, raw)
                            } else {
                                snapshotStore.remove(uri)
                            }
                        }
                        items
                    }
                val failure = ordered.exceptionOrNull() ?: return@withContext ordered
                if (failure.isOrderedReadUnusable()) {
                    // The endpoint is experimental upstream. When it stops
                    // answering in a shape this build can read, the thread still
                    // opens, through the read it replaced, without thread
                    // positions. Never silently: this line is how lexicon drift
                    // in the SDK gets noticed. The first one in a process is an
                    // error (a non-fatal with a stack trace); while the endpoint
                    // stays down every open lands here, and those are warnings
                    // (breadcrumbs), so one outage is one report per process.
                    val priority = if (orderedReadHealth.isFirstUnusableRead()) Log.ERROR else Log.WARN
                    Timber.tag(TAG).log(
                        priority,
                        failure,
                        "getPostThreadV2(rkey=%s) is unusable (%s); falling back to getPostThread",
                        uri.substringAfterLast('/'),
                        failure.javaClass.name,
                    )
                    return@withContext getPostThread(uri)
                }
                if (failure.isPostGone()) cacheSafely { snapshotStore.remove(uri) }
                Timber.tag(TAG).w(
                    failure,
                    "getPostThreadV2(rkey=%s) failed: %s",
                    uri.substringAfterLast('/'),
                    failure.javaClass.name,
                )
                ordered
            }

        override suspend fun getOtherReplies(uri: String): Result<ImmutableList<ThreadItem>> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val client = xrpcClientProvider.authenticated()
                    UnspeccedService(client)
                        .getPostThreadOtherV2(GetPostThreadOtherV2Request(anchor = AtUri(uri)))
                        .toRawThreadEntries()
                        .toThreadItems(moderationPreferences.prefs.value, viewerDid())
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(
                        throwable,
                        "getPostThreadOtherV2(rkey=%s) failed: %s",
                        uri.substringAfterLast('/'),
                        throwable.javaClass.name,
                    )
                }
            }

        override suspend fun cachedPostThread(uri: String): ImmutableList<ThreadItem>? =
            withContext(dispatcher) {
                // A cache that can't be read is a miss, never a crash.
                val raw =
                    try {
                        snapshotStore.read(uri) ?: snapshotStore.readFocusOnly(uri)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        Timber.tag(TAG).w(error, "Thread snapshot read failed: %s", error.javaClass.name)
                        null
                    } ?: return@withContext null
                raw.toThreadItems(moderationPreferences.prefs.value, viewerDid()).takeIf { items -> items.isNotEmpty() }
            }

        /**
         * The snapshot cache is a speed layer: a storage failure is logged and
         * swallowed so it never turns a successful fetch into an error.
         */
        private suspend fun cacheSafely(block: suspend () -> Unit) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Thread snapshot cache write failed: %s", error.javaClass.name)
            }
        }

        private fun viewerDid(): String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        /** A definitive "this post is gone" answer — the same test post-detail uses for its NotFound state. */
        private fun Throwable.isPostGone(): Boolean = this is XrpcError && (errorName.equals("NotFound", ignoreCase = true) || status == HTTP_NOT_FOUND)

        /**
         * Whether the ordered read failed in a way that means "this endpoint is
         * not usable from this build" rather than "this request failed": a
         * response that does not decode, one whose anchor item the SDK could not
         * read, or a server that does not know the method or rejects how it was
         * called. A connectivity failure, an auth
         * failure and a post that is gone are none of those, and are returned
         * as they are.
         */
        private fun Throwable.isOrderedReadUnusable(): Boolean =
            when (this) {
                is SerializationException -> true
                // Decoded, but the SDK could not read the anchor item: drift
                // inside the item union, which never surfaces as a decode error.
                is UnreadableOrderedThreadException -> true
                is XrpcError ->
                    status == HTTP_NOT_IMPLEMENTED ||
                        UNUSABLE_ERROR_NAMES.any { it.equals(errorName, ignoreCase = true) } ||
                        (status == HTTP_NOT_FOUND && !errorName.equals("NotFound", ignoreCase = true))
                else -> false
            }

        private companion object {
            const val TAG = "PostThreadRepository"
            const val HTTP_NOT_FOUND = 404
            const val HTTP_NOT_IMPLEMENTED = 501

            // The official client's linear-view request. These are the values
            // the server is exercised with, which matters for an endpoint that
            // may change without notice.
            const val ORDERED_BELOW = 10L
            const val ORDERED_BRANCHING_FACTOR = 1L
            const val ORDERED_SORT = "top"

            val UNUSABLE_ERROR_NAMES = setOf("MethodNotImplemented", "XRPCNotSupported", "InvalidRequest")
        }
    }
