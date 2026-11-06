package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.postinteractions.PostDeletionListener
import javax.inject.Inject

/**
 * Removes a post the viewer deleted from every cached feed partition.
 *
 * The cache cannot be left to catch up on its own. A partition that supports
 * `since` renders from disk on open and only ever adds above its head, so a
 * deleted post stays until the reader happens to pull to refresh — and the
 * in-memory flag that hides it meanwhile is gone after the next process death.
 *
 * Three grains, in one transaction so no reader sees them disagree:
 *
 * - **Entries** go, so a local re-projection cannot rebuild the post.
 * - **Items led by the post** go. The row is removed rather than left to read
 *   as nothing, because that is the state the reader's saved place already
 *   falls back from. A chain is the exception: its earlier posts are still
 *   live, so it stays and loses this one on read.
 * - **The stored post** becomes a tombstone rather than a deleted row. It can
 *   still be a chain member or a reply's context, and a tombstone is what makes
 *   each of those read paths drop it.
 *
 * A reply cluster that holds the post as its root or parent is not rebuilt: it
 * reads as nothing until the next refresh, since a cluster with a hole in it
 * does not render.
 */
internal class FeedCachePostRemover
    @Inject
    constructor(
        private val feedPostDao: FeedPostDao,
        private val feedItemDao: FeedItemDao,
        private val cachedPostDao: CachedPostDao,
        private val sessionStateProvider: SessionStateProvider,
        private val transactionRunner: FeedCacheTransactionRunner,
    ) : PostDeletionListener {
        override suspend fun onPostDeleted(postUri: String) {
            val accountDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: return
            transactionRunner.run {
                feedPostDao.deleteEntriesFor(accountDid, postUri)
                feedItemDao
                    .itemsWithLeaf(accountDid, postUri)
                    .filterNot { it.variant == VARIANT_CHAIN }
                    .forEach { feedItemDao.deleteItemAt(accountDid, it.feedType, it.feedUri, it.position) }
                cachedPostDao.markUnavailable(accountDid, postUri, UNAVAILABLE_NOT_FOUND)
            }
        }
    }
