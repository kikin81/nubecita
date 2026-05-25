package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.kikin81.atproto.app.bsky.graph.GetMutesRequest
import io.github.kikin81.atproto.app.bsky.graph.GraphService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.auth.XrpcClientProvider
import timber.log.Timber

/**
 * Cached snapshot of the signed-in user's muted DIDs. [PushDispatcher] reads
 * [snapshot] synchronously in its `onMessageReceived` hot path — paginating
 * `app.bsky.graph.getMutes` per-push would (a) add a network roundtrip inside
 * the 10-second ANR limit and (b) fail open under Doze throttling, surfacing
 * pushes for muted accounts whenever the network is slow.
 *
 * See `design.md`'s "Mute filter via cached Set<DID> + 12-hour foreground
 * refresh" decision.
 *
 * **Refresh cadence:** [refresh] paginates `getMutes`, persists the resulting
 * `Set<String>` plus a timestamp to DataStore, and emits to [snapshot]. The
 * 12-hour debounce skips network calls whose previous successful refresh was
 * inside the window unless `force = true` overrides. The foreground-trigger
 * (Phase 2 §5.2 / `AppLifecycleObserver`) calls this on `ON_START`; the
 * upcoming same-device mute-write feature (`nubecita-oftc.5`) will call with
 * `force = true` to invalidate the cache immediately.
 *
 * **Startup:** call [loadFromDisk] once at process start to hydrate
 * [snapshot] with the previously-persisted set before any
 * [PushDispatcher.dispatch] runs. Without this, the very first push received
 * after a cold start would see an empty mute set and surface a muted-actor
 * notification until the first refresh completes.
 *
 * **Failure handling:** a failed refresh preserves the existing snapshot —
 * defaulting to an empty set on transient failures would silently un-mute
 * every actor for the duration of the outage. Failures are returned as
 * `Result.failure` so callers can schedule a retry (the foreground trigger
 * already retries on the next `ON_START`).
 */
class MutedActorRepository(
    private val xrpcClientProvider: XrpcClientProvider,
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _snapshot = MutableStateFlow<Set<String>>(emptySet())
    val snapshot: StateFlow<Set<String>> = _snapshot.asStateFlow()

    suspend fun loadFromDisk() {
        runCatchingExceptCancellation {
            val stored = dataStore.data.first()[KEY_MUTED_DIDS]
            if (stored != null) _snapshot.value = stored.toSet()
        }.onFailure {
            // Disk corruption / IO failure — degrade to empty snapshot.
            // PushDispatcher's mute filter fails-open in that case (we may
            // surface a notification for a muted actor for the brief window
            // until the next foreground refresh succeeds), which is the
            // right trade-off vs. crashing the app at startup. The
            // launched coroutine in AppLifecycleObserver.start() relies on
            // this not throwing.
            Timber.tag(TAG).w(it, "loadFromDisk failed; snapshot stays empty until next refresh")
        }
    }

    suspend fun refresh(force: Boolean = false): Result<Unit> =
        runCatchingExceptCancellation {
            if (!force) {
                val lastRefresh = dataStore.data.first()[KEY_LAST_REFRESH_MS]
                if (lastRefresh != null && clock() - lastRefresh < REFRESH_INTERVAL_MS) {
                    return@runCatchingExceptCancellation
                }
            }
            val client = xrpcClientProvider.authenticated()
            val service = GraphService(client)
            // Manual pagination: atproto-kotlin's `mutesFlow` (which delegates to
            // `paginate`) breaks WITHOUT emitting when the first page's response
            // cursor is unchanged from the request cursor (both null). That
            // strands the common "all mutes fit on one page → server returns
            // no cursor" case, which is exactly what a typical user looks like.
            // Inline the loop here until the upstream `paginate` is fixed.
            val dids =
                buildSet {
                    var cursor: String? = null
                    do {
                        val response = service.getMutes(GetMutesRequest(cursor = cursor))
                        for (mute in response.mutes) add(mute.did.raw)
                        cursor = response.cursor
                    } while (cursor != null)
                }
            // Persist BEFORE updating the in-memory snapshot. The class KDoc
            // promises "a failed refresh preserves the existing snapshot" —
            // if the DataStore write throws (disk full, encryption keystore
            // unavailable), bubbling the failure up before mutating
            // `_snapshot.value` keeps that contract intact regardless of
            // whether the failure originated network-side or persistence-
            // side. The debounce read at the top is now also inside the
            // runCatchingExceptCancellation block so a DataStore read failure
            // returns Result.failure too rather than propagating as an
            // unhandled exception.
            dataStore.edit { prefs ->
                prefs[KEY_LAST_REFRESH_MS] = clock()
                prefs[KEY_MUTED_DIDS] = dids
            }
            _snapshot.value = dids
        }

    companion object {
        private val KEY_LAST_REFRESH_MS = longPreferencesKey("muted_last_refresh_ms")
        private val KEY_MUTED_DIDS = stringSetPreferencesKey("muted_dids")

        // 12 hours. See design.md's "12 hours is conservative" rationale.
        internal const val REFRESH_INTERVAL_MS: Long = 12L * 60 * 60 * 1000

        private const val TAG = "MutedActorRepo"
    }
}
