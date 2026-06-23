package net.kikin.nubecita.core.review

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Supplies the app's OS-recorded first-install time, used by [ReviewPolicy] for
 * the "≥N days since install" gate. Preferred over a self-stamped timestamp: it
 * reflects long-time users correctly on upgrade (no artificial delay), survives
 * app-data clears, and needs no startup initializer. Faked in manager tests.
 */
internal fun interface InstallTimeProvider {
    fun firstInstallTime(): Instant
}

internal class DefaultInstallTimeProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : InstallTimeProvider {
        // getPackageInfo(name, flags: Int) is deprecated on API 33+ in favour of
        // the PackageInfoFlags overload, but the int form is correct on all our
        // supported levels (min 24) and avoids version branching for a single read.
        @Suppress("DEPRECATION")
        override fun firstInstallTime(): Instant =
            Instant.fromEpochMilliseconds(
                context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime,
            )
    }
