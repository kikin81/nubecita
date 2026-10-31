package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * The composer's authoring actions as an M3 Expressive floating toolbar
 * (standard colors), with Post as the toolbar's FAB. The caller anchors it
 * bottom-center and applies `imePadding()`, so it sits on top of the keyboard
 * when it is open and at the bottom of the composer otherwise. Always visible:
 * no scroll-away behaviour, so Post never leaves the screen.
 *
 * Each action is an icon-only [IconButton] named by its icon's content
 * description, which the button merges, so the label is announced once with
 * the button role (GIF is named by its visible text).
 *
 * @param canAddImage false while a GIF is attached (GIF and photos share the
 *   embed slot) or while submitting.
 * @param canAddGif false while photos are attached or while submitting.
 * @param showAudience false in reply mode: threadgate/postgate belong to the
 *   thread root.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ComposerFloatingToolbar(
    canPost: Boolean,
    isSubmitting: Boolean,
    canAddImage: Boolean,
    canAddGif: Boolean,
    showAudience: Boolean,
    onPostClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onGifClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAudienceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        floatingActionButton = {
            ComposerPostFab(
                enabled = canPost && !isSubmitting,
                isSubmitting = isSubmitting,
                onClick = onPostClick,
            )
        },
    ) {
        IconButton(onClick = onAddImageClick, enabled = canAddImage) {
            NubecitaIcon(
                name = NubecitaIconName.AddPhotoAlternate,
                contentDescription = stringResource(R.string.composer_add_image_action),
            )
        }
        IconButton(onClick = onGifClick, enabled = canAddGif) {
            Text(text = stringResource(R.string.composer_gif_chip), style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = onLanguageClick, enabled = !isSubmitting) {
            NubecitaIcon(
                name = NubecitaIconName.Language,
                contentDescription = stringResource(R.string.composer_language_action),
            )
        }
        if (showAudience) {
            IconButton(onClick = onAudienceClick, enabled = !isSubmitting) {
                NubecitaIcon(
                    name = NubecitaIconName.Settings,
                    contentDescription = stringResource(R.string.composer_audience_title),
                )
            }
        }
    }
}

/**
 * Post as the toolbar's FAB. The toolbar FAB has no `enabled` parameter, so a
 * draft that can't be posted renders at reduced emphasis, is announced as
 * disabled and ignores taps. While submitting it swaps to the brand wavy
 * spinner, announced as "Submitting".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposerPostFab(
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    FloatingToolbarDefaults.StandardFloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier =
            Modifier
                .alpha(if (enabled || isSubmitting) 1f else DISABLED_FAB_ALPHA)
                .semantics { if (!enabled) disabled() },
    ) {
        val submittingDescription = stringResource(R.string.composer_submitting)
        AnimatedContent(
            targetState = isSubmitting,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "composer-post-fab-content",
        ) { submitting ->
            if (submitting) {
                NubecitaWavyProgressIndicator(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .semantics { contentDescription = submittingDescription },
                )
            } else {
                NubecitaIcon(
                    name = NubecitaIconName.Send,
                    contentDescription = stringResource(R.string.composer_post_action),
                )
            }
        }
    }
}

private const val DISABLED_FAB_ALPHA = 0.38f
