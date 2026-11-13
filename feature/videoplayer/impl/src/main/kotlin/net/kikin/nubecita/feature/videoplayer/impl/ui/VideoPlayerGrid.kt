@file:OptIn(ExperimentalGridApi::class, ExperimentalMediaQueryApi::class)

package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.GridConfigurationScope
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.mediaQuery
import androidx.compose.ui.unit.dp

/**
 * Named areas of the fullscreen player's single [androidx.compose.foundation.layout.Grid].
 *
 * The children are tagged once with `Modifier.gridItem(areaId = ...)` and never
 * move in the composition. Only the [VideoPlayerGridConfiguration] that maps
 * these ids to physical cells changes with the device posture.
 */
internal enum class VideoPlayerArea {
    /** Poster + Media3 `PlayerSurface`. */
    Player,

    /** Chrome: close, transport, seek bar, post actions. */
    Controller,
}

/**
 * The two geometries of the player grid. Both place the SAME two children, so
 * switching between them is a measure-only change: `Grid` reads its config
 * lambda during the measure pass, which means a fold or unfold relayouts the
 * player without recomposing it, and the `PlayerSurface` (a `SurfaceView`) is
 * never detached or recreated.
 *
 * The lambdas are stable singletons on purpose. A config lambda allocated
 * inline at the call site would get a new identity on every recomposition and
 * the position-polling tick recomposes this screen four times a second.
 */
internal object VideoPlayerGridConfiguration {
    /**
     * Flat posture: one cell. Both areas map to it and the controller overlays
     * the player because `Grid` draws children in declaration order.
     */
    val Default: GridConfigurationScope.() -> Unit = {
        column(1f)
        row(1f)
        area(areaId = VideoPlayerArea.Player, row = 1, column = 1)
        area(areaId = VideoPlayerArea.Controller, row = 1, column = 1)
    }

    /**
     * Tabletop posture: two half-height rows. The player sits on the upright
     * half above the fold, the controller on the flat half below it.
     */
    val Tabletop: GridConfigurationScope.() -> Unit = {
        column(1f)
        row(0.5f)
        row(0.5f)
        area(areaId = VideoPlayerArea.Player, row = 1, column = 1)
        area(areaId = VideoPlayerArea.Controller, row = 2, column = 1)
    }
}

/**
 * Named areas of the chrome's own grid. Same idea one level down: the three
 * bands are declared once and only their cells change.
 */
internal enum class VideoPlayerChromeArea {
    /** Close button. */
    TopBar,

    /** Skip back, play/pause, skip forward, plus the mute / pop-out row. */
    Transport,

    /** Seek bar, time labels, post action group. */
    BottomBar,
}

internal object VideoPlayerChromeGridConfiguration {
    /**
     * Overlay: one cell, the bands are pinned to its top, center and bottom
     * edges by their `gridItem` alignment. Identical to the previous `Box`.
     */
    val Overlay: GridConfigurationScope.() -> Unit = {
        column(1f)
        row(1f)
        area(areaId = VideoPlayerChromeArea.TopBar, row = 1, column = 1)
        area(areaId = VideoPlayerChromeArea.Transport, row = 1, column = 1)
        area(areaId = VideoPlayerChromeArea.BottomBar, row = 1, column = 1)
    }

    /**
     * Stacked: three rows. The half-height tabletop panel is too short for the
     * centered transport cluster to clear the bottom band when they share a
     * cell, so each band gets its own track. The middle track takes whatever
     * the two content-sized bands leave.
     */
    val Stacked: GridConfigurationScope.() -> Unit = {
        column(1f)
        row(GridTrackSize.Auto)
        row(minmax(0.dp, 1.fr))
        row(GridTrackSize.Auto)
        area(areaId = VideoPlayerChromeArea.TopBar, row = 1, column = 1)
        area(areaId = VideoPlayerChromeArea.Transport, row = 2, column = 1)
        area(areaId = VideoPlayerChromeArea.BottomBar, row = 3, column = 1)
    }
}

/**
 * True while the window is folded into tabletop posture (hinge horizontal,
 * half opened).
 *
 * Call this in composition and pass the result down. Do not call `mediaQuery`
 * inside a `Grid(config = { ... })` block: that block runs in the measure
 * pass, where composition locals cannot be read.
 *
 * `LocalUiMediaScope` only has a value when
 * `ComposeUiFlags.isMediaQueryIntegrationEnabled` was set before the
 * ComposeView root composed, and reading it otherwise throws.
 * `NubecitaApplication` sets the flag, but instrumented tests boot
 * `HiltTestApplication` instead (see `HiltTestRunner`), so
 * `MarketingScreenshotJourney` opens this screen with the flag off. Hence the
 * guard: with the integration off the posture is unknown and the player keeps
 * its flat layout rather than crashing.
 *
 * Stateless content and previews take the posture as a parameter instead of
 * calling this themselves.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
@ReadOnlyComposable
internal fun isTabletopPosture(): Boolean {
    if (!ComposeUiFlags.isMediaQueryIntegrationEnabled) return false
    return queryTabletopPosture()
}

/**
 * The posture query itself, with no guard: it needs a `LocalUiMediaScope` in
 * scope and throws without one. Production code calls [isTabletopPosture].
 * This exists for previews and screenshot fixtures, which provide their own
 * scope ([PreviewUiMediaScope]) while the global flag stays off.
 */
@Composable
@ReadOnlyComposable
internal fun queryTabletopPosture(): Boolean = mediaQuery { windowPosture == UiMediaScope.Posture.Tabletop }
