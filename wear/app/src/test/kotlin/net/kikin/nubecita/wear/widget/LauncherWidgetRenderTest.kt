package net.kikin.nubecita.wear.widget

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.glance.wear.core.ContainerInfo
import androidx.glance.wear.core.WearWidgetParams
import androidx.glance.wear.core.WidgetInstanceId
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
    ): ByteArray =
        runBlocking {
            val widget = LauncherWidget { unread }
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
    fun theCountReachesTheDocument() {
        val three = render(3, ContainerInfo.CONTAINER_TYPE_LARGE)
        val none = render(0, ContainerInfo.CONTAINER_TYPE_LARGE)

        assertFalse("3 unread and 0 unread render the same document", three.contentEquals(none))
    }
}
