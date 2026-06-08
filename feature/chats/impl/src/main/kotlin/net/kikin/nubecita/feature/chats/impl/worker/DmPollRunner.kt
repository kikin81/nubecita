package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.DmPollCursorStore
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toDmNotificationContent
import net.kikin.nubecita.feature.chats.impl.data.toDmNotifyPlan
import javax.inject.Inject

/**
 * The background DM-poll orchestration (design D3/D5), extracted from
 * [DmPollWorker] as a plain injectable so it's JVM-unit-testable without
 * WorkManager — the worker's `doWork()` is a one-line delegate (design D10).
 *
 * Per run, in order:
 * 1. **Gates** — no-op (success) when signed out or message-checking is off, so
 *    a non-chatter pays nothing.
 * 2. **Foreground suppression (D5)** — if the app is foregrounded, return
 *    immediately WITHOUT polling or advancing the cursor; the in-app surface
 *    already shows unread, and holding the cursor lets the next background run
 *    re-evaluate these events.
 * 3. **Unread set** — refresh the convo list (one `listConvos`) for the
 *    read-state filter; a failed refresh retries.
 * 4. **Detect** — `getLog` from the stored cursor, then [toDmNotifyPlan]
 *    (inbound + still-unread + capped). A `null` cursor is the first-ever poll:
 *    establish the baseline cursor WITHOUT notifying the pre-existing backlog.
 * 5. **Notify + advance** — post per-convo notifications, then persist the
 *    advanced cursor.
 *
 * Auth (§4.2): all network goes through [ChatRepository], whose
 * `XrpcClientProvider.authenticated()` refreshes via `:core:auth`'s single
 * source of truth (refresh mutex), so a background refresh can't race the
 * foreground app into a logout.
 */
internal class DmPollRunner
    @Inject
    constructor(
        private val repository: ChatRepository,
        private val cursorStore: DmPollCursorStore,
        private val messageChecking: MessageCheckingPreference,
        private val sessionStateProvider: SessionStateProvider,
        private val foreground: AppForegroundSignal,
        private val notifier: DmNotifier,
    ) {
        enum class Outcome { SUCCESS, RETRY }

        suspend fun run(): Outcome {
            val viewerDid =
                (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    ?: return Outcome.SUCCESS
            if (!messageChecking.enabled.first()) return Outcome.SUCCESS

            // D5: foreground-suppress before any network or cursor mutation.
            if (foreground.isForegrounded()) return Outcome.SUCCESS

            repository.refreshConvos().getOrElse { return Outcome.RETRY }
            // A successful refresh always populates the cache (possibly empty),
            // so null here would mean an unexpected invariant break — retry
            // rather than silently advance the cursor past un-notified events.
            val convos = repository.observeConvos().value ?: return Outcome.RETRY
            val unreadConvoIds =
                convos
                    .asSequence()
                    .filterNot { it.muted }
                    .filter { it.unreadCount > 0 }
                    .map { it.convoId }
                    .toSet()

            val cursor = cursorStore.cursor(viewerDid).first()
            val page = repository.getLog(cursor).getOrElse { return Outcome.RETRY }

            // First-ever poll: set the baseline so we don't notify the whole
            // pre-existing backlog on install / sign-in.
            if (cursor == null) {
                page.nextCursor?.let { cursorStore.setCursor(viewerDid, it) }
                return Outcome.SUCCESS
            }

            val plan = page.toDmNotifyPlan(viewerDid = viewerDid, unreadConvoIds = unreadConvoIds)
            if (plan.toNotify.isNotEmpty()) {
                val convoById = convos.associateBy { it.convoId }
                notifier.notify(
                    plan.toNotify.map { event ->
                        val convo = convoById[event.convoId]
                        val content =
                            event.toDmNotificationContent(
                                senderDisplayName = convo?.displayName,
                                senderHandle = convo?.otherUserHandle.orEmpty(),
                            )
                        DmNotification(
                            convoId = event.convoId,
                            // The convo is always present (its id came from the same
                            // cache the unread set is derived from); fall back to the
                            // sender DID — for an inbound 1:1 DM that IS the other
                            // user — so the tap deep-link target is never empty.
                            otherUserDid = convo?.otherUserDid?.takeIf { it.isNotEmpty() } ?: event.senderDid,
                            title = content.title,
                            body = content.body,
                            timestampMillis = event.sentAt.toEpochMilliseconds(),
                        )
                    },
                )
            }
            plan.advancedCursor?.let { cursorStore.setCursor(viewerDid, it) }
            return Outcome.SUCCESS
        }
    }
