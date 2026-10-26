package net.kikin.nubecita.wear.dms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import net.kikin.nubecita.core.wearcontract.WearDmReactions
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.ui.WearScrollIndicator

/** The canned quick replies, localized; tapping one sends it and goes back. */
@Composable
fun QuickReplyScreen(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val replies = stringArrayResource(R.array.wear_dms_quick_replies).toList()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, modifier = modifier, scrollIndicator = { WearScrollIndicator(listState) }) { contentPadding ->
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
                    Text(stringResource(R.string.wear_dms_reply))
                }
            }
            items(replies, key = { it }) { reply ->
                FilledTonalButton(
                    onClick = { onPick(reply) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text(reply, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/** The phone's quick reactions, two rows of three; tapping one sends it and goes back. */
@Composable
fun ReactionScreen(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState, modifier = modifier, scrollIndicator = { WearScrollIndicator(listState) }) { contentPadding ->
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
                    Text(stringResource(R.string.wear_dms_react))
                }
            }
            WearDmReactions.EMOJI.chunked(3).forEach { row ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                        row.forEach { emoji ->
                            Button(onClick = { onPick(emoji) }, modifier = Modifier.weight(1f, fill = false)) { Text(emoji) }
                        }
                    }
                }
            }
        }
    }
}
