package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.SupporterBadge
import net.kikin.nubecita.designsystem.component.VerificationBadge
import net.kikin.nubecita.designsystem.extendedTypography
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

private val AvatarSize = 96.dp
private val AvatarRingWidth = 4.dp
private val BannerHeight = 200.dp
private val AvatarOverlap = 48.dp
private val BannerCornerRadius = 28.dp

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
    showSupporterBadge: Boolean,
    onRetryHeader: () -> Unit,
    onVerificationBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    when {
        header != null ->
            ProfileHeroLoaded(
                header = header,
                showSupporterBadge = showSupporterBadge,
                onVerificationBadgeClick = onVerificationBadgeClick,
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
    showSupporterBadge: Boolean,
    onVerificationBadgeClick: () -> Unit,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    val bannerShape =
        RoundedCornerShape(
            topStart = BannerCornerRadius,
            topEnd = BannerCornerRadius,
        )
    Column(modifier = modifier.fillMaxWidth()) {
        // Parent Box intentionally has NO clip — the avatar below overlaps the
        // banner's bottom edge via offset, and a parent clip would crop its
        // bottom half. Instead, the rounded-corner clip is applied to the
        // banner image and the scrim individually.
        Box(modifier = Modifier.fillMaxWidth().height(BannerHeight + topInset)) {
            // 1. Actual Banner Image
            NubecitaAsyncImage(
                model = header.bannerUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(bannerShape),
                contentScale = ContentScale.Crop,
            )

            // 2. Top Scrim (for status bar legibility)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(topInset + 32.dp)
                        .clip(bannerShape)
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
                    contentScale = ContentScale.Crop,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    // weight(fill = false) lets a long name take only the space it
                    // needs and never squeeze the badge out; single-line clipping of
                    // extreme names is refined in nubecita-vw45.5.
                    modifier = Modifier.weight(1f, fill = false),
                    text = header.displayName ?: header.handle,
                    style = MaterialTheme.extendedTypography.displayName,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (header.verifiedBadge != VerifiedBadge.None) {
                    // Wrap in a Box that owns the touch target / clip / click so the
                    // 22dp badge stays centered and the ripple is a clean circle.
                    // minimumInteractiveComponentSize first so the clickable area is the
                    // enlarged ≥48dp box (Material/WCAG min) — Modifier.clickable doesn't
                    // apply it the way M3 buttons do.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(R.string.verification_badge_open_details),
                                    onClick = onVerificationBadgeClick,
                                ),
                    ) {
                        VerificationBadge(badge = header.verifiedBadge, size = 22.dp)
                    }
                }
            }
            Text(
                text = "@${header.handle}",
                style = MaterialTheme.extendedTypography.handle,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showSupporterBadge) {
                Spacer(Modifier.height(4.dp))
                SupporterBadge()
            }

            if (header.viewerModeration.isMutedByViewer) {
                Spacer(Modifier.height(4.dp))
                MutedNotice()
            }

            if (header.bio != null) {
                Spacer(Modifier.height(8.dp))
                // rememberUpdatedState so the click listener captured in the
                // remembered AnnotatedString always launches with the latest context
                // (defensive: in this app the Activity context is stable for the
                // composition's lifetime, but this keeps the retained callback fresh
                // if LocalContext is ever re-provided without bio/linkColor changing).
                val context by rememberUpdatedState(LocalContext.current)
                val linkColor = MaterialTheme.colorScheme.primary
                // getProfile has no bio facets, so auto-detect URLs and route taps
                // to an in-app Custom Tab (like the external-embed cards).
                val bioText =
                    remember(header.bio, linkColor) {
                        buildBioAnnotatedString(bio = header.bio, linkColor = linkColor) { url ->
                            openBioLinkInCustomTab(context, url)
                        }
                    }
                Text(
                    text = bioText,
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

/**
 * Inline chip shown below the display name / handle when the authenticated
 * viewer has muted this profile's author. Reads from
 * [ProfileHeaderUi.viewerModeration.isMutedByViewer].
 *
 * Visual language: [MaterialTheme.colorScheme.surfaceContainerLow] fill
 * (the "recessed inset" token per the design-system surface-roles table)
 * with [MaterialTheme.colorScheme.onSurfaceVariant] content — a passive
 * notice, not an actionable affordance. Mirrors the pill shape of
 * [SupporterBadge].
 */
@Composable
private fun MutedNotice(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.VolumeOff,
            contentDescription = null,
            opticalSize = 16.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.profile_muted_notice),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileHeroLoading(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val loadingBannerShape =
            RoundedCornerShape(
                topStart = BannerCornerRadius,
                topEnd = BannerCornerRadius,
            )
        Box(modifier = Modifier.fillMaxWidth().height(BannerHeight + topInset)) {
            // Banner placeholder — clip+background applied locally so the
            // overlapping avatar below isn't cropped by a parent clip.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(loadingBannerShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
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
                Box(
                    modifier =
                        Modifier
                            .size(AvatarSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.height(AvatarOverlap + 8.dp))
        Box(
            modifier =
                Modifier
                    .size(width = 160.dp, height = 24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
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
                .padding(top = topInset)
                .padding(horizontal = 16.dp, vertical = 24.dp),
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
