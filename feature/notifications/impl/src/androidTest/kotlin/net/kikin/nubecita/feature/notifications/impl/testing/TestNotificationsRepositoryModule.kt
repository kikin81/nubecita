package net.kikin.nubecita.feature.notifications.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import net.kikin.nubecita.feature.notifications.impl.di.NotificationsRepositoryModule

/**
 * Replaces the production [NotificationsRepositoryModule] in
 * `@HiltAndroidTest` instrumentation tests with a binding that wires
 * `NotificationsRepository → FakeNotificationsRepository`. Mirrors
 * `:feature:feed:impl`'s `TestFeedRepositoryModule`.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NotificationsRepositoryModule::class],
)
internal interface TestNotificationsRepositoryModule {
    @Binds
    fun bindFakeNotificationsRepository(impl: FakeNotificationsRepository): NotificationsRepository
}
