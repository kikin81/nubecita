package net.kikin.nubecita.feature.mediaviewer.impl

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay

/**
 * Entry metadata that replaces `NavDisplay`'s predictive-back transition for
 * the viewer.
 *
 * The image is a shared element: it is drawn in the shared-transition overlay
 * and travels to its card by itself. Everything else on the page (the black
 * background, the close / save buttons, the post details) belongs to the
 * page's own exit. `NavDisplay`'s default predictive exit is
 * `scaleOut(targetScale = 0.7f)` with no fade, so that page shrank toward the
 * middle of the screen as an opaque black box while the image headed for the
 * card, then vanished on the last frame (nubecita-vxrn).
 *
 * Nothing but the image should move, so the page only fades, and fast: a
 * predictive back seeks the whole transition with the gesture, and the page
 * has to be out of the way before the image has shrunk far enough to show the
 * box around it.
 *
 * Only the predictive key is set. A plain pop (close button, swipe down) keeps
 * `NavDisplay`'s default, which already fades the page in place.
 */
internal fun mediaViewerTransitionMetadata(): Map<String, Any> = NavDisplay.predictivePopTransitionSpec { mediaViewerPredictivePopTransform() }

/**
 * `NavDisplay`'s default predictive pop with the exit swapped from a scale to
 * a fade. The enter half is kept as it is: the screen underneath is composed
 * from scratch when the gesture starts, and fading it in covers its first
 * frames.
 */
internal fun mediaViewerPredictivePopTransform(): ContentTransform = fadeIn(animationSpec = UnderneathFadeInSpec) togetherWith fadeOut(animationSpec = PageFadeOutSpec)

/**
 * Mirrors `MaterialTheme.motionScheme.fastEffectsSpec()`, which the viewer's
 * chrome already uses to show and hide. Spelled out because a transition spec
 * is built outside composition, where the theme can't be read.
 */
internal val PageFadeOutSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 3800f)

/** The spec `NavDisplay`'s default predictive pop fades the incoming screen with. */
internal val UnderneathFadeInSpec: FiniteAnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1600f)
