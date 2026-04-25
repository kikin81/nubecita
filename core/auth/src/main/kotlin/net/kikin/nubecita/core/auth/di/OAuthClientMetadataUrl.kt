package net.kikin.nubecita.core.auth.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the OAuth client metadata URL — the publicly hosted
 * JSON document that Bluesky's authorization server fetches during PAR.
 * The URL is build-variant-specific (dev points at GitHub Pages, prod
 * will swap to a custom domain) so `:app` provides this binding from
 * `BuildConfig`, and `:core:auth`'s `AtOAuthModule` injects it.
 *
 * Using a qualifier instead of an inline value-class wrapper because
 * Hilt's KSP processor doesn't handle inline class parameters in
 * `@Provides` functions (mangled JVM names like `provideAtOAuth-VimTCkc`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OAuthClientMetadataUrl
