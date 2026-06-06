package net.kikin.nubecita.shell.adaptive

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

/**
 * Wraps a list-detail [SceneStrategy] so its pane assignment is scoped to the
 * **active tab's segment** of the flattened `MainShell` back stack.
 *
 * `MainShellNavState.backStack` concatenates the start tab's full stack with
 * the active tab's full stack (so system / predictive back can "exit through
 * home"). Handing that whole concatenation to the Material3
 * `ListDetailSceneStrategy` made a tab switch render the *previous* tab's
 * detail entry next to the *new* tab's list — e.g. switching to Chats with a
 * post open on Feed showed `Chats list | Feed's PostDetail`, because the
 * strategy picks the last detail entry across ALL tabs in the flattened stack
 * (nubecita-xqp7 / nubecita-s1f3).
 *
 * This wrapper slices the entries to the active tab's segment — everything from
 * [activeSegmentStart] to the end, which always includes the real top entry —
 * before delegating. The list-detail panes therefore reflect the active tab
 * only. The entries beneath the segment are left untouched in the `NavDisplay`
 * back stack and keep serving predictive / system back.
 *
 * [activeSegmentStart] is read on every `calculateScene` call (it reads
 * `MainShellNavState` snapshot state), so the scene recomputes on tab switch.
 */
internal class ActiveTabScopedSceneStrategy<T : Any>(
    private val delegate: SceneStrategy<T>,
    private val activeSegmentStart: () -> Int,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val scope = this
        val start = activeSegmentStart().coerceIn(0, entries.lastIndex.coerceAtLeast(0))
        val segment = if (start == 0) entries else entries.drop(start)
        if (segment.isEmpty()) return null
        return with(delegate) { scope.calculateScene(segment) }
    }
}
