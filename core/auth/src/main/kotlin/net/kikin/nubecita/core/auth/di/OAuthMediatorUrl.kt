package net.kikin.nubecita.core.auth.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the base URL of the OAuth token-mediating backend — the
 * Nubecita-operated service that adds client authentication to the pushed
 * authorization request, code exchange, refresh and revocation, so sessions
 * qualify for a confidential client's lifetime.
 *
 * An empty string means "no backend": the app talks to the authorization
 * server directly, as a public client. `:app` provides the value from
 * `BuildConfig`, like the other OAuth settings.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OAuthMediatorUrl
