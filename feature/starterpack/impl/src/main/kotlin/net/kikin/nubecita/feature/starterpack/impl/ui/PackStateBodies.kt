package net.kikin.nubecita.feature.starterpack.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.starterpack.impl.R
import net.kikin.nubecita.feature.starterpack.impl.StarterPackError

/** Skeleton rows while the pack and its first page load. */
@Composable
internal fun PackLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.starterpack_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        PackSkeletonBar(widthFraction = 0.6f, height = 24.dp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        repeat(5) { PackMemberSkeleton() }
    }
}

@Composable
private fun PackSkeletonBar(
    widthFraction: Float,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(fraction = widthFraction)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun PackMemberSkeleton() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(modifier = Modifier.weight(1f)) {
            PackSkeletonBar(widthFraction = 0.5f, height = 14.dp)
            Spacer(Modifier.height(6.dp))
            PackSkeletonBar(widthFraction = 0.7f, height = 10.dp)
        }
    }
}

/** Full-screen retry layout for a pack that failed to load. */
@Composable
internal fun PackErrorBody(
    error: StarterPackError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titleRes, bodyRes) =
        when (error) {
            StarterPackError.Network ->
                R.string.starterpack_error_network_title to R.string.starterpack_error_network_body
            StarterPackError.Unknown ->
                R.string.starterpack_error_unknown_title to R.string.starterpack_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = 64.dp,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.starterpack_error_retry))
        }
    }
}

/** "This pack is empty", shown under the header of a pack with no members. */
@Composable
internal fun PackEmptyBody(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.starterpack_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.starterpack_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
