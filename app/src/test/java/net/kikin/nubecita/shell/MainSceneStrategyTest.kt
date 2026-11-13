package net.kikin.nubecita.shell

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
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

    @Test
    fun `pane expansion at medium width offers only the even split`() {
        val expansion = calculateListDetailPaneExpansion(640.dp)

        assertEquals(listOf(PaneExpansionAnchor.Proportion(0.5f)), expansion.anchors)
        assertEquals(0, expansion.initialAnchoredIndex)
    }

    @Test
    fun `pane expansion on an unfolded foldable starts even between minimum widths`() {
        val expansion = calculateListDetailPaneExpansion(852.dp)

        assertEquals(
            listOf(
                PaneExpansionAnchor.Offset.fromStart(360.dp),
                PaneExpansionAnchor.Proportion(0.5f),
                PaneExpansionAnchor.Offset.fromEnd(360.dp),
            ),
            expansion.anchors,
        )
        assertEquals(PaneExpansionAnchor.Proportion(0.5f), expansion.anchors[expansion.initialAnchoredIndex])
    }

    @Test
    fun `pane expansion on a large tablet keeps the 440dp list as the start`() {
        val expansion = calculateListDetailPaneExpansion(1280.dp)

        assertEquals(PaneExpansionAnchor.Offset.fromStart(440.dp), expansion.anchors[expansion.initialAnchoredIndex])
        assertEquals(4, expansion.anchors.size)
    }

    @Test
    fun `pane expansion switches from foldable to large-tablet anchors at 1200dp`() {
        assertEquals(3, calculateListDetailPaneExpansion(1199.dp).anchors.size)
        assertEquals(4, calculateListDetailPaneExpansion(1200.dp).anchors.size)
    }

    @Test
    fun `pane expansion keeps the unfolded anchors while folded`() {
        // The expansion state resets a position that is not among the current anchors,
        // so a fold (compact) must not change the anchor list.
        assertEquals(calculateListDetailPaneExpansion(852.dp), calculateListDetailPaneExpansion(411.dp))
    }
}
