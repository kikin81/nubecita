package net.kikin.nubecita.core.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Inject

/**
 * The signed-in account's identity, as shown wherever the app says "you"
 * (the composer's posting identity, the outgoing-post card, a future account
 * switcher). [displayName] is never blank: it falls back to [handle].
 */
data class CurrentAccount(
    val did: String,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * Shared, cached source of the signed-in account. One app-scoped instance, so
 * every consumer reads the same identity and the profile is fetched once per
 * session rather than once per screen. The future multi-account switcher is the
 * place that will change which account this emits.
 */
interface CurrentAccountRepository {
    /** The signed-in account, or `null` while signed out or still loading the session. */
    val account: StateFlow<CurrentAccount?>
}

/**
 * Seeds the account from the session the moment it signs in (display name =
 * handle, no avatar), then enriches it from `app.bsky.actor.getProfile` once per
 * session. A failed fetch keeps the seed. Token refreshes re-emit the same
 * signed-in account, which [distinctUntilChanged] collapses so they don't
 * refetch.
 */
internal class DefaultCurrentAccountRepository
    @Inject
    constructor(
        private val sessionStateProvider: SessionStateProvider,
        private val actorProfileRepository: ActorProfileRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : CurrentAccountRepository {
        // Seeded synchronously, so a screen created while already signed in reads the
        // account straight away instead of a null until the collector first runs.
        private val _account = MutableStateFlow((sessionStateProvider.state.value as? SessionState.SignedIn)?.toSeed())
        override val account: StateFlow<CurrentAccount?> = _account.asStateFlow()

        init {
            scope.launch {
                sessionStateProvider.state
                    .map { state -> (state as? SessionState.SignedIn)?.let { SignedInAccount(it.did, it.handle) } }
                    .distinctUntilChanged()
                    .collectLatest { signedIn ->
                        if (signedIn == null) {
                            _account.value = null
                            return@collectLatest
                        }
                        _account.value = seed(signedIn.did, signedIn.handle)
                        actorProfileRepository.fetchProfile(signedIn.did).onSuccess { profile ->
                            _account.value =
                                CurrentAccount(
                                    did = signedIn.did,
                                    handle = profile.handle,
                                    displayName = profile.displayName ?: profile.handle,
                                    avatarUrl = profile.avatarUrl,
                                )
                        }
                    }
            }
        }

        private data class SignedInAccount(
            val did: String,
            val handle: String,
        )
    }

/** The account before its profile loads: the handle stands in for the display name. */
private fun seed(
    did: String,
    handle: String,
) = CurrentAccount(did = did, handle = handle, displayName = handle, avatarUrl = null)

private fun SessionState.SignedIn.toSeed() = seed(did, handle)
