package net.kikin.nubecita.wear.widget

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.glance.wear.core.ContainerInfo
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WidgetInstanceId
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds the launcher's Remote Compose document for each count state, in both
 * the widget-card and full-screen-tile forms — the guard against a Glance Wear /
 * Remote Compose alpha bump breaking the document, which would otherwise only
 * show up as a broken card on a watch.
 */
@SuppressLint("RestrictedApi") // WearWidgetParams / captureRawContent are LIBRARY_GROUP in the alpha.
@RunWith(RobolectricTestRunner::class)
// A plain Application: the Hilt app would bootstrap the whole graph for a render check.
@Config(sdk = [35], application = Application::class)
class LauncherWidgetRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun params(containerType: Int) = WearWidgetParams(WidgetInstanceId("test", 1), containerType, 227f, 227f, 12f, 23f, 0f)

    private fun render(
        unread: Int?,
        containerType: Int,
        signedIn: Boolean = true,
    ): ByteArray =
        runBlocking {
            val widget = LauncherWidget(signedIn = { signedIn }, unreadCount = { unread })
            val p = params(containerType)
            widget.provideWidgetData(context, p).captureRawContent(context, p).rcDocument
        }

    @Test
    fun everyCountStateRendersInBothForms() {
        for (type in listOf(ContainerInfo.CONTAINER_TYPE_LARGE, ContainerInfo.CONTAINER_TYPE_TILE_COMPAT)) {
            for (unread in listOf(null, 0, 1, 3, 100)) {
                assertTrue("unread=$unread type=$type", render(unread, type).isNotEmpty())
            }
        }
    }

    @Test
    fun signedOut_rendersTheSignInPromptInBothForms() {
        for (type in listOf(ContainerInfo.CONTAINER_TYPE_LARGE, ContainerInfo.CONTAINER_TYPE_TILE_COMPAT)) {
            val signedOut = render(3, type, signedIn = false)
            assertTrue("type=$type", signedOut.isNotEmpty())
            assertFalse("signed out still shows the tiles (type=$type)", signedOut.contentEquals(render(3, type)))
        }
    }

    @Test
    fun failingDataReads_stillRender_asSignedInWithoutACount() {
        // The session (Play services) and count (Room) reads can fail; the service's scope has no
        // handler, so a throw here would crash the watch app. Fall back to the plain Messages card.
        val p = params(ContainerInfo.CONTAINER_TYPE_LARGE)
        val failing =
            runBlocking {
                LauncherWidget(signedIn = { error("GMS unavailable") }, unreadCount = { error("db closed") })
                    .provideWidgetData(context, p)
                    .captureRawContent(context, p)
                    .rcDocument
            }

        assertTrue(failing.contentEquals(render(null, ContainerInfo.CONTAINER_TYPE_LARGE)))
    }

    @Test
    fun theCountReachesTheDocument() {
        val three = render(3, ContainerInfo.CONTAINER_TYPE_LARGE)
        val none = render(0, ContainerInfo.CONTAINER_TYPE_LARGE)

        assertFalse("3 unread and 0 unread render the same document", three.contentEquals(none))
    }

    @Test
    fun theRefreshPath_rendersFromTheRealSession_andCount() {
        // On Wear OS 7 a refresh pushes the document rendered by the instance it's called on, so
        // that instance must read the real session and count, not a stand-in.
        val p = params(ContainerInfo.CONTAINER_TYPE_LARGE)
        val refreshed =
            runBlocking {
                GlanceLauncherWidgetRefresh(context, FakeSession(PhoneSession.SignedIn(WearAccount("did:plc:a", "a.test"))), FakeDms(unread = 3))
                    .widget
                    .provideWidgetData(context, p)
                    .captureRawContent(context, p)
                    .rcDocument
            }

        assertTrue(refreshed.contentEquals(render(3, ContainerInfo.CONTAINER_TYPE_LARGE)))
    }

    private class FakeSession(
        value: PhoneSession,
    ) : PhoneSessionSource {
        override val session: Flow<PhoneSession> = flowOf(value)
    }

    private class FakeDms(
        private val unread: Int?,
    ) : WatchDmRepository {
        override fun state(accountDid: String): Flow<WatchDmStateEntity?> = emptyFlow()

        override fun convos(accountDid: String): Flow<List<WatchConvoEntity>> = emptyFlow()

        override fun lastMessages(accountDid: String): Flow<List<WatchMessageEntity>> = emptyFlow()

        override fun messages(
            accountDid: String,
            convoId: String,
        ): Flow<List<WatchMessageEntity>> = emptyFlow()

        override suspend fun latestUnreadCount(): Int? = unread
    }
}
