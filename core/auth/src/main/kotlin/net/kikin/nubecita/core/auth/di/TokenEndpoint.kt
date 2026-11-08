package net.kikin.nubecita.core.auth.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the engine and `HttpClient` that carry the two
 * token-endpoint POSTs which spend something single-use: the token refresh
 * and the authorization-code exchange (`AtOAuth.tokenHttpClient`).
 *
 * Their engine never re-sends a request whose connection dropped. A re-sent
 * refresh replays a token the server may already have consumed, and the
 * server answers a replay by deleting the whole session (nubecita-3zst).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TokenEndpoint
