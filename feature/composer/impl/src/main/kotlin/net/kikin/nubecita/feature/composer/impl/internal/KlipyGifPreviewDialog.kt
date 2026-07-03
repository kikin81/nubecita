package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.data.models.previewAspectRatio
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Long-press preview overlay for a KLIPY item: a larger animated render plus a
 * Report affordance (KLIPY compliance). Report opens a reason menu; picking a
 * reason submits and dismisses. Close (or scrim/back) dismisses without report.
 */
@Composable
internal fun KlipyGifPreviewDialog(
    media: KlipyMediaUi,
    onReport: (KlipyReportReason) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reportMenuOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.klipy_preview_close)) }
        },
        dismissButton = {
            Box {
                TextButton(onClick = { reportMenuOpen = true }) {
                    Text(stringResource(R.string.klipy_report))
                }
                DropdownMenu(expanded = reportMenuOpen, onDismissRequest = { reportMenuOpen = false }) {
                    KlipyReportReason.entries.forEach { reason ->
                        DropdownMenuItem(
                            text = { Text(stringResource(reason.labelRes())) },
                            onClick = {
                                reportMenuOpen = false
                                onReport(reason)
                            },
                        )
                    }
                }
            }
        },
        text = {
            NubecitaAsyncImage(
                model = media.embedUrl,
                contentDescription = media.title ?: stringResource(R.string.klipy_gif_description),
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(media.previewAspectRatio)
                        .clip(RoundedCornerShape(12.dp)),
            )
        },
    )
}

private fun KlipyReportReason.labelRes(): Int =
    when (this) {
        KlipyReportReason.SPAM -> R.string.klipy_report_spam
        KlipyReportReason.NUDITY -> R.string.klipy_report_nudity
        KlipyReportReason.VIOLENCE -> R.string.klipy_report_violence
        KlipyReportReason.OTHER -> R.string.klipy_report_other
    }
