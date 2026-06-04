package net.kikin.nubecita.shell.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
 * width-capped [Surface] card. Only the top entry of [dialogRun] is shown; when
 * the run changes (a dialog sub-route is pushed or popped) the card's content
 * swaps in place via [AnimatedContent] instead of a new dialog being stacked —
 * the Play Store "modal with nested content" behaviour. The content beneath the
 * run stays composed underneath ([overlaidEntries]) — so e.g. the profile
 * screen behind the editor keeps observing its `ownProfileUpdates` refresh
 * signal while the dialog is open.
 *
 * Coalescing + content-swap uses the same stable-scene-key technique as
 * navigation3's `ListDetailScene` (the strategy keys this scene on the *bottom*
 * of the run, so `NavDisplay` keeps the same scene and this `AnimatedContent`
 * does the transition). Cloned from navigation3's internal `DialogScene`,
 * adding the scrim + 640dp card chrome — an editor/composer wants a roomier
 * surface than the platform default dialog width, which is why we set
 * `usePlatformDefaultWidth = false` and size the card ourselves (640dp =
 * `BottomSheetDefaults.SheetMaxWidth`).
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
                        // Swap the card's content when the run's top entry
                        // changes (push/pop within the dialog). A short cross-
                        // fade reads cleanly in both directions and avoids the
                        // wrong-way slide a directional transition would show on
                        // back.
                        AnimatedContent(
                            targetState = dialogRun.last(),
                            contentKey = { it.contentKey },
                            transitionSpec = {
                                fadeIn(tween(durationMillis = 150)) togetherWith
                                    fadeOut(tween(durationMillis = 150))
                            },
                            label = "adaptiveDialogContent",
                        ) { runEntry ->
                            Box { runEntry.Content() }
                        }
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
            // Key on the BOTTOM of the run so the scene stays stable across
            // pushes/pops within the dialog (NavDisplay keeps the same scene and
            // the AnimatedContent does the transition).
            key = dialogRun.first().contentKey,
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
