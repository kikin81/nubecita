package net.kikin.nubecita.shell.adaptive

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import kotlinx.serialization.Serializable
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.shell.calculateListDetailDirective
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SpatialWindowResizeStateTest {
    @Serializable private data object FeedTab : NavKey

    @Serializable private data object SearchTab : NavKey

    @Serializable private data object PostDetailSubRoute : NavKey

    private fun newNavState(): MainShellNavState =
        MainShellNavState(
            startRoute = FeedTab,
            topLevelKeyState = mutableStateOf(FeedTab),
            backStacks =
                mapOf(
                    FeedTab to NavBackStack<NavKey>(FeedTab),
                    SearchTab to NavBackStack<NavKey>(SearchTab),
                ),
        )

    @Test
    fun `rapid freeform window resize updates pane directive while preserving navigation state`() {
        val navState = newNavState()
        navState.add(PostDetailSubRoute) // FeedTab: [FeedTab, PostDetailSubRoute]

        assertEquals(listOf<NavKey>(FeedTab, PostDetailSubRoute), navState.backStack.toList())
        assertSame(FeedTab, navState.topLevelKey)

        val resizeSequence =
            listOf(
                360f to 1, // Compact -> 1 pane
                640f to 2, // Medium -> 2 panes
                1024f to 2, // Expanded -> 2 panes
                1600f to 2, // Ultra-wide spatial panel -> 2 panes
                400f to 1, // Resized back to compact -> 1 pane
            )

        resizeSequence.forEach { (widthDp, expectedPartitions) ->
            val adaptiveInfo =
                WindowAdaptiveInfo(
                    windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp = widthDp, heightDp = 800f),
                    windowPosture = Posture(),
                )
            val directive = calculateListDetailDirective(adaptiveInfo, isSpatialUiEnabled = false)
            assertEquals(expectedPartitions, directive.maxHorizontalPartitions)

            // Navigation state, active tab, and back stack depth must remain intact across every resize
            assertSame(FeedTab, navState.topLevelKey)
            assertEquals(listOf<NavKey>(FeedTab, PostDetailSubRoute), navState.backStack.toList())
        }
    }

    @Test
    fun `freeform window resize in spatial environment maintains dual panes across all widths`() {
        val navState = newNavState()
        navState.add(PostDetailSubRoute)

        val widths = listOf(360f, 500f, 720f, 1080f, 1920f)
        widths.forEach { widthDp ->
            val adaptiveInfo =
                WindowAdaptiveInfo(
                    windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp = widthDp, heightDp = 800f),
                    windowPosture = Posture(),
                )
            val directive = calculateListDetailDirective(adaptiveInfo, isSpatialUiEnabled = true)
            assertEquals(2, directive.maxHorizontalPartitions)
            assertEquals(480.dp, directive.defaultPanePreferredWidth)

            assertSame(FeedTab, navState.topLevelKey)
            assertEquals(listOf<NavKey>(FeedTab, PostDetailSubRoute), navState.backStack.toList())
        }
    }
}
