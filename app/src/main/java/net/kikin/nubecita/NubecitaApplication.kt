package net.kikin.nubecita

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp
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
        // safe here.
        val factory =
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)

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
