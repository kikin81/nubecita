package net.kikin.nubecita.pip

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import net.kikin.nubecita.R
import net.kikin.nubecita.core.video.PipBridge
import net.kikin.nubecita.core.video.PipController
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.clampPipAspectRatio
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * Activity-scoped implementation of [PipBridge] — the concrete bridge to the
 * platform Picture-in-Picture APIs (design D5). Owned and lifecycle-managed by
 * `MainActivity`; the Compose layer reaches it through a `CompositionLocal` in a
 * later task and calls [updateParams].
 *
 * Responsibilities:
 * - Build & apply [PictureInPictureParams] (aspect ratio, play/pause action,
 *   source-rect hint), arming **auto-enter on API 31+** and falling back to
 *   manual entry via [onUserLeaveHint] on API 26–30 (design D7).
 * - Mirror the system PiP mode into [PipController.isInPip] via the Activity's
 *   `onPictureInPictureModeChanged` listener. Pausing on a real dismiss is the
 *   job of `SharedVideoPlayer`'s background-pause observer once it becomes
 *   PiP-aware (design D6 / a later task) — not duplicated here.
 * - Service the in-window play/pause [RemoteAction] through a
 *   `RECEIVER_NOT_EXPORTED` broadcast receiver wired to [SharedVideoPlayer].
 *
 * Never offers PiP when [PipController.isEnabled] is false (device unsupported
 * or not Pro): [updateParams] no-ops and auto-enter stays disarmed.
 */
class ActivityPipBridge(
    private val activity: ComponentActivity,
    private val pipController: PipController,
    private val sharedVideoPlayer: SharedVideoPlayer,
) : PipBridge {
    private var registered = false

    private val toggleReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != ACTION_TOGGLE) return
                // Read the live state and flip it. isPlaying updates asynchronously
                // via the ExoPlayer listener, so refresh the action using the target
                // state for an immediate icon swap.
                val nowPlaying = sharedVideoPlayer.isPlaying.value
                if (nowPlaying) sharedVideoPlayer.pause() else sharedVideoPlayer.play()
                updateParams(sharedVideoPlayer.videoAspectRatio.value, isPlaying = !nowPlaying)
            }
        }

    /** Register the PiP-mode listener and the play/pause receiver. Call from `onCreate`. */
    fun start() {
        if (registered) return
        registered = true
        activity.addOnPictureInPictureModeChangedListener { info ->
            pipController.setInPip(info.isInPictureInPictureMode)
        }
        ContextCompat.registerReceiver(
            activity,
            toggleReceiver,
            IntentFilter(ACTION_TOGGLE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Unregister the receiver. Call from `onDestroy`. */
    fun stop() {
        if (!registered) return
        registered = false
        runCatching { activity.unregisterReceiver(toggleReceiver) }
            .onFailure { Timber.tag(TAG).w(it, "PiP receiver already unregistered") }
    }

    override fun updateParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect?,
    ) {
        // Gate the *call* on device support (setPictureInPictureParams throws on a
        // device without PiP). Whether auto-enter is ARMED is decided inside
        // buildParams via `isEnabled && isPlaying` — so always re-publishing here
        // also disarms auto-enter the moment Pro lapses, rather than leaving stale
        // params that could still auto-enter (design risk: entitlement loss).
        if (!pipController.deviceSupportsPip) return
        runCatching { activity.setPictureInPictureParams(buildParams(aspectRatio, isPlaying, sourceRectHint)) }
            .onFailure { Timber.tag(TAG).w(it, "setPictureInPictureParams failed") }
    }

    /**
     * Manual-entry fallback for API 26–30, which lack `setAutoEnterEnabled`.
     * Called from `MainActivity.onUserLeaveHint`; enters PiP only while the perk
     * is enabled and something is playing. A no-op on API 31+, where auto-enter
     * (armed in [buildParams]) handles the transition.
     */
    fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!pipController.isEnabled.value || !sharedVideoPlayer.isPlaying.value) return
        val params = buildParams(sharedVideoPlayer.videoAspectRatio.value, isPlaying = true, sourceRectHint = null)
        runCatching { activity.enterPictureInPictureMode(params) }
            .onFailure { Timber.tag(TAG).w(it, "enterPictureInPictureMode failed") }
    }

    private fun buildParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect?,
    ): PictureInPictureParams {
        val clamped = clampPipAspectRatio(aspectRatio ?: Float.NaN)
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(Rational((clamped * RATIONAL_DENOMINATOR).roundToInt(), RATIONAL_DENOMINATOR))
                .setActions(listOf(toggleAction(isPlaying)))
        if (sourceRectHint != null) builder.setSourceRectHint(sourceRectHint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(pipController.isEnabled.value && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun toggleAction(isPlaying: Boolean): RemoteAction {
        val labelRes = if (isPlaying) R.string.pip_action_pause else R.string.pip_action_play
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val label = activity.getString(labelRes)
        val pendingIntent =
            PendingIntent.getBroadcast(
                activity,
                REQUEST_TOGGLE,
                Intent(ACTION_TOGGLE).setPackage(activity.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return RemoteAction(Icon.createWithResource(activity, iconRes), label, label, pendingIntent)
    }

    private companion object {
        const val TAG = "ActivityPipBridge"
        const val ACTION_TOGGLE = "net.kikin.nubecita.pip.ACTION_TOGGLE"
        const val REQUEST_TOGGLE = 0x9117
        const val RATIONAL_DENOMINATOR = 1000
    }
}
