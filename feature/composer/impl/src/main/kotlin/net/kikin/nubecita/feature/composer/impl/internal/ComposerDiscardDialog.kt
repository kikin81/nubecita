package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Discard-confirmation dialog for the composer. Branches on width
 * class so that:
 *
 * - **Compact** uses [BasicAlertDialog]. The composer at Compact is a
 *   `NavDisplay` route — there is no other Window in the stack, so
 *   one Compose Dialog hosting the confirmation is the cleanest
 *   shape.
 * - **Medium / Expanded** uses [Popup] wrapping an M3 [Surface] card
 *   with rounded corners + tonalElevation. The composer at these
 *   widths is itself a `Dialog` that already paints a platform scrim;
 *   stacking another `Dialog` would composite a second dim layer
 *   ("double-scrim regression"), which screens darker than M3's
 *   single dialog spec. A `Popup` doesn't paint a scrim of its own,
 *   so the existing composer scrim is preserved verbatim.
 *
 * Layoutlib (Compose Preview / screenshot tests) can't host
 * `BasicAlertDialog` or `Popup` because those require an
 * Activity-hosted `Window`; previews and screenshot fixtures render
 * [ComposerDiscardDialogContent] directly instead. This mirrors the
 * `ComposerOverlayCard` pattern.
 *
 * The action list is data-driven ([ComposerDialogAction]); V1 ships
 * exactly `Cancel` + `Discard` but the eventual third "Save draft"
 * action drops in without a layout rewrite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposerDiscardDialog(
    actions: ImmutableList<ComposerDialogAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    if (isCompact) {
        BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier) {
            ComposerDiscardDialogContent(actions = actions)
        }
    } else {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = onDismiss,
            properties =
                PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
        ) {
            ComposerDiscardDialogContent(actions = actions, modifier = modifier)
        }
    }
}

/**
 * Pure rendering of the discard-confirmation card. Extracted so
 * Compose previews + screenshot fixtures can render it directly
 * without a `BasicAlertDialog` / `Popup` wrapper (Layoutlib can't
 * host either).
 *
 * Visual treatment matches M3 AlertDialog: the dialog shape and
 * tonal elevation come from [AlertDialogDefaults]; padding is the
 * 24dp spec value; actions sit in a [FlowRow] aligned to the
 * end-bottom corner so a future third action wraps gracefully.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComposerDiscardDialogContent(
    actions: ImmutableList<ComposerDialogAction>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.composer_discard_title),
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                actions.forEach { action ->
                    val colors =
                        if (action.destructive) {
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            ButtonDefaults.textButtonColors()
                        }
                    TextButton(onClick = action.onClick, colors = colors) {
                        Text(text = stringResource(action.label))
                    }
                }
            }
        }
    }
}
