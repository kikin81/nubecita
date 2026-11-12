package net.kikin.nubecita.wear.launch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.navigation.WearConvos
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.navigation.WearLanding

/** Where a launch from outside the app (complication, launcher widget) opens it. */
enum class WearLaunchTarget { Landing, Feed, Messages, NewPost }

/**
 * The target for [action]. A [restoring] activity (rotation, process-death
 * restore) brings back its own back stack, so the link is never replayed.
 */
fun launchTargetOf(
    action: String?,
    restoring: Boolean,
): WearLaunchTarget =
    when {
        restoring -> WearLaunchTarget.Landing
        action == MainActivity.ACTION_OPEN_FEED -> WearLaunchTarget.Feed
        action == MainActivity.ACTION_OPEN_MESSAGES -> WearLaunchTarget.Messages
        action == MainActivity.ACTION_NEW_POST -> WearLaunchTarget.NewPost
        else -> WearLaunchTarget.Landing
    }

/** The start back stack: the landing screen always underneath, so back returns there. */
fun initialBackStack(target: WearLaunchTarget): List<NavKey> =
    when (target) {
        WearLaunchTarget.Landing, WearLaunchTarget.NewPost -> listOf(WearLanding)
        WearLaunchTarget.Feed -> listOf(WearLanding, WearHome)
        WearLaunchTarget.Messages -> listOf(WearLanding, WearConvos)
    }

/** What to do with a New post launch once the landing screen is up. */
enum class PendingNewPost { Open, Drop, Wait }

/**
 * Waits until the phone has answered whether it posts from the watch, then
 * opens the input if it does or drops the request — so availability that
 * arrives later never pops the input open on its own.
 */
fun resolvePendingNewPost(
    postingChecked: Boolean,
    postingAvailable: Boolean,
): PendingNewPost =
    when {
        !postingChecked -> PendingNewPost.Wait
        postingAvailable -> PendingNewPost.Open
        else -> PendingNewPost.Drop
    }

/**
 * Opens the app at [target] in a fresh task: a tap means "take me there",
 * not "resume where I was". One request code per target, so the
 * PendingIntents don't collapse into one.
 */
fun launchIntent(
    context: Context,
    target: WearLaunchTarget,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        target.ordinal,
        Intent(context, MainActivity::class.java)
            .setAction(target.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private val WearLaunchTarget.action: String
    get() =
        when (this) {
            WearLaunchTarget.Landing -> Intent.ACTION_MAIN
            WearLaunchTarget.Feed -> MainActivity.ACTION_OPEN_FEED
            WearLaunchTarget.Messages -> MainActivity.ACTION_OPEN_MESSAGES
            WearLaunchTarget.NewPost -> MainActivity.ACTION_NEW_POST
        }
