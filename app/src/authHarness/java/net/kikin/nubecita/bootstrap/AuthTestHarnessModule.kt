package net.kikin.nubecita.bootstrap

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Registers the harness receiver at startup (nubecita-zh7p). Lives in the
 * `authHarness` source set alongside the receiver, so a build that did not opt
 * in contributes nothing to the initializer set and has no harness bindings in
 * its graph at all.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AuthTestHarnessModule {
    @Provides
    @IntoSet
    fun provideAuthTestHarnessInitializer(
        @ApplicationContext context: Context,
        receiver: AuthTestHarnessReceiver,
    ): AppInitializer = AppInitializer { receiver.register(context) }
}
