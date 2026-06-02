package net.kikin.nubecita.shell.adaptive

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import kotlinx.serialization.Serializable
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gate tests for [AdaptiveDialogSceneStrategy]: an `adaptiveDialog()`-tagged
 * entry becomes a dialog [OverlayScene] only at Medium+ width; at Compact it
 * declines (returns null → the entry renders full-screen via the fallback
 * scene). End-to-end rendering is covered by tablet/phone instrumented runs;
 * this pins the branch the whole pattern hinges on.
 */
class AdaptiveDialogSceneStrategyTest {
    @Serializable
    private data object TestRoute : NavKey

    private val expanded = WindowSizeClass(widthDp = 840f, heightDp = 1200f)
    private val compact = WindowSizeClass(widthDp = 400f, heightDp = 800f)

    private fun entry(tagged: Boolean): NavEntry<NavKey> =
        NavEntry(
            key = TestRoute,
            metadata = if (tagged) adaptiveDialog() else emptyMap(),
            content = { },
        )

    private fun sceneFor(
        windowSizeClass: WindowSizeClass,
        tagged: Boolean,
    ) = with(SceneStrategyScope<NavKey>()) {
        AdaptiveDialogSceneStrategy<NavKey>(windowSizeClass).run {
            calculateScene(listOf(entry(tagged)))
        }
    }

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
}
