package net.kikin.nubecita.core.video.playback

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Reports whether the system **Data Saver** is currently restricting this app's
 * data use. The vertical feed reads this to skip prefetching the *next* clip
 * (the active clip still plays) — honoring the user's data/battery preference
 * (epic nubecita-zdv8 Slice 5c; spec video-playback-engine "Battery discipline").
 *
 * `RESTRICT_BACKGROUND_STATUS_ENABLED` is the "Data Saver on and this app is not
 * allow-listed" signal; `WHITELISTED`/`DISABLED` both mean unrestricted. Checked
 * on demand (at feed open) — Data Saver rarely toggles mid-session, so a live
 * observer isn't worth the wiring. Requires `ACCESS_NETWORK_STATE`.
 */
public class DataSaverStatus
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        public fun isActive(): Boolean {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
            return connectivity.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }
    }
