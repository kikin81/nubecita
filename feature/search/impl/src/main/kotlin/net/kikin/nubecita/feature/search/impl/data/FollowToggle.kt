package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.postinteractions.FollowRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** How a [FollowToggle.toggle] ended. The caller logs analytics and tells the screen. */
internal enum class FollowOutcome {
    Followed,
    Unfollowed,
    FollowFailed,
    UnfollowFailed,
}

/**
 * The one follow / unfollow action for every surface that lists people (the People
 * tab, the Explore suggestions). Follow state has a single source, [FollowOverlay]:
 * this writes the optimistic override, calls [FollowRepository], confirms the override
 * with the new follow record URI, and on failure puts back what the overlay held
 * before the tap.
 *
 * One tap per DID is in flight at a time, across every caller: a second tap on the
 * same account (from either surface) is dropped until the first settles.
 *
 * Each caller keeps what is its own: analytics and the error effect, from [FollowOutcome].
 */
@Singleton
internal class FollowToggle
    @Inject
    constructor(
        private val followRepository: FollowRepository,
        private val followOverlay: FollowOverlay,
    ) {
        private val activeJobs = ConcurrentHashMap<String, Job>()

        /**
         * Toggles the follow state of [did], currently shown as [isFollowing] with
         * follow record [followUri]. Returns false, and does nothing, when a tap on
         * [did] is already in flight or when an unfollow is asked of an account whose
         * follow record URI is unknown. Otherwise the optimistic write is already in
         * the overlay when this returns, and [onOutcome] runs when the call settles.
         */
        fun toggle(
            scope: CoroutineScope,
            did: String,
            isFollowing: Boolean,
            followUri: String?,
            onOutcome: (FollowOutcome) -> Unit,
        ): Boolean {
            if (activeJobs[did]?.isActive == true) return false
            // An account known to be followed without its record URI cannot be unfollowed.
            if (isFollowing && followUri == null) return false
            val previous = followOverlay[did]

            if (isFollowing) {
                followOverlay.set(did, FollowOverride(isFollowing = false, followUri = null))
            } else {
                followOverlay.set(did, FollowOverride(isFollowing = true, followUri = null))
            }
            val job =
                scope.launch {
                    if (isFollowing && followUri != null) {
                        followRepository
                            .unfollow(followUri)
                            .onSuccess { onOutcome(FollowOutcome.Unfollowed) }
                            .onFailure {
                                restore(did, previous)
                                onOutcome(FollowOutcome.UnfollowFailed)
                            }
                    } else {
                        followRepository
                            .follow(did)
                            .onSuccess { newFollowUri ->
                                followOverlay.set(did, FollowOverride(isFollowing = true, followUri = newFollowUri))
                                onOutcome(FollowOutcome.Followed)
                            }.onFailure {
                                restore(did, previous)
                                onOutcome(FollowOutcome.FollowFailed)
                            }
                    }
                }
            activeJobs[did] = job
            return true
        }

        /** Puts the overlay back to what it held before the optimistic write, or drops the entry. */
        private fun restore(
            did: String,
            previous: FollowOverride?,
        ) {
            if (previous != null) followOverlay.set(did, previous) else followOverlay.clear(did)
        }
    }
