package net.kikin.nubecita.core.review

import android.app.Activity
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultReviewManagerTest {
    private val now = Instant.parse("2026-06-23T12:00:00Z")
    private val activity = mockk<Activity>(relaxed = true)

    private val eligible =
        ReviewState(
            firstLaunchAt = now - 10.days,
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

    @Test
    fun `onAppLaunch stamps first launch with the current time`() =
        runTest {
            val prefs = FakeReviewPreferences(eligible)
            val client = FakeReviewClient()

            manager(prefs, client).onAppLaunch()

            assertEquals(1, prefs.stampCalls)
            assertEquals(now, prefs.stampedAt)
        }

    private fun manager(
        prefs: ReviewPreferences,
        client: ReviewClient,
    ) = DefaultReviewManager(client, prefs, fixedClock(now), UnconfinedTestDispatcher())

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
        var stampCalls = 0
        var stampedAt: Instant? = null

        override suspend fun currentState(): ReviewState = state

        override suspend fun incrementPostCount() {
            incrementCalls++
            state = state.copy(successfulPostCount = state.successfulPostCount + 1)
        }

        override suspend fun recordReviewRequested(now: Instant) {
            recordCalls++
            recordedAt = now
            state = state.copy(requestCount = state.requestCount + 1, lastRequestedAt = now)
        }

        override suspend fun stampFirstLaunchIfUnset(now: Instant) {
            stampCalls++
            stampedAt = now
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
}
