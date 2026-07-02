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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.MessageSendStatus
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ReactionUi
import net.kikin.nubecita.feature.chats.impl.RepliedMessageUi
import kotlin.time.Instant

private fun mu(
    id: String = "m",
    isOutgoing: Boolean,
    text: String = "Hello there",
    isDeleted: Boolean = false,
    sendStatus: MessageSendStatus = MessageSendStatus.Sent,
    reactions: ImmutableList<ReactionUi> = persistentListOf(),
    replyTo: RepliedMessageUi? = null,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) "did:plc:me" else "did:plc:alice",
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse("2026-05-14T12:00:00Z"),
        sendStatus = sendStatus,
        reactions = reactions,
        replyTo = replyTo,
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
@Preview(name = "bubble-reply-incoming-light", showBackground = true)
@Preview(name = "bubble-reply-incoming-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReplyIncoming() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Incoming reply quoting the viewer's own message → "You" label on the preview.
                MessageBubble(
                    mu(
                        isOutgoing = false,
                        text = "On it — I'll ping you tomorrow.",
                        replyTo =
                            RepliedMessageUi(
                                id = "r1",
                                senderDid = "did:plc:me",
                                text = "Let me know when the PR is ready to review!",
                                isFromViewer = true,
                            ),
                    ),
                    runIndex = 0,
                    runCount = 1,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-reply-outgoing-light", showBackground = true)
@Preview(name = "bubble-reply-outgoing-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReplyOutgoing() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Outgoing reply quoting the peer's message → no "You" label, just the quote.
                MessageBubble(
                    mu(
                        isOutgoing = true,
                        text = "Sure, will do.",
                        replyTo =
                            RepliedMessageUi(
                                id = "r2",
                                senderDid = "did:plc:alice",
                                text = "Did you check out the new design token mappings?",
                                isFromViewer = false,
                            ),
                    ),
                    runIndex = 0,
                    runCount = 1,
                )
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
@Preview(name = "bubble-outgoing-sending-light", showBackground = true)
@Preview(name = "bubble-outgoing-sending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingSending() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MessageBubble(
                    mu(isOutgoing = true, text = "On my way", sendStatus = MessageSendStatus.Sending),
                    runIndex = 0,
                    runCount = 1,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-outgoing-failed-light", showBackground = true)
@Preview(name = "bubble-outgoing-failed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingFailed() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MessageBubble(
                    mu(isOutgoing = true, text = "Did this go through?", sendStatus = MessageSendStatus.Failed),
                    runIndex = 0,
                    runCount = 1,
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "bubble-reactions-light", showBackground = true)
@Preview(name = "bubble-reactions-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BubbleReactions() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MessageBubble(
                    mu(
                        isOutgoing = false,
                        text = "nice work!",
                        reactions =
                            persistentListOf(
                                ReactionUi("👍", 2, false),
                                ReactionUi("❤️", 1, true),
                            ),
                    ),
                    runIndex = 0,
                    runCount = 1,
                )
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
