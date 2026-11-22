package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed

/**
 * Local, per-account cache of full profiles (`app.bsky.actor.getProfile`
 * responses), so a profile the viewer has opened before renders instantly
 * while the network refresh runs.
 *
 * A speed layer, never the source of truth: callers render a cached profile
 * and then always refresh it, writing the fresh response back with [write].
 * Everything is scoped to the signed-in account; with no session every call
 * is a no-op (reads return null).
 */
interface ProfileCache {
    /**
     * The cached profile for [actor] (a DID or a handle), or null if none is
     * cached. A hit counts as an open for the cache's recency cap.
     */
    suspend fun read(actor: String): ProfileViewDetailed?

    /** Stores [profile], then trims the account's cache to its cap (the account's own profile is always kept). */
    suspend fun write(profile: ProfileViewDetailed)

    /** Drops the cached profile for [actor] (a DID or a handle) — e.g. once the account is gone. */
    suspend fun remove(actor: String)
}
