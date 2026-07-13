package net.kikin.nubecita.core.bookmarks.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.bookmarks.BookmarkRepository
import net.kikin.nubecita.core.bookmarks.internal.DefaultBookmarkRepository
import javax.inject.Singleton

/**
 * Production-flavor Hilt binding for the bookmark repository (real network).
 *
 * AGP source-set selection picks exactly one bookmarks write module per variant:
 * - `productionDebug` / `productionRelease` see **this** module (binds
 *   [DefaultBookmarkRepository] → [BookmarkRepository]).
 * - `benchDebug` / `benchRelease` see the bench counterpart at
 *   `core/bookmarks/src/bench/.../di/BookmarksWriteModule.kt` (offline no-op
 *   fake).
 *
 * The shared FQN (`net.kikin.nubecita.core.bookmarks.di.BookmarksWriteModule`) is
 * intentional: both modules cannot coexist on one variant's classpath. Mirrors
 * the `PostInteractionsWriteModule` split in `:core:post-interactions`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarksWriteModule {
    @Binds
    @Singleton
    internal abstract fun bindBookmarkRepository(
        impl: DefaultBookmarkRepository,
    ): BookmarkRepository
}
