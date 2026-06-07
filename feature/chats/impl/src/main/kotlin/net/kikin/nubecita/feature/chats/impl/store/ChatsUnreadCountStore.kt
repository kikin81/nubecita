package net.kikin.nubecita.feature.chats.impl.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-singleton store for the aggregate unread-DM count, exposed as a
 * [StateFlow] consumed by `MainShell`'s Chats bottom-nav badge. Mirrors
 * `NotificationsUnreadCountStore`: populated by
 * [ChatsUnreadPollingObserver]'s `ProcessLifecycleOwner`-scoped polling
 * loop (foreground-only, 60s cadence) and zeroed on logout.
 *
 * Aggregation: [refresh] drives [ChatRepository.refreshConvos] (which also
 * keeps the inbox list fresh — one `listConvos` round-trip serves both the
 * badge and the Chats screen) and then sums the per-convo `unreadCount`
 * across **non-muted** convos from the repository's cache. A muted thread
 * still shows its in-row count but must not light up the bottom nav.
 *
 * Approximation note: `refreshConvos` fetches one `listConvos` page, so the
 * aggregate covers the most recent page of convos. The badge caps at 99+
 * anyway, so under-counting past a full page is invisible in practice.
 *
 * The class is `public` (the `:app` `MainShell` consumer holds it at the
 * Hilt `@EntryPoint` boundary) but its `@Inject` constructor is `internal`
 * so only Hilt constructs the singleton — mirrors
 * `NotificationsUnreadCountStore`.
 *
 * This is foreground-only (no background work) — see the v1 scope on
 * nubecita-1fy.14; background notifications are nubecita-1fy.15.
 */
@Singleton
class ChatsUnreadCountStore
    @Inject
    internal constructor(
        private val repository: ChatRepository,
    ) {
        private val _unreadCount = MutableStateFlow(0)

        /** Current aggregate unread-DM count; `0` initially and on [clear]. */
        val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

        /**
         * Serializes overlapping [refresh] calls — a tick firing while a
         * previous refresh is still in flight collapses to one round-trip via
         * [Mutex.tryLock]. Purely defensive at the 60s cadence.
         */
        private val refreshMutex = Mutex()

        /**
         * Refresh the convo list and recompute the aggregate unread count.
         * Returns the new total on success (so the polling observer can drive
         * its backoff), or the [ChatRepository.refreshConvos] failure
         * unchanged. On failure the count is left as-is.
         *
         * Single-flight: a call made while a previous [refresh] is in flight
         * short-circuits with a snapshot of the current value; the skipped
         * tick is not retried (the next 60s tick picks it up).
         */
        suspend fun refresh(): Result<Int> {
            if (!refreshMutex.tryLock()) return Result.success(_unreadCount.value)
            return try {
                repository.refreshConvos().map {
                    val total =
                        repository
                            .observeConvos()
                            .value
                            .orEmpty()
                            .filterNot { it.muted }
                            .sumOf { it.unreadCount }
                    _unreadCount.value = total
                    total
                }
            } finally {
                refreshMutex.unlock()
            }
        }

        /** Reset to `0`. Called by [ChatsUnreadPollingObserver] on sign-out. Idempotent. */
        fun clear() {
            _unreadCount.value = 0
        }
    }
