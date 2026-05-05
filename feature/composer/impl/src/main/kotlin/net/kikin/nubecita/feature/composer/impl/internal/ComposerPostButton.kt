package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.composer.impl.R
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus

/**
 * Post button. Shows the "Post" label by default; while
 * [submitStatus] is `Submitting` the content morphs to a small
 * inline [CircularProgressIndicator] and the tap is disabled.
 *
 * The morph is implemented via [AnimatedContent] keyed on the
 * status — fade-in / fade-out, no extra layout-shift animation
 * (the button stays the same size; only its content swaps). M3
 * doesn't yet expose a stable wavy progress indicator inside a
 * button, so V1 uses the standard circular variant; the wavy
 * treatment lands as a polish update once Compose Material3 stable
 * surfaces it.
 *
 * Compose-perf notes:
 * - `AnimatedContent` is keyed on a sealed-status sum so the
 *   recomposition only fires when the variant changes, not on every
 *   `submitStatus` instance refresh.
 * - The `enabled` parameter is computed by the caller (the screen
 *   composable), which keeps the button's recomposition scope
 *   minimal — the button only re-renders when its three direct
 *   inputs (`enabled`, `submitStatus`, `onClick`) change.
 * - `onClick` is an unstable lambda parameter — callers MUST pass a
 *   `remember`-stabilized lambda or method reference (the typical
 *   `viewModel::handleEvent` form) to avoid invalidating this
 *   composition every parent recomposition.
 */
@Composable
internal fun ComposerPostButton(
    enabled: Boolean,
    submitStatus: ComposerSubmitStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSubmitting = submitStatus is ComposerSubmitStatus.Submitting
    Button(
        onClick = onClick,
        enabled = enabled && !isSubmitting,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = isSubmitting,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "composer-post-button-content",
        ) { submitting ->
            if (submitting) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .semantics {
                                contentDescription = "Submitting"
                            },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(text = stringResource(R.string.composer_post_action))
            }
        }
    }
}
