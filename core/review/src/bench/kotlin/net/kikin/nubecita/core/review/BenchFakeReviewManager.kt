package net.kikin.nubecita.core.review

import android.app.Activity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [ReviewManager]: a pure no-op. The keyless / macrobenchmark
 * builds must make zero Google Play calls (a real review card would steal
 * window focus and break UI automation), so neither stamping nor prompting
 * happens here. Bound by the bench `ReviewModule`.
 */
@Singleton
internal class BenchFakeReviewManager
    @Inject
    constructor() : ReviewManager {
        override suspend fun onPostPublished(activity: Activity) = Unit
    }
