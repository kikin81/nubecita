package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.kikin.nubecita.core.common.session.SessionClearable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The follow changes the viewer has committed this session: DID to follow URI,
 * with null meaning they unfollowed.
 *
 * A followers list is cached by Paging, so following someone on their profile
 * and coming back would otherwise show a stale Follow button, and tapping it
 * would create a second follow record that a later unfollow leaves behind.
 * Shared state rather than a signal, so a screen that opens later still sees it.
 */
@Singleton
internal class ConnectionFollowStore
    @Inject
    constructor() : SessionClearable {
        private val _committed = MutableStateFlow<PersistentMap<String, String?>>(persistentMapOf())
        val committed: StateFlow<PersistentMap<String, String?>> = _committed.asStateFlow()

        fun record(
            did: String,
            followUri: String?,
        ) {
            _committed.update { it.put(did, followUri) }
        }

        override suspend fun clearSession() {
            _committed.value = persistentMapOf()
        }
    }
