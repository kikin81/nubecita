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
    //
    // Day-separator position: the chip belongs at the TOP of its day-group on
    // screen. With LazyColumn(reverseLayout = true), screen-top = HIGHEST source
    // index, so the chip must be emitted AFTER its bucket items in source. We
    // only emit a chip when the next (older) bucket is on a different day, or
    // when this is the last (oldest) bucket — that way same-day adjacent runs
    // (alternating senders within one day) share a single chip placed at the
    // top of the day-group on screen, matching the Bluesky / iMessage /
    // Google Chat convention. (Earlier rev emitted the chip BEFORE the bucket,
    // which placed it at the screen-BOTTOM of the day-group under reverseLayout
    // — opposite of the expected layout.)
    for (i in runBuckets.indices) {
        val bucket = runBuckets[i]
        val runCount = bucket.size
        val bucketDay = bucket.first().sentAt.toLocalDate(zone)
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
        val isLastBucket = i == runBuckets.lastIndex
        val nextBucketDay =
            runBuckets
                .getOrNull(i + 1)
                ?.first()
                ?.sentAt
                ?.toLocalDate(zone)
        val crossesDayBoundary = nextBucketDay != null && nextBucketDay != bucketDay
        if (isLastBucket || crossesDayBoundary) {
            result.add(ThreadItem.DaySeparator(label = formatDayLabel(bucketDay, nowLocalDate)))
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
