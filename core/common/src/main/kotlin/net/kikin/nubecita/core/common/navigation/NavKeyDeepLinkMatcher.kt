package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher

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
 * invariant and force runtime casts at the call site. This `fun
 * interface` re-narrows the result type to [NavKey] at the boundary.
 *
 * Returns `null` when the matcher does not match the request. The
 * first non-null result in iteration order wins — see [asNavKeyMatcher]
 * below for the contract this relies on, and the alpha03-matcher
 * decision (nubecita-kf6k.4) for the rationale on declaration-order
 * resolution.
 */
fun interface NavKeyDeepLinkMatcher {
    fun match(request: DeepLinkRequest): NavKey?
}

/**
 * Wrap an alpha03 [UriDeepLinkMatcher] whose key type is a [NavKey] as
 * a [NavKeyDeepLinkMatcher] suitable for the Hilt multibinding.
 *
 * Typical use from a feature `:impl` Hilt module:
 * ```
 * @Provides @IntoSet
 * fun provideProfileDeepLinkMatcher(): NavKeyDeepLinkMatcher =
 *     UriDeepLinkMatcher(
 *         uriPattern = "https://bsky.app/profile/{handle}".toUri(),
 *         serializer = serializer<Profile>(),
 *     ).asNavKeyMatcher()
 * ```
 */
fun <T : NavKey> UriDeepLinkMatcher<T>.asNavKeyMatcher(): NavKeyDeepLinkMatcher = NavKeyDeepLinkMatcher { request -> match(request)?.key }
