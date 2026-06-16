package net.kikin.nubecita.feature.moderation.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.BlockedAccount

/**
 * State for the blocked-accounts list. The load lifecycle is mutually exclusive
 * (loading / loaded / initial-error) so it's a sealed [BlockedAccountsStatus]
 * sum per the MVI conventions; an empty Loaded list renders the empty state.
 * Unblock is optimistic — the row leaves [BlockedAccountsStatus.Loaded.accounts]
 * immediately and is restored on failure (via the VM), so no extra flag is needed.
 */
@Immutable
internal data class BlockedAccountsState(
    val status: BlockedAccountsStatus = BlockedAccountsStatus.Loading,
) : UiState

internal sealed interface BlockedAccountsStatus {
    data object Loading : BlockedAccountsStatus

    data class Loaded(
        val accounts: ImmutableList<BlockedAccount> = persistentListOf(),
    ) : BlockedAccountsStatus

    /** Initial load failed; the body shows a retry affordance. */
    data object Error : BlockedAccountsStatus
}

internal sealed interface BlockedAccountsEvent : UiEvent {
    data object Retry : BlockedAccountsEvent

    data class UnblockClicked(
        val account: BlockedAccount,
    ) : BlockedAccountsEvent
}

internal sealed interface BlockedAccountsEffect : UiEffect {
    /** A committed unblock failed; the row was restored and a transient error is shown. */
    data object ShowUnblockError : BlockedAccountsEffect
}
