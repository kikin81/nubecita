package net.kikin.nubecita.feature.moderation.impl.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.moderation.impl.ModerationRepository
import net.kikin.nubecita.feature.moderation.impl.data.DefaultModerationRepository
import net.kikin.nubecita.feature.moderation.impl.data.DefaultSubjectPreviewResolver
import net.kikin.nubecita.feature.moderation.impl.data.SubjectPreviewResolver
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Hilt bindings for the moderation data layer.
 *
 * `@Binds` is `internal` to `:feature:moderation:impl` — the
 * [ModerationRepository] interface is the only public surface, and
 * future moderation children (oftc.4 Block, oftc.5 Mute) will land
 * their bindings in this same module rather than introducing new ones.
 *
 * [SubjectPreviewResolver] is bound here too — it shares the resolver
 * concern with future moderation flows (Block/Mute confirmation
 * dialogs will want the same author handle + snippet header treatment).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModerationDataModule {
    @Binds
    @Singleton
    abstract fun bindModerationRepository(impl: DefaultModerationRepository): ModerationRepository

    @Binds
    @Singleton
    abstract fun bindSubjectPreviewResolver(impl: DefaultSubjectPreviewResolver): SubjectPreviewResolver

    companion object {
        /**
         * Wall clock for `ReportDialogViewModel`'s success-card `sentAt`
         * stamp. Production binds [Clock.System]; tests construct the VM
         * directly with a fixed-instant clock so the success state is
         * deterministic across runs. Lives here (rather than `:core:common`)
         * because it has no other consumers yet — promote when the second
         * needs it.
         */
        @Provides
        @OptIn(ExperimentalTime::class)
        fun provideClock(): Clock = Clock.System
    }
}
