package net.kikin.nubecita.feature.moderation.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.moderation.api.Block

/**
 * Presenter for the Block-account confirmation dialog.
 *
 * Assisted-injected with the [Block] NavKey (same bridge as
 * `ReportDialogViewModel`), so the target DID + handle seed the state
 * synchronously. Confirm creates the `app.bsky.graph.block` record via
 * [BlockRepository]; success dismisses the sheet, failure surfaces a retryable
 * inline error.
 */
@HiltViewModel(assistedFactory = BlockDialogViewModel.Factory::class)
internal class BlockDialogViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: Block,
        private val blockRepository: BlockRepository,
    ) : MviViewModel<BlockDialogState, BlockDialogEvent, BlockDialogEffect>(
            BlockDialogState(handle = route.handle),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: Block): BlockDialogViewModel
        }

        override fun handleEvent(event: BlockDialogEvent) {
            when (event) {
                BlockDialogEvent.OnCancelClicked -> sendEffect(BlockDialogEffect.RequestDismiss)
                BlockDialogEvent.OnConfirmClicked -> confirm()
            }
        }

        private fun confirm() {
            if (uiState.value.isSubmitting) return // single-flight; ignore double-tap.
            setState { copy(isSubmitting = true, hasError = false) }
            viewModelScope.launch {
                blockRepository
                    .blockActor(route.did)
                    .onSuccess { sendEffect(BlockDialogEffect.RequestDismiss) }
                    .onFailure { setState { copy(isSubmitting = false, hasError = true) } }
            }
        }
    }
