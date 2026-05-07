package net.kikin.nubecita.shell.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.kikin.nubecita.core.common.navigation.ComposerOverlayState
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.ComposerScreen
import net.kikin.nubecita.feature.composer.impl.ComposerViewModel

/**
 * Centered-dialog overlay host for the composer at Medium / Expanded
 * widths. Renders nothing when [state] is [ComposerOverlayState.Closed];
 * renders a [Dialog] containing the composer when [state] is
 * [ComposerOverlayState.Open].
 *
 * Container choice — see `openspec/changes/unified-composer/design.md`
 * § "Adaptive container":
 *
 * - `Dialog(usePlatformDefaultWidth = false)` so we control width
 *   ourselves. Without this, `Dialog` clamps to the platform theme's
 *   `windowMinWidthMajor/Minor` (~320dp) and the canvas is too narrow.
 * - `decorFitsSystemWindows = false` so the IME insets push the
 *   composer's content up the same way they would in a full-screen
 *   route. Without this the keyboard overlaps the text field.
 * - `Modifier.widthIn(max = 640.dp)` matching `BottomSheetDefaults.SheetMaxWidth`
 *   and the M3 dialog spec's content-heavy upper bound (~720dp). Wider
 *   wastes canvas; narrower would force three-image rows into a
 *   cramped column.
 *
 * ViewModel scoping: Compose's `Dialog` provides its own
 * `LocalViewModelStoreOwner` whose lifetime is the Dialog's
 * composition. When [state] transitions Open → Closed, the Dialog
 * leaves composition, the owner's `ViewModelStore.clear()` fires,
 * and the [ComposerViewModel] is disposed. A subsequent Open(...)
 * with a fresh `replyToUri` creates a brand-new VM. No manual VM
 * scoping required.
 *
 * Dismissal:
 *
 * - System back / outside-tap → [Dialog.onDismissRequest] → invokes
 *   [onClose] which sets the launcher state back to Closed.
 * - Composer's `OnSubmitSuccess` effect → screen calls
 *   `onSubmitSuccess(uri)` → invokes [onClose].
 * - Composer's close-button (`NavigateBack` effect) → screen calls
 *   `onNavigateBack` → invokes [onClose].
 *
 * V1 does not surface optimistic insertion; on submit success the
 * overlay closes and the underlying feed picks up the new post on
 * its next refresh.
 */
@Composable
internal fun ComposerOverlay(
    state: ComposerOverlayState,
    onClose: () -> Unit,
) {
    val openState = state as? ComposerOverlayState.Open ?: return

    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // `BoxWithConstraints` reads the actual incoming constraints
        // from the Dialog's content slot. We can't rely on
        // `Modifier.widthIn(max = X).fillMaxWidth()` to clamp the
        // surface ourselves: Compose's `Dialog` (with
        // `usePlatformDefaultWidth = false`) hands unbounded width
        // constraints down through its content wrapper, so the cap
        // never engages and the surface stretches to whatever the
        // content's intrinsic width is — usually full-screen.
        // Reading `maxWidth` here and clamping at this level fixes
        // the cap and lets centering work via `contentAlignment`.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Manual scrim. With `usePlatformDefaultWidth = false`
                    // the window goes `MATCH_PARENT`, suppressing the
                    // platform dim layer (there's nothing visible
                    // behind a full-screen window to dim), so we paint
                    // our own and wire tap-outside-to-dismiss manually.
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(onClose) {
                        detectTapGestures { onClose() }
                    },
            contentAlignment = Alignment.Center,
        ) {
            // Cap at 640dp matching `BottomSheetDefaults.SheetMaxWidth`
            // and the M3 dialog spec's content-heavy upper bound. On
            // narrower windows (e.g. Medium portrait at 600dp) the
            // surface tracks the available width.
            val cardWidth = if (maxWidth < 640.dp) maxWidth else 640.dp
            Box(
                modifier =
                    Modifier
                        .width(cardWidth)
                        .fillMaxHeight(),
            ) {
                ComposerOverlayContent(
                    replyToUri = openState.replyToUri,
                    onClose = onClose,
                )
            }
        }
    }
}

/**
 * Inner content of the overlay Dialog. Split out as a separate
 * Composable so [hiltViewModel] can be a default parameter — the
 * Slack compose-lint rule `ComposeViewModelInjection` flags inline
 * `hiltViewModel(...)` in a Composable body as an "implicit
 * dependency", and reshaping to a default param makes the VM
 * injection explicit and the Composable testable.
 *
 * The default-param expression is evaluated lazily at call time, so
 * [replyToUri] is correctly captured by the `creationCallback`
 * lambda each time this Composable is composed (a new Open(...)
 * with a fresh URI gets a fresh VM with the right route).
 *
 * Suppresses [compose:vm-forwarding-check]: yes, the [viewModel] is
 * passed downstream into [ComposerScreen]. The intent of the
 * forwarding-check rule is to flag VMs threaded through 3+ layers
 * of Composables; here it's exactly one hop (Content → Screen) and
 * exists only because the `ComposeViewModelInjection` rule requires
 * the VM live in a default param of a named Composable. The two
 * rules are at odds for this specific shape; we satisfy the more
 * load-bearing one (`ComposeViewModelInjection`, which the wider
 * codebase respects on every other screen).
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
private fun ComposerOverlayContent(
    replyToUri: String?,
    onClose: () -> Unit,
    viewModel: ComposerViewModel =
        hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(
            creationCallback = { factory ->
                factory.create(ComposerRoute(replyToUri = replyToUri))
            },
        ),
) {
    ComposerOverlayCard {
        ComposerScreen(
            onNavigateBack = onClose,
            onSubmitSuccess = { _ -> onClose() },
            viewModel = viewModel,
        )
    }
}

/**
 * Visual chrome for the composer overlay — a centered M3 [Surface]
 * capped at 640dp wide. Extracted so production [ComposerOverlay]
 * (which wraps it in a real [Dialog]) and screenshot fixtures (which
 * render this directly without a Dialog, since Layoutlib can't host
 * Compose Dialogs) draw the SAME visual primitive.
 *
 * The 640dp width cap matches `BottomSheetDefaults.SheetMaxWidth`
 * and lands in the upper end of the M3 dialog spec range. Sized for
 * tablet portrait (~600dp) through expanded landscape phones (~840dp);
 * wider would feel like a wasted canvas, narrower would push images
 * and parent-post previews into a cramped column.
 */
@Composable
internal fun ComposerOverlayCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .fillMaxSize()
                // Consume taps inside the card so they don't bubble up
                // to the scrim's tap-to-dismiss handler. Inner
                // interactive elements (text field, buttons) consume
                // their own gestures before reaching here, so this only
                // catches taps on inert chrome (padding, blank surface).
                .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Box { content() }
    }
}
