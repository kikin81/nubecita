package net.kikin.nubecita.core.feedcache

import androidx.room.withTransaction
import net.kikin.nubecita.core.database.NubecitaDatabase
import javax.inject.Inject

/**
 * Seam over Room's [withTransaction] extension so the [FeedRemoteMediator]'s
 * atomic clear+insert / append blocks can run inside one DB transaction in
 * production yet be JVM-unit-tested with a pass-through fake — Room's
 * `withTransaction` is a suspend extension on the (final) generated database
 * class and cannot be stubbed with MockK without an instrumented DB.
 */
internal interface FeedCacheTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

/**
 * Production implementation: wraps the singleton [NubecitaDatabase] and runs
 * [block] inside `db.withTransaction { … }`, so all DAO writes the mediator
 * issues commit atomically (or roll back together on failure).
 */
internal class RoomFeedCacheTransactionRunner
    @Inject
    constructor(
        private val database: NubecitaDatabase,
    ) : FeedCacheTransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
    }
