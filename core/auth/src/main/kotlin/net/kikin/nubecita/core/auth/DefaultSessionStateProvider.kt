package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
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
                        SessionState.SignedIn(handle = handle, did = did, pdsUrl = session.pdsUrl)
                    else -> SessionState.Loading
                }
        }
    }

/**
 * Classify a PDS service endpoint as self-hosted (a non-Bluesky-operated PDS).
 *
 * Bluesky operates BOTH the entryway host `bsky.social` AND the per-user
 * `*.host.bsky.network` PDS hosts (e.g. `hollowfoot.us-west.host.bsky.network`),
 * so a naive `!host.contains("bsky.social")` check would misclassify every
 * bsky.network-hosted account as self-hosted. Self-hosted = the host is neither
 * `bsky.social` nor `host.bsky.network` nor a subdomain of `host.bsky.network`.
 * An absent/unparseable host can't be classified → treated as not self-hosted.
 */
internal fun isSelfHostedPds(pdsUrl: String?): Boolean {
    val host = pdsUrl?.let { runCatching { URI(it).host }.getOrNull() }?.lowercase()
    if (host.isNullOrEmpty()) return false
    val bskyOperated =
        host == "bsky.social" || host == "host.bsky.network" || host.endsWith(".host.bsky.network")
    return !bskyOperated
}
