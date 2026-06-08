package net.kikin.nubecita.feature.chats.impl.worker

import javax.inject.Qualifier

/**
 * Hilt qualifier for the `@DrawableRes Int` status-bar icon
 * [MessagingStyleDmNotifier] passes to `NotificationCompat.Builder.setSmallIcon`.
 * Provided by `:app` so `:feature:chats:impl` carries no branding (mirrors
 * `:core:push`'s `@PushSmallIconRes`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DmNotificationSmallIcon
