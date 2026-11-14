package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.bench.BenchScenario
import net.kikin.nubecita.wear.bench.BenchScenarios
import net.kikin.nubecita.wear.bench.phoneAppStatus
import net.kikin.nubecita.wear.bench.session
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.PostActionKind
import net.kikin.nubecita.wear.data.WatchActionRepository
import net.kikin.nubecita.wear.data.WatchPostActionRepository
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import net.kikin.nubecita.wear.data.db.WatchPendingPostEntity
import net.kikin.nubecita.wear.data.sync.WatchSync
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench flavor: by default a signed-in session and a phone that "has the
 * app", with no Data Layer traffic. Used by the Play Store screenshot lane
 * and the Wear journeys, which need every screen without a paired phone
 * running a matching build. [BenchScenario] switches to the other states.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface BenchWearDataModule {
    @Binds
    fun bindPhoneConnection(impl: BenchPhoneConnection): PhoneConnection

    @Binds
    fun bindPhoneSessionSource(impl: BenchPhoneSessionSource): PhoneSessionSource

    @Binds
    fun bindPhoneActionSupport(impl: BenchPhoneActionSupport): PhoneActionSupport

    @Binds
    fun bindWatchActionRepository(impl: BenchWatchActionRepository): WatchActionRepository

    @Binds
    fun bindWatchPostActionRepository(impl: BenchWatchPostActionRepository): WatchPostActionRepository

    @Binds
    fun bindWatchSync(impl: BenchWatchSync): WatchSync
}

/** Bench flavor: there are no Data Layer items to catch up on. */
internal class BenchWatchSync
    @Inject
    constructor() : WatchSync {
        override suspend fun catchUp() = Unit
    }

internal class BenchPhoneSessionSource
    @Inject
    constructor() : PhoneSessionSource {
        override val session: Flow<PhoneSession> = BenchScenarios.current.map { it.session() }.distinctUntilChanged()
    }

internal class BenchPhoneConnection
    @Inject
    constructor() : PhoneConnection {
        override suspend fun phoneAppStatus(): PhoneAppStatus = BenchScenarios.current.value.phoneAppStatus()

        override suspend fun requestSync() = Unit

        override fun openNubecitaOnPhone() = Unit

        override fun openUrlOnPhone(url: String) = Unit
    }

/** Bench flavor: the phone runs actions, so the controls show in screenshots, unless the scenario says otherwise. */
internal class BenchPhoneActionSupport
    @Inject
    constructor() : PhoneActionSupport {
        override suspend fun available() = BenchScenarios.current.value != BenchScenario.NoActions

        override suspend fun dmAvailable() = true

        override suspend fun postAvailable() = BenchScenarios.current.value != BenchScenario.NoPosting
    }

/**
 * Bench flavor: actions are confirmed in memory, so a tap toggles the control
 * with no phone: at once, or after [BenchScenario.SLOW_MS] in the slow
 * scenario so the pending state can be seen. Nothing reaches Bluesky.
 */
@Singleton
internal class BenchWatchActionRepository
    @Inject
    constructor() : WatchActionRepository {
        private val actions = MutableStateFlow(emptyList<WatchPendingActionEntity>())
        private var next = 0L

        override fun pending(accountDid: String): Flow<List<WatchPendingActionEntity>> = actions.map { all -> all.filter { it.accountDid == accountDid } }

        override suspend fun submit(
            accountDid: String,
            action: WearPostAction,
        ) {
            val id = next++
            val kind = PostActionKind.of(action)
            val record = if (kind.on && kind != PostActionKind.BOOKMARK) "at://did:plc:nubecitabench/${kind.wire}/$id" else null
            val pending = WatchPendingActionEntity(actionId = "bench-$id", accountDid = accountDid, postUri = action.postUri, kind = kind.wire, createdAtMs = id)
            val confirmed = pending.copy(confirmed = true, recordUri = record, completedAtMs = id)
            if (BenchScenarios.current.value == BenchScenario.SlowActions) {
                actions.update { it + pending }
                delay(BenchScenario.SLOW_MS)
                actions.update { all -> all.map { if (it.actionId == pending.actionId) confirmed else it } }
            } else {
                actions.update { it + confirmed }
            }
        }
    }

/** Bench flavor: what was posted from the watch this process, oldest first, so it shows in the feed. */
@Singleton
internal class BenchPostedPosts
    @Inject
    constructor() {
        val posts = MutableStateFlow(emptyList<Posted>())

        data class Posted(
            val text: String,
            val atMs: Long,
        )
    }

/**
 * Bench flavor: a post shows "Posting…" for [POSTING_MS], then joins the top
 * of the Following feed, in memory. In the failing scenario it ends as "Not
 * posted" instead, and so does a retry. Nothing reaches Bluesky.
 */
@Singleton
internal class BenchWatchPostActionRepository
    @Inject
    constructor(
        private val posted: BenchPostedPosts,
    ) : WatchPostActionRepository {
        private val rows = MutableStateFlow(emptyList<WatchPendingPostEntity>())
        private var next = 0L

        override fun pending(accountDid: String): Flow<List<WatchPendingPostEntity>> = rows.map { all -> all.filter { it.accountDid == accountDid } }

        override suspend fun post(
            accountDid: String,
            text: String,
        ) {
            val id = "bench-post-${next++}"
            rows.update { it + WatchPendingPostEntity(actionId = id, accountDid = accountDid, text = text, createdAtMs = next) }
            settle(id)
        }

        override suspend fun retry(actionId: String) {
            rows.update { all -> all.map { if (it.actionId == actionId) it.copy(status = WatchPendingPostEntity.STATUS_SENDING) else it } }
            settle(actionId)
        }

        private suspend fun settle(actionId: String) {
            delay(POSTING_MS)
            if (BenchScenarios.current.value == BenchScenario.PostFails) {
                rows.update { all -> all.map { if (it.actionId == actionId) it.copy(status = WatchPendingPostEntity.STATUS_FAILED) else it } }
                return
            }
            val row = rows.value.firstOrNull { it.actionId == actionId } ?: return
            // Into the feed first, so the text is never missing from both lists.
            posted.posts.update { it + BenchPostedPosts.Posted(row.text, System.currentTimeMillis()) }
            rows.update { all -> all.filterNot { it.actionId == actionId } }
        }

        private companion object {
            // Long enough for a journey step to see "Posting…".
            const val POSTING_MS = 10_000L
        }
    }
