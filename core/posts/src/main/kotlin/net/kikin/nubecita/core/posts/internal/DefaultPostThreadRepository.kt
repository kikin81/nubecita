package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
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

        private companion object {
            const val TAG = "PostThreadRepository"
            const val HTTP_NOT_FOUND = 404
        }
    }
