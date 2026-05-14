package net.kikin.nubecita.core.auth.di

import javax.inject.Qualifier

/**
 * Hilt qualifier for the OAuth scope string sent in the PAR request body.
 *
 * The scope must include `atproto` (required by Bluesky) plus any
 * `transition:*` umbrella scopes the app needs (`transition:generic` for
 * the standard AppView, `transition:chat.bsky` for chat AppView RPCs).
 * The value is build-variant-specific because different flavors may
 * ship with different capability sets, so `:app` provides this binding
 * from `BuildConfig` and `:core:auth`'s `AtOAuthModule` injects it into
 * the `AtOAuth` constructor.
 *
 * Pairs with [OAuthClientMetadataUrl]: the requested scope must be a
 * subset of the `scope` advertised by the hosted client-metadata.json,
 * so the two values move in lockstep across build variants.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OAuthScope
