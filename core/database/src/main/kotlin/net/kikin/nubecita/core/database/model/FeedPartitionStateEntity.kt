package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Per-partition reader state: what is true about one cached feed for one
 * account, as opposed to the rows the feed contains.
 *
 * Separate from `feed_remote_keys`, which is about pagination cursors, and from
 * `feed_item`, which is per-row. This is the home for facts about the partition
 * as a whole — the preference fingerprint, the reader's scroll anchor, and the
 * pill-dismissal marker when that lands.
 *
 * [prefsFingerprint] records the viewer preferences the partition's **grouping
 * projection** was computed with. The projection is not a pure function of the
 * fetched page: moderation preferences and the feed-view reply tuners both feed
 * it, and both change at runtime from Settings. Without this, a preference
 * change would leave the already-projected rows as they were and the setting
 * would appear to do nothing until the partition happened to be refetched.
 *
 * It is a fingerprint rather than the preference values because its only job is
 * to answer "are these still the preferences this projection was built with?".
 * Note that it therefore cannot be *projected with* — see the design's D27 for
 * what that costs.
 *
 * The three anchor columns are the reader's place in this partition (D6). It
 * lives here, keyed exactly like the cache, rather than in DataStore, so that
 * sign-out purges it with the same `clearAccount` call that drops the posts —
 * one delete, no orphan, no second lifecycle, and multi-account falls out of
 * the key for free.
 *
 * - [anchorItemKey] identifies the item. Not its position: moderation toggles,
 *   a mute, a changed reply filter and the chain re-projection all change how
 *   many rows the same entries become, so an index saved in one session points
 *   somewhere else in the next. The key is derived from content (D12), so it
 *   still resolves after a re-projection moved the row.
 * - [anchorPosition] is where that item was. It is kept **as well as** the key
 *   because a dead anchor has to resolve *downward* (D11) — to the items just
 *   under where the reader stopped, which are the ones they had not read — and
 *   a key that no longer exists gives nothing to resolve downward *from*. It
 *   is also what the count-cap trim reads to avoid deleting the reader's
 *   backlog (D19).
 * - [anchorOffset] is the pixel scroll offset within that row, so restore lands
 *   mid-row where the reader actually was rather than snapping to its top.
 */
@Entity(
    tableName = "feed_partition_state",
    primaryKeys = ["account_did", "feed_type", "feed_uri"],
)
data class FeedPartitionStateEntity(
    @ColumnInfo(name = "account_did") val accountDid: String,
    @ColumnInfo(name = "feed_type") val feedType: String,
    @ColumnInfo(name = "feed_uri") val feedUri: String,
    @ColumnInfo(name = "prefs_fingerprint") val prefsFingerprint: String? = null,
    @ColumnInfo(name = "anchor_item_key") val anchorItemKey: String? = null,
    @ColumnInfo(name = "anchor_position") val anchorPosition: Int? = null,
    @ColumnInfo(name = "anchor_offset") val anchorOffset: Int? = null,
)
