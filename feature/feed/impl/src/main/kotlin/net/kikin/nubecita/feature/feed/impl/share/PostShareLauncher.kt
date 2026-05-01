package net.kikin.nubecita.feature.feed.impl.share

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
 * picker. `FLAG_ACTIVITY_NEW_TASK` is required when the caller is a
 * non-Activity Context — FeedScreen runs inside an Activity but adding
 * the flag is harmless and guards against future composition-local
 * Context changes.
 */
internal fun Context.launchPostShare(intent: PostShareIntent) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, intent.text)
        }
    val chooser =
        Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    startActivity(chooser)
}
