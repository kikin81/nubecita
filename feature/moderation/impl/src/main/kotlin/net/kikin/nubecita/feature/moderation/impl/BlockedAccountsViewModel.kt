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
            val originalIndex = loaded.accounts.indexOfFirst { it.did == account.did }
            if (originalIndex < 0) return // already gone — ignore a double-tap.
            // Optimistically drop the row.
            setState { copy(status = BlockedAccountsStatus.Loaded(loaded.accounts.filterNot { it.did == account.did }.toImmutableList())) }
            viewModelScope.launch {
                blockRepository
                    .unblockActor(account.blockUri)
                    .onFailure {
                        // Re-insert ONLY this account into the CURRENT list (at its
                        // original position, clamped), so a concurrent unblock that
                        // succeeded isn't resurrected by restoring a stale snapshot.
                        setState {
                            val current = status as? BlockedAccountsStatus.Loaded ?: return@setState this
                            if (current.accounts.any { it.did == account.did }) return@setState this
                            val restored =
                                current.accounts.toMutableList().apply {
                                    add(originalIndex.coerceIn(0, size), account)
                                }
                            copy(status = BlockedAccountsStatus.Loaded(restored.toImmutableList()))
                        }
                        sendEffect(BlockedAccountsEffect.ShowUnblockError)
                    }
            }
        }
    }
