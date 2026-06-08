package net.kikin.nubecita.feature.chats.impl.worker

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the [DmPollRunner] collaborators. [DmNotifier] is bound to the
 * placeholder [LoggingDmNotifier]; §5 replaces it with the real `MessagingStyle`
 * notifier (swap this one binding). Single-component, all variants — inert in
 * bench because the worker is never scheduled there.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DmWorkerBindingsModule {
    @Binds
    abstract fun bindAppForegroundSignal(impl: ProcessLifecycleForegroundSignal): AppForegroundSignal

    @Binds
    abstract fun bindDmNotifier(impl: LoggingDmNotifier): DmNotifier
}
