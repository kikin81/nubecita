package net.kikin.nubecita.core.common.time.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Provides the app-wide wall [Clock]. Production binds [Clock.System]; tests
 * construct consumers directly with a fixed-instant clock so time-dependent
 * state is deterministic.
 *
 * Canonical home for the `Clock` binding — hoisted here from
 * `:feature:moderation:impl` once a second consumer (`:core:review`) appeared,
 * as that module's original comment anticipated.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    @Provides
    @OptIn(ExperimentalTime::class)
    fun provideClock(): Clock = Clock.System
}
