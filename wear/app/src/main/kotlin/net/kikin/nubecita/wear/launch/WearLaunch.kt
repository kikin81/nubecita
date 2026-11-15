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

/** The intent extra that names the [WearLaunchTarget] to open. Absent means the landing screen. */
const val EXTRA_LAUNCH_TARGET = "net.kikin.nubecita.wear.extra.LAUNCH_TARGET"

/**
 * The target named by the [target] extra. [legacyAction] is the intent action:
 * earlier versions named the target there, and a complication can hold one of
 * those PendingIntents until its next update. A [restoring] activity
 * (rotation, process-death restore) brings back its own back stack, so the
 * link is never replayed.
 */
fun launchTargetOf(
    target: String?,
    legacyAction: String?,
    restoring: Boolean,
): WearLaunchTarget =
    when {
        restoring -> WearLaunchTarget.Landing
        target != null -> WearLaunchTarget.entries.firstOrNull { it.extra == target } ?: WearLaunchTarget.Landing
        legacyAction == MainActivity.ACTION_OPEN_FEED -> WearLaunchTarget.Feed
        legacyAction == MainActivity.ACTION_OPEN_MESSAGES -> WearLaunchTarget.Messages
        legacyAction == MainActivity.ACTION_NEW_POST -> WearLaunchTarget.NewPost
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
 *
 * The intent is the launcher's own (MAIN / LAUNCHER) with the target in an
 * extra. Android compares a later launcher start with the intent that created
 * the task, ignoring extras, and puts a second MainActivity on top of the
 * task when they differ.
 */
fun launchIntent(
    context: Context,
    target: WearLaunchTarget,
): PendingIntent =
    PendingIntent.getActivity(
        context,
        target.ordinal,
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClass(context, MainActivity::class.java)
            .putExtra(EXTRA_LAUNCH_TARGET, target.extra)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private val WearLaunchTarget.extra: String
    get() =
        when (this) {
            WearLaunchTarget.Landing -> "landing"
            WearLaunchTarget.Feed -> "feed"
            WearLaunchTarget.Messages -> "messages"
            WearLaunchTarget.NewPost -> "new_post"
        }
