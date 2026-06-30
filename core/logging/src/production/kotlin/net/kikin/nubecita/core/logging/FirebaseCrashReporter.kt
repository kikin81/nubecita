package net.kikin.nubecita.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject

/** Production [CrashReporter] backed by the Firebase Crashlytics singleton. */
internal class FirebaseCrashReporter
    @Inject
    constructor(
        private val crashlytics: FirebaseCrashlytics,
    ) : CrashReporter {
        override fun log(message: String) {
            crashlytics.log(message)
        }

        override fun recordException(throwable: Throwable) {
            crashlytics.recordException(throwable)
        }

        override fun setCustomKey(
            key: String,
            value: String,
        ) {
            crashlytics.setCustomKey(key, value)
        }
    }
