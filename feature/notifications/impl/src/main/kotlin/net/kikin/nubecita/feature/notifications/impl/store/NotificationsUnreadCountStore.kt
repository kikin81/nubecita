package net.kikin.nubecita.feature.notifications.impl.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-singleton store for Bluesky's `app.bsky.notification.getUnreadCount`
 * response, exposed as a [StateFlow] consumed by `MainShell`'s bottom-nav
 * badge. The store is populated by [NotificationsPollingObserver]'s
 * `ProcessLifecycleOwner`-scoped polling loop (60-second cadence while the
 * app is foregrounded) and zeroed on logout.
 *
 * The class itself is `public` (not module-internal) so the `MainShell`
 * consumer in `:app` can hold the `StateFlow<Int>` type at the
 * Hilt `@EntryPoint` boundary that gets added in bd issue nubecita-1fy.1.9.
 * The `@Inject` constructor is `internal`, so construction stays
 * module-local — only Hilt itself can instantiate the singleton, and no
 * other feature module reaches in to build one directly. Mirrors the
 * pattern `PushModule` uses for its exposed-but-internally-constructed
 * collaborators.
 *
 * Mark-read optimistic-zeroing (per design D6 in
 * `openspec/changes/add-feature-notifications/design.md`) is the
 * `NotificationsViewModel`'s `TabExited` reducer concern, NOT this class's.
 * The next 60-second poll corrects the count if the server-side `updateSeen`
 * call failed.
 */
@Singleton
class NotificationsUnreadCountStore
    @Inject
    internal constructor(
        private val repository: NotificationsRepository,
    ) {
        private val _unreadCount = MutableStateFlow(0)

        /**
         * Current unread count. Initial value is `0`; transitions occur after
         * [refresh] returns a success and on [clear].
         */
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        /**
         * Serializes overlapping [refresh] calls — an aggressive poll firing
         * while a previous request is still in flight collapses to one
         * network round-trip via [Mutex.tryLock]. The polling cadence is 60
         * seconds and `getUnreadCount` typically completes in <500ms, so the
         * single-flight is purely defensive (slow networks, retry backoff
         * resets racing with the next tick).
         */
        private val refreshMutex = Mutex()

        /**
         * Pull the latest count from the server. Returns the [Result] from
         * [NotificationsRepository.unreadCount] so the calling lifecycle
         * observer can drive its exponential backoff on failures.
         *
         * Single-flight: if a previous [refresh] is still in flight,
         * subsequent calls short-circuit immediately with a successful
         * snapshot of the current cached value. The skipped tick is NOT
         * retried — the next polling tick (60s later under nominal
         * conditions) will pick up wherever the in-flight request leaves
         * off.
         */
        suspend fun refresh(): Result<Int> {
            if (!refreshMutex.tryLock()) return Result.success(_unreadCount.value)
            return try {
                val result = repository.unreadCount()
                result.onSuccess { _unreadCount.value = it }
                result
            } finally {
                refreshMutex.unlock()
            }
        }

        /**
         * Reset the count to `0`. Called by [NotificationsPollingObserver]
         * when it observes a [net.kikin.nubecita.core.auth.SessionState.SignedOut]
         * transition. Idempotent — clearing zero over zero is a no-op
         * observable as a no-emit on the [StateFlow] (distinct-until-changed
         * is `MutableStateFlow`'s default behavior).
         */
        fun clear() {
            _unreadCount.value = 0
        }
    }
