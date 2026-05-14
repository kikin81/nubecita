package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.MessageUi
import kotlin.time.Instant

private fun mu(
    id: String = "m",
    isOutgoing: Boolean,
    text: String = "Hello there",
    isDeleted: Boolean = false,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) "did:plc:me" else "did:plc:alice",
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse("2026-05-14T12:00:00Z"),
    )

@PreviewTest
@Preview(name = "bubble-incoming-single-light", showBackground = true)
@Preview(name = "bubble-incoming-single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomingSingle() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MessageBubble(mu(isOutgoing = false), runIndex = 0, runCount = 1)
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-outgoing-single-light", showBackground = true)
@Preview(name = "bubble-outgoing-single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingSingle() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MessageBubble(mu(isOutgoing = true), runIndex = 0, runCount = 1)
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-incoming-run3-light", showBackground = true)
@Preview(name = "bubble-incoming-run3-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomingRunOfThree() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MessageBubble(mu(id = "a", isOutgoing = false, text = "first message"), runIndex = 0, runCount = 3)
                MessageBubble(mu(id = "b", isOutgoing = false, text = "middle one"), runIndex = 1, runCount = 3)
                MessageBubble(mu(id = "c", isOutgoing = false, text = "last in the run"), runIndex = 2, runCount = 3)
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-outgoing-run3-light", showBackground = true)
@Preview(name = "bubble-outgoing-run3-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingRunOfThree() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MessageBubble(mu(id = "a", isOutgoing = true, text = "first message"), runIndex = 0, runCount = 3)
                MessageBubble(mu(id = "b", isOutgoing = true, text = "middle one"), runIndex = 1, runCount = 3)
                MessageBubble(mu(id = "c", isOutgoing = true, text = "last in the run"), runIndex = 2, runCount = 3)
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-deleted-light", showBackground = true)
@Preview(name = "bubble-deleted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeletedBubble() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MessageBubble(mu(isOutgoing = false, isDeleted = true, text = ""), runIndex = 0, runCount = 1)
            }
        }
    }
}
