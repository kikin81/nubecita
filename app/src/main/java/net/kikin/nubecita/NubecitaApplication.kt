package net.kikin.nubecita

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
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
