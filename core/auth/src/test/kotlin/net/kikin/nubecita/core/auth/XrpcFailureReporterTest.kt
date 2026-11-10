package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.analytics.AnalyticsValidator
import net.kikin.nubecita.core.analytics.ApiError
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class XrpcFailureReporterTest {
    @Test
    fun `a failed call is logged as api_error with its endpoint code and status`() {
        val analytics = RecordingAnalyticsClient()

        XrpcFailureReporter(analytics).onError(
            "app.bsky.actor.getPreferences",
            XrpcError.Unknown("use_dpop_nonce", "Authorization server requires nonce in DPoP proof", 401),
        )

        val event = analytics.events.single() as ApiError
        assertEquals(ApiError("app.bsky.actor.getPreferences", "use_dpop_nonce", 401), event)
        AnalyticsValidator.requireValid(event)
    }
}
