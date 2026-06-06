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
 *
 * ## Why a wrapper and not per-tab `sceneKey`
 *
 * Material3's `listPane()/detailPane()` take a `sceneKey` precisely to host
 * multiple list-detail scaffolds in one `NavDisplay`, and giving each tab a
 * distinct `sceneKey` would scope panes without this wrapper — *if* every entry
 * knew its tab. It doesn't: a `PostDetail` (or author `Profile`) sub-route is
 * pushed onto whichever tab is active, and its metadata is derived from the
 * route instance alone, which carries no host-tab. Per-tab `sceneKey` therefore
 * can't tag shared sub-routes correctly. Positional slicing by the active tab's
 * root is host-tab-agnostic and needs no per-route knowledge, so it is the right
 * mechanism for this multi-tab + shared-sub-route topology.
 *
 * ## Invariants this relies on
 *
 * - The active segment is the **suffix** of the flattened back stack (it always
 *   includes `entries.last()`), guaranteed because `MainShellNavState.backStack`
 *   appends the active tab's stack last. The delegate scene's back-pop count is
 *   slice-relative (`scaffoldEntries.size - previousEntries.size` pops via the
 *   scope's `onBack` = `removeLast()`), which is correct only while each pop maps
 *   to exactly one active-tab entry — i.e. while the segment is that suffix. A
 *   future change to the flattening must preserve this.
 * - Predictive-back preview cannot see below the active-tab root (the start-tab
 *   prefix is sliced off), so a back gesture that would switch from a non-start
 *   tab to the start tab falls back to `NavDisplay`'s default transition rather
 *   than a scaffold-driven one. That path is `removeLast()` case 2 (tab switch),
 *   not a scaffold-internal pane change, so it is correct — just not animated by
 *   the scaffold.
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
