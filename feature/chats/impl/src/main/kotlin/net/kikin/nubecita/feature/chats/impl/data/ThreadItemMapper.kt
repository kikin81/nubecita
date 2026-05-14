package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ThreadItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

private val WEEKDAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val MONTH_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * Builds the screen's flat `ThreadItem` stream from a newest-first time-sorted
 * list of [MessageUi]. Inserts `DaySeparator` items at local-calendar-day
 * boundaries; assigns `runIndex` (oldest-first within run) + `runCount` +
 * `showAvatar` to every `Message` item.
 *
 * Timezone: AT Protocol returns ISO-8601 in UTC. Day-boundary comparison runs
 * in the user's local zone via [zone] — naive UTC comparison would inject
 * spurious separators mid-conversation for any non-UTC user. The default is
 * `ZoneId.systemDefault()`; tests pin the zone explicitly.
 *
 * Day-separator chips break runs for shape + avatar purposes — a same-sender
 * pair straddling midnight (in [zone]) renders as two distinct runs of 1.
 *
 * Output order matches the input (newest-first); each run's items also appear
 * newest-first, but `runIndex` is assigned oldest-first within the run so the
 * shape resolver's `isFirst` / `isLast` reasoning matches screen-top semantics
 * under `LazyColumn(reverseLayout = true)`.
 */
internal fun List<MessageUi>.toThreadItems(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): ImmutableList<ThreadItem> {
    if (isEmpty()) return persistentListOf()

    val nowLocalDate = now.toLocalDate(zone)
    val result = mutableListOf<ThreadItem>()
    val runBuckets = mutableListOf<MutableList<MessageUi>>()

    // Walk newest → oldest; group into runs by (senderDid, local-day).
    var currentSender: String? = null
    var currentDay: LocalDate? = null
    for (m in this) {
        val day = m.sentAt.toLocalDate(zone)
        if (currentSender == null || m.senderDid != currentSender || day != currentDay) {
            runBuckets.add(mutableListOf())
            currentSender = m.senderDid
            currentDay = day
        }
        runBuckets.last().add(m)
    }

    // Emit, walking newest-first by run.
    for (i in runBuckets.indices) {
        val bucket = runBuckets[i]
        val runCount = bucket.size
        val bucketDay = bucket.first().sentAt.toLocalDate(zone)
        // Emit a separator before this run when its day differs from the
        // previous (newer) run's day, OR for the very first (newest) run.
        val sameDayAsPrev =
            i > 0 && runBuckets[i - 1].first().sentAt.toLocalDate(zone) == bucketDay
        if (!sameDayAsPrev) {
            result.add(ThreadItem.DaySeparator(label = formatDayLabel(bucketDay, nowLocalDate)))
        }
        // Bucket is newest-first; assign runIndex such that 0 = oldest.
        bucket.forEachIndexed { posInBucket, m ->
            val runIndex = runCount - 1 - posInBucket
            result.add(
                ThreadItem.Message(
                    message = m,
                    runIndex = runIndex,
                    runCount = runCount,
                    showAvatar = !m.isOutgoing && runIndex == 0,
                ),
            )
        }
    }

    return result.toImmutableList()
}

private fun Instant.toLocalDate(zone: ZoneId): LocalDate = ZonedDateTime.ofInstant(java.time.Instant.parse(toString()), zone).toLocalDate()

private fun formatDayLabel(
    sentDay: LocalDate,
    nowDay: LocalDate,
): String =
    when {
        sentDay == nowDay -> "Today"
        sentDay == nowDay.minusDays(1) -> "Yesterday"
        sentDay.isAfter(nowDay.minusDays(7)) -> sentDay.format(WEEKDAY_FORMATTER)
        else -> sentDay.format(MONTH_DAY_FORMATTER)
    }
