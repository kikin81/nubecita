package net.kikin.nubecita.shell

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainSceneStrategyTest {
    @Test
    fun `calculateListDetailDirective in standard compact renders single pane`() {
        val compactAdaptiveInfo =
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp = 400f, heightDp = 800f),
                windowPosture = Posture(),
            )
        val directive = calculateListDetailDirective(compactAdaptiveInfo)
        assertEquals(1, directive.maxHorizontalPartitions)
    }

    @Test
    fun `calculateListDetailDirective in standard medium renders two panes with 412dp list pane`() {
        val mediumAdaptiveInfo =
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp = 640f, heightDp = 800f),
                windowPosture = Posture(),
            )
        val directive = calculateListDetailDirective(mediumAdaptiveInfo)
        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(412.dp, directive.defaultPanePreferredWidth)
    }

    @Test
    fun `calculateListDetailDirective in standard expanded renders two panes with 440dp list pane`() {
        val expandedAdaptiveInfo =
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp = 1024f, heightDp = 768f),
                windowPosture = Posture(),
            )
        val directive = calculateListDetailDirective(expandedAdaptiveInfo)
        assertEquals(2, directive.maxHorizontalPartitions)
        assertEquals(440.dp, directive.defaultPanePreferredWidth)
    }
}
