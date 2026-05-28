package net.kikin.nubecita

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.HiltAndroidApp
import net.kikin.nubecita.bootstrap.AppInitializer
import net.kikin.nubecita.core.push.NotificationChannelInstaller
import net.kikin.nubecita.firebase.appCheckFactory
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NubecitaApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var notificationChannelInstaller: NotificationChannelInstaller

    /**
     * Per-flavor startup hooks. Under `production`, contains lambdas wrapping
     * `AppLifecycleObserver.start()`, `PushRegistrationCoordinator.start()`,
     * and `NotificationsPollingObserver.start()`. Under `bench`, empty —
     * those three coordinators are never constructed, Firebase Messaging
     * stays dormant, and the APK emits zero network traffic during the
     * Macrobench measurement window. See `bootstrap/AppInitializer.kt`.
     */
    @Inject lateinit var appInitializers: Set<@JvmSuppressWildcards AppInitializer>

    override fun onCreate() {
        super.onCreate()

        // Install App Check provider before any Firebase service is touched.
        // FirebaseInitProvider has already run (it's a manifest ContentProvider,
        // earlier than Application.onCreate), so FirebaseAppCheck.getInstance() is
        // safe here. The factory is selected at compile time via source-set split:
        // app/src/debug/.../AppCheckFactory.kt vs app/src/release/.../AppCheckFactory.kt
        // — keeps DebugAppCheckProviderFactory off the release classpath.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory())

        // Plant a DebugTree in debug builds only — release builds get no tree, so
        // every Timber.* call short-circuits to a no-op without a per-call BuildConfig
        // check at the use site. Crash-reporter trees (Crashlytics, Sentry) plug in
        // here when added.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Push-related startup wiring — channel installation is direct
        // because the must-precede-coordinator constraint matters: any
        // inbound push delivered before the registration coordinator opts
        // FCM auto-init back on must find its NotificationChannel already
        // created (createNotificationChannel is idempotent on a duplicate
        // ID + same configuration). The remaining three coordinators
        // (AppLifecycleObserver, PushRegistrationCoordinator,
        // NotificationsPollingObserver) flow through `appInitializers`
        // so the bench-flavor source set can supply an empty set and
        // keep the Macrobench APK silent. Iteration order across the
        // set is undefined — the three coordinators are independent and
        // fire-and-forget into the application scope.
        notificationChannelInstaller.install(this)
        appInitializers.forEach { it.start() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
