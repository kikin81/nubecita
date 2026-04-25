package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

internal class DefaultSessionStateProvider
    @Inject
    constructor(
        private val sessionStore: OAuthSessionStore,
    ) : SessionStateProvider {
        private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
        override val state: StateFlow<SessionState> = _state.asStateFlow()

        override suspend fun refresh() {
            val session = sessionStore.load()
            _state.value =
                if (session != null) {
                    SessionState.SignedIn(handle = session.handle, did = session.did)
                } else {
                    SessionState.SignedOut
                }
        }
    }
