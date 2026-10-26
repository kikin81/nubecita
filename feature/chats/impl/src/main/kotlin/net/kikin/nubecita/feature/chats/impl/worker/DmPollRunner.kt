package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.DmPollCursorStore
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import net.kikin.nubecita.core.wearsync.WearDmPublisher
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toDmNotificationContent
import net.kikin.nubecita.feature.chats.impl.data.toDmNotifyPlan
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

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
 * 6. **Watch** (W4) — only while a watch has the app: fetch messages for the
 *    conversations [getLog] saw change (seeding the shown ones once) and publish
 *    the watch's DM snapshot. Isolated: a watch failure never touches the
 *    notifications, the cursor or the outcome.
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
        private val wearDms: WearDmPublisher,
        private val clock: Clock,
    ) {
        enum class Outcome { SUCCESS, RETRY }

        suspend fun run(): Outcome {
            Timber.tag(LOG_TAG).d("run: start")
            // A cold worker process starts with the session StateFlow at Loading
            // (only MainActivity refreshes it on app cold start). Without this, a
            // backgrounded run reads Loading, treats it as signed-out, and skips —
            // mirror WidgetRefreshRunner and refresh here. Rethrow Cancellation; a
            // storage error -> SUCCESS (don't retry-loop on corruption).
            try {
                sessionStateProvider.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(LOG_TAG).w(e, "session refresh failed -> SUCCESS")
                return Outcome.SUCCESS
            }
            val viewerDid =
                (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    ?: run {
                        Timber.tag(LOG_TAG).d("gate: session state is %s (not SignedIn) -> SUCCESS", sessionStateProvider.state.value.javaClass.simpleName)
                        return Outcome.SUCCESS
                    }
            if (!messageChecking.enabled.first()) {
                Timber.tag(LOG_TAG).d("gate: message-checking disabled -> SUCCESS")
                return Outcome.SUCCESS
            }

            // D5: foreground-suppress before any network or cursor mutation.
            if (foreground.isForegrounded()) {
                Timber.tag(LOG_TAG).d("gate: app foregrounded -> SUCCESS (suppressed)")
                return Outcome.SUCCESS
            }

            repository.refreshConvos().onFailure {
                Timber.tag(LOG_TAG).w(it, "refreshConvos failed -> RETRY")
                return Outcome.RETRY
            }
            // A successful refresh always populates the cache (possibly empty),
            // so null here would mean an unexpected invariant break — retry
            // rather than silently advance the cursor past un-notified events.
            val convos =
                repository.observeConvos().value ?: run {
                    Timber.tag(LOG_TAG).w("convos null after refresh -> RETRY")
                    return Outcome.RETRY
                }
            val unreadConvoIds =
                convos
                    .asSequence()
                    .filterNot { it.muted }
                    .filter { it.unreadCount > 0 }
                    .map { it.convoId }
                    .toSet()

            val cursor = cursorStore.cursor(viewerDid).first()
            val pageResult = repository.getLog(cursor)
            val page =
                pageResult.getOrElse {
                    // If getLog fails, retry at the WorkManager level.
                    // Log the exception to help debug persistent failures.
                    Timber.tag(LOG_TAG).w(it, "getLog failed (cursor=%s) -> RETRY", cursor)
                    return Outcome.RETRY
                }

            // First-ever poll: set the baseline so we don't notify the whole
            // pre-existing backlog on install / sign-in.
            if (cursor == null) {
                // We MUST advance the cursor here even if we don't notify, otherwise
                // the next run will find the same events and try to notify them.
                val next = page.nextCursor
                Timber.tag(LOG_TAG).d("first poll: baseline cursor set to %s, no notify -> SUCCESS", next)
                if (next != null) {
                    cursorStore.setCursor(viewerDid, next)
                }
                syncWatch(viewerDid, convos, page)
                return Outcome.SUCCESS
            }

            val plan = page.toDmNotifyPlan(viewerDid = viewerDid, unreadConvoIds = unreadConvoIds)
            Timber.tag(LOG_TAG).d(
                "plan: %d event(s) to notify out of %d total log events (unreadConvos=%d, nextCursor=%s)",
                plan.toNotify.size,
                page.events.size,
                unreadConvoIds.size,
                plan.advancedCursor,
            )
            if (plan.toNotify.isNotEmpty()) {
                val convoById = convos.associateBy { it.convoId }
                notifier.notify(
                    plan.toNotify.map { event ->
                        val convo = convoById[event.convoId]
                        // Direct convos carry the other user's name/handle for the
                        // notification's sender attribution; group convos are titled by
                        // the group name. The tap deep-link is addressed by convo id for
                        // BOTH kinds (see [deepLinkUri] below), so a group opens the group
                        // rather than the message sender's 1:1 DM (nubecita-g1ph).
                        val direct = convo as? ConvoRowUi.Direct
                        val content =
                            event.toDmNotificationContent(
                                senderDisplayName = direct?.displayName ?: (convo as? ConvoRowUi.Group)?.name,
                                senderHandle = direct?.otherUserHandle.orEmpty(),
                            )
                        DmNotification(
                            convoId = event.convoId,
                            // Convo-addressed tap target (nubecita://chat/convo/{convoId}).
                            // A group has no single "other user", so addressing by the
                            // sender's DID (the pre-fix behaviour) resolved to a 1:1 DM
                            // with that member instead of opening the group.
                            deepLinkUri = ChatNotificationIds.deepLinkUri(event.convoId),
                            title = content.title,
                            body = content.body,
                            timestampMillis = event.sentAt.toEpochMilliseconds(),
                        )
                    },
                )
            }
            plan.advancedCursor?.let { cursorStore.setCursor(viewerDid, it) }
            syncWatch(viewerDid, convos, page)
            return Outcome.SUCCESS
        }

        private suspend fun syncWatch(
            viewerDid: String,
            convos: List<ConvoRowUi>,
            page: ChatLogPage,
        ) {
            try {
                if (!wearDms.hasWatch()) return
                val previous = wearDms.lastSnapshot(viewerDid)
                val shown = convos.forWatch()
                val changed = page.events.mapTo(HashSet()) { it.convoId }
                val toFetch = convosToFetch(shown, changed, previous)
                // A conversation whose fetch fails keeps its previous messages.
                val fetched = toFetch.mapNotNull { id -> repository.getMessages(id, limit = WEAR_DM_MESSAGES).getOrNull()?.let { id to it.messages } }.toMap()
                val snapshot = buildWearDmSnapshot(viewerDid, clock.now().toEpochMilliseconds(), shown, fetched, previous)
                if (snapshot != previous?.copy(publishedAtMs = snapshot.publishedAtMs)) wearDms.publish(snapshot)
                Timber.tag(LOG_TAG).d("watch: fetched %d of %d convo(s)", fetched.size, shown.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(LOG_TAG).w(e, "watch DM snapshot failed")
            }
        }

        private companion object {
            const val LOG_TAG = "DmPoll"
        }
    }
