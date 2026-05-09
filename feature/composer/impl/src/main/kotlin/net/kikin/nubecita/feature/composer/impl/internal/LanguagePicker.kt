package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Adaptive wrapper around [LanguagePickerContent]. At Compact width
 * uses [ModalBottomSheet]; at Medium / Expanded uses [Popup] over an
 * M3 [Surface] card — same width-class branching pattern as
 * [ComposerDiscardDialog], sidestepping the double-scrim issue when
 * the composer is itself a Compose `Dialog`.
 *
 * Manages the picker's local draft selection: tap-toggle mutates
 * [draft] without dispatching to the VM; only [onConfirm] commits the
 * draft. Drag-down on the bottom sheet, scrim-tap on the popup, and
 * Cancel all map to [onDismiss] without commit.
 *
 * The picker's initial selection is computed by the caller (typically
 * `state.selectedLangs ?: listOf(viewModel.deviceLocaleTag)`) and
 * passed in as [initialSelection].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun LanguagePicker(
    allTags: ImmutableList<String>,
    initialSelection: ImmutableList<String>,
    deviceLocaleTag: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(initialSelection) {
        mutableStateOf(initialSelection.toList())
    }
    val toggle: (String) -> Unit = { tag ->
        draft = if (draft.contains(tag)) draft - tag else draft + tag
    }
    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isCompact) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier,
        ) {
            LanguagePickerContent(
                allTags = allTags,
                draftSelection = draft.toImmutableList(),
                deviceLocaleTag = deviceLocaleTag,
                onToggle = toggle,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
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
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 480.dp),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    LanguagePickerContent(
                        allTags = allTags,
                        draftSelection = draft.toImmutableList(),
                        deviceLocaleTag = deviceLocaleTag,
                        onToggle = toggle,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}
