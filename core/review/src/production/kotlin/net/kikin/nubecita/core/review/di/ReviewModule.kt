package net.kikin.nubecita.core.review.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import net.kikin.nubecita.core.review.DefaultReviewManager
import net.kikin.nubecita.core.review.DefaultReviewPreferences
import net.kikin.nubecita.core.review.PlayReviewClient
import net.kikin.nubecita.core.review.ReviewClient
import net.kikin.nubecita.core.review.ReviewManager
import net.kikin.nubecita.core.review.ReviewPreferences
import timber.log.Timber
import javax.inject.Singleton
import com.google.android.play.core.review.ReviewManager as PlayReviewManager

/**
 * Production-flavor Hilt wiring for `:core:review`. Binds the real
 * Play-backed implementations and provides the capability's own DataStore,
 * clock, and Play `ReviewManager`.
 *
 * The shared FQN `net.kikin.nubecita.core.review.di.ReviewModule` matters: the
 * bench parallel at `src/bench/.../di/ReviewModule.kt` binds the no-op manager,
 * and the two cannot coexist on one variant's classpath. Mirrors
 * `:core:actors`' production/bench `ActorsModule` split.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReviewModule {
    @Binds
    @Singleton
    abstract fun bindReviewManager(impl: DefaultReviewManager): ReviewManager

    @Binds
    @Singleton
    abstract fun bindReviewClient(impl: PlayReviewClient): ReviewClient

    @Binds
    @Singleton
    abstract fun bindReviewPreferences(impl: DefaultReviewPreferences): ReviewPreferences

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.System

        @Provides
        @Singleton
        fun providePlayReviewManager(
            @ApplicationContext context: Context,
        ): PlayReviewManager = ReviewManagerFactory.create(context)

        @Provides
        @Singleton
        @ReviewDataStore
        fun provideReviewDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler =
                    ReplaceFileCorruptionHandler {
                        Timber.w(it, "Review preferences corrupted; replacing with empty store")
                        emptyPreferences()
                    },
                produceFile = { context.preferencesDataStoreFile("review_preferences") },
            )
    }
}
