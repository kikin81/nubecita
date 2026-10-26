package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.wearcontract.WearAction
import net.kikin.nubecita.core.wearcontract.WearDmActions
import net.kikin.nubecita.feature.chats.impl.FakeChatRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DmWearActionHandlerTest {
    private val repo = FakeChatRepository()
    private val handler = DmWearActionHandler(repo)

    @Test
    fun aQuickReply_isSent_andReturnsTheSentMessageId() =
        runTest {
            val result = handler.execute(WearAction.DmReply("c1", "On my way"))

            assertEquals(Result.success("msg-sent-1"), result)
            assertEquals("c1", repo.lastSendConvoId)
            assertEquals("On my way", repo.lastSendText)
        }

    @Test
    fun aReaction_isAdded_andReturnsNoRecord() =
        runTest {
            val result = handler.execute(WearAction.DmReact("c1", "m9", "👍"))

            assertEquals(Result.success(null), result)
            assertEquals(listOf(Triple("c1", "m9", "👍")), repo.addReactionCalls)
        }

    @Test
    fun aFailedSend_isPassedThrough() =
        runTest {
            repo.nextSendResult = Result.failure(IllegalStateException("blocked"))

            assertTrue(handler.execute(WearAction.DmReply("c1", "Thanks!")).isFailure)
        }

    @Test
    fun claimsOnlyDmActions_andExpiresThemAfterAnHour() {
        assertTrue(handler.canHandle(WearAction.DmReply("c1", "hi")))
        assertTrue(handler.canHandle(WearAction.DmReact("c1", "m1", "❤️")))
        assertFalse(handler.canHandle(WearAction.Like("at://p", "cid")))
        assertEquals(WearDmActions.MAX_AGE_MS, handler.maxAgeMs(WearAction.DmReply("c1", "hi")))
    }
}
