package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.actor.SearchActorsTypeaheadRequest
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import net.kikin.nubecita.data.models.ActorUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ActorTypeaheadRepository] backed by the atproto-kotlin
 * SDK's [ActorService]. Routes through the same authenticated
 * [io.github.kikin81.atproto.runtime.XrpcClient] every other
 * `:core:posting` reader uses — even though the lexicon documents
 * `searchActorsTypeahead` as not requiring auth, keeping the wiring
 * symmetric avoids a separate unauthenticated-client code path and
 * means the call participates in the same token-refresh interceptor
 * as `resolveHandle` and `createRecord`.
 *
 * Sends `q` (not the deprecated `term` parameter) and a fixed
 * `limit = 8` — the V1 dropdown shows ~3.5 visible rows before
 * scrolling, and 8 results comfortably overflows that without
 * blowing up the response payload. Tunable in-PR if manual feel
 * disagrees.
 *
 * All failures collapse to [Result.failure]; per the
 * [ActorTypeaheadRepository] contract, callers don't distinguish
 * failure subtypes. Cancellation re-throws so structured
 * concurrency in the composer ViewModel's `mapLatest` operator
 * cancels the upstream coroutine cleanly.
 */
@Singleton
internal class DefaultActorTypeaheadRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : ActorTypeaheadRepository {
        override suspend fun searchTypeahead(query: String): Result<List<ActorUi>> =
            try {
                val client = xrpcClientProvider.authenticated()
                val service = ActorService(client)
                val response =
                    service.searchActorsTypeahead(
                        SearchActorsTypeaheadRequest(q = query, limit = TYPEAHEAD_LIMIT),
                    )
                Result.success(response.actors.map { it.toActorUi() })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // Logged at debug — typeahead failures are routine
                // (typos that produce no matches are success-empty,
                // but a transient network blip during typing fires
                // here on every keystroke that escapes the debounce).
                // Higher levels would pollute logcat; debug is
                // recoverable via `adb logcat -s ActorTypeaheadRepo:D`.
                Timber.tag(TAG).d(t, "searchTypeahead(q=%s) failed", query)
                Result.failure(t)
            }

        private fun ProfileViewBasic.toActorUi(): ActorUi =
            ActorUi(
                did = did.raw,
                handle = handle.raw,
                // Normalize blank → null. The boundary type's contract
                // says null means "no display name"; surfacing an empty
                // string would force every consumer to re-check
                // `.isBlank()` to render the handle fallback.
                displayName = displayName?.takeIf { it.isNotBlank() },
                avatarUrl = avatar?.raw,
            )

        private companion object {
            private const val TAG = "ActorTypeaheadRepo"

            /**
             * `app.bsky.actor.searchActorsTypeahead`'s `limit` is
             * declared as `Long?` in the SDK; a fixed `8L` matches
             * Bluesky's mobile UX and keeps the per-call payload tight.
             */
            private const val TYPEAHEAD_LIMIT = 8L
        }
    }
