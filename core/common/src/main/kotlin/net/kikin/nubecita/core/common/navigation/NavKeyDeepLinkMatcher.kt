package net.kikin.nubecita.core.common.navigation

import androidx.core.net.toUri
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.actionFilter
import kotlinx.serialization.KSerializer

/**
 * Adapter around a [androidx.navigation3.runtime.deeplink.DeepLinkMatcher]
 * whose match result is a navigation key. Feature `:impl` modules
 * contribute one of these per supported URL shape via `@Provides @IntoSet`
 * so the Activity-side router (see `MainActivity.handleIntent`) can
 * iterate matchers without knowing each one's concrete `NavKey` subtype.
 *
 * The underlying [androidx.navigation3.runtime.deeplink.DeepLinkMatcher]
 * is parameterised by `T : Any`, not `T : NavKey`, so a raw
 * `Set<DeepLinkMatcher<*>>` Hilt multibinding would lose the navigation
 * invariant and force runtime casts at the call site. This class
 * re-narrows the result type to [NavKey] at the boundary.
 *
 * # Specificity ordering
 *
 * Hilt-multibound `Set<T>` iteration order is not contractually stable
 * (Hilt uses `LinkedHashSet` by today's implementation, but the
 * declaration order across modules is component-graph dependent and not
 * a guarantee). The `MainActivity.handleIntent` iterator therefore
 * sorts the set by [patternSpecificity] descending before scanning for
 * the first non-null match, making the chosen winner deterministic
 * regardless of registration order.
 *
 * Specificity is the URI pattern's path-segment count. With our two
 * planned matchers — `/profile/{handle}` (2 segments) and
 * `/profile/{handle}/post/{rkey}` (4 segments) — the post matcher
 * sorts above the profile matcher, and `pathSegments.size` gate in
 * `UriDeepLinkMatcher.matchUri` cleanly short-circuits the wrong
 * matcher before regex evaluation (decision: nubecita-kf6k.4).
 *
 * If two matchers register with the same specificity AND their
 * patterns overlap on a given request, the tie-break falls back to
 * Set iteration order, which is non-deterministic. That's an app
 * design issue rather than a library one — overlapping patterns at
 * the same specificity should be resolved by collapsing them into a
 * single matcher with branching logic in the screen, or by adopting
 * the full `MatchResult.compareTo` ordering (exact-path, path-arg
 * count, total-arg count) at the call site. For our current two
 * patterns this case cannot occur.
 *
 * Construct via [uriDeepLinkMatcher] from feature `:impl` Hilt modules.
 */
class NavKeyDeepLinkMatcher
    internal constructor(
        val patternSpecificity: Int,
        private val matcher: (DeepLinkRequest) -> NavKey?,
    ) {
        fun match(request: DeepLinkRequest): NavKey? = matcher(request)
    }

/**
 * Factory for a [NavKeyDeepLinkMatcher] backed by an alpha03
 * [UriDeepLinkMatcher]. Derives [NavKeyDeepLinkMatcher.patternSpecificity]
 * automatically from the URI pattern's path-segment count, so feature
 * modules don't need to think about ordering — they just declare the
 * pattern they want to match.
 *
 * Typical use from a feature `:impl` Hilt module:
 * ```
 * @Provides @IntoSet
 * fun provideProfileDeepLinkMatcher(): NavKeyDeepLinkMatcher =
 *     uriDeepLinkMatcher(
 *         uriPattern = "https://bsky.app/profile/{handle}",
 *         serializer = serializer<Profile>(),
 *         filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
 *         accept = { profile -> isValidActor(profile.handle) },
 *     )
 * ```
 *
 * Patterns MUST include the scheme — alpha03's
 * `UriDeepLinkMatcher.matchUri` does an exact case-insensitive scheme
 * compare and rejects null pattern schemes against `"https"` requests.
 * See nubecita-kf6k.4 for the source citation.
 *
 * @param uriPattern The pattern to match (e.g. `"https://bsky.app/profile/{handle}"`).
 * @param serializer The `@Serializable` NavKey's KSerializer.
 * @param filters Optional alpha03 filters (mimeType, action). Empty by default.
 * @param accept Optional post-decode predicate run against the decoded
 *   [NavKey] before publishing. Returning `false` rejects the match
 *   (the matcher returns `null` for that request). Used to enforce
 *   feature-side input validation — e.g. rejecting malformed handles
 *   extracted from a `{handle}` placeholder — without leaking the
 *   regex into the URI pattern. Default accepts everything.
 */
fun <T : NavKey> uriDeepLinkMatcher(
    uriPattern: String,
    serializer: KSerializer<T>,
    filters: List<DeepLinkMatcher.Filter> = emptyList(),
    accept: (T) -> Boolean = { true },
): NavKeyDeepLinkMatcher {
    val parsedPattern = uriPattern.toUri()
    val matcher = UriDeepLinkMatcher(parsedPattern, serializer, filters)
    return NavKeyDeepLinkMatcher(
        patternSpecificity = parsedPattern.pathSegments.size,
    ) { request -> matcher.match(request)?.key?.takeIf(accept) }
}

/**
 * Filter that restricts a [UriDeepLinkMatcher] to requests whose
 * intent action matches [expectedAction] (typically
 * `Intent.ACTION_VIEW`).
 *
 * The intent filter declared in `AndroidManifest.xml` already
 * constrains the OS to deliver only `VIEW` actions, but the matcher
 * is fed a [DeepLinkRequest] constructed from arbitrary intents
 * (including ones forwarded in-process or built by tests). This
 * filter is the defence-in-depth re-check: a `Intent.ACTION_SEND`
 * (or any non-VIEW action) at the matcher boundary returns no match
 * and falls through to the unmatched-link `Timber.d` log in
 * `MainActivity.handleIntent`.
 *
 * Listed in the kf6k.5 security checklist as the "Constrain
 * `intent.action`" requirement.
 *
 * Construct with:
 * ```
 * filters = listOf(IntentActionFilter(Intent.ACTION_VIEW))
 * ```
 */
class IntentActionFilter(
    expectedAction: String,
) : DeepLinkMatcher.Filter by DeepLinkMatcher.actionFilter(expectedAction)
