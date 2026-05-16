package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.R

/**
 * Primary CTA rendered at the top of the typeahead screen (vrba.10):
 * a tappable row with primary-container background, a leading search
 * icon, and the localized label "Search for "{query}"".
 *
 * Stateless. Tap dispatches [onClick] (the typeahead screen forwards
 * to a parent callback that submits the current query — same code
 * path as [net.kikin.nubecita.feature.search.impl.SearchEvent.SubmitClicked]).
 *
 * `internal` so previews + screenshot tests can render the row
 * without a Hilt graph.
 */
@Composable
internal fun SearchForCtaButton(
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Search,
                contentDescription = null,
            )
            Text(
                text = stringResource(R.string.search_typeahead_search_for_cta, query),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(name = "SearchForCtaButton — short query", showBackground = true)
@Composable
private fun SearchForCtaButtonShortPreview() {
    NubecitaTheme {
        SearchForCtaButton(query = "alice", onClick = {})
    }
}

@Preview(name = "SearchForCtaButton — long query (truncates)", showBackground = true)
@Composable
private fun SearchForCtaButtonLongPreview() {
    NubecitaTheme {
        SearchForCtaButton(
            query = "a really long search query that should ellipsize",
            onClick = {},
        )
    }
}

@Preview(
    name = "SearchForCtaButton — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchForCtaButtonDarkPreview() {
    NubecitaTheme {
        SearchForCtaButton(query = "compose", onClick = {})
    }
}
