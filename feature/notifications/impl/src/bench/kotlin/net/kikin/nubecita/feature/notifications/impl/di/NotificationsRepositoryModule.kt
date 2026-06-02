package net.kikin.nubecita.feature.notifications.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.notifications.impl.data.BenchFakeNotificationsRepository
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository

/**
 * Bench-flavor counterpart to the production [NotificationsRepositoryModule] at
 * `feature/notifications/impl/src/production/.../di/NotificationsRepositoryModule.kt`.
 *
 * AGP source-set selection picks exactly one of the two per variant:
 * - `productionDebug` / `productionRelease` see the production module
 *   (binds `DefaultNotificationsRepository` → `NotificationsRepository`).
 * - `benchDebug` / `benchRelease` see this module (binds
 *   [BenchFakeNotificationsRepository] → `NotificationsRepository`).
 *
 * The shared FQN matters: both modules sit at
 * `net.kikin.nubecita.feature.notifications.impl.di.NotificationsRepositoryModule`,
 * so they cannot coexist on one variant's classpath. Source-set merging
 * takes care of the variant pick automatically — see `:feature:feed:impl`'s
 * parallel production/bench `FeedRepositoryModule` split for the established
 * precedent.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface NotificationsRepositoryModule {
    @Binds
    fun bindNotificationsRepository(impl: BenchFakeNotificationsRepository): NotificationsRepository
}
