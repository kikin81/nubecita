package net.kikin.nubecita.feature.chats.impl

import android.content.Intent
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.actionExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.di.ChatDeepLinkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the DM-notification deep-link matcher contributed by
 * [ChatDeepLinkModule]. Instrumented (not JVM) because the alpha03
 * `UriDeepLinkMatcher` resolves URI patterns through `android.net.Uri`, which
 * needs the Android runtime — same rationale as `ProfileDeepLinkMatcherTest`.
 */
@RunWith(AndroidJUnit4::class)
class ChatDeepLinkMatcherTest {
    private val matcher = ChatDeepLinkModule.provideChatDeepLinkMatcher()

    private fun viewRequest(uri: String): DeepLinkRequest = DeepLinkRequest(uri, DeepLinkRequest.actionExtra(Intent.ACTION_VIEW))

    @Test
    fun nubecitaChatDid_matchesChatWithOtherUserDid() {
        val matched = matcher.match(viewRequest("nubecita://chat/did:plc:abcdefghijklmnopqrstuvwx"))
        assertEquals(Chat(otherUserDid = "did:plc:abcdefghijklmnopqrstuvwx"), matched)
    }

    @Test
    fun nonDidTarget_isRejectedByAccept() {
        // accept = { startsWith("did:") } — a handle-shaped target is rejected
        // so a malformed tap never reaches getConvoForMembers.
        assertNull(matcher.match(viewRequest("nubecita://chat/alice.bsky.social")))
    }

    @Test
    fun wrongHost_doesNotMatch() {
        assertNull(matcher.match(viewRequest("nubecita://profile/did:plc:abcdefghijklmnopqrstuvwx")))
    }

    @Test
    fun nonViewAction_isRejectedByIntentActionFilter() {
        val request =
            DeepLinkRequest(
                "nubecita://chat/did:plc:abcdefghijklmnopqrstuvwx",
                DeepLinkRequest.actionExtra(Intent.ACTION_SEND),
            )
        assertNull(matcher.match(request))
    }
}
