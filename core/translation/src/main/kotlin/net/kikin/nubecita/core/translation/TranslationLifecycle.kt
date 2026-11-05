package net.kikin.nubecita.core.translation

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Releases the translation models when the app goes to the background.
 *
 * The **process** lifecycle, not an activity's: the memory belongs to the
 * process, and a rotation is no reason to throw away a loaded model. With five
 * translators resident the process measured around 400MB RSS — enough to make
 * the app an appealing target for the low-memory killer while it sits in the
 * background doing nothing.
 *
 * Both dependencies are [Lazy] so that registering the observer does not
 * itself build them. A reader who never translates should never construct an
 * engine, and `start()` runs for everyone.
 */
@Singleton
class TranslationLifecycle
    @Inject
    internal constructor(
        private val engine: Lazy<TranslationEngine>,
        private val identifier: Lazy<LanguageIdentifier>,
    ) : DefaultLifecycleObserver {
        /** Begin watching the process lifecycle. Call once, from the main thread. */
        fun start() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        override fun onStop(owner: LifecycleOwner) {
            engine.get().release()
            identifier.get().release()
        }
    }
