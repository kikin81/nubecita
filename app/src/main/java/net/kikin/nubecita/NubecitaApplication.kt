package net.kikin.nubecita

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kikin.nubecita.bootstrap.AppInitializer
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.logging.CrashlyticsTree
import net.kikin.nubecita.core.push.NotificationChannelInstaller
import net.kikin.nubecita.firebase.appCheckFactory
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NubecitaApplication :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    // Lazy: Coil constructs the loader on its first `newImageLoader` call (first
    // image), which is after the first frame — no need to build it during onCreate
    // field injection (nubecita-z04l).
    @Inject lateinit var imageLoader: Lazy<ImageLoader>

    @Inject lateinit var notificationChannelInstaller: NotificationChannelInstaller

    /**
     * Hilt-aware factory for `@HiltWorker` workers (the background DM-poll
     * worker, nubecita-1fy.15). Supplied to WorkManager via
     * [workManagerConfiguration]; the default `androidx.startup`
     * `WorkManagerInitializer` is removed in the manifest so this Hilt
     * configuration is the one WorkManager uses (on-demand init on first
     * `WorkManager.getInstance(context)`). Inert until a worker is enqueued —
     * an empty factory map is valid, including in the bench flavor.
     *
     * Lazy: WorkManager reads [workManagerConfiguration] on first
     * `WorkManager.getInstance(context)` (on-demand), so the factory needn't be
     * built during onCreate field injection (nubecita-z04l).
     */
    @Inject lateinit var workerFactory: Lazy<HiltWorkerFactory>

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory.get())
                .build()

    /**
     * Per-flavor startup hooks. Under `production`, contains lambdas wrapping
     * `AppLifecycleObserver.start()`, `PushRegistrationCoordinator.start()`,
     * and `NotificationsPollingObserver.start()`. Under `bench`, empty —
     * those three coordinators are never constructed, Firebase Messaging
     * stays dormant, and the APK emits zero network traffic during the
     * Macrobench measurement window. See `bootstrap/AppInitializer.kt`.
     */
    @Inject lateinit var appInitializers: Lazy<Set<@JvmSuppressWildcards AppInitializer>>

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    // Production binds FirebaseCrashReporter; bench binds NoOpCrashReporter
    // (so the field resolves in every flavor even though the tree is only
    // planted in production below). See `:core:logging`.
    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()

        // Install App Check provider before any Firebase service is touched.
        // FirebaseInitProvider has already run (it's a manifest ContentProvider,
        // earlier than Application.onCreate), so FirebaseAppCheck.getInstance() is
        // safe here. The factory is selected at compile time via source-set split:
        // app/src/debug/.../AppCheckFactory.kt vs app/src/release/.../AppCheckFactory.kt
        // — keeps DebugAppCheckProviderFactory off the release classpath.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory())

        // Logging trees by build:
        //  - debug            → DebugTree (logcat only).
        //  - productionRelease → CrashlyticsTree: Timber.e/wtf become Crashlytics
        //                        non-fatals; Timber.w becomes a breadcrumb (context,
        //                        not an issue) — see CrashlyticsTree's convention.
        //  - bench release     → no tree, so every Timber.* short-circuits and the
        //                        Macrobench APK stays silent.
        when {
            BuildConfig.DEBUG -> Timber.plant(Timber.DebugTree())
            BuildConfig.FLAVOR == "production" -> Timber.plant(CrashlyticsTree(crashReporter))
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
        // Defer the AppInitializer graph off the onCreate critical path. `appInitializers`
        // is now `Lazy`, so super.onCreate()'s field injection no longer constructs it —
        // that deep graph (repositories, DAOs) was blocking the main thread during
        // onCreate and ANR'ing background cold starts (nubecita-jicb). Build it on
        // Dispatchers.Default, then run the cheap fire-and-forget start()s back on Main
        // (lifecycle registration / ProcessLifecycleOwner are main-thread-only). This
        // runs on background process starts too — it's an app-scope coroutine, not a frame.
        applicationScope.launch(Dispatchers.Default) {
            // ApplicationScope has a SupervisorJob but no CoroutineExceptionHandler,
            // so an uncaught throw here would reach the global handler and crash the
            // app. These initializers are non-critical (polling/analytics/push); a
            // failure is logged (a Crashlytics non-fatal in production) rather than
            // fatal. CancellationException is rethrown to preserve cooperative cancel.
            try {
                val initializers = appInitializers.get()
                withContext(Dispatchers.Main) { initializers.forEach { it.start() } }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                Timber.e(t, "AppInitializer startup failed")
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()
}
