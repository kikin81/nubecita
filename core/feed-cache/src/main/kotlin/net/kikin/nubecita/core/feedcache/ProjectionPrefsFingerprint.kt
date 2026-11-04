package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedViewPrefs

/**
 * A stable identity for the viewer preferences a grouping projection was
 * computed with.
 *
 * The projection is not a pure function of the fetched page: moderation
 * preferences decide what is hidden or covered, and the feed-view tuners decide
 * which replies, reposts and quotes survive at all. Both change at runtime from
 * Settings, so a persisted projection needs to record which ones produced it —
 * otherwise flipping a setting leaves the stored rows untouched and the setting
 * appears to do nothing.
 *
 * Every field that feeds the projection is included, and nothing that does not.
 * A field left out would make a real change invisible; a field wrongly included
 * would re-project for no reason. The label map is sorted by name so two equal
 * preference sets cannot produce different strings through iteration order.
 *
 * The viewer DID is deliberately absent: it is part of the partition key, so a
 * different account is a different partition rather than the same one under new
 * preferences.
 */
internal fun projectionPrefsFingerprint(
    moderation: ModerationPrefs,
    feedView: FeedViewPrefs,
): String =
    buildString {
        append("adult=").append(moderation.adultContentEnabled)
        append("|labels=")
        moderation.visibilities
            .entries
            .sortedBy { it.key.name }
            .joinTo(this, separator = ",") { "${it.key.name}:${it.value.name}" }
        append("|replies=").append(feedView.hideReplies)
        append("|repliesByUnfollowed=").append(feedView.hideRepliesByUnfollowed)
        append("|reposts=").append(feedView.hideReposts)
        append("|quotes=").append(feedView.hideQuotePosts)
    }
