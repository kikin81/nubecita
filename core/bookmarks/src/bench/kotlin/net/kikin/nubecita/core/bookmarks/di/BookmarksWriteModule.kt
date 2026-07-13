package net.kikin.nubecita.core.bookmarks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.internal.BenchFakeBookmarkRepository
import javax.inject.Singleton

/**
 * Bench-flavor counterpart to the production [BookmarksWriteModule] at
 * `core/bookmarks/src/production/.../di/BookmarksWriteModule.kt`. Binds the
 * offline no-op [BenchFakeBookmarkRepository] so the bench build never issues a
 * bookmark network call. The shared FQN is intentional — see the production
 * module's KDoc.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarksWriteModule {
    @Binds
    @Singleton
    internal abstract fun bindBookmarkRepository(
        impl: BenchFakeBookmarkRepository,
    ): BookmarkRepository
}
