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
import net.kikin.nubecita.feature.search.impl.SearchFeedsError

/**
 * Full-screen retry layout for the Feeds tab's [SearchFeedsError]
 * variants. Mirrors [PeopleInitialErrorBody].
 */
@Composable
internal fun FeedsInitialErrorBody(
    error: SearchFeedsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val (titleRes, bodyRes) =
        when (error) {
            SearchFeedsError.Network ->
                R.string.search_feeds_error_network_title to R.string.search_feeds_error_network_body
            SearchFeedsError.RateLimited ->
                R.string.search_feeds_error_rate_limited_title to R.string.search_feeds_error_rate_limited_body
            is SearchFeedsError.Unknown ->
                R.string.search_feeds_error_unknown_title to R.string.search_feeds_error_unknown_body
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
            Text(stringResource(R.string.search_feeds_error_retry))
        }
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "FeedsInitialErrorBody — Network (light)", showBackground = true)
@Composable
private fun FeedsInitialErrorBodyNetworkPreview() {
    NubecitaTheme {
        FeedsInitialErrorBody(error = SearchFeedsError.Network, onRetry = {})
    }
}

@Preview(
    name = "FeedsInitialErrorBody — RateLimited (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedsInitialErrorBodyRateLimitedDarkPreview() {
    NubecitaTheme {
        FeedsInitialErrorBody(error = SearchFeedsError.RateLimited, onRetry = {})
    }
}

@Preview(name = "FeedsInitialErrorBody — Unknown (light)", showBackground = true)
@Composable
private fun FeedsInitialErrorBodyUnknownPreview() {
    NubecitaTheme {
        FeedsInitialErrorBody(
            error = SearchFeedsError.Unknown(cause = "Decode failure"),
            onRetry = {},
        )
    }
}
