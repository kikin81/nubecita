package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Persisted record of a single recent search query.
 *
 * The `query` text itself is the primary key so re-recording an
 * existing search naturally upserts the row (incrementing
 * [recordedAt]) without producing duplicates. Only the search-feature
 * `RecentSearchRepository` consumes this entity; it maps DAO flows to
 * `Flow<List<String>>` before exposing them to ViewModels, so the
 * `recordedAt` column never crosses the repository boundary.
 *
 * This entity deliberately ships **without** an `asExternalModel()`
 * extension or a corresponding `:data:models` data class. The Room
 * foundation convention prescribes that pattern for entities whose
 * structure ViewModels need to read; here the only externally-visible
 * field is the query string itself, so a wrapping data class would
 * just be a one-field shim.
 */
@Entity(tableName = "recent_search")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Instant,
)
