package net.kikin.nubecita.feature.chats.impl.worker

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.wearsync.WearActionHandler

/**
 * Binds the [DmPollRunner] collaborators. Single-component, all variants —
 * inert in bench because the worker is never scheduled there.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DmWorkerBindingsModule {
    @Binds
    abstract fun bindAppForegroundSignal(impl: ProcessLifecycleForegroundSignal): AppForegroundSignal

    @Binds
    abstract fun bindDmNotifier(impl: MessagingStyleDmNotifier): DmNotifier

    @Binds
    abstract fun bindDmWorkScheduler(impl: WorkManagerDmWorkScheduler): DmWorkScheduler

    /** Watch quick replies and reactions (W4), into `:core:wear-sync`'s handler set. */
    @Binds
    @IntoSet
    abstract fun bindDmWearActionHandler(impl: DmWearActionHandler): WearActionHandler
}
