package net.kikin.nubecita.core.postinteractions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.data.models.ActorUi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** How a [FollowToggle.toggle] ended. The caller logs analytics and tells the screen. */
enum class FollowOutcome {
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
 * The call runs in the application scope, not the caller's: a tap whose screen is left
 * mid-call still completes, so the overlay always settles to the server's answer and
 * never keeps a phantom "following" that cannot be undone. The caller's [FollowOutcome]
 * callback is delivered on the caller's scope, and only while that is still active; callers use
 * it for analytics and their error effect, never for state (state comes from the overlay).
 *
 * Every overlay write for a tap is made on behalf of the account that tapped, captured
 * at tap time. If the session has moved to another account by the time the call returns,
 * the result is dropped (the overlay refuses writes for an account that is no longer signed
 * in): it is invisible to the new account and stale for the old one, and keeping it would
 * only get in the new account's way.
 *
 * One tap per DID is in flight at a time, across every caller: a second tap on the
 * same account (from either surface) is dropped until the first settles.
 */
@Singleton
class FollowToggle
    @Inject
    constructor(
        private val followRepository: FollowRepository,
        private val followOverlay: FollowOverlay,
        private val sessionStateProvider: SessionStateProvider,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) {
        /** In-flight taps, keyed by (account that tapped, target): one account's tap never blocks another's. */
        private val activeJobs = ConcurrentHashMap<Pair<String, String>, Job>()

        /**
         * Toggles the follow state of [did], currently shown as [isFollowing] with
         * follow record [followUri]. [scope] is the caller's, used only to decide whether
         * [onOutcome] is still wanted. Returns false, and does nothing, when nobody is
         * signed in, when a tap on [did] is already in flight, or when an unfollow is asked
         * of an account whose follow record URI is unknown. Otherwise the optimistic write
         * is already in the overlay when this returns, and [onOutcome] runs when the call
         * settles, if [scope] is still active then.
         */
        fun toggle(
            scope: CoroutineScope,
            did: String,
            isFollowing: Boolean,
            followUri: String?,
            onOutcome: (FollowOutcome) -> Unit,
        ): Boolean {
            val account = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: return false
            if (activeJobs[account to did]?.isActive == true) return false
            // An account known to be followed without its record URI cannot be unfollowed.
            if (isFollowing && followUri == null) return false
            val previous = followOverlay[did]

            followOverlay.write(did, FollowOverride(isFollowing = !isFollowing, followUri = null), account)
            val job =
                appScope.launch {
                    fun settled(outcome: FollowOutcome) {
                        // Delivered on the caller's own dispatcher; a cancelled scope never runs it.
                        scope.launch { onOutcome(outcome) }
                    }
                    if (isFollowing && followUri != null) {
                        followRepository
                            .unfollow(followUri)
                            .onSuccess { settled(FollowOutcome.Unfollowed) }
                            .onFailure {
                                followOverlay.write(did, previous, account)
                                settled(FollowOutcome.UnfollowFailed)
                            }
                    } else {
                        followRepository
                            .follow(did)
                            .onSuccess { newFollowUri ->
                                followOverlay.write(did, FollowOverride(isFollowing = true, followUri = newFollowUri), account)
                                settled(FollowOutcome.Followed)
                            }.onFailure {
                                followOverlay.write(did, previous, account)
                                settled(FollowOutcome.FollowFailed)
                            }
                    }
                }
            activeJobs[account to did] = job
            return true
        }

        /**
         * [toggle] for a row on screen: the one place that decides what [actor]'s row becomes.
         * [updateActor] rewrites that row in the caller's list (a targeted rewrite of the
         * CURRENT state, never a snapshot restore): once now for the optimistic flip, then for
         * the outcome per [afterFollowOutcome]. [onOutcome] runs after the row was rewritten, for
         * what is the caller's own (analytics, an error message). Returns what [toggle] returns.
         */
        fun toggleActor(
            scope: CoroutineScope,
            actor: ActorUi,
            updateActor: ((ActorUi) -> ActorUi) -> Unit,
            onOutcome: (FollowOutcome) -> Unit,
        ): Boolean {
            val did = actor.did
            val wasFollowing = actor.isFollowing
            val previousFollowUri = actor.followUri
            val started =
                toggle(scope, did, wasFollowing, previousFollowUri) { outcome ->
                    updateActor { it.afterFollowOutcome(outcome, previousFollowUri, followOverlay[did]?.followUri) }
                    onOutcome(outcome)
                }
            // The toggle has already written the optimistic override; show it now rather than
            // when the overlay's collector next runs.
            if (started) {
                val overrides = followOverlay.current()
                updateActor { it.withFollowOverride(overrides) }
            }
            return started
        }
    }

/**
 * What a row becomes when its follow call settles with [outcome]: followed (with the record
 * [newFollowUri]), unfollowed (unchanged: the overlay already cleared it), or rolled back to the
 * state before the tap ([previousFollowUri] is the record URI the row carried).
 */
fun ActorUi.afterFollowOutcome(
    outcome: FollowOutcome,
    previousFollowUri: String?,
    newFollowUri: String?,
): ActorUi =
    when (outcome) {
        FollowOutcome.Followed -> copy(isFollowing = true, followUri = newFollowUri)
        FollowOutcome.Unfollowed -> this
        FollowOutcome.FollowFailed -> copy(isFollowing = false, followUri = null)
        FollowOutcome.UnfollowFailed -> copy(isFollowing = true, followUri = previousFollowUri)
    }
