package net.kikin.nubecita.core.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * Source of the `notifications_enabled` user-property value: whether push
 * notifications are effectively on right now. Behind an interface so the
 * analytics coordinator can be unit-tested with a fake flow.
 */
interface NotificationsEnabledSource {
    /**
     * `true` only when BOTH the system POST_NOTIFICATIONS permission is granted
     * (and notifications aren't disabled at the channel/app level) AND push
     * registration has succeeded. Re-evaluated on every app foreground, since
     * the OS permission can be toggled in Settings outside the app.
     */
    val notificationsEnabled: Flow<Boolean>
}

internal class NotificationsEnabledProvider
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val registrationStore: PushRegistrationStateStore,
    ) : NotificationsEnabledSource {
        override val notificationsEnabled: Flow<Boolean> =
            combine(permissionOnForeground(), registrationStore.state) { permitted, registration ->
                permitted && registration.status == PushRegistrationState.Status.Succeeded
            }.distinctUntilChanged()

        // Re-emit the current permission on every app foreground (ON_START) plus
        // once on subscription, because the user can flip POST_NOTIFICATIONS in
        // system Settings while the app is backgrounded. The observer is added
        // on the main thread (ProcessLifecycleOwner's LifecycleRegistry requires it).
        private fun permissionOnForeground(): Flow<Boolean> =
            callbackFlow {
                val owner = ProcessLifecycleOwner.get()
                val observer =
                    LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) trySend(permissionGranted(context))
                    }
                trySend(permissionGranted(context))
                owner.lifecycle.addObserver(observer)
                awaitClose { owner.lifecycle.removeObserver(observer) }
            }.flowOn(Dispatchers.Main.immediate)
                .distinctUntilChanged()
    }

/**
 * Whether notifications can actually be delivered: the runtime
 * POST_NOTIFICATIONS grant (API 33+) AND notifications not disabled at the
 * app/channel level. Mirrors `NubecitaFcmService.notificationsAllowed`.
 */
internal fun permissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}
