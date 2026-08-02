package net.kikin.nubecita.core.actors

import javax.inject.Qualifier

/**
 * Qualifies the unauthenticated [io.github.kikin81.atproto.runtime.XrpcClient]
 * pointed at the public AppView, for lexicons that serve anonymous callers.
 *
 * Qualified rather than a bare `XrpcClient` binding: `XrpcClient` is a general
 * type, and an unqualified singleton of it in `SingletonComponent` is silently
 * injectable anywhere — which is how you end up making an unauthenticated call
 * from an authenticated surface and never noticing. Asking for
 * `@AnonymousXrpcClient` makes the choice explicit at the injection site.
 *
 * Authenticated work continues to go through
 * `XrpcClientProvider.authenticated()`, which resolves the signed-in user's own
 * PDS. The two are not interchangeable: the anonymous client can only serve
 * public reads.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnonymousXrpcClient
