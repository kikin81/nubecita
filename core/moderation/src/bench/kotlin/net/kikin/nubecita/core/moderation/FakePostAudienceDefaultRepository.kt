package net.kikin.nubecita.core.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.core.posting.PostAudience
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic in-memory [PostAudienceDefaultRepository] for the bench flavor —
 * no network, no account. Starts at [PostAudience.DEFAULT] and mutates the
 * cached value in place so the composer's audience pre-fill works in offline
 * bench / smoke builds.
 */
@Singleton
internal class FakePostAudienceDefaultRepository
    @Inject
    constructor() : PostAudienceDefaultRepository {
        private val _default = MutableStateFlow(PostAudience.DEFAULT)
        override val default: StateFlow<PostAudience> = _default.asStateFlow()

        override suspend fun refresh() = Unit

        override fun resetToDefault() {
            _default.value = PostAudience.DEFAULT
        }

        override suspend fun setDefault(audience: PostAudience) {
            _default.value = audience
        }
    }
