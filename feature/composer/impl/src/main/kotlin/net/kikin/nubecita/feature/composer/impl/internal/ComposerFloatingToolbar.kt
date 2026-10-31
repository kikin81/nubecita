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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 * disabled and swallows taps (no ripple). While submitting it swaps to the brand wavy
 * spinner, announced as "Submitting".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ComposerPostFab(
    enabled: Boolean,
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    // M3's disabled colors, composited opaque over the canvas: an alpha layer
    // would clip the FAB's shadow to its bounds.
    val dimmed = !enabled && !isSubmitting
    val toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    FloatingToolbarDefaults.StandardFloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor =
            if (dimmed) onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA).compositeOver(surface) else toolbarColors.fabContainerColor,
        contentColor = if (dimmed) onSurface.copy(alpha = DISABLED_CONTENT_ALPHA) else toolbarColors.fabContentColor,
        modifier =
            Modifier
                .semantics { if (!enabled) disabled() }
                .then(if (enabled) Modifier else Modifier.blockTouches()),
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

private const val DISABLED_CONTAINER_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * Consumes every pointer change in the Initial pass, before the FAB's own
 * clickable sees it. The clickable ignores an already-consumed press, so a
 * disabled FAB neither ripples nor clicks.
 */
private fun Modifier.blockTouches(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
