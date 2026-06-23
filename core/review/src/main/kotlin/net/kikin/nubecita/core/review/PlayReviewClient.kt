package net.kikin.nubecita.core.review

import android.app.Activity
import com.google.android.play.core.review.ReviewInfo
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.android.play.core.review.ReviewManager as PlayReviewManager

/**
 * [ReviewClient] over the real Google Play review API. A thin adapter: it
 * awaits Play's `Task`s via `kotlinx-coroutines-play-services` and shuttles the
 * `ReviewInfo` through an opaque [ReviewHandle]. The injected [playManager] is
 * created once from the application `Context`; only [launchReview] needs the
 * foreground [Activity].
 */
internal class PlayReviewClient
    @Inject
    constructor(
        private val playManager: PlayReviewManager,
    ) : ReviewClient {
        override suspend fun requestReview(activity: Activity): ReviewHandle = ReviewHandle(playManager.requestReviewFlow().await())

        override suspend fun launchReview(
            activity: Activity,
            handle: ReviewHandle,
        ) {
            playManager.launchReviewFlow(activity, handle.raw as ReviewInfo).await()
        }
    }
