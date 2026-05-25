package net.kikin.nubecita.core.push.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the `@DrawableRes Int` that
 * [net.kikin.nubecita.core.push.PushNotificationBuilder] passes to
 * `NotificationCompat.Builder.setSmallIcon`. Provided by `:app` (or any
 * consumer) so `:core:push` stays free of branding.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PushSmallIconRes
