package net.kikin.nubecita.feature.moderation.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.BlockedAccount
import javax.inject.Inject

/**
 * Presenter for the blocked-accounts list (`nubecita-oftc.17`).
 *
 * Loads the viewer's blocked accounts on init / retry. Unblock is optimistic:
 * the row is removed immediately and `unblockActor` fires; a failure restores
 * the prior list and surfaces a transient error. Unblock is non-destructive
 * (re-blockable), so there's no confirm dialog.
 */
@HiltViewModel
internal class BlockedAccountsViewModel
    @Inject
    constructor(
        private val blockRepository: BlockRepository,
    ) : MviViewModel<BlockedAccountsState, BlockedAccountsEvent, BlockedAccountsEffect>(
            BlockedAccountsState(),
        ) {
        init {
            load()
        }

        override fun handleEvent(event: BlockedAccountsEvent) {
            when (event) {
                BlockedAccountsEvent.Retry -> load()
                is BlockedAccountsEvent.UnblockClicked -> unblock(event.account)
            }
        }

        private fun load() {
            setState { copy(status = BlockedAccountsStatus.Loading) }
            viewModelScope.launch {
                blockRepository
                    .blockedAccounts()
                    .onSuccess { accounts -> setState { copy(status = BlockedAccountsStatus.Loaded(accounts.toImmutableList())) } }
                    .onFailure { setState { copy(status = BlockedAccountsStatus.Error) } }
            }
        }

        private fun unblock(account: BlockedAccount) {
            val loaded = uiState.value.status as? BlockedAccountsStatus.Loaded ?: return
            val previous = loaded.accounts
            // Optimistically drop the row.
            setState { copy(status = BlockedAccountsStatus.Loaded(previous.filterNot { it.did == account.did }.toImmutableList())) }
            viewModelScope.launch {
                blockRepository
                    .unblockActor(account.blockUri)
                    .onFailure {
                        // Restore the pre-unblock list and surface the error.
                        setState { copy(status = BlockedAccountsStatus.Loaded(previous)) }
                        sendEffect(BlockedAccountsEffect.ShowUnblockError)
                    }
            }
        }
    }
