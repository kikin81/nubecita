package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R

/**
 * Empty-state body for the People tab. Per design A4: large icon,
 * display-style heading with the user's query, body copy, and one
 * action — "Clear search" (filled tonal). No sort toggle (the People
 * tab has no sort).
 *
 * Icon: the design calls for `PersonOff`, but the vendored icon-font
 * subset does not include it (verified at implementation time —
 * mirrors vrba.6's `Inbox` fallback when `SearchOff` was absent). We
 * use [NubecitaIconName.Inbox] until the subset gains `PersonOff`.
 */
@Composable
internal fun PeopleEmptyBody(
    currentQuery: String,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            // `PersonOff` is absent from the icon-font subset; `Inbox` is the
            // documented fallback (matches vrba.6's PostsEmptyBody choice).
            name = NubecitaIconName.Inbox,
            contentDescription = stringResource(R.string.search_people_empty_icon_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.search_people_empty_title, currentQuery),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_people_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onClearQuery) {
            Text(stringResource(R.string.search_people_empty_clear))
        }
    }
}

private val ICON_SIZE = 96.dp

@Preview(name = "PeopleEmptyBody — light", showBackground = true)
@Composable
private fun PeopleEmptyBodyLightPreview() {
    NubecitaTheme {
        PeopleEmptyBody(currentQuery = "alice", onClearQuery = {})
    }
}

@Preview(
    name = "PeopleEmptyBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleEmptyBodyDarkPreview() {
    NubecitaTheme {
        PeopleEmptyBody(currentQuery = "xyzqq", onClearQuery = {})
    }
}
