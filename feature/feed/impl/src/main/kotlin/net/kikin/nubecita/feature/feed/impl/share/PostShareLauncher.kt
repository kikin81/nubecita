package net.kikin.nubecita.feature.feed.impl.share

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Fire the system share sheet for a pre-computed [PostShareIntent].
 *
 * Pure platform glue — the VM produces the [PostShareIntent] and the
 * screen calls this from inside its effect collector. Kept out of the
 * VM so unit tests don't have to mock `Context` / `Intent` to assert
 * the share payload.
 *
 * `Intent.createChooser` forces the system share sheet (instead of any
 * default app the user may have set) so every share is a deliberate
 * picker. `FLAG_ACTIVITY_NEW_TASK` is added **only** when the caller
 * isn't an Activity — adding it from an Activity context puts the
 * chooser/target in a new task, which can leave the user on the
 * launcher (instead of returning to nubecita) when they back out of
 * the share target. From a non-Activity Context, the flag is required
 * by `Context.startActivity`'s contract.
 */
internal fun Context.launchPostShare(intent: PostShareIntent) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, intent.text)
        }
    val chooser =
        Intent.createChooser(sendIntent, null).apply {
            if (this@launchPostShare !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    startActivity(chooser)
}
