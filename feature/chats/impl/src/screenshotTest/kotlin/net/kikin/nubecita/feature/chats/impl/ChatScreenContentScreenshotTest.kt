package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

private const val VIEWER = "did:plc:viewer"
private const val PEER = "did:plc:alice"

private fun mu(
    id: String,
    isOutgoing: Boolean,
    text: String,
    sentAt: String,
    isDeleted: Boolean = false,
    embed: EmbedUi.RecordOrUnavailable? = null,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) VIEWER else PEER,
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse(sentAt),
        embed = embed,
    )

private fun recordEmbed(
    authorHandle: String = "post-author.bsky.social",
    authorDisplayName: String = "Post Author",
    text: String,
    createdAt: String = "2026-05-14T16:00:00Z",
): EmbedUi.Record =
    EmbedUi.Record(
        quotedPost =
            QuotedPostUi(
                uri = "at://did:plc:quoted-author/app.bsky.feed.post/q",
                cid = "bafyreifakequotedcid000000000000000000000000000",
                author =
                    AuthorUi(
                        did = "did:plc:quoted-author",
                        handle = authorHandle,
                        displayName = authorDisplayName,
                        avatarUrl = null,
                    ),
                createdAt = Instant.parse(createdAt),
                text = text,
                facets = persistentListOf(),
                embed = QuotedEmbedUi.Empty,
            ),
    )

private val LOADED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        otherUserAvatarHue = 217,
        status =
            ChatLoadStatus.Loaded(
                // Matches the mapper's emission order: newest message at source[0]
                // (= screen-bottom with reverseLayout), DaySeparator at the END of its
                // bucket (= screen-top of the day-group), per Bluesky / iMessage /
                // GChat convention.
                items =
                    persistentListOf(
                        ThreadItem.Message(
                            message = mu("m4", isOutgoing = false, text = "see you soon", sentAt = "2026-05-14T17:35:00Z"),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = true,
                        ),
                        ThreadItem.Message(
                            message = mu("m3", isOutgoing = true, text = "On my way", sentAt = "2026-05-14T17:30:00Z"),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m2", isOutgoing = false, text = "Quick coffee?", sentAt = "2026-05-14T17:28:00Z"),
                            runIndex = 1,
                            runCount = 2,
                            showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m1", isOutgoing = false, text = "Hey", sentAt = "2026-05-14T17:27:00Z"),
                            runIndex = 0,
                            runCount = 2,
                            showAvatar = true,
                        ),
                        ThreadItem.DaySeparator(
                            epochDay =
                                java.time.LocalDate
                                    .parse("2026-05-14")
                                    .toEpochDay(),
                            label = "Today",
                        ),
                    ),
            ),
    )

/**
 * Loaded state exercising the embed-card render paths added by
 * `nubecita-nn3.7`:
 * - Record embed paired with parent text (text bubble + card stacked).
 * - Record-embed-only message (empty wire `text` — text bubble omitted).
 * - `RecordUnavailable` chip (NotFound — peer shared a deleted post).
 *
 * Lives as its own state, not folded into [LOADED_STATE], so the
 * pre-embed baseline screenshots remain unchanged.
 */
private val LOADED_WITH_EMBEDS_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        otherUserAvatarHue = 217,
        status =
            ChatLoadStatus.Loaded(
                items =
                    persistentListOf(
                        // Newest-first source order; reverseLayout flips visually on screen.
                        ThreadItem.Message(
                            message =
                                mu(
                                    id = "m4",
                                    isOutgoing = true,
                                    text = "have you seen this?",
                                    sentAt = "2026-05-14T17:32:00Z",
                                    embed =
                                        recordEmbed(
                                            authorHandle = "ana.bsky.social",
                                            authorDisplayName = "Ana",
                                            text =
                                                "Trying out the new edge-to-edge insets on Android 16 " +
                                                    "today and it really does feel like a different OS.",
                                        ),
                                ),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message =
                                mu(
                                    id = "m3",
                                    isOutgoing = false,
                                    text = "",
                                    sentAt = "2026-05-14T17:31:00Z",
                                    embed =
                                        recordEmbed(
                                            authorHandle = "ben.bsky.social",
                                            authorDisplayName = "Ben",
                                            text = "Pure embed-share: zero parent text, all signal in the card.",
                                        ),
                                ),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = true,
                        ),
                        ThreadItem.Message(
                            message =
                                mu(
                                    id = "m2",
                                    isOutgoing = false,
                                    text = "this one's gone now :(",
                                    sentAt = "2026-05-14T17:30:00Z",
                                    embed =
                                        EmbedUi.RecordUnavailable(
                                            EmbedUi.RecordUnavailable.Reason.NotFound,
                                        ),
                                ),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = true,
                        ),
                        ThreadItem.Message(
                            message =
                                mu(
                                    id = "m1",
                                    isOutgoing = false,
                                    text = "ok last one — check these",
                                    sentAt = "2026-05-14T17:28:00Z",
                                ),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = true,
                        ),
                        ThreadItem.DaySeparator(
                            epochDay =
                                java.time.LocalDate
                                    .parse("2026-05-14")
                                    .toEpochDay(),
                            label = "Today",
                        ),
                    ),
            ),
    )

private val EMPTY_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.Loaded(items = persistentListOf()),
    )

private val LOADING_STATE =
    ChatScreenViewState(otherUserHandle = "alice.bsky.social", otherUserDisplayName = "Alice")

private val NETWORK_ERROR_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.Network),
    )

private val NOT_ENROLLED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.NotEnrolled),
    )

@PreviewTest
@Preview(name = "chat-loaded-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loaded-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = LOADED_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-loaded-embeds-light", showBackground = true, heightDp = 900)
@Preview(name = "chat-loaded-embeds-dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenLoadedWithEmbedsScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = LOADED_WITH_EMBEDS_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-empty-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-empty-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = EMPTY_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-loading-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loading-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = LOADING_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-network-error-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-network-error-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenNetworkErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = NETWORK_ERROR_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-not-enrolled-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-not-enrolled-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenNotEnrolledScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = NOT_ENROLLED_STATE, onEvent = {})
    }
}
