package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import net.kikin.nubecita.feature.search.impl.SearchActorsError

/**
 * Full-screen retry layout for the People tab's [SearchActorsError]
 * variants. Mirrors `PostsInitialErrorBody`. Maps each error variant
 * to a title/body stringResource pair and a single "Try again"
 * outlined button.
 */
@Composable
internal fun PeopleInitialErrorBody(
    error: SearchActorsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val (titleRes, bodyRes) =
        when (error) {
            SearchActorsError.Network ->
                R.string.search_people_error_network_title to R.string.search_people_error_network_body
            SearchActorsError.RateLimited ->
                R.string.search_people_error_rate_limited_title to R.string.search_people_error_rate_limited_body
            is SearchActorsError.Unknown ->
                R.string.search_people_error_unknown_title to R.string.search_people_error_unknown_body
        }
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
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.search_people_error_retry))
        }
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "PeopleInitialErrorBody — Network (light)", showBackground = true)
@Composable
private fun PeopleInitialErrorBodyNetworkPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(error = SearchActorsError.Network, onRetry = {})
    }
}

@Preview(
    name = "PeopleInitialErrorBody — RateLimited (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleInitialErrorBodyRateLimitedDarkPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(error = SearchActorsError.RateLimited, onRetry = {})
    }
}

@Preview(name = "PeopleInitialErrorBody — Unknown (light)", showBackground = true)
@Composable
private fun PeopleInitialErrorBodyUnknownPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(
            error = SearchActorsError.Unknown(cause = "Decode failure"),
            onRetry = {},
        )
    }
}
