package net.kikin.nubecita.feature.composer.impl.internal

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import net.kikin.nubecita.core.posting.ComposerAttachment

/**
 * Composer photo-picker plumbing. Returns a `() -> Unit` action that
 * launches the system photo picker (`PickVisualMedia` /
 * `PickMultipleVisualMedia`) with `maxItems` derived from the
 * composer's remaining 4-image capacity, then re-emits the picked
 * URIs to [onPick] after wrapping them with the `ContentResolver`-
 * resolved MIME types.
 *
 * The launcher contract is **captured at registration time** by
 * `rememberLauncherForActivityResult` — re-instantiating the contract
 * across recompositions does NOT change the registered `maxItems`.
 * Wrapping the body in `key(remainingCapacity)` resets the remember
 * slots when the cap shrinks, forcing a fresh registration with the
 * narrower limit. This keeps the picker UI honest ("Select up to 2",
 * not "Select up to 4") as the user adds attachments.
 *
 * `PickMultipleVisualMedia` requires `maxItems >= 2`. When
 * `remainingCapacity == 1` the helper switches to single-pick
 * (`PickVisualMedia`) so the picker still shows "Select 1" rather
 * than the multi-pick chrome with a forced lower bound.
 *
 * MIME-type resolution: `ContentResolver.getType(Uri)` returns the
 * picker-derived type (`image-slash-jpeg`, `image-slash-png`, etc.).
 * When the provider returns `null` we keep the URI but fall back to
 * the generic image wildcard MIME (see `GENERIC_IMAGE_MIME` below) —
 * the upload path tolerates it because the atproto SDK forwards
 * whatever string we pass as the `inputContentType` arg of
 * `RepoService.uploadBlob(...)`. (The slash is spelled out in this
 * KDoc on purpose: Kotlin block comments nest, so a literal "image"
 * followed by a forward-slash and a star inside a doc comment opens
 * a nested block comment that the lexer can't close.)
 *
 * Three-layer cap enforcement (per design.md): the picker's `maxItems`
 * is the user-visible cap, the ViewModel reducer is the source of
 * truth and silently truncates surplus, and the "Add image" UI
 * affordance is disabled at `attachments.size == 4`. This helper is
 * the first layer.
 *
 * Test seam: the launcher contract types come from `androidx.activity`,
 * which can't run in a JVM unit test. The picker integration is
 * exercised by the `:feature:composer:impl` instrumented (`androidTest`)
 * suite that wtq.5's task-5.5 lands; reducer-side cap behavior stays
 * in the existing `ComposerViewModelTest` JVM coverage.
 */
@Composable
internal fun rememberComposerImagePicker(
    remainingCapacity: Int,
    onPick: (List<ComposerAttachment>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    // Stabilize the callback against recompositions of the caller —
    // the launcher's onResult lambda captures this via state delegate
    // so a fresh `onPick` from the parent is read on the NEXT
    // emission instead of stale-pinning the first one we see.
    val currentOnPicked by rememberUpdatedState(onPick)

    if (remainingCapacity <= 0) {
        // No-op action when the composer is at the cap; the calling
        // affordance is also disabled, but a defensive empty action
        // means a stray click never reaches the launcher.
        return remember { {} }
    }

    return key(remainingCapacity) {
        // The PickVisualMediaRequest payload is identical for both
        // single- and multi-pick; only the contract differs. Restrict
        // to images per the lexicon.
        val request =
            remember {
                PickVisualMediaRequest(
                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                )
            }
        if (remainingCapacity == 1) {
            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) {
                        currentOnPicked(listOf(uri.toAttachment(context.contentResolver)))
                    }
                }
            remember(launcher) { { launcher.launch(request) } }
        } else {
            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = remainingCapacity),
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        val resolver = context.contentResolver
                        currentOnPicked(uris.map { it.toAttachment(resolver) })
                    }
                }
            remember(launcher) { { launcher.launch(request) } }
        }
    }
}

private fun Uri.toAttachment(resolver: ContentResolver): ComposerAttachment =
    ComposerAttachment(
        uri = this,
        // Photo Picker URIs almost always carry a concrete type; the
        // generic `image/*` is a defensive fallback only — the upload
        // path forwards whatever we pass as the SDK's
        // `inputContentType` arg, which the PDS accepts as a hint.
        mimeType = resolver.getType(this) ?: GENERIC_IMAGE_MIME,
    )

/** Used only when [ContentResolver.getType] returns null. */
private const val GENERIC_IMAGE_MIME = "image/*"
