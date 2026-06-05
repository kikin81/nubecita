package net.kikin.nubecita.shell.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.contains
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.window.core.layout.WindowSizeClass
import net.kikin.nubecita.core.common.navigation.AdaptiveDialogKey

private val MAX_CARD_WIDTH = 640.dp

/**
 * An [OverlayScene] that renders a *run* of consecutive `adaptiveDialog`
 * entries as a single centered dialog: a scrim with tap-to-dismiss and a
 * width-capped [Surface] card showing only the run's TOP entry. The entries
 * BELOW the run stay composed underneath ([overlaidEntries]) — so e.g. the
 * profile screen behind the editor keeps observing its `ownProfileUpdates`
 * refresh while the dialog is open.
 *
 * **Coalescing.** The whole consecutive `adaptiveDialog` run is owned by ONE
 * scene (`entries = dialogRun`), so pushing a sub-route does NOT stack a second
 * dialog + scrim — there is always exactly one dialog. Only the top route is
 * drawn; `NavDisplay` transitions between scenes when the top changes.
 *
 * **Scene identity (nubecita-6k7e).** `NavDisplay` identifies overlay scenes by
 * `AnimatedSceneKey = (sceneClass, scene.key)` and tracks them in an
 * `equals`-deduped list. So this scene MUST (a) key on the run's TOP entry — an
 * earlier version keyed on the BOTTOM, so `[Settings]` and `[Settings,About]`
 * shared one key and `NavDisplay` never re-rendered the pushed sub-route (it
 * stayed invisible on tablet); and (b) implement value equality (the [Scene]
 * contract requires it) so a same-run recomposition is deduped instead of
 * churning the overlay list.
 *
 * Cloned from navigation3's internal `DialogScene`, adding the scrim + 640dp
 * card chrome — an editor/composer wants a roomier surface than the platform
 * default dialog width, which is why we set `usePlatformDefaultWidth = false`
 * and size the card ourselves (640dp = `BottomSheetDefaults.SheetMaxWidth`).
 */
internal class AdaptiveDialogScene<T : Any>(
    override val key: Any,
    private val dialogRun: List<NavEntry<T>>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {
    // The scene owns the whole consecutive dialog run so it can swap the visible
    // entry via AnimatedContent; only the top entry is shown at any time.
    override val entries: List<NavEntry<T>> = dialogRun

    // The Scene contract REQUIRES value equality (data class or equals/hashCode):
    // NavDisplay tracks OverlayScenes in an append-only, equals-deduped list. With
    // the default reference identity, pushing a sub-route (e.g. Settings → About)
    // produced a NEW instance whose `key` (the run's BOTTOM contentKey) is
    // unchanged, so it collided with the stale instance on the same AnimatedSceneKey
    // and the frozen `dialogRun.last()` (== Settings) kept rendering — the sub-route
    // never appeared on tablet (nubecita-6k7e). Comparing the run's contentKeys
    // makes a grown/shrunk run a DIFFERENT scene, so NavDisplay swaps to the fresh
    // instance (whose `dialogRun.last()` is the pushed route) while an unchanged run
    // stays equal (no flicker). Keyed on contentKey, not NavEntry identity, since
    // NavEntry instances are re-created each recomposition.
    private val runKeys: List<Any> = dialogRun.map { it.contentKey }
    private val overlaidKeys: List<Any> = overlaidEntries.map { it.contentKey }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdaptiveDialogScene<*>) return false
        return key == other.key && runKeys == other.runKeys && overlaidKeys == other.overlaidKeys
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + runKeys.hashCode()
        result = 31 * result + overlaidKeys.hashCode()
        return result
    }

    override val content: @Composable () -> Unit = {
        val lifecycleOwner = rememberLifecycleOwner()
        Dialog(
            onDismissRequest = onBack,
            // Own the width ourselves (the platform default is too narrow for a
            // form) and let the IME push content up like a full-screen route.
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            // Manual scrim + tap-outside-to-dismiss: with a
                            // MATCH_PARENT window the platform dim layer is
                            // suppressed, so we paint our own.
                            .background(Color.Black.copy(alpha = 0.5f))
                            .pointerInput(onBack) { detectTapGestures { onBack() } },
                    contentAlignment = Alignment.Center,
                ) {
                    val cardWidth = if (maxWidth < MAX_CARD_WIDTH) maxWidth else MAX_CARD_WIDTH
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier =
                            Modifier
                                .width(cardWidth)
                                .fillMaxHeight()
                                // Absorb taps on inert chrome so they don't reach
                                // the scrim's dismiss handler.
                                .pointerInput(Unit) { detectTapGestures { } },
                    ) {
                        // Draw only the run's TOP route. Each route is its own
                        // scene (keyed on the top entry), so NavDisplay handles
                        // the push/pop transition between scenes; this card just
                        // renders the current top.
                        Box { dialogRun.last().Content() }
                    }
                }
            }
        }
    }
}

