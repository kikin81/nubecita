package net.kikin.nubecita.core.image

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

/**
 * System photo-picker plumbing. Returns a `() -> Unit` action that
 * launches the system photo picker (`PickVisualMedia` /
 * `PickMultipleVisualMedia`) with `maxItems` derived from
 * [remainingCapacity], then re-emits the picked URIs to [onPick] after
 * wrapping them with the `ContentResolver`-resolved MIME types as
 * [PickedImage]s.
 *
 * The launcher contract is **captured at registration time** by
 * `rememberLauncherForActivityResult` — re-instantiating the contract
 * across recompositions does NOT change the registered `maxItems`.
 * Wrapping the body in `key(remainingCapacity)` resets the remember
 * slots when the cap shrinks, forcing a fresh registration with the
 * narrower limit. This keeps the picker UI honest ("Select up to 2",
 * not "Select up to 4") as the caller's remaining capacity changes.
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
 * Test seam: the launcher contract types come from `androidx.activity`,
 * which can't run in a JVM unit test. The picker integration is
 * exercised by instrumented (`androidTest`) suites in consuming
 * feature modules; caller-side cap behavior stays in those modules'
 * JVM coverage.
 */
@Composable
fun rememberImagePicker(
    remainingCapacity: Int,
    // Declared BEFORE onPick deliberately. Existing call sites pass onPick as a
    // trailing lambda, so a new function-typed parameter after it would silently
    // rebind that lambda to this one — a compile error here, but the kind that
    // would be a runtime bug if the types happened to line up.
    onPickVideo: ((Uri) -> Unit)? = null,
    onPick: (List<PickedImage>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    // Stabilize the callback against recompositions of the caller —
    // the launcher's onResult lambda captures this via state delegate
    // so a fresh `onPick` from the parent is read on the NEXT
    // emission instead of stale-pinning the first one we see.
    val currentOnPicked by rememberUpdatedState(onPick)
    val currentOnPickedVideo by rememberUpdatedState(onPickVideo)

    if (remainingCapacity <= 0) {
        // No-op action when the caller is at the cap; the calling
        // affordance is also disabled, but a defensive empty action
        // means a stray click never reaches the launcher.
        return remember { {} }
    }

    return key(remainingCapacity) {
        // The PickVisualMediaRequest payload is identical for both
        // single- and multi-pick; only the contract differs.
        //
        // Media type widens to ImageAndVideo only when the caller can accept a
        // video. A caller without [onPickVideo] stays images-only rather than
        // showing videos it would silently discard.
        val allowsVideo = currentOnPickedVideo != null
        val request =
            remember(allowsVideo) {
                PickVisualMediaRequest(
                    mediaType =
                        if (allowsVideo) {
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo
                        } else {
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        },
                )
            }
        if (remainingCapacity == 1) {
            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    if (uri != null) {
                        routePicked(listOf(uri), context.contentResolver, currentOnPicked, currentOnPickedVideo)
                    }
                }
            remember(launcher) { { launcher.launch(request) } }
        } else {
            val launcher =
                rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = remainingCapacity),
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        routePicked(uris, context.contentResolver, currentOnPicked, currentOnPickedVideo)
                    }
                }
            remember(launcher) { { launcher.launch(request) } }
        }
    }
}

/**
 * Split a picker result by MIME and dispatch each kind to its handler.
 *
 * A post carries **either** one video or up to four images, never both, so a
 * mixed selection cannot be honoured as picked. The video wins and the images
 * are dropped — it is the costlier choice to re-make, and the composer
 * announces the drop rather than doing it silently.
 */
private fun routePicked(
    uris: List<Uri>,
    resolver: ContentResolver,
    onImages: (List<PickedImage>) -> Unit,
    onVideo: ((Uri) -> Unit)?,
) {
    val firstVideo = if (onVideo != null) uris.firstOrNull { resolver.isVideo(it) } else null
    if (firstVideo != null && onVideo != null) {
        onVideo(firstVideo)
        return
    }
    val images = uris.filterNot { resolver.isVideo(it) }
    if (images.isNotEmpty()) onImages(images.map { it.toPickedImage(resolver) })
}

private fun ContentResolver.isVideo(uri: Uri): Boolean = getType(uri)?.startsWith("video/") == true

private fun Uri.toPickedImage(resolver: ContentResolver): PickedImage =
    PickedImage(
        uri = this,
        // Photo Picker URIs almost always carry a concrete type; the
        // generic `image/*` is a defensive fallback only — the upload
        // path forwards whatever we pass as the SDK's
        // `inputContentType` arg, which the PDS accepts as a hint.
        mimeType = resolver.getType(this) ?: GENERIC_IMAGE_MIME,
    )

/** Used only when [ContentResolver.getType] returns null. */
private const val GENERIC_IMAGE_MIME = "image/*"
