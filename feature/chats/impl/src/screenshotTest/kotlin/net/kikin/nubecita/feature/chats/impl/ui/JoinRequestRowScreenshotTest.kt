package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.JoinRequestUi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// `now` is pinned to a fixed Instant so the row's "requested {relative}"
// label — produced live by `rememberChatRelativeTimeText` — renders the same
// string on every screenshot run. Requests are built `2.hours` back so the
// relative label renders "2h". Mirrors the ConvoListItem fixture-clock pattern.
private val JOIN_REQUEST_NOW = Instant.parse("2026-05-13T12:00:00Z")

private object JoinRequestFixtureClock : Clock {
    override fun now(): Instant = JOIN_REQUEST_NOW
}

private fun sample(displayName: String?) =
    JoinRequestUi(
        did = "did:plc:alice",
        handle = "alice.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        requestedAt = JOIN_REQUEST_NOW - 2.hours, // renders "2h"
    )

@PreviewTest
@Preview(name = "join-request-default-light", showBackground = true)
@Preview(name = "join-request-default-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinRequestRowDefault() {
    CompositionLocalProvider(LocalClock provides JoinRequestFixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface {
                JoinRequestRow(
                    request = sample(displayName = "Alice Liddell"),
                    inFlight = false,
                    onApprove = {},
                    onReject = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "join-request-handle-only-light", showBackground = true)
@Preview(name = "join-request-handle-only-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinRequestRowHandleOnly() {
    CompositionLocalProvider(LocalClock provides JoinRequestFixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface {
                JoinRequestRow(
                    request = sample(displayName = null),
                    inFlight = false,
                    onApprove = {},
                    onReject = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "join-request-in-flight-light", showBackground = true)
@Preview(name = "join-request-in-flight-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinRequestRowInFlight() {
    CompositionLocalProvider(LocalClock provides JoinRequestFixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface {
                JoinRequestRow(
                    request = sample(displayName = "Alice Liddell"),
                    inFlight = true,
                    onApprove = {},
                    onReject = {},
                )
            }
        }
    }
}
