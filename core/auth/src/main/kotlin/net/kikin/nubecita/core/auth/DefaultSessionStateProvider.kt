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
            val handle = session?.handle
            val did = session?.did
            _state.value =
                when {
                    session == null -> SessionState.SignedOut
                    // atproto-kotlin v8 made OAuthSession.{did,handle,pdsUrl} nullable: a freshly-
                    // minted signup session may transiently have null identity until the new
                    // account's DID document is resolvable via the PLC directory. Treat that
                    // window as Loading so MainActivity's splash overlay stays up; the next
                    // refresh() (triggered after completeLogin's bounded-retry hydration) lands
                    // on SignedIn with non-null fields.
                    handle != null && did != null ->
                        SessionState.SignedIn(handle = handle, did = did)
                    else -> SessionState.Loading
                }
        }
    }
