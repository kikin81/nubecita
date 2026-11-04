package net.kikin.nubecita.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Per-partition reader state: what is true about one cached feed for one
 * account, as opposed to the rows the feed contains.
 *
 * Separate from `feed_remote_keys`, which is about pagination cursors, and from
 * `feed_item`, which is per-row. This is the home for facts about the partition
 * as a whole — today the preference fingerprint, and the scroll anchor and
 * pill-dismissal marker when those land.
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
)
