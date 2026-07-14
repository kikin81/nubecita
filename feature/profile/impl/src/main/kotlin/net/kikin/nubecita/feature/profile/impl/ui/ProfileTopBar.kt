package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.R

internal const val PROFILE_BAR_FADE_MULTIPLIER: Float = 0.5f

internal fun computeBarAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeWindowPx: Int,
): Float =
    when {
        firstVisibleItemIndex > 0 -> 1f
        fadeWindowPx <= 0 -> 0f
        else -> (firstVisibleItemScrollOffset.toFloat() / fadeWindowPx).coerceIn(0f, 1f)
    }

/**
 * Collapsing top bar for the Profile screen.
 *
 * Updated with circular "expressive" buttons for navigation and
 * settings to ensure legibility over any background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,
    listState: LazyListState,
    ownProfile: Boolean,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBookmarks: (() -> Unit)? = null,
) {
    val alpha by remember(listState) {
        derivedStateOf {
            val firstItemSize =
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == 0 }
                    ?.size ?: 0
            val fadeWindowPx = (firstItemSize * PROFILE_BAR_FADE_MULTIPLIER).toInt()
            computeBarAlpha(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                fadeWindowPx = fadeWindowPx,
            )
        }
    }
    ProfileTopBar(
        header = header,
        alpha = alpha,
        ownProfile = ownProfile,
        onBack = onBack,
        onSettings = onSettings,
        modifier = modifier,
        onBookmarks = onBookmarks,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileTopBar(
    header: ProfileHeaderUi?,
    alpha: Float,
    ownProfile: Boolean,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBookmarks: (() -> Unit)? = null,
) {
    val barColors =
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha),
        )

    TopAppBar(
        title = {
            if (header != null) {
                Column(modifier = Modifier.alpha(alpha)) {
                    Text(
                        text = header.displayName ?: header.handle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${header.handle}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                ProfileTopBarCircleButton(
                    onClick = onBack,
                    icon = NubecitaIconName.ArrowBack,
                    contentDescription = stringResource(R.string.profile_topbar_back_content_description),
                    modifier = Modifier.mirror(),
                )
            }
        },
        actions = {
            // Bookmarks + Settings are own-profile only (both private utilities).
            if (ownProfile && onBookmarks != null) {
                ProfileTopBarCircleButton(
                    onClick = onBookmarks,
                    icon = NubecitaIconName.Bookmark,
                    contentDescription = stringResource(R.string.profile_action_bookmarks),
                )
            }
            if (ownProfile && onSettings != null) {
                ProfileTopBarCircleButton(
                    onClick = onSettings,
                    icon = NubecitaIconName.Settings,
                    contentDescription = stringResource(R.string.profile_action_settings),
                )
            }
        },
        colors = barColors,
        modifier = modifier,
    )
}

/**
 * Circular button for the TopAppBar that ensures legibility over
 * the banner image by having its own semi-opaque surface background.
 */
@Composable
private fun ProfileTopBarCircleButton(
    onClick: () -> Unit,
    icon: NubecitaIconName,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(8.dp).size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            NubecitaIcon(
                name = icon,
                contentDescription = contentDescription,
                filled = true,
            )
        }
    }
}
