package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.window.core.layout.WindowSizeClass
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.feature.composer.impl.KlipyPickerViewModel
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEffect
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEvent

/**
 * Adaptive KLIPY picker. Compact width → [ModalBottomSheet]; Medium / Expanded →
 * [Popup] over an M3 [Surface] card — the same width-class branch as
 * [AudiencePicker] / [LanguagePicker], avoiding the double-scrim when the
 * composer is itself a Compose `Dialog`.
 *
 * Stateful entry point: owns [KlipyPickerViewModel], collects its paging feed
 * and effects. [onSelectGif] is invoked (then [onDismiss]) when the user picks
 * an item; [onReportResult] reports whether a submitted report succeeded (for a
 * host snackbar). The stateless grid lives in [KlipyPickerContent].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun KlipyPicker(
    onSelectGif: (KlipyMediaUi) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReportResult: (succeeded: Boolean) -> Unit = {},
    viewModel: KlipyPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.media.collectAsLazyPagingItems()

    // The effect keys on `viewModel` (stable), so capture the latest host
    // callbacks via rememberUpdatedState rather than referencing the params
    // directly inside the restarting collector.
    val currentOnSelectGif by rememberUpdatedState(onSelectGif)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnReportResult by rememberUpdatedState(onReportResult)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is KlipyPickerEffect.GifSelected -> {
                    currentOnSelectGif(effect.media)
                    currentOnDismiss()
                }
                is KlipyPickerEffect.ReportCompleted -> currentOnReportResult(effect.succeeded)
            }
        }
    }

    val content: @Composable () -> Unit = {
        KlipyPickerContent(
            query = state.query,
            selectedTab = state.tab,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            items = items,
            onQueryChange = { viewModel.handleEvent(KlipyPickerEvent.QueryChanged(it)) },
            onSelectTab = { viewModel.handleEvent(KlipyPickerEvent.TabSelected(it)) },
            onSelectCategory = { viewModel.handleEvent(KlipyPickerEvent.CategorySelected(it)) },
            onItemClick = { viewModel.handleEvent(KlipyPickerEvent.ItemSelected(it)) },
            onItemLongPress = { viewModel.handleEvent(KlipyPickerEvent.ItemPreviewed(it)) },
        )
    }

    val isCompact =
        !currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isCompact) {
        val sheetState =
            rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
            content()
        }
    } else {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.widthIn(max = 480.dp),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    content()
                }
            }
        }
    }

    state.preview?.let { media ->
        KlipyGifPreviewDialog(
            media = media,
            onReport = { reason -> viewModel.handleEvent(KlipyPickerEvent.ItemReported(media, reason)) },
            onDismiss = { viewModel.handleEvent(KlipyPickerEvent.PreviewDismissed) },
        )
    }
}