/**
 * A [SceneStrategy] that presents entries tagged with
 * [net.kikin.nubecita.core.common.navigation.adaptiveDialog] as a centered
 * [AdaptiveDialogScene] at Medium / Expanded widths, and **declines** (returns
 * `null`) at Compact — where the entry falls through to the normal full-screen
 * scene. This is the single, shared mechanism behind every "full-screen on
 * phone / dialog on tablet" surface: a feature opts in by tagging its route's
 * metadata, nothing more.
 *
 * The width gate mirrors `ListDetailSceneStrategy`; the dialog body mirrors
 * navigation3's built-in `DialogSceneStrategy`. Per the overlay-scene contract,
 * register this BEFORE any non-overlay strategy in `NavDisplay`'s
 * `sceneStrategies` list.
 */
internal class AdaptiveDialogSceneStrategy<T : Any>(
    private val windowSizeClass: WindowSizeClass,
) : SceneStrategy<T> {
    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        // Compact → decline; the entry renders full-screen via the fallback scene.
        if (!windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            return null
        }
        val lastEntry = entries.lastOrNull() ?: return null
        if (AdaptiveDialogKey !in lastEntry.metadata) return null
        // Coalesce the maximal run of consecutive adaptiveDialog entries at the
        // top of the back stack into ONE dialog: pushing/popping within the run
        // swaps the card content (stable scene key) rather than stacking a
        // separate dialog + scrim per entry — the Play Store "modal with nested
        // content" behaviour, via the same stable-key technique ListDetailScene
        // uses for its detail pane.
        var runStart = entries.lastIndex
        while (runStart > 0 && AdaptiveDialogKey in entries[runStart - 1].metadata) {
            runStart--
        }
        val dialogRun = entries.subList(runStart, entries.size)
        return AdaptiveDialogScene(
            // Key on the TOP of the run. NavDisplay identifies overlay scenes by
            // `AnimatedSceneKey = (sceneClass, scene.key)`; keying on the run's
            // BOTTOM made [Settings] and [Settings,About] share one key, so
            // pushing a sub-route produced a "same scene" that NavDisplay never
            // re-rendered — the sub-route stayed invisible on tablet
            // (nubecita-6k7e). Keying on the top makes each pushed/popped route a
            // distinct scene that NavDisplay actually renders.
            key = dialogRun.last().contentKey,
            dialogRun = dialogRun,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.subList(0, runStart),
            onBack = onBack,
        )
    }
}

/**
 * Remembers an [AdaptiveDialogSceneStrategy] keyed on the current window size
 * class (re-keys on fold/rotation, like `rememberListDetailSceneStrategy`). Add
 * the result to `NavDisplay(sceneStrategies = …)` BEFORE any non-overlay
 * strategy.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberAdaptiveDialogSceneStrategy(): SceneStrategy<T> {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return remember(windowSizeClass) { AdaptiveDialogSceneStrategy(windowSizeClass) }
}
