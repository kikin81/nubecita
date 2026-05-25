package net.kikin.nubecita.core.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import timber.log.Timber
import javax.inject.Inject

/**
 * Entry point for inbound FCM payloads. FCM instantiates this service when a
 * `data` message arrives; Hilt's [AndroidEntryPoint] injects the
 * dependencies. Two callbacks matter:
 *
 * - [onNewToken] hands the rotated FCM token to
 *   [PushRegistrationCoordinator.onTokenRotated], which (if signed in)
 *   cancels any in-flight register and posts a fresh one against the new
 *   token.
 * - [onMessageReceived] feeds the `remoteMessage.data` map through
 *   [PushDispatcher] and, on `Show`, builds + publishes a notification via
 *   [PushNotificationBuilder]. After every individual `notify`, an
 *   idempotent per-reason group-summary `notify` updates the shade-collapsed
 *   summary line in place.
 *
 * FCM caps `onMessageReceived` at ~10 seconds before ANR. The dispatcher's
 * filter chain is pure-CPU (no I/O), the mute snapshot is an in-memory
 * [kotlinx.coroutines.flow.StateFlow] read, and the notification build /
 * publish is synchronous Android API — well under budget. The only
 * potentially-blocking call is `onTokenRotated`, which is launched into the
 * application scope so this method returns immediately.
 */
@AndroidEntryPoint
class NubecitaFcmService : FirebaseMessagingService() {
    @Inject lateinit var coordinator: PushRegistrationCoordinator

    @Inject lateinit var dispatcher: PushDispatcher

    @Inject lateinit var notificationBuilder: PushNotificationBuilder

    @Inject lateinit var mutedActorRepository: MutedActorRepository

    @Inject lateinit var sessionStateProvider: SessionStateProvider

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onNewToken(token: String) {
        appScope.launch { coordinator.onTokenRotated(token) }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val activeSessionDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
        val isForeground =
            ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        val outcome =
            dispatcher.dispatch(
                data = remoteMessage.data,
                activeSessionDid = activeSessionDid,
                isAppForeground = isForeground,
                mutedActors = mutedActorRepository.snapshot.value,
            )
        when (outcome) {
            is DispatchOutcome.Drop ->
                Timber.tag(TAG).d("Push dropped: %s", outcome.reason)
            is DispatchOutcome.Show ->
                publish(outcome.payload)
        }
    }

    // Lint can't follow the runtime POST_NOTIFICATIONS check across the
    // notificationsAllowed() guard call below, so it flags the
    // NotificationManagerCompat.notify(...) sites as MissingPermission even
    // though they're gated by a real permission check. Suppress here rather
    // than at every notify site (DRY) and rely on notificationsAllowed()
    // staying the single gate.
    @SuppressLint("MissingPermission")
    private fun publish(payload: PushPayload) {
        if (!notificationsAllowed()) {
            Timber.tag(TAG).d("Notifications not allowed; skipping publish")
            return
        }
        val managerCompat = NotificationManagerCompat.from(this)

        val individual = notificationBuilder.build(payload, this)
        val individualId = PushNotificationBuilder.notifyIdFor(payload)
        managerCompat.notify(individualId, individual)

        // Group summary: count the individual notifications currently
        // grouped under this reason (the just-posted one is already in
        // active notifications by this point) and re-publish the summary in
        // place with the updated count.
        val groupCount = countActiveInGroup(payload.reason)
        val summary = notificationBuilder.buildSummary(payload.reason, groupCount, this)
        val summaryId = PushNotificationBuilder.summaryNotifyIdFor(payload.reason)
        managerCompat.notify(summaryId, summary)
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun countActiveInGroup(reason: PushPayload.Reason): Int {
        val systemManager = getSystemService(NotificationManager::class.java)
        val groupKey = PushNotificationBuilder.groupKeyFor(reason)
        val active =
            systemManager.activeNotifications.count { sbn ->
                val notif = sbn.notification
                notif.group == groupKey && (notif.flags and Notification.FLAG_GROUP_SUMMARY) == 0
            }
        // Lower bound 1 — getActiveNotifications can lag the just-posted
        // notify by a tick on some OEMs, and we never want the summary to
        // claim "0 new likes."
        return active.coerceAtLeast(1)
    }

    private companion object {
        const val TAG = "NubecitaFCM"
    }
}
