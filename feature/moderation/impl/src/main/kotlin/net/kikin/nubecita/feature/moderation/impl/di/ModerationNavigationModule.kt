package net.kikin.nubecita.feature.moderation.impl.di

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.impl.ReportDialogScreen
import net.kikin.nubecita.feature.moderation.impl.ReportDialogViewModel

/**
 * Provides the `@MainShell`-qualified `EntryProviderInstaller` that
 * registers the `Report` NavKey inside `MainShell`'s inner `NavDisplay`.
 *
 * The entry wraps [ReportDialogScreen] in a Material 3
 * [ModalBottomSheet]. Three dismissal paths converge on
 * `LocalMainShellNavState.current.removeLast()`:
 *
 * 1. The user taps outside the sheet or drags it down — M3's
 *    `ModalBottomSheet.onDismissRequest` fires, which calls `pop()`.
 * 2. The user presses Back while `step == Subject` — the screen's
 *    `BackHandler` is disabled, so the press falls through to the
 *    sheet's predictive-back handler, which also fires
 *    `onDismissRequest`.
 * 3. The VM emits `RequestDismiss` (cancel button, post-success auto-
 *    timer, or `OnBackPressed` from the Subject step) — the screen's
 *    effect collector calls the `onDismiss` lambda we wire here, which
 *    closes the sheet first (preserving the slide-down animation)
 *    before popping the NavKey.
 *
 * The entry does NOT carry `ListDetailSceneStrategy.listPane{}` /
 * `detailPane{}` metadata per the spec — Report is a transient overlay
 * that the scene strategy resolves wherever it fits (full-screen on
 * Compact, overlaying the detail pane on Medium / Expanded).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ModerationNavigationModule {
    @OptIn(ExperimentalMaterial3Api::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideReportEntries(): EntryProviderInstaller =
        {
            entry<Report> { route ->
                val navState = LocalMainShellNavState.current
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                val viewModel =
                    hiltViewModel<ReportDialogViewModel, ReportDialogViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )

                // Single source of truth for "close the sheet and pop the
                // NavKey": animate the sheet down to give the M3 motion
                // its frames, then pop. Used by both the VM's
                // RequestDismiss path AND the sheet's outside-tap /
                // drag-dismiss path so they look identical.
                val dismiss =
                    remember(scope, sheetState, navState) {
                        {
                            scope.launch {
                                sheetState.hide()
                                navState.removeLast()
                            }
                            Unit
                        }
                    }

                ModalBottomSheet(
                    onDismissRequest = dismiss,
                    sheetState = sheetState,
                ) {
                    ReportDialogScreen(
                        viewModel = viewModel,
                        onDismiss = dismiss,
                    )
                }
            }
        }
}
