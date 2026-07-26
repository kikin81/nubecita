package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadState
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideo

/**
 * The attached video and where its upload has got to.
 *
 * Deliberately plain. `nubecita-uu6c.6` replaces this with the designed card
 * (thumbnail, per-stage treatment, alt-text entry); this exists so the pipeline
 * is observable — an upload the user cannot see is an upload they cannot tell
 * apart from a hang.
 *
 * Each stage shows its own progress rather than a combined bar: compression,
 * upload and server-side processing take wildly different times, so one
 * percentage would stall and jump misleadingly.
 */
@Composable
internal fun ComposerVideoCard(
    video: ComposerVideo,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(video.uploadState.labelRes()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.composer_video_remove))
                }
            }

            video.stageProgress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            (video.uploadState as? VideoUploadState.Failed)?.let { failed ->
                // The server's own words where it has them. A locally-invented
                // message would be worse: the two account-level refusals
                // (unverified email, daily quota) are phrased by Bluesky, and
                // it will phrase future ones we do not know about.
                failed.error.detail()?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.composer_video_retry))
                }
            }
        }
    }
}

private fun VideoUploadState.labelRes(): Int =
    when (this) {
        VideoUploadState.CheckingLimits -> R.string.composer_video_checking
        is VideoUploadState.Compressing -> R.string.composer_video_compressing
        is VideoUploadState.Uploading -> R.string.composer_video_uploading
        is VideoUploadState.Processing -> R.string.composer_video_processing
        is VideoUploadState.Ready -> R.string.composer_video_ready
        is VideoUploadState.Failed -> R.string.composer_video_failed
    }

private fun VideoUploadError.detail(): String? =
    when (this) {
        is VideoUploadError.NotPermitted -> message
        is VideoUploadError.CompressionFailed -> message
        is VideoUploadError.UploadFailed -> message
        is VideoUploadError.ProcessingFailed -> message
        is VideoUploadError.Network -> message
        is VideoUploadError.TooLong -> null
    }
