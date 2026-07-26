package net.kikin.nubecita.feature.composer.impl

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.posting.ComposerVideoEmbed
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.ComposerVideoCard
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideo
import net.kikin.nubecita.feature.composer.impl.state.ComposerVideoStage

/**
 * Baselines for every stage of the video attachment card.
 *
 * The poster never loads in the screenshot host (no network, and
 * `Uri.EMPTY` decodes to nothing), so `NubecitaAsyncImage` renders its
 * placeholder `ColorPainter` — deterministic, and it keeps the stage overlay
 * as the only thing that differs between baselines. That is the point: these
 * images exist to catch a stage rendering the wrong affordance, not to prove
 * Coil works.
 *
 * Progress is a fixed value per fixture rather than a live flow, so the
 * determinate bar lands at a stable width.
 */
private fun video(
    stage: ComposerVideoStage,
    alt: String = "",
    error: VideoUploadError? = null,
): ComposerVideo =
    ComposerVideo(
        uri = Uri.EMPTY,
        alt = alt,
        stage = stage,
        error = error,
        embed =
            if (stage == ComposerVideoStage.Ready) {
                ComposerVideoEmbed(
                    blob =
                        Blob(
                            ref = CidLink(link = "bafyreiexamplecidforascreenshotbaseline000000000000"),
                            mimeType = "video/mp4",
                            size = 4_096L,
                        ),
                    alt = alt,
                    aspectRatio = AspectRatio(width = 1080, height = 1920),
                )
            } else {
                null
            },
    )

@Composable
private fun Card(
    stage: ComposerVideoStage,
    progress: Float? = null,
    alt: String = "",
    error: VideoUploadError? = null,
) {
    ComposerVideoCard(
        video = video(stage, alt, error),
        progressFlow = MutableStateFlow(progress),
        onRemove = {},
        onRetry = {},
        onEditAlt = {},
    )
}

/**
 * The in-flight stages. `CheckingLimits` has no fraction, so it must show the
 * indeterminate spinner rather than a zero-width bar — a distinction that is
 * invisible in code review and obvious in a baseline.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun ComposerVideoCardInFlightPreviews() {
    NubecitaCanvasPreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(stage = ComposerVideoStage.CheckingLimits)
            Card(stage = ComposerVideoStage.Compressing, progress = 0.35f)
            Card(stage = ComposerVideoStage.Uploading, progress = 0.8f)
            Card(stage = ComposerVideoStage.Processing, progress = 0.5f)
        }
    }
}

/**
 * Terminal states. Ready gains the alt affordance — and its label changes
 * between "Add alt" and "Edit alt" — while Failed gains retry plus the error
 * line.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun ComposerVideoCardTerminalPreviews() {
    NubecitaCanvasPreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(stage = ComposerVideoStage.Ready)
            Card(stage = ComposerVideoStage.Ready, alt = "a cat knocking over a glass")
            Card(
                stage = ComposerVideoStage.Failed,
                error = VideoUploadError.Network("Connection lost"),
            )
        }
    }
}

/**
 * The failure variants that carry server text versus the ones that fall back to
 * local copy. `NotPermitted` passing the server's own message through is the
 * behaviour most likely to regress into a generic string.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun ComposerVideoCardErrorPreviews() {
    NubecitaCanvasPreviewTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                stage = ComposerVideoStage.Failed,
                error =
                    VideoUploadError.NotPermitted(
                        "Account email must be verified to upload video.",
                    ),
            )
            Card(
                stage = ComposerVideoStage.Failed,
                error = VideoUploadError.TooLong(durationMs = 400_000, maxDurationMs = 180_000),
            )
            Card(
                stage = ComposerVideoStage.Failed,
                error = VideoUploadError.CompressionFailed(null),
            )
        }
    }
}
