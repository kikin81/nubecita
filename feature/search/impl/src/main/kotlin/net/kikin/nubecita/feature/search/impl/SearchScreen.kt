package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Scaffold for the Search tab. Renders a centered title so QA can
 * visually confirm `:feature:search:impl` is wired into `MainShell` and
 * the `:app`-side placeholder has been removed.
 *
 * The full implementation (parent SearchViewModel + TextFieldState
 * input row + recent-search chips + Posts / People tabs) lands across
 * subsequent children of the Search epic
 * (nubecita-vrba.5 → nubecita-vrba.8). When that arrives, the body of
 * this composable is replaced wholesale.
 */
@Composable
internal fun SearchScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.search_screen_scaffold_title))
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    NubecitaTheme {
        SearchScreen()
    }
}
