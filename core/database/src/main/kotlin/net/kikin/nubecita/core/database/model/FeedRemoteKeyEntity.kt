package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * The single Paging 3 `RemoteMediator` cursor for one cached feed partition,
 * keyed identically to [FeedPostEntity]'s partition (`account_did`,
 * `feed_type`, `feed_uri`). [nextCursor] is the atproto pagination cursor to
 * pass on the next `APPEND`; `null` means end-of-pagination / not-yet-fetched.
 *
 * One row per partition (not per position) — there is exactly one live cursor
 * per feed. The row is cleared only when the whole partition is cleared
 * (`REFRESH` / `clearAccount`); a count-cap trim leaves it intact.
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
)
