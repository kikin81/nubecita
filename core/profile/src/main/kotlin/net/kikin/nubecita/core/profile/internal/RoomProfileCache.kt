package net.kikin.nubecita.core.profile.internal

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.database.dao.CachedProfileDao
import net.kikin.nubecita.core.database.model.CachedProfileEntity
import net.kikin.nubecita.core.profile.ProfileCache
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * [ProfileCache] over the `cached_profile` table, storing each profile as its
 * wire `profileViewDetailed` JSON so new lexicon fields need no migration.
 */
internal class RoomProfileCache
    @Inject
    constructor(
        private val dao: CachedProfileDao,
        private val sessionStateProvider: SessionStateProvider,
        private val clock: Clock,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileCache {
        override suspend fun read(actor: String): ProfileViewDetailed? =
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext null
                val row = dao.byActor(account, actor) ?: return@withContext null
                val profile =
                    try {
                        ProfileCacheJson.decodeFromString(ProfileViewDetailed.serializer(), row.profileBlob)
                    } catch (error: SerializationException) {
                        // A blob this build can't read (written by a different
                        // schema, or corrupt) is useless — drop it so the next
                        // refresh rewrites it instead of failing here forever.
                        Timber.tag(TAG).w(error, "Dropping unreadable cached profile")
                        dao.deleteByActor(account, row.did)
                        return@withContext null
                    }
                dao.touch(account, row.did, clock.now())
                profile
            }

        override suspend fun write(profile: ProfileViewDetailed) {
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext
                val now = clock.now()
                dao.upsert(
                    CachedProfileEntity(
                        accountDid = account,
                        did = profile.did.raw,
                        handle = profile.handle.raw,
                        profileBlob = ProfileCacheJson.encodeToString(ProfileViewDetailed.serializer(), profile),
                        fetchedAt = now,
                        lastOpenedAt = now,
                    ),
                )
                dao.trimToCap(account, cap = MAX_CACHED_PROFILES, keepDid = account)
            }
        }

        override suspend fun remove(actor: String) {
            withContext(dispatcher) {
                val account = accountDid() ?: return@withContext
                dao.deleteByActor(account, actor)
            }
        }

        private fun accountDid(): String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        private companion object {
            const val TAG = "ProfileCache"
            const val MAX_CACHED_PROFILES = 200
        }
    }

/**
 * Mirrors `:core:feed-cache`'s `CacheJson` (and `XrpcClient.DefaultJson`):
 * encode drops absent optionals, decode tolerates fields this build doesn't
 * know. Keep the two configurations identical.
 */
private val ProfileCacheJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
