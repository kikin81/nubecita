package net.kikin.nubecita.core.review

import android.app.Activity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.ReviewRequested
import net.kikin.nubecita.core.analytics.ReviewTrigger
import net.kikin.nubecita.core.analytics.UserProperty
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultReviewManagerTest {
    private val now = Instant.parse("2026-06-23T12:00:00Z")
    private val activity = mockk<Activity>(relaxed = true)
    private val analytics = RecordingAnalyticsClient()

    private val reader =
        ReviewState(
            successfulPostCount = 0,
            requestCount = 0,
            lastRequestedAt = null,
            activeDayCount = 5,
            likeCount = 3,
        )

    @Test
    fun `an eligible reader is asked at a positive moment`() =
        runTest {
            val prefs = FakeReviewPreferences(reader)
            val client = FakeReviewClient()

            manager(prefs, client).onPositiveMoment(activity, ReviewTrigger.Follow)

            assertEquals(1, client.launchCalls)
            assertEquals(1, prefs.recordCalls)
        }

    @Test
    fun `a positive moment does nothing for someone not yet eligible`() =
        runTest {
            val prefs = FakeReviewPreferences(reader.copy(activeDayCount = 1))
            val client = FakeReviewClient()

            manager(prefs, client).onPositiveMoment(activity, ReviewTrigger.Share)

            assertEquals(0, client.requestCalls)
        }

    /** The trigger is the whole point of the event: it says which moment earns reviews. */
    @Test
    fun `a request is logged with the moment that triggered it`() =
        runTest {
            manager(FakeReviewPreferences(reader), FakeReviewClient())
                .onPositiveMoment(activity, ReviewTrigger.MediaExit)

            assertEquals(listOf<AnalyticsEvent>(ReviewRequested(ReviewTrigger.MediaExit)), analytics.events)
        }

    @Test
    fun `a post that earns a request is logged as the post trigger`() =
        runTest {
            manager(FakeReviewPreferences(eligible), FakeReviewClient()).onPostPublished(activity)

            assertEquals(listOf<AnalyticsEvent>(ReviewRequested(ReviewTrigger.Post)), analytics.events)
        }

    @Test
    fun `a failed request logs nothing`() =
        runTest {
            manager(FakeReviewPreferences(reader), FakeReviewClient(requestThrows = true))
                .onPositiveMoment(activity, ReviewTrigger.Follow)

            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `likes and active days reach the counters`() =
        runTest {
            val prefs = FakeReviewPreferences(reader.copy(activeDayCount = 0, likeCount = 0))
            val manager = manager(prefs, FakeReviewClient())

            manager.recordLike()
            manager.recordActiveDay()

            assertEquals(1, prefs.currentState().likeCount)
            assertEquals(1, prefs.currentState().activeDayCount)
        }

    private val eligible =
        ReviewState(
            successfulPostCount = 5,
            requestCount = 0,
            lastRequestedAt = null,
        )

    @Test
    fun `ineligible publish increments post count but does not request`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible.copy(successfulPostCount = 0))
            val client = FakeReviewClient()

            manager(prefs, client).onPostPublished(activity)

            assertEquals(1, prefs.incrementCalls)
            assertEquals(0, client.requestCalls)
            assertEquals(0, prefs.recordCalls)
        }

    @Test
    fun `eligible publish requests, launches, and records the attempt`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible)
            val client = FakeReviewClient()

            manager(prefs, client).onPostPublished(activity)

            assertEquals(1, client.requestCalls)
            assertEquals(1, client.launchCalls)
            assertEquals(1, prefs.recordCalls)
            assertEquals(now, prefs.recordedAt)
        }

    @Test
    fun `request failure is silent and not recorded`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible)
            val client = FakeReviewClient(requestThrows = true)

            manager(prefs, client).onPostPublished(activity)

            assertEquals(1, client.requestCalls)
            assertEquals(0, client.launchCalls)
            assertEquals(0, prefs.recordCalls)
        }

    @Test
    fun `launch failure is silent but the attempt is recorded`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible)
            val client = FakeReviewClient(launchThrows = true)

            manager(prefs, client).onPostPublished(activity)

            assertEquals(1, client.launchCalls)
            assertEquals(1, prefs.recordCalls)
        }

    @Test
    fun `within cooldown does not request`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible.copy(requestCount = 1, lastRequestedAt = now - 10.days))
            val client = FakeReviewClient()

            manager(prefs, client).onPostPublished(activity)

            assertEquals(0, client.requestCalls)
            assertEquals(1, prefs.incrementCalls)
        }

    private fun manager(
        prefs: ReviewPreferences,
        client: ReviewClient,
    ) = DefaultReviewManager(
        client,
        prefs,
        // Installed 10 days ago — clears the new-user window.
        InstallTimeProvider { now - 10.days },
        fixedClock(now),
        UnconfinedTestDispatcher(),
        analytics,
    )

    private fun fixedClock(instant: Instant) =
        object : Clock {
            override fun now(): Instant = instant
        }

    private class FakeReviewPreferences(
        private var state: ReviewState,
    ) : ReviewPreferences {
        var incrementCalls = 0
        var recordCalls = 0
        var recordedAt: Instant? = null

        override suspend fun currentState(): ReviewState = state

        override suspend fun incrementPostCount() {
            incrementCalls++
            state = state.copy(successfulPostCount = state.successfulPostCount + 1)
        }

        override suspend fun recordActiveDay(epochDay: Long) {
            state = state.copy(activeDayCount = state.activeDayCount + 1)
        }

        override suspend fun recordLike() {
            state = state.copy(likeCount = state.likeCount + 1)
        }

        override suspend fun recordReviewRequested(now: Instant) {
            recordCalls++
            recordedAt = now
            state = state.copy(requestCount = state.requestCount + 1, lastRequestedAt = now)
        }
    }

    private class FakeReviewClient(
        private val requestThrows: Boolean = false,
        private val launchThrows: Boolean = false,
    ) : ReviewClient {
        var requestCalls = 0
        var launchCalls = 0

        override suspend fun requestReview(activity: Activity): ReviewHandle {
            requestCalls++
            if (requestThrows) error("request failed")
            return ReviewHandle(Any())
        }

        override suspend fun launchReview(
            activity: Activity,
            handle: ReviewHandle,
        ) {
            launchCalls++
            if (launchThrows) error("launch failed")
        }
    }

    private class RecordingAnalyticsClient : AnalyticsClient {
        val events = mutableListOf<AnalyticsEvent>()

        override fun log(event: AnalyticsEvent) {
            events += event
        }

        override fun setUserProperty(property: UserProperty) = Unit

        override fun logScreen(screen: AnalyticsScreen) = Unit
    }
}
