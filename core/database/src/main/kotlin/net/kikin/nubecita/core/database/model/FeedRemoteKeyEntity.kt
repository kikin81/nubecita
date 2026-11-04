package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The single Paging 3 `RemoteMediator` cursor for one cached feed partition,
 * keyed identically to [FeedPostEntity]'s partition (`account_did`,
 * `feed_type`, `feed_uri`). [nextCursor] is the atproto pagination cursor to
 * pass on the next `APPEND`; `null` means end-of-pagination / not-yet-fetched.
 *
 * One row per partition (not per position). The row is cleared only when the
 * whole partition is cleared (`REFRESH` / `clearAccount`); a count-cap trim
 * leaves it intact.
 *
 * The other three cursors belong to the **prepend** (catch-up) direction and
 * are independent of [nextCursor], which only ever advances downwards:
 *
 * - [boundarySince] — the `since` value a prepend passes: the `startCursor` of
 *   the newest page ever written to this partition. `startCursor` is a
 *   page-level response field, so this cannot be derived from any single row.
 * - [fillCursor] — how far a multi-page catch-up has got *within* that range,
 *   so an interrupted fill resumes rather than restarting.
 * - [pendingBoundary] — the `startCursor` returned by the newest fill page,
 *   promoted to [boundarySince] once the range is exhausted.
 *
 * They are separate columns rather than a reused cursor because a prepend that
 * wrote back nothing would re-fetch the same range from a stale boundary on
 * every run and never make progress, and one that overwrote [nextCursor] would
 * break forward pagination. Write them with the DAO's targeted updates, not a
 * whole-row upsert.
 */
@Entity(
    tableName = "feed_remote_keys",
    primaryKeys = ["account_did", "feed_type", "feed_uri"],
)
data class FeedRemoteKeyEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    @ColumnInfo(name = "next_cursor") val nextCursor: String?,
    @ColumnInfo(name = "boundary_since") val boundarySince: String? = null,
    @ColumnInfo(name = "fill_cursor") val fillCursor: String? = null,
    @ColumnInfo(name = "pending_boundary") val pendingBoundary: String? = null,
)
