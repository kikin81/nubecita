package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
 * people list — including ones served again from [SearchFirstPages]'s cache
 * and the shared Explore suggestions, which still carry the follow state they
 * were fetched with. Every reader of people (the People tab, the Explore
 * suggestions) applies the same overlay, so they agree without a refetch.
 *
 * There was no existing shared follow-state cache to reuse: `FollowRepository`
 * only performs the call, and `PostInteractionsCache` holds post state.
 *
 * Each override remembers the account that wrote it. Readers go through
 * [current] / [overrides], which skip the entries of any other account
 * synchronously, so one account's follow actions never show for the next even
 * in the window before the (asynchronous) clear on an account change has run.
 */
@Singleton
internal class FollowOverlay
    @Inject
    constructor(
        @ApplicationScope scope: CoroutineScope,
        sessionStateProvider: SessionStateProvider,
    ) {
        private class Entry(
            val override: FollowOverride,
            val account: String?,
        )

        private val session = sessionStateProvider.state

        private fun currentDid(): String? = (session.value as? SessionState.SignedIn)?.did

        private val entries = MutableStateFlow<PersistentMap<String, Entry>>(persistentMapOf())

        private fun Map<String, Entry>.visibleTo(did: String?): Map<String, FollowOverride> = filterValues { it.account == did }.mapValues { it.value.override }

        /** The overrides of the signed-in account, read now. */
        fun current(): Map<String, FollowOverride> = entries.value.visibleTo(currentDid())

        /** The override for [did] written by the signed-in account, read now. */
        operator fun get(did: String): FollowOverride? = entries.value[did]?.takeIf { it.account == currentDid() }?.override

        /** The overrides of the signed-in account, re-emitted on every write and every account change. */
        val overrides: Flow<Map<String, FollowOverride>> =
            combine(entries, session.map { (it as? SessionState.SignedIn)?.did }.distinctUntilChanged()) { map, did ->
                map.visibleTo(did)
            }

        init {
            var knownDid = currentDid()
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                session
                    .map { (it as? SessionState.SignedIn)?.did }
                    .distinctUntilChanged()
                    .collect { did ->
                        if (did != knownDid) {
                            knownDid = did
                            entries.value = persistentMapOf()
                        }
                    }
            }
        }

        fun set(
            did: String,
            override: FollowOverride,
        ) {
            val account = currentDid()
            entries.update { it.putting(did, Entry(override, account)) }
        }

        /** Drops the override for [did], e.g. to roll back an optimistic write. */
        fun clear(did: String) {
            entries.update { it.removing(did) }
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

/** This suggested account with the viewer's own follow action applied, when there is one. */
internal fun SuggestedAccountUi.withFollowOverride(overrides: Map<String, FollowOverride>): SuggestedAccountUi {
    val override = overrides[did] ?: return this
    return if (isFollowing == override.isFollowing && followUri == override.followUri) {
        this
    } else {
        copy(isFollowing = override.isFollowing, followUri = override.followUri)
    }
}
