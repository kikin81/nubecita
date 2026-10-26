package net.kikin.nubecita.wear.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import javax.inject.Inject

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
