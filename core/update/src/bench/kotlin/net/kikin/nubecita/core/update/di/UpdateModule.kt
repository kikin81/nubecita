package net.kikin.nubecita.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.update.BenchNoOpInAppUpdateController
import net.kikin.nubecita.core.update.InAppUpdateController
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production `UpdateModule` at
 * `core/update/src/production/.../di/UpdateModule.kt`. AGP source-set selection
 * picks exactly one of the two per variant; this one binds the no-op
 * [BenchNoOpInAppUpdateController], so the bench build issues zero Play calls and
 * pulls in none of the DataStore / Play providers. Shared FQN, mirrors
 * `:core:review`'s bench `ReviewModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindController(impl: BenchNoOpInAppUpdateController): InAppUpdateController
}
