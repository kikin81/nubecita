package net.kikin.nubecita

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.hilt.android.HiltAndroidApp
import net.kikin.nubecita.firebase.appCheckFactory
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NubecitaApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var imageLoader: ImageLoader

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
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader
}
