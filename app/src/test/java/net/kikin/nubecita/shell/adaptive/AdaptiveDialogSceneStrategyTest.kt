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
 *    that owns the whole run (one scrim, no modal-on-modal) — the Play Store
 *    "modal with nested content" behaviour.
 *  - Scene identity (nubecita-6k7e regression): the scene is keyed on the run's
 *    TOP entry so a pushed sub-route is a DISTINCT scene NavDisplay re-renders,
 *    and value equality dedups same-run recompositions. Keying on the bottom
 *    (the old bug) made `[B]` and `[B,C]` share a key, so the sub-route never
 *    rendered on tablet.
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
    fun `scene is keyed on the run top so a pushed sub-route is a distinct scene`() {
        val deep = sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to true))!!
        val shallow = sceneForStack(expanded, listOf(RouteA to false, RouteB to true))!!
        // nubecita-6k7e: NavDisplay identifies overlay scenes by (class, key). A
        // push must change the key (top: B → C) or NavDisplay treats the grown
        // run as the SAME scene and never renders the sub-route (the tablet bug).
        assertEquals(entryOf(RouteC, true).contentKey, deep.key)
        assertEquals(entryOf(RouteB, true).contentKey, shallow.key)
        assertTrue(shallow.key != deep.key)
    }

    @Test
    fun `scenes for the same run are equal so recompositions are deduped`() {
        // The Scene contract requires value equality; without it NavDisplay's
        // append-only overlay list churns a fresh instance every recomposition.
        val a = sceneForStack(expanded, listOf(RouteA to false, RouteB to true))!!
        val b = sceneForStack(expanded, listOf(RouteA to false, RouteB to true))!!
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `scenes for different runs are not equal so the swap is seen`() {
        val shallow = sceneForStack(expanded, listOf(RouteA to false, RouteB to true))!!
        val deep = sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to true))!!
        assertTrue(shallow != deep)
    }

    @Test
    fun `a non-dialog entry on top of a dialog declines so it renders full-screen`() {
        assertNull(sceneForStack(expanded, listOf(RouteA to false, RouteB to true, RouteC to false)))
    }
}
