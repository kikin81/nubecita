package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.extendedTypography
import net.kikin.nubecita.designsystem.hero.BoldHeroGradient
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

private val AvatarSize = 88.dp
private val AvatarRingWidth = 4.dp

/**
 * Hero card orchestrator. Three header lifecycle branches:
 *
 * 1. `header != null` → full hero (gradient backdrop + avatar + name +
 *    handle + bio + stats + meta + actions). Note: a non-null
 *    `headerError` does NOT change this rendering — `headerError` is
 *    a sticky-flat-state flag whose user-visible signal is the
 *    snackbar at the screen level. The hero stays put.
 * 2. `header == null && headerError == null` → loading skeleton.
 * 3. `header == null && headerError != null` → inline error with Retry.
 *
 * `BoldHeroGradient` from :designsystem owns the Palette extraction,
 * caching, and contrast guard. We pass the banner URL and the
 * deterministic avatarHue fallback; the gradient swaps in once the
 * Palette extraction completes (which is synchronous on cache hit).
 */
@Composable
internal fun ProfileHero(
    header: ProfileHeaderUi?,
    headerError: ProfileError?,
    ownProfile: Boolean,
    onRetryHeader: () -> Unit,
    onEditTap: () -> Unit,
    onOverflowTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        header != null ->
            ProfileHeroLoaded(
                header = header,
                ownProfile = ownProfile,
                onEditTap = onEditTap,
                onOverflowTap = onOverflowTap,
                modifier = modifier,
            )
        headerError != null ->
            ProfileHeroError(
                error = headerError,
                onRetry = onRetryHeader,
                modifier = modifier,
            )
        else -> ProfileHeroLoading(modifier = modifier)
    }
}

@Composable
private fun ProfileHeroLoaded(
    header: ProfileHeaderUi,
    ownProfile: Boolean,
    onEditTap: () -> Unit,
    onOverflowTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BoldHeroGradient(
            banner = header.bannerUrl,
            avatarHue = header.avatarHue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Avatar with surface ring — the inner Box paints the
                // ring (MaterialTheme.colorScheme.surface) so the avatar
                // detaches visually from the gradient backdrop.
                Box(
                    modifier =
                        Modifier
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
                // White text overlays. The bottom stop of the gradient
                // is contrast-guarded (WCAG AA 4.5:1) against white by
                // BoldHeroGradient.enforceContrastAgainstWhite, so
                // hardcoded Color.White here is safe by construction.
                Text(
                    text = header.displayName ?: header.handle,
                    style = MaterialTheme.extendedTypography.displayName,
                    color = Color.White,
                )
                Text(
                    text = "@${header.handle}",
                    style = MaterialTheme.extendedTypography.handle,
                    color = Color.White,
                )
            }
        }
        if (header.bio != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = header.bio,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
        ProfileActionsRow(
            ownProfile = ownProfile,
            onEdit = onEditTap,
            onOverflow = onOverflowTap,
        )
    }
}

@Composable
private fun ProfileHeroLoading(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(AvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
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
                .padding(24.dp),
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
