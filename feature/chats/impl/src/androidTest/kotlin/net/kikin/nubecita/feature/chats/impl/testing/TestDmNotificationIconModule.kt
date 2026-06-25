package net.kikin.nubecita.feature.chats.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.chats.impl.worker.DmNotificationSmallIcon

/**
 * Test-only provider for `@DmNotificationSmallIcon` (`@DrawableRes Int`), which
 * `MessagingStyleDmNotifier` injects. The production binding lives in
 * `:app/src/main/.../ChatsAppModule.kt` (`R.drawable.ic_launcher_foreground`),
 * which is not on the classpath when running `:feature:chats:impl/src/androidTest/`.
 *
 * Without this shim the module's androidTest Hilt graph fails to build with
 * `[Dagger/MissingBinding] @DmNotificationSmallIcon java.lang.Integer cannot be
 * provided` once `DmReplyHandler` → `MessagingStyleDmNotifier` is reachable from
 * any `@HiltAndroidTest` in the set (nubecita-29rw).
 *
 * Uses `@InstallIn(SingletonComponent::class)` rather than `@TestInstallIn`
 * because the production module isn't visible from this module's androidTest
 * classpath — a graph-completion shim, not a swap. Mirrors [TestOAuthConfigModule].
 */
@Module
@InstallIn(SingletonComponent::class)
internal object TestDmNotificationIconModule {
    @Provides
    @DmNotificationSmallIcon
    fun provideDmNotificationSmallIconRes(): Int = android.R.drawable.stat_notify_chat
}
