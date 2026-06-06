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
 * This wrapper slices the entries to the active tab's segment — from the active
 * tab's root entry to the end — before delegating. The list-detail panes
 * therefore reflect the active tab only. The entries beneath the segment are
 * left untouched in the `NavDisplay` back stack and keep serving predictive /
 * system back.
 *
 * The segment is found by **key-matching**, not an absolute index: the entries
 * handed to `calculateScene` are not always 1:1 with `MainShellNavState.backStack`
 * — during predictive back `NavDisplay` re-runs the strategy over a scene's
 * (smaller) `previousEntries`. Locating the active tab's root *within the entries
 * actually passed* is immune to that size skew; if the root isn't present (a
 * predictive-back hypothetical that has already popped past it), the wrapper
 * falls back to the whole list so the preview still resolves to a valid scene.
 *
 * [activeTabKey] is read on every `calculateScene` call (it reads
 * `MainShellNavState.topLevelKey` snapshot state), so the scene recomputes on
 * tab switch. Matching is by [NavEntry.contentKey], which is `key.toString()`
 * for the default-keyed top-level tab roots.
 */
internal class ActiveTabScopedSceneStrategy<T : Any>(
    private val delegate: SceneStrategy<T>,
    private val activeTabKey: () -> T,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val scope = this
        val targetContentKey = activeTabKey().toString()
        val start = entries.indexOfLast { it.contentKey == targetContentKey }
        val segment = if (start >= 0) entries.drop(start) else entries
        if (segment.isEmpty()) return null
        return with(delegate) { scope.calculateScene(segment) }
    }
}
