package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.extendedTypography
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

private val AvatarSize = 96.dp
private val AvatarRingWidth = 4.dp
private val BannerHeight = 200.dp
private val AvatarOverlap = 48.dp

/**
 * Hero card orchestrator. Updated to Material 3 Expressive "overlapping" design.
 *
 * Renders the actual banner image edge-to-edge at the top, with the
 * circular avatar overlapping the banner's bottom edge. Displays
 * name, handle, bio, stats, and meta below.
 */
@Composable
internal fun ProfileHero(
    header: ProfileHeaderUi?,
    headerError: ProfileError?,
    onRetryHeader: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    when {
        header != null ->
            ProfileHeroLoaded(
                header = header,
                modifier = modifier,
                topInset = topInset,
            )
        headerError != null ->
            ProfileHeroError(
                error = headerError,
                onRetry = onRetryHeader,
                modifier = modifier,
                topInset = topInset,
            )
        else -> ProfileHeroLoading(modifier = modifier, topInset = topInset)
    }
}

@Composable
private fun ProfileHeroLoaded(
    header: ProfileHeaderUi,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(BannerHeight + topInset)) {
            // 1. Actual Banner Image
            NubecitaAsyncImage(
                model = header.bannerUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            // 2. Top Scrim (for status bar legibility)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(topInset + 32.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                    ),
                            ),
                        ),
            )

            // 3. Overlapping Avatar
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = AvatarOverlap)
                        .size(AvatarSize + AvatarRingWidth * 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                NubecitaAsyncImage(
                    model = header.avatarUrl,
                    contentDescription =
                        stringResource(
                            R.string.profile_avatar_content_description,
                            header.displayName ?: header.handle,
                        ),
                    modifier =
                        Modifier
                            .size(AvatarSize)
                            .clip(CircleShape),
                )
            }
        }

        Spacer(Modifier.height(AvatarOverlap + 8.dp))

        // Info Column
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = header.displayName ?: header.handle,
                style = MaterialTheme.extendedTypography.displayName,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "@${header.handle}",
                style = MaterialTheme.extendedTypography.handle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (header.bio != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = header.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ProfileStatsRow(
                postsCount = header.postsCount,
                followersCount = header.followersCount,
                followsCount = header.followsCount,
            )
            ProfileMetaRow(
                website = header.website,
                location = header.location,
                joinedDisplay = header.joinedDisplay,
            )
        }
    }
}

@Composable
private fun ProfileHeroLoading(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BannerHeight + topInset)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .size(width = 160.dp, height = 24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier =
                Modifier
                    .size(width = 120.dp, height = 14.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun ProfileHeroError(
    error: ProfileError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val bodyRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_body
            is ProfileError.Unknown -> R.string.profile_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = topInset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_header_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.profile_header_error_retry))
        }
    }
}
