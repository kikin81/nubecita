package net.kikin.nubecita.feature.settings.impl.data

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
 * Subset of `app.bsky.actor.getProfile` that the Settings identity
 * header consumes — `displayName` + `avatarUrl`. Everything else the
 * Profile screen reads from `getProfile` (banner, bio, stats, viewer
 * relationship) is irrelevant here, so we keep this surface minimal
 * rather than reaching across into `:feature:profile:impl`'s
 * internal `ProfileRepository`.
 *
 * If a second non-Profile consumer of `getProfile` shows up later
 * (e.g. a hover card, a notification author chip, the multi-account
 * picker), promote a shared `:core:profile` module — same escape
 * hatch noted in `:feature:profile:impl/data/AuthorProfileMapper`.
 */
internal data class SettingsAccountHeader(
    val displayName: String?,
    val avatarUrl: String?,
)

internal interface SettingsAccountRepository {
    /**
     * Resolves the signed-in user's display name and avatar URL via
     * `app.bsky.actor.getProfile`. Returns `null` for either field
     * when the wire response omits it or returns it blank. Network
     * / auth failures are surfaced as a `Result.failure`; the screen
     * is responsible for rendering its fallback (greeting → "Hi!",
     * avatar → initials disc) on failure.
     */
    suspend fun fetchHeader(actor: String): Result<SettingsAccountHeader>
}

internal class DefaultSettingsAccountRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SettingsAccountRepository {
        override suspend fun fetchHeader(actor: String): Result<SettingsAccountHeader> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ActorService(client).getProfile(GetProfileRequest(actor = AtIdentifier(actor)))
                    SettingsAccountHeader(
                        displayName = response.displayName?.takeUnless { it.isBlank() },
                        avatarUrl = response.avatar?.raw,
                    )
                }.onFailure { throwable ->
                    // `actor` is a raw DID (PII). Log only the error
                    // identity — matches the redaction discipline applied
                    // to DIDs in `:core:auth/DefaultXrpcClientProvider`
                    // and `:feature:profile:impl/DefaultProfileRepository`.
                    Timber.tag(TAG).e(throwable, "fetchHeader failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val TAG = "SettingsAccountRepo"
        }
    }
