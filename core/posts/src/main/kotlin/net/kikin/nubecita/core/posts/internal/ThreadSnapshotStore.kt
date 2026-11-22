package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.ThreadSnapshotDao
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotItemEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Stores and restores post-detail thread snapshots: the flattened, unmoderated
 * [RawThreadEntry] list, with post bodies in `cached_post` (the app's single
 * post store) and the ordering in `thread_snapshot_item`. Callers re-map what
 * [read] returns with the viewer's current moderation prefs.
 *
 * Everything is scoped to the signed-in account; with no session each call is
 * a no-op and reads return null.
 */
internal class ThreadSnapshotStore
    @Inject
    constructor(
        private val threadSnapshotDao: ThreadSnapshotDao,
        private val cachedPostDao: CachedPostDao,
        private val sessionStateProvider: SessionStateProvider,
        private val clock: Clock,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /** The last snapshot of the thread focused on [focusUri], or null if none (or its focus body is gone). */
        suspend fun read(focusUri: String): List<RawThreadEntry>? =
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext null
                val items = threadSnapshotDao.items(account, focusUri)
                if (items.isEmpty()) return@withContext null
                val posts =
                    cachedPostDao
                        .byUris(account, items.filter { it.hasBody() }.map { it.postUri })
                        .associateBy { it.uri }
                val entries =
                    items.mapNotNull { item ->
                        if (!item.hasBody()) {
                            RawThreadEntry(item.kind, item.postUri, item.depth, authorDid = item.authorDid)
                        } else {
                            // A body evicted from the post store just drops its slot.
                            posts[item.postUri]?.decode()?.let { RawThreadEntry(item.kind, item.postUri, item.depth, post = it) }
                        }
                    }
                if (entries.none { it.kind == ThreadSnapshotKind.FOCUS }) return@withContext null
                threadSnapshotDao.touch(account, focusUri, clock.now())
                entries
            }

        /**
         * The focus alone, from any surface that cached it (the feed, usually),
         * for a thread that has never been opened. Null if the post isn't cached.
         */
        suspend fun readFocusOnly(focusUri: String): List<RawThreadEntry>? =
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext null
                val post = cachedPostDao.byUri(account, focusUri)?.decode() ?: return@withContext null
                listOf(RawThreadEntry(ThreadSnapshotKind.FOCUS, focusUri, depth = 0, post = post))
            }

        /**
         * Replaces the snapshot for [focusUri] with [entries], keeps the 50 most
         * recently opened snapshots, then collects post bodies nothing references.
         */
        suspend fun write(
            focusUri: String,
            entries: List<RawThreadEntry>,
        ) {
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext
                val now = clock.now()
                cachedPostDao.upsert(entries.mapNotNull { it.post?.toCachedPost(account) })
                threadSnapshotDao.replace(
                    ThreadSnapshotEntity(accountDid = account, focusUri = focusUri, fetchedAt = now, lastOpenedAt = now),
                    entries.mapIndexed { position, entry ->
                        ThreadSnapshotItemEntity(
                            accountDid = account,
                            focusUri = focusUri,
                            position = position,
                            kind = entry.kind,
                            postUri = entry.uri,
                            depth = entry.depth,
                            authorDid = entry.authorDid,
                        )
                    },
                )
                threadSnapshotDao.evictBeyond(account, cap = MAX_SNAPSHOTS)
                cachedPostDao.deleteUnreferenced(account)
            }
        }

        /** Drops the snapshot for [focusUri] (e.g. the post is gone), then collects its posts. */
        suspend fun remove(focusUri: String) {
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext
                threadSnapshotDao.delete(account, focusUri)
                cachedPostDao.deleteUnreferenced(account)
            }
        }

        private fun accountDid(): String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        private fun ThreadSnapshotItemEntity.hasBody(): Boolean = kind != ThreadSnapshotKind.BLOCKED && kind != ThreadSnapshotKind.NOT_FOUND

        private fun CachedPostEntity.decode(): PostView? {
            val blob = postBlob ?: return null
            return try {
                ThreadCacheJson.decodeFromString(PostView.serializer(), blob)
            } catch (error: SerializationException) {
                Timber.tag(TAG).w(error, "Unreadable cached post in a thread snapshot")
                null
            }
        }

        private companion object {
            const val TAG = "ThreadSnapshotStore"
            const val MAX_SNAPSHOTS = 50
        }
    }

/** Mirrors `:core:feed-cache`'s `toCachedPost`: the same row shape for the same post, whichever surface wrote it. */
private fun PostView.toCachedPost(accountDid: String): CachedPostEntity =
    CachedPostEntity(
        accountDid = accountDid,
        uri = uri.raw,
        cid = cid.raw,
        authorDid = author.did.raw,
        indexedAt = runCatching { Instant.parse(indexedAt.raw) }.getOrDefault(Instant.fromEpochMilliseconds(0)),
        text = (record["text"] as? JsonPrimitive)?.content,
        postBlob = ThreadCacheJson.encodeToString(PostView.serializer(), this),
    )

/**
 * Mirrors `:core:feed-cache`'s `CacheJson` — both write `cached_post.post_blob`,
 * so the two configurations must stay identical.
 */
private val ThreadCacheJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
