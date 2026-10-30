package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.wear.data.WatchDmActionRepository
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Bench flavor: fixed, fictional conversations for screenshots, with no phone. */
@Module
@InstallIn(SingletonComponent::class)
internal interface BenchWatchDmModule {
    @Binds
    fun bindWatchDmRepository(impl: BenchWatchDmRepository): WatchDmRepository

    @Binds
    fun bindWatchDmActionRepository(impl: BenchWatchDmActionRepository): WatchDmActionRepository
}

internal class BenchWatchDmRepository
    @Inject
    constructor() : WatchDmRepository {
        private val now = System.currentTimeMillis()

        private fun minutesAgo(m: Int) = now - m * 60_000L

        private val convos =
            listOf(
                Triple("dm-ana", "Ana Ruiz", false) to listOf(Msg(false, null, "Are we still on for the hike Saturday?", 12), Msg(true, null, "Yes! 8am at the trailhead.", 9), Msg(false, null, "Perfect, I'll bring coffee.", 3)),
                Triple("dm-trip", "Weekend Trip", true) to listOf(Msg(false, "Ben", "Who's driving?", 50), Msg(false, "Cleo", "I can, the car fits four.", 44), Msg(true, null, "I'll bring snacks.", 40)),
                Triple("dm-cleo", "Cleo Marsh", false) to listOf(Msg(false, null, "Loved the lake photos!", 180)),
            )

        override fun state(accountDid: String): Flow<WatchDmStateEntity?> = flowOf(WatchDmStateEntity(accountDid, now, unreadCount = 3))

        override suspend fun latestUnreadCount(): Int = 3

        override fun convos(accountDid: String): Flow<List<WatchConvoEntity>> =
            flowOf(
                convos.mapIndexed { position, (meta, messages) ->
                    val (id, title, group) = meta
                    WatchConvoEntity(
                        accountDid,
                        id,
                        position,
                        title,
                        group,
                        unreadCount = UNREAD.getOrElse(position) { 0 },
                        muted = false,
                        lastMessageAtMs = minutesAgo(messages.last().minutesAgo),
                    )
                },
            )

        override fun lastMessages(accountDid: String): Flow<List<WatchMessageEntity>> = flowOf(convos.map { (meta, messages) -> messages.last().toEntity(accountDid, meta.first, messages.lastIndex) })

        override fun messages(
            accountDid: String,
            convoId: String,
        ): Flow<List<WatchMessageEntity>> =
            flowOf(
                convos
                    .firstOrNull { it.first.first == convoId }
                    ?.second
                    ?.mapIndexed { i, m -> m.toEntity(accountDid, convoId, i) }
                    .orEmpty(),
            )

        private companion object {
            // Ana has two unread, the group one, the rest none.
            val UNREAD = listOf(2, 1)
        }

        private data class Msg(
            val mine: Boolean,
            val sender: String?,
            val text: String,
            val minutesAgo: Int,
        )

        private fun Msg.toEntity(
            accountDid: String,
            convoId: String,
            position: Int,
        ) = WatchMessageEntity(accountDid, convoId, position, "$convoId-$position", mine, sender, text, minutesAgo(minutesAgo), isDeleted = false, hasAttachment = false)
    }

/** Bench flavor: replies and reactions show as sent at once, in memory. Nothing reaches Bluesky. */
@Singleton
internal class BenchWatchDmActionRepository
    @Inject
    constructor() : WatchDmActionRepository {
        private val rows = MutableStateFlow(emptyList<WatchPendingDmEntity>())
        private var next = 0L

        override fun pending(accountDid: String): Flow<List<WatchPendingDmEntity>> = rows.map { all -> all.filter { it.accountDid == accountDid } }

        override suspend fun sendReply(
            accountDid: String,
            convoId: String,
            text: String,
        ) = add(accountDid, convoId, WatchPendingDmEntity.KIND_REPLY, text, null)

        override suspend fun sendReaction(
            accountDid: String,
            convoId: String,
            messageId: String,
            emoji: String,
        ) = add(accountDid, convoId, WatchPendingDmEntity.KIND_REACT, emoji, messageId)

        override suspend fun retry(actionId: String) = Unit

        private fun add(
            accountDid: String,
            convoId: String,
            kind: String,
            text: String,
            target: String?,
        ) {
            val id = next++
            rows.update { it + WatchPendingDmEntity("bench-dm-$id", accountDid, convoId, kind, text, target, sentMessageId = null, createdAtMs = id, status = WatchPendingDmEntity.STATUS_SENT) }
        }
    }
