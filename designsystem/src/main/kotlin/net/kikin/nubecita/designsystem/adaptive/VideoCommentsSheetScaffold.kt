package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/**
 * Default dimensions and constants for [VideoCommentsSheetScaffold].
 */
object VideoCommentsSheetScaffoldDefaults {
    val MinSideSheetWidth: Dp = 360.dp
    val MaxSideSheetWidth: Dp = 440.dp
    val DefaultSideSheetPreferredWidth: Dp = 400.dp
    val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * Returns whether the sheet is settled in the [SheetValue.Hidden] state with no
 * active animation running.
 */
@OptIn(ExperimentalMaterial3Api::class)
val SheetState.isSettledHidden: Boolean
    get() = currentValue == SheetValue.Hidden && !isAnimationRunning

/**
 * Checks whether the comments sheet is settled in hidden state across form factors,
 * used to gate parent gestures (e.g., `VerticalPager.userScrollEnabled`).
 */
@OptIn(ExperimentalMaterial3Api::class)
fun isCommentsSheetSettledHidden(
    isCompact: Boolean,
    isCommentsOpen: Boolean,
    sheetState: SheetState,
): Boolean =
    if (isCompact) {
        !isCommentsOpen || sheetState.isSettledHidden
    } else {
        !isCommentsOpen
    }

/**
 * Adaptive scaffold hosting video content in the primary pane and comments
 * content in either an anchored bottom sheet (Compact < 600dp) or a zero-scrim
 * side pane using [SupportingPaneScaffold] (Medium & Expanded >= 600dp).
 *
 * Conforms to OpenSpec decisions D1 & D2:
 * - **Compact**: Anchored bottom sheet overlaying lower screen with zeroed default
 *   window insets to coordinate with docked pill insets.
 * - **Medium / Expanded**: Two-pane split via [SupportingPaneScaffold] with hinge
 *   awareness, right pane constrained between 360dp and 440dp (default 400dp),
 *   zero modal dimming scrim (video controls stay interactive), and scoped
 *   [WindowInsets.ime] padding applied strictly to the comments side sheet.
 */
@Suppress("ktlint:compose:content-slot-reused")
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoCommentsSheetScaffold(
    isCommentsOpen: Boolean,
    onDismiss: () -> Unit,
    commentsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
        ),
    adaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
    videoContent: @Composable () -> Unit,
) {
    val movableVideo = remember(videoContent) { movableContentOf { videoContent() } }
    val movableComments = remember(commentsContent) { movableContentOf { commentsContent() } }

    val isCompact =
        !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
        )

    if (isCompact) {
        Box(modifier = modifier.fillMaxSize()) {
            movableVideo()

            if (isCommentsOpen) {
                if (LocalInspectionMode.current) {
                    // In inspection/preview mode, layoutlib does not render Dialog/ModalBottomSheet
                    // separate windows. Render an in-layout bottom sheet overlay to provide pixel-accurate
                    // screenshot baselines.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.32f)),
                    )
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(0.5f),
                        shape = VideoCommentsSheetScaffoldDefaults.BottomSheetShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                BottomSheetDefaults.DragHandle()
                            }
                            movableComments()
                        }
                    }
                } else {
                    ModalBottomSheet(
                        onDismissRequest = onDismiss,
                        sheetState = sheetState,
                        shape = VideoCommentsSheetScaffoldDefaults.BottomSheetShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        scrimColor = BottomSheetDefaults.ScrimColor,
                        dragHandle = { BottomSheetDefaults.DragHandle() },
                        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                    ) {
                        movableComments()
                    }
                }
            }
        }
    } else {
        val directive =
            remember(adaptiveInfo) {
                calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(adaptiveInfo).copy(
                    defaultPanePreferredWidth = VideoCommentsSheetScaffoldDefaults.DefaultSideSheetPreferredWidth,
                )
            }
        val scaffoldValue =
            remember(isCommentsOpen) {
                ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded,
                    secondary = if (isCommentsOpen) PaneAdaptedValue.Expanded else PaneAdaptedValue.Hidden,
                    tertiary = PaneAdaptedValue.Hidden,
                )
            }

        SupportingPaneScaffold(
            directive = directive,
            value = scaffoldValue,
            mainPane = {
                AnimatedPane(modifier = Modifier.fillMaxSize()) {
                    movableVideo()
                }
            },
            supportingPane = {
                AnimatedPane(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .widthIn(
                                min = VideoCommentsSheetScaffoldDefaults.MinSideSheetWidth,
                                max = VideoCommentsSheetScaffoldDefaults.MaxSideSheetWidth,
                            ).windowInsetsPadding(WindowInsets.ime),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        movableComments()
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}
