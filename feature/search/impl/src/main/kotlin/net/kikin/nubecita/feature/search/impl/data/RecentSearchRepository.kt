package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the user's most recent search queries (LRU, capacity
 * [LRU_CAPACITY]). The repository exposes only the query text via
 * `Flow<List<String>>` — the underlying entity's `recordedAt`
 * timestamp is an internal ordering signal and never crosses this
 * boundary.
 *
 * Wraps [RecentSearchDao] from `:core:database`. The DAO accepts the
 * timestamp as a parameter so tests can drive recency without a clock
 * abstraction; production calls source the timestamp from
 * [Clock.System.now].
 *
 * Blank queries are silently ignored; non-blank queries are trimmed of
 * surrounding whitespace before persistence.
 */
@Singleton
internal class RecentSearchRepository
    @Inject
    constructor(
        private val dao: RecentSearchDao,
    ) {
        fun observeRecent(): Flow<List<String>> = dao.observeAll().map { entities -> entities.map(RecentSearchEntity::query) }

        suspend fun record(query: String) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return
            dao.upsertAndTrim(
                entity = RecentSearchEntity(query = trimmed, recordedAt = Clock.System.now()),
                capacity = LRU_CAPACITY,
            )
        }

        suspend fun clearAll() {
            dao.clearAll()
        }

        private companion object {
            const val LRU_CAPACITY = 10
        }
    }
