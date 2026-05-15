package net.kikin.nubecita.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.database.MANUAL_MIGRATIONS
import net.kikin.nubecita.core.database.NubecitaDatabase
import javax.inject.Singleton

/**
 * Provides the singleton [NubecitaDatabase]. No
 * `fallbackToDestructiveMigration` — migration failures should surface
 * as crashes in development so the schema export + migration test
 * harness has a chance to catch them before release.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesNubecitaDatabase(
        @ApplicationContext context: Context,
    ): NubecitaDatabase =
        Room
            .databaseBuilder(
                context,
                NubecitaDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(*MANUAL_MIGRATIONS)
            .build()

    private const val DATABASE_NAME = "nubecita.db"
}
