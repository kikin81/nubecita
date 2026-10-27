package net.kikin.nubecita.wear.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.wear.data.DataLayerWatchActionSender
import net.kikin.nubecita.wear.data.WatchActionSender
import net.kikin.nubecita.wear.data.db.WatchActionDao
import net.kikin.nubecita.wear.data.db.WatchDatabase
import net.kikin.nubecita.wear.data.db.WatchDmDao
import net.kikin.nubecita.wear.data.db.WatchFeedDao
import net.kikin.nubecita.wear.data.sync.DataLayerWatchSync
import net.kikin.nubecita.wear.data.sync.FileWatchImageStore
import net.kikin.nubecita.wear.data.sync.WatchImageStore
import net.kikin.nubecita.wear.data.sync.WatchSync
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object WatchDatabaseModule {
    @Provides
    @Singleton
    fun provideWatchDatabase(
        @ApplicationContext context: Context,
    ): WatchDatabase =
        Room
            .databaseBuilder(context, WatchDatabase::class.java, "watch.db")
            // Everything here is a copy of what the phone syncs; the next
            // sync refills it, so a destructive migration loses nothing.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWatchFeedDao(database: WatchDatabase): WatchFeedDao = database.feedDao()

    @Provides
    fun provideWatchActionDao(database: WatchDatabase): WatchActionDao = database.actionDao()

    @Provides
    fun provideWatchDmDao(database: WatchDatabase): WatchDmDao = database.dmDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal interface WatchDataBindsModule {
    @Binds
    fun bindWatchImageStore(impl: FileWatchImageStore): WatchImageStore

    @Binds
    fun bindWatchSync(impl: DataLayerWatchSync): WatchSync

    @Binds
    fun bindWatchActionSender(impl: DataLayerWatchActionSender): WatchActionSender

    // WatchFeedRepository, WatchActionRepository and WatchDmRepository are bound by the Wear app per flavor: Room in
    // production, fixtures in bench (Play Store screenshots).
}
