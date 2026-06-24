package net.kikin.nubecita.core.update.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.update.AppUpdateClient
import net.kikin.nubecita.core.update.DefaultInAppUpdateController
import net.kikin.nubecita.core.update.DefaultUpdatePreferences
import net.kikin.nubecita.core.update.InAppUpdateController
import net.kikin.nubecita.core.update.PlayAppUpdateClient
import net.kikin.nubecita.core.update.UpdatePreferences
import timber.log.Timber
import javax.inject.Singleton

/**
 * Production-flavor Hilt wiring for `:core:update`. Binds the real
 * Play-backed implementations and provides the capability's own DataStore and
 * Play `AppUpdateManager`.
 *
 * The shared FQN `net.kikin.nubecita.core.update.di.UpdateModule` matters: the
 * bench parallel at `src/bench/.../di/UpdateModule.kt` binds the no-op
 * controller, and the two cannot coexist on one variant's classpath. Mirrors
 * `:core:review`'s production/bench `ReviewModule` split.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindController(impl: DefaultInAppUpdateController): InAppUpdateController

    @Binds
    @Singleton
    abstract fun bindClient(impl: PlayAppUpdateClient): AppUpdateClient

    @Binds
    @Singleton
    abstract fun bindPreferences(impl: DefaultUpdatePreferences): UpdatePreferences

    companion object {
        @Provides
        @Singleton
        fun provideAppUpdateManager(
            @ApplicationContext context: Context,
        ): AppUpdateManager = AppUpdateManagerFactory.create(context)

        @Provides
        @Singleton
        @UpdateDataStore
        fun provideUpdateDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler {
                        Timber.w(it, "Update preferences corrupted; replacing with empty store")
                        emptyPreferences()
                    },
                produceFile = { context.preferencesDataStoreFile("update_preferences") },
            )
    }
}
