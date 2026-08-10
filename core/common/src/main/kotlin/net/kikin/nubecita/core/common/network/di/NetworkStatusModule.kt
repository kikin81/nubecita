package net.kikin.nubecita.core.common.network.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.network.DefaultNetworkStatus
import net.kikin.nubecita.core.common.network.NetworkStatus
import javax.inject.Singleton

/**
 * Binds the metered-connection signal.
 *
 * Public (with an internal binding inside) so a downstream instrumentation test
 * can swap it via `@TestInstallIn(replaces = [NetworkStatusModule::class])` and
 * drive the metered state directly — otherwise a wifi-only autoplay test would
 * depend on the emulator's actual connection. Mirrors `PostingModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkStatusModule {
    @Binds
    @Singleton
    internal abstract fun bindNetworkStatus(impl: DefaultNetworkStatus): NetworkStatus
}
