package net.kikin.nubecita.data.models

import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Instant

/**
 * One row in the notifications surface's LazyColumn. Single-event and
 * multi-event aggregated rows are both UI items; the [Single]/[Aggregated]
 * split is a type-level signal for renderers (the chevron disclosure only
 * exists for aggregated).
 *
 * Aggregation is page-scoped and runs in `NotificationsMapper` —
 * notifications with the same `(reason, reasonSubject)` collapse into one
 * [Aggregated] row, with [actors] holding the contributing accounts in
 * `indexedAt` descending order. Follows aggregate on `(reason, sameDay)`
 * because they have no `reasonSubject`.
 *
 * [subjectPost] is the post the row references — the user's own post for
 * like/repost-shaped reasons, the actor's new post for reply/quote/mention.
 * Null when the reason has no associated post (follow, verified,
 * starterpack-joined, etc.) or when the post was deleted between indexing
 * and the page fetch.
 */
@Stable
public sealed interface NotificationItemUi {
    /**
     * Stable identity used as the `key` lambda input on the host
     * `LazyColumn`. The mapper derives this from the lexicon `uri` for
     * single rows and `${reason}|${reasonSubject ?: indexedAt-day}` for
     * aggregated rows.
     */
    public val itemKey: String
    public val reason: NotificationReason
    public val indexedAt: Instant
    public val isRead: Boolean
    public val actors: ImmutableList<AuthorUi>
    public val subjectPost: PostUi?

    @Stable
    public data class Single(
        override val itemKey: String,
        override val reason: NotificationReason,
        override val indexedAt: Instant,
        override val isRead: Boolean,
        override val actors: ImmutableList<AuthorUi>,
        override val subjectPost: PostUi?,
    ) : NotificationItemUi {
        init {
            require(actors.size == 1) {
                "NotificationItemUi.Single requires exactly one actor; got ${actors.size}"
            }
        }
    }

    @Stable
    public data class Aggregated(
        override val itemKey: String,
        override val reason: NotificationReason,
        override val indexedAt: Instant,
        override val isRead: Boolean,
        override val actors: ImmutableList<AuthorUi>,
        override val subjectPost: PostUi?,
    ) : NotificationItemUi {
        init {
            require(actors.size >= 2) {
                "NotificationItemUi.Aggregated requires two or more actors; got ${actors.size}"
            }
        }
    }
}
