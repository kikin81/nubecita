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
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.core.wearcontract.WearPostAction
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.PostActionKind
import net.kikin.nubecita.wear.data.WatchActionRepository
import net.kikin.nubecita.wear.data.db.WatchPendingActionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench flavor: a signed-in session and a phone that always "has the app",
 * with no Data Layer traffic. Used by the Play Store screenshot lane, which
 * needs signed-in screens without a paired phone running a matching build.
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
}

internal class BenchPhoneSessionSource
    @Inject
    constructor() : PhoneSessionSource {
        override val session: Flow<PhoneSession> =
            flowOf(PhoneSession.SignedIn(WearAccount(did = "did:plc:nubecitabench", handle = "nubecita.bsky.social")))
    }

internal class BenchPhoneConnection
    @Inject
    constructor() : PhoneConnection {
        override suspend fun phoneAppStatus(): PhoneAppStatus = PhoneAppStatus.Installed

        override suspend fun requestSync() = Unit

        override fun openNubecitaOnPhone() = Unit
    }

/** Bench flavor: the phone always runs actions, so the controls show in screenshots. */
internal class BenchPhoneActionSupport
    @Inject
    constructor() : PhoneActionSupport {
        override suspend fun available() = true
    }

/**
 * Bench flavor: actions are confirmed at once, in memory, so a tap toggles the
 * control with no phone. Nothing reaches Bluesky.
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
            actions.update { it + WatchPendingActionEntity(actionId = "bench-$id", accountDid = accountDid, postUri = action.postUri, kind = kind.wire, createdAtMs = id, confirmed = true, recordUri = record, completedAtMs = id) }
        }
    }
