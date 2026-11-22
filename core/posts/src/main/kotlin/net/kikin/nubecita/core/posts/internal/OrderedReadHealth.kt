package net.kikin.nubecita.core.posts.internal

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this process has already reported that the ordered thread read
 * (`getPostThreadV2`) is unusable.
 *
 * The thread repository is unscoped, one per screen, and while the endpoint is
 * down every thread open falls back to the older read. Reporting each of those
 * as an error would spend a session's worth of Crashlytics non-fatals on the
 * same fact; reporting none would hide it. So the first one in a process is the
 * error, and the rest are breadcrumbs. This is the process-wide memory of
 * "already said".
 */
@Singleton
internal class OrderedReadHealth
    @Inject
    constructor() {
        private val reported = AtomicBoolean(false)

        /** True exactly once per process: for the first unusable read. */
        fun isFirstUnusableRead(): Boolean = reported.compareAndSet(false, true)
    }
