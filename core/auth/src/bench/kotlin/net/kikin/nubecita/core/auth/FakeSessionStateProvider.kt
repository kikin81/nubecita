package net.kikin.nubecita.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [SessionStateProvider]. Reports a deterministic
 * [SessionState.SignedIn] at construction so `MainActivity`'s splash-
 * routing predicate resolves on the first frame and the bench's setup
 * phase never has to wait for an OAuth handshake.
 *
 * Backed by a [MutableStateFlow] (matching the production impl's contract
 * comment on `SessionStateProvider.state`) so the synchronous
 * `state.value` read used by `setKeepOnScreenCondition` works from the
 * platform's frame callback.
 *
 * [refresh] is a no-op — the bench session is hardcoded and doesn't read
 * from any underlying store.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeSessionStateProvider
    @Inject
    constructor() : SessionStateProvider {
        private val _state =
            MutableStateFlow<SessionState>(
                SessionState.SignedIn(
                    handle = "bench.nubecita.app",
                    did = "did:plc:benchnubecita0000000000000",
                ),
            )

        override val state: StateFlow<SessionState> = _state.asStateFlow()

        override suspend fun refresh() = Unit
    }
