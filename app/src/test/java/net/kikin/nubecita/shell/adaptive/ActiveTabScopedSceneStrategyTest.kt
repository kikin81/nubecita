package net.kikin.nubecita.shell.adaptive

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Pins the active-tab scoping of [ActiveTabScopedSceneStrategy] (nubecita-xqp7
 * / nubecita-s1f3): the wrapper slices the flattened MainShell back stack to
 * the active tab's segment before delegating, so list-detail pane assignment
 * never sees another tab's entries.
 *
 * End-to-end pane rendering is covered by the tablet instrumented run; this
 * pins the slice math the whole fix hinges on.
 */
class ActiveTabScopedSceneStrategyTest {
    @Serializable private data object Feed : NavKey

    @Serializable private data object PostDetail : NavKey

    @Serializable private data object Profile : NavKey

    @Serializable private data object Chats : NavKey

    private fun entry(route: NavKey): NavEntry<NavKey> = NavEntry(key = route, content = {})

    /** Records the entries handed to the delegate; returns [result]. */
    private class RecordingStrategy(
        private val result: Scene<NavKey>?,
    ) : SceneStrategy<NavKey> {
        var received: List<NavEntry<NavKey>>? = null
            private set

        override fun SceneStrategyScope<NavKey>.calculateScene(
            entries: List<NavEntry<NavKey>>,
        ): Scene<NavKey>? {
            received = entries
            return result
        }
    }

    private class FakeScene(
        override val entries: List<NavEntry<NavKey>>,
    ) : Scene<NavKey> {
        override val key: Any = "fake"
        override val previousEntries: List<NavEntry<NavKey>> = emptyList()
        override val content: @Composable () -> Unit = {}
    }

    private fun calculate(
        delegate: SceneStrategy<NavKey>,
        segmentStart: Int,
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? =
        with(SceneStrategyScope<NavKey>()) {
            ActiveTabScopedSceneStrategy(delegate) { segmentStart }.run {
                calculateScene(entries)
            }
        }

    @Test
    fun `start-tab active (index 0) delegates the whole stack`() {
        val entries = listOf(Feed, PostDetail, Profile).map(::entry)
        val delegate = RecordingStrategy(result = null)

        calculate(delegate, segmentStart = 0, entries = entries)

        assertEquals(entries, delegate.received)
    }

    @Test
    fun `non-start tab active drops the start-tab segment before delegating`() {
        // Flattened: [Feed, PostDetail] (start segment, size 2) + [Chats] (active).
        val entries = listOf(Feed, PostDetail, Chats).map(::entry)
        val delegate = RecordingStrategy(result = null)

        calculate(delegate, segmentStart = 2, entries = entries)

        // Only the active (Chats) segment reaches the list-detail strategy —
        // the previous tab's PostDetail can no longer leak into the detail pane.
        assertEquals(entries.drop(2), delegate.received)
    }

    @Test
    fun `returns the delegate scene unchanged`() {
        val entries = listOf(Feed, PostDetail, Chats).map(::entry)
        val expected = FakeScene(entries.drop(2))
        val delegate = RecordingStrategy(result = expected)

        val scene = calculate(delegate, segmentStart = 2, entries = entries)

        assertSame(expected, scene)
    }

    @Test
    fun `an out-of-range segment start is clamped to keep the top entry`() {
        // Defensive: a stale index past the end must never produce an empty
        // segment — the real top entry is always kept so the scene can resolve.
        val entries = listOf(Feed, PostDetail, Chats).map(::entry)
        val delegate = RecordingStrategy(result = null)

        calculate(delegate, segmentStart = 99, entries = entries)

        assertEquals(listOf(entries.last()), delegate.received)
    }
}
