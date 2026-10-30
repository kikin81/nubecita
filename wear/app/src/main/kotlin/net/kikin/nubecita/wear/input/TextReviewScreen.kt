package net.kikin.nubecita.wear.input

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import net.kikin.nubecita.core.common.text.GraphemeCounter
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.ui.WearScrollIndicator

/** How long reviewed text is against its limit, counted as Bluesky does (graphemes). */
@Immutable
data class TextLength(
    val graphemes: Int,
    val max: Int,
) {
    val isOverLimit: Boolean get() = graphemes > max
    val canSend: Boolean get() = graphemes in 1..max

    companion object {
        fun of(
            text: String,
            max: Int,
        ) = TextLength(GraphemeCounter.count(text.trim()), max)
    }
}

/**
 * What the user dictated or typed, before it goes anywhere (W6): a dictation
 * mistake is sent under their name, so nothing is queued without this step.
 * Send is the edge button and stays disabled over [maxGraphemes]; Change opens
 * the input again. Swiping back sends nothing.
 *
 * The text is a plain item with no `transformedHeight`: that edge morph is for
 * surfaces and crops text.
 */
@Composable
fun TextReviewScreen(
    title: String,
    text: String,
    maxGraphemes: Int,
    onSend: () -> Unit,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    // Opens on the last item (Change): the edge button only shows its label
    // once the list is at its end, and Send is what the user came for.
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(initialAnchorItemIndex = 3),
) {
    val length = TextLength.of(text, maxGraphemes)
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
        edgeButton = {
            EdgeButton(onClick = onSend, enabled = length.canSend, buttonSize = EdgeButtonSize.Small) {
                Text(stringResource(R.string.wear_input_send))
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(title)
                }
            }
            item {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                )
            }
            item {
                Text(
                    if (length.isOverLimit) {
                        stringResource(R.string.wear_input_too_long, length.graphemes, length.max)
                    } else {
                        "${length.graphemes} / ${length.max}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (length.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            item {
                FilledTonalButton(
                    onClick = onChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = { Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize)) },
                    label = { Text(stringResource(R.string.wear_input_change)) },
                )
            }
        }
    }
}
