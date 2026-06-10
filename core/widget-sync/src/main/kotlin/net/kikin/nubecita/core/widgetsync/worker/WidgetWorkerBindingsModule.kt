package net.kikin.nubecita.core.widgetsync.worker

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.widgetsync.WidgetRefreshLauncher

/**
 * Binds the [WidgetRefreshRunner] / scheduler collaborators. Single-component,
 * all variants — inert in bench because the worker is never scheduled there
 * (the bench flavor's empty `AppInitializer` set omits the scheduler).
 * Mirrors `:feature:chats:impl`'s `DmWorkerBindingsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetWorkerBindingsModule {
    @Binds
    abstract fun bindAppForegroundSignal(impl: ProcessLifecycleForegroundSignal): AppForegroundSignal

    @Binds
    abstract fun bindWidgetWorkScheduler(impl: WorkManagerWidgetWorkScheduler): WidgetWorkScheduler

    @Binds
    abstract fun bindWidgetRefreshLauncher(impl: DefaultWidgetRefreshLauncher): WidgetRefreshLauncher
}
