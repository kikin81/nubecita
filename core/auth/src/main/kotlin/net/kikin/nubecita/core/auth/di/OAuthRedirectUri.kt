package net.kikin.nubecita.core.auth.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the OAuth redirect URI — the custom-scheme URI the
 * authorization server redirects back to after the user approves the
 * grant. Per AT Protocol's Discoverable Client rule the scheme is the
 * reversed FQDN of `client_id` (nubecita.app → `app.nubecita:/oauth-redirect`),
 * NOT the app's `applicationId`. Must match the `redirect_uris` entry in
 * the hosted `client-metadata.json` verbatim.
 *
 * `:app` provides this binding from `BuildConfig` so dev / prod / future
 * flavors can supply different values without touching `:core:auth`.
 *
 * Using a qualifier instead of an inline value-class wrapper because
 * Hilt's KSP processor doesn't handle inline class parameters in
 * `@Provides` functions (mangled JVM names like `provideAtOAuth-VimTCkc`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OAuthRedirectUri
