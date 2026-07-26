package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideo
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideoStage

/** Thumbnail aspect. A poster is fitted into it rather than dictating height. */
private const val THUMBNAIL_ASPECT = 16f / 9f

/**
 * The attached video, its stage, and how to get rid of it.
 *
 * [progressFlow] is a flow rather than a value, and that is load-bearing. The
 * fraction changes about four times a second; a plain parameter would recompose
 * this card on every tick. Collected here and read **only inside**
 * [LinearProgressIndicator]'s lambda, the read is recorded in the draw scope —
 * so a tick redraws the bar and recomposes nothing.
 *
 * Whether a bar is shown at all is decided from the *stage*, not the fraction.
 * Testing `progress() != null` in a `when` would read the value at composition
 * scope and undo the whole arrangement.
 *
 * Each stage shows its own fraction rather than one combined bar: compression,
 * upload and server-side processing take wildly different times, so a single
 * percentage would stall and jump misleadingly.
 */
@Composable
internal fun ComposerVideoCard(
    video: ComposerVideo,
    progressFlow: StateFlow<Float?>,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onEditAlt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.s2),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        ) {
            VideoThumbnail(video = video, progressFlow = progressFlow)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(video.stage.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = MaterialTheme.spacing.s2),
                )
                Row {
                    // Alt text only once the video will actually ship. Offering
                    // it mid-upload invites work the user could lose to a
                    // failure.
                    if (video.isReady) {
                        TextButton(onClick = onEditAlt) {
                            Text(
                                stringResource(
                                    if (video.alt.isBlank()) {
                                        R.string.composer_video_add_alt
                                    } else {
                                        R.string.composer_video_edit_alt
                                    },
                                ),
                            )
                        }
                    }
                    if (video.hasFailed) {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.composer_video_retry))
                        }
                    }
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.composer_video_remove))
                    }
                }
            }

            video.error?.let { error ->
                Text(
                    text = error.resolveMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Poster frame with the stage overlaid.
 *
 * The poster is the source URI itself — Coil decodes a video frame — so the
 * user sees the clip they picked rather than a grey box while it uploads.
 */
@Composable
private fun VideoThumbnail(
    video: ComposerVideo,
    progressFlow: StateFlow<Float?>,
) {
    // Declared with `by` and accessed only inside the progress lambda below, so
    // the state read lands in the draw scope rather than this composable's.
    val progress by progressFlow.collectAsStateWithLifecycle()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(THUMBNAIL_ASPECT)
                .clip(RoundedCornerShape(MaterialTheme.spacing.s1)),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaAsyncImage(
            model = video.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            video.hasFailed ->
                NubecitaIcon(
                    name = NubecitaIconName.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )

            video.isReady ->
                NubecitaIcon(
                    name = NubecitaIconName.PlayArrow,
                    contentDescription = null,
                    filled = true,
                )

            // Determinate for stages that report a fraction, a spinner for the
            // ones that do not (the limits probe). Decided from the stage so
            // the fraction itself is never read at composition scope.
            video.stage.hasProgress ->
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.s4),
                )

            // The shared brand spinner, not a raw M3 one — the repo guards
            // against re-importing CircularProgressIndicator outside
            // :designsystem so every 'loading' ring in the app matches.
            else -> NubecitaWavyProgressIndicator(modifier = Modifier.size(MaterialTheme.spacing.s8))
        }
    }
}

private fun ComposerVideoStage.labelRes(): Int =
    when (this) {
        ComposerVideoStage.CheckingLimits -> R.string.composer_video_checking
        ComposerVideoStage.Compressing -> R.string.composer_video_compressing
        ComposerVideoStage.Uploading -> R.string.composer_video_uploading
        ComposerVideoStage.Processing -> R.string.composer_video_processing
        ComposerVideoStage.Ready -> R.string.composer_video_ready
        ComposerVideoStage.Failed -> R.string.composer_video_failed
    }

/**
 * A message the user can act on, per failure kind.
 *
 * Server-supplied text wins where it exists. The two account-level refusals —
 * an unverified email and an exhausted daily quota — are phrased by Bluesky,
 * and it will phrase future ones this client has never heard of. A local
 * paraphrase would be worse and would go stale.
 */
@Composable
private fun VideoUploadError.resolveMessage(): String =
    when (this) {
        is VideoUploadError.NotPermitted ->
            message ?: stringResource(R.string.composer_video_error_not_permitted)

        is VideoUploadError.TooLong ->
            stringResource(R.string.composer_video_error_too_long, maxDurationMs / 60_000)

        is VideoUploadError.CompressionFailed ->
            stringResource(R.string.composer_video_error_compression)

        is VideoUploadError.UploadFailed ->
            message ?: stringResource(R.string.composer_video_error_upload)

        is VideoUploadError.ProcessingFailed ->
            message ?: stringResource(R.string.composer_video_error_processing)

        is VideoUploadError.Network ->
            stringResource(R.string.composer_video_error_network)
    }

/** Stages that report a completion fraction. */
private val ComposerVideoStage.hasProgress: Boolean
    get() =
        this == ComposerVideoStage.Compressing ||
            this == ComposerVideoStage.Uploading ||
            this == ComposerVideoStage.Processing
