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
 * Wraps [RecentSearchDao] from `:core:database`. This repository
 * always sources the timestamp from [Clock.System.now] — there is no
 * clock-injection seam at the repo layer. The DAO's `upsertAndTrim`
 * does take the timestamp as a parameter, which is what lets the
 * `:core:database` androidTest exercise the LRU + dedup semantics
 * deterministically. Repo-level tests assert on presence / trimming /
 * recency ordering of seeded entries, not on the timestamp `record`
 * produces.
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
