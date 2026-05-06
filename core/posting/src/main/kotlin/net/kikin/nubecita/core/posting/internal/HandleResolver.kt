package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.com.atproto.identity.IdentityService
import io.github.kikin81.atproto.com.atproto.identity.ResolveHandleRequest
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.XrpcClientProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves an AT Protocol `@handle` to its canonical `did:plc:…` /
 * `did:web:…` DID via `com.atproto.identity.resolveHandle`. The
 * composer's [FacetExtractor] uses this to turn each parsed `@handle`
 * token into a `FacetMention(did = …)` reference.
 *
 * Failure semantics: returns `null` for any non-cancellation failure
 * (handle doesn't resolve, network error, malformed handle). Per the
 * AT Protocol docs at https://docs.bsky.app/docs/advanced-guides/posts:
 *
 * > If the handle can't be resolved, just skip it! It will be rendered
 * > as text in the post instead of a link.
 *
 * Wrapping the SDK call behind this small interface lets the
 * [FacetExtractor] tests inject a `Map<String, Did?>`-backed fake
 * without standing up a real `XrpcClient` + Ktor mock per test.
 *
 * Cancellation propagates unchanged — callers run inside structured-
 * concurrency scopes (the composer's `viewModelScope` for typeahead,
 * `coroutineScope { … awaitAll() }` for facet resolution at submit) and
 * a swallowed cancel would leak coroutines past their parent.
 */
internal interface HandleResolver {
    suspend fun resolve(handle: String): Did?
}

/**
 * Production [HandleResolver] backed by the atproto-kotlin SDK's
 * [IdentityService]. Uses the same authenticated [XrpcClient] the rest
 * of the posting layer uses, even though `resolveHandle` itself is
 * documented as not requiring auth — keeps the wiring symmetric and
 * avoids a separate unauthenticated-client code path.
 */
@Singleton
internal class DefaultHandleResolver
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : HandleResolver {
        override suspend fun resolve(handle: String): Did? =
            try {
                val client = xrpcClientProvider.authenticated()
                val service = IdentityService(client)
                service.resolveHandle(ResolveHandleRequest(handle = Handle(handle))).did
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // Per docs: silently drop unresolvable handles; the
                // facet extractor falls back to plain text. Log at
                // debug so a flaky network or a typo'd handle is
                // diagnosable on `adb logcat` without polluting
                // logcat at higher levels for normal user typos.
                Timber.tag(TAG).d(t, "resolve(%s) failed; treating as unresolvable", handle)
                null
            }

        private companion object {
            private const val TAG = "HandleResolver"
        }
    }
