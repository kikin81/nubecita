package net.kikin.nubecita.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.R
import net.kikin.nubecita.feature.chats.impl.worker.DmNotificationSmallIcon

/**
 * `:app`-side binding for `:feature:chats:impl`'s branding-dependent config:
 * `@DmNotificationSmallIcon` is the status-bar icon for background DM
 * notifications. Reuses `ic_launcher_foreground` (same as `@PushSmallIconRes`);
 * kept in `:app` so the feature module carries no branding.
 */
@Module
@InstallIn(SingletonComponent::class)
object ChatsAppModule {
    @Provides
    @DmNotificationSmallIcon
    fun provideDmNotificationSmallIconRes(): Int = R.drawable.ic_launcher_foreground
}
