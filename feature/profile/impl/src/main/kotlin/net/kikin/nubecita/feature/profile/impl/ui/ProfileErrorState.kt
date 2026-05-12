package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Tab-level inline error with a Retry button. Used when
 * `TabLoadStatus.InitialError(error)`. Header-level errors render a
 * separate variant inside ProfileHero (different layout — the hero
 * is wider and uses a card-tinted background).
 */
@Composable
internal fun ProfileErrorState(
    error: ProfileError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_title
            is ProfileError.Unknown -> R.string.profile_error_unknown_title
        }
    val bodyRes =
        when (error) {
            ProfileError.Network -> R.string.profile_error_network_body
            is ProfileError.Unknown -> R.string.profile_error_unknown_body
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(text = stringResource(R.string.profile_error_retry))
        }
    }
}
