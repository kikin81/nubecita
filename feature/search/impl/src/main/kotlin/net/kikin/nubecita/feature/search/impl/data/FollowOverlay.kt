package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject
import javax.inject.Singleton

/** What the viewer has just done to one account, as the people results should show it. */
internal data class FollowOverride(
    val isFollowing: Boolean,
    val followUri: String?,
)

/**
 * The viewer's follow actions in this session, keyed by DID, laid over every
 * people page — including ones served again from [SearchFirstPages]'s cache,
 * which still carry the follow state they were fetched with. Every reader of
 * people pages (the People tab, and later the summary pane) applies the same
 * overlay, so they agree without a refetch.
 *
 * There was no existing shared follow-state cache to reuse: `FollowRepository`
 * only performs the call, and `PostInteractionsCache` holds post state.
 *
 * Emptied when the signed-in account changes or signs out, with the same
 * synchronous-baseline approach as [SearchFirstPages].
 */
@Singleton
internal class FollowOverlay
    @Inject
    constructor(
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
    ) {
        private val session = sessionStateProvider.state

        private fun currentDid(): String? = (session.value as? SessionState.SignedIn)?.did

        private val _overrides = MutableStateFlow<PersistentMap<String, FollowOverride>>(persistentMapOf())

        /** Overrides by account DID. */
        val overrides: StateFlow<PersistentMap<String, FollowOverride>> = _overrides

        init {
            var knownDid = currentDid()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                session
                    .map { (it as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .collect { did ->
                        if (did != knownDid) {
                            knownDid = did
                            _overrides.value = persistentMapOf()
                        }
                    }
            }
        }

        fun set(
            did: String,
            override: FollowOverride,
        ) {
            _overrides.update { it.put(did, override) }
        }

        /** Drops the override for [did], e.g. to roll back an optimistic write. */
        fun clear(did: String) {
            _overrides.update { it.remove(did) }
        }
    }

/** This actor with the viewer's own follow action applied, when there is one. */
internal fun ActorUi.withFollowOverride(overrides: Map<String, FollowOverride>): ActorUi {
    val override = overrides[did] ?: return this
    return if (isFollowing == override.isFollowing && followUri == override.followUri) {
        this
    } else {
        copy(isFollowing = override.isFollowing, followUri = override.followUri)
    }
}
