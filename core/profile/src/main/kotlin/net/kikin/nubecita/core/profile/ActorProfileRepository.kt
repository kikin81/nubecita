package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetProfileRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

/**
 * Slim wrapper around `app.bsky.actor.getProfile` that returns the
 * subset of fields most consumers actually need ([ActorProfile]).
 *
 * Lives in `:core:profile` so surfaces that need
 * `displayName + avatarUrl` for an arbitrary actor don't have to
 * reach into a sibling feature's internal repository. Settings's
 * identity header is the first non-Profile consumer; hover cards and
 * the future multi-account picker are the obvious next ones.
 *
 * Surfaces that need a richer projection (banner, bio, stats, viewer
 * relationship — the Profile hero) keep their own repositories and
 * call the same XRPC directly; the full wire model has fields that
 * aren't broadly reusable. Composing this repo from Profile's would
 * collapse one network call but force every Profile consumer to load
 * `:core:profile` types as well — not worth it until a third "full
 * projection" consumer surfaces.
 *
 * [fetchProfile]'s `actor` accepts either a DID (`did:plc:...`) or a
 * handle (`alice.bsky.social`); the lexicon resolves both forms.
 */
interface ActorProfileRepository {
    suspend fun fetchProfile(actor: String): Result<ActorProfile>
}

internal class DefaultActorProfileRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ActorProfileRepository {
        override suspend fun fetchProfile(actor: String): Result<ActorProfile> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    ActorService(client)
                        .getProfile(GetProfileRequest(actor = AtIdentifier(actor)))
                        .toActorProfile()
                }.onFailure { throwable ->
                    // `actor` is a raw DID or handle (PII). Log only the error
                    // identity — matches the redaction discipline applied to
                    // DIDs in `:core:auth/DefaultXrpcClientProvider` and
                    // `:feature:profile:impl/DefaultProfileRepository`.
                    Timber.tag(TAG).e(throwable, "fetchProfile failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val TAG = "ActorProfileRepo"
        }
    }
