package net.kikin.nubecita.shell.adaptive

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import kotlinx.serialization.Serializable
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gate tests for [AdaptiveDialogSceneStrategy].
 *
 * Two behaviours are pinned:
 *  - Width gate: an `adaptiveDialog()`-tagged entry becomes a dialog
 *    [OverlayScene] only at Medium+ width; at Compact it declines (→ the entry
 *    renders full-screen via the fallback scene).
 *  - Coalescing: a *run* of consecutive tagged entries renders as ONE dialog
 *    that owns the whole run (so its content can swap via `AnimatedContent`),
 *    keyed on the bottom of the run so the key stays stable across pushes/pops
 *    within the dialog — the Play Store "modal with nested content" behaviour.
 *
 * End-to-end rendering is covered by tablet/phone instrumented runs; this pins
 * the branches the whole pattern hinges on.
 */
class AdaptiveDialogSceneStrategyTest {
    @Serializable private data object RouteA : NavKey

    @Serializable private data object RouteB : NavKey

    @Serializable private data object RouteC : NavKey

    private val expanded = WindowSizeClass(widthDp = 840f, heightDp = 1200f)
    private val compact = WindowSizeClass(widthDp = 400f, heightDp = 800f)

    private fun entryOf(
        route: NavKey,
        tagged: Boolean,
    ): NavEntry<NavKey> =
        NavEntry(
            key = route,
            metadata = if (tagged) adaptiveDialog() else emptyMap(),
            content = { },
        )

    private fun sceneForStack(
        windowSizeClass: WindowSizeClass,
        stack: List<Pair<NavKey, Boolean>>,
    ): Scene<NavKey>? =
        with(SceneStrategyScope<NavKey>()) {
            AdaptiveDialogSceneStrategy<NavKey>(windowSizeClass).run {
                calculateScene(stack.map { entryOf(it.first, it.second) })
            }
        }

    private fun sceneFor(
        windowSizeClass: WindowSizeClass,
        tagged: Boolean,
    ): Scene<NavKey>? = sceneForStack(windowSizeClass, listOf(RouteA to tagged))

    // --- width gate ---------------------------------------------------------

    @Test
    fun `tagged entry at expanded width becomes a dialog overlay`() {
        assertTrue(sceneFor(expanded, tagged = true) is OverlayScene)
    }

    @Test
    fun `tagged entry at compact width declines so it renders full-screen`() {
        assertNull(sceneFor(compact, tagged = true))
    }

    @Test
    fun `untagged entry at expanded width declines`() {
        assertNull(sceneFor(expanded, tagged = false))
    }

    // --- coalescing ---------------------------------------------------------

    @Test
    fun `consecutive tagged entries coalesce into one overlay owning the run`() {
        val scene = sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to true))
        assertTrue(scene is OverlayScene)
        // The single scene owns the whole dialog run [B, C] (so it can swap
        // content), not just the top entry. Compare by contentKey (a value
        // derived from the route, not the route object itself).
        val expectedRun = listOf(entryOf(RouteB, true).contentKey, entryOf(RouteC, true).contentKey)
        assertEquals(expectedRun, scene!!.entries.map { it.contentKey })
    }

    @Test
    fun `coalesced scene overlays only the content beneath the dialog run`() {
        val scene = sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to true))
        scene as OverlayScene
        // One scrim over the real content (A), not over another dialog (B).
        assertEquals(
            listOf(entryOf(RouteA, false).contentKey),
            scene.overlaidEntries.map { it.contentKey },
        )
    }

    @Test
    fun `coalesced scene key is stable across pushes within the run`() {
        val deep = sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to true))!!
        val shallow = sceneForStack(expanded, listOf(RouteA to false, RouteB to true))!!
        // Both keyed on the bottom of the run (B) → NavDisplay keeps the same
        // dialog scene and swaps content instead of re-creating the dialog.
        assertEquals(shallow.key, deep.key)
    }

    @Test
    fun `a non-dialog entry on top of a dialog declines so it renders full-screen`() {
        assertNull(sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to false)))
    }
}
