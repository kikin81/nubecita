package net.kikin.nubecita

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.HiltAndroidApp
import net.kikin.nubecita.core.push.AppLifecycleObserver
import net.kikin.nubecita.core.push.NotificationChannelInstaller
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import net.kikin.nubecita.firebase.appCheckFactory
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NubecitaApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: ImageLoader

    @Inject lateinit var notificationChannelInstaller: NotificationChannelInstaller

    @Inject lateinit var pushRegistrationCoordinator: PushRegistrationCoordinator

    @Inject lateinit var appLifecycleObserver: AppLifecycleObserver

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

        // Push notifications wiring — order matters:
        // 1. Install channels first so any inbound push posted before
        //    PushRegistrationCoordinator.start() returns finds its channel
        //    already created (createNotificationChannel is idempotent and
        //    no-ops on a duplicate ID + same configuration).
        // 2. Start the lifecycle observer second — it hydrates the muted-actor
        //    snapshot from disk and schedules a foreground refresh, both
        //    fire-and-forget into the application scope.
        // 3. Start the registration coordinator third — it begins collecting
        //    SessionStateProvider.state and reacts to whatever session state
        //    MainActivity's refresh() resolves to (Loading initially).
        notificationChannelInstaller.install(this)
        appLifecycleObserver.start()
        // Coordinator.start() also opts FCM auto-init back on. The manifest
        // disables auto-init for instrumented-test safety; reaching this
        // line implies we're running under the real NubecitaApplication
        // (not HiltTestApplication) so the SERVICE Hilt component is set
        // up correctly and it's safe to let Firebase instantiate
        // NubecitaFcmService.
        pushRegistrationCoordinator.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
