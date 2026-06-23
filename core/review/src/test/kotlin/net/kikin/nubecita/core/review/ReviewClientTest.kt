package net.kikin.nubecita.core.review

import android.app.Activity
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import com.google.android.play.core.review.ReviewManager as PlayReviewManager

@OptIn(ExperimentalCoroutinesApi::class)
internal class ReviewClientTest {
    @Test
    fun `requestReview wraps the play ReviewInfo in a handle`() =
        runTest {
            val info = mockk<ReviewInfo>()
            val play = mockk<PlayReviewManager>()
            every { play.requestReviewFlow() } returns Tasks.forResult(info)

            val handle = PlayReviewClient(play).requestReview(mockk(relaxed = true))

            assertSame(info, handle.raw)
        }

    @Test
    fun `launchReview delegates to play launchReviewFlow with the handle's info`() =
        runTest {
            val info = mockk<ReviewInfo>()
            val activity = mockk<Activity>(relaxed = true)
            val play = mockk<PlayReviewManager>()
            every { play.launchReviewFlow(activity, info) } returns Tasks.forResult(null)

            PlayReviewClient(play).launchReview(activity, ReviewHandle(info))

            verify { play.launchReviewFlow(activity, info) }
        }
}
