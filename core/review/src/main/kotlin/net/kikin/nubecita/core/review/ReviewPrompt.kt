package net.kikin.nubecita.core.review

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.ReviewTrigger

/**
 * Ask for a review at a positive moment, if the reader is eligible.
 *
 * One call from a screen, so a trigger point needs no new ViewModel plumbing.
 * The request needs an Activity, which a ViewModel never has, and it must
 * outlive the screen that asked (closing the gallery pops it on the next
 * frame), so it runs on the Activity's own scope rather than a screen's.
 */
fun Activity.requestReviewIfEligible(trigger: ReviewTrigger) {
    val owner = this as? LifecycleOwner ?: return
    val manager = EntryPointAccessors.fromApplication(applicationContext, ReviewEntryPoint::class.java).reviewManager()
    owner.lifecycleScope.launch { manager.onPositiveMoment(this@requestReviewIfEligible, trigger) }
}

/** Reaches the singleton [ReviewManager] from screens, which can't inject it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReviewEntryPoint {
    fun reviewManager(): ReviewManager
}
