package net.kikin.nubecita.feature.feed.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.spacing

/**
 * Placeholder Feed screen.
 *
 * The Hilt graph compiles end-to-end against this composable so the
 * `Feed` Nav3 entry resolves a real ViewModel. The real screen
 * (LazyColumn + PullToRefreshBox + scroll-position retention + the
 * preview / screenshot-test trio) ships in nubecita-1d5 — that ticket
 * replaces this file in full.
 *
 * Do NOT add `@Preview` annotations here. The previews + screenshot
 * tests are part of the screen ticket's acceptance criteria, not this
 * scaffolding change.
 */
@Composable
internal fun FeedScreen(
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(MaterialTheme.spacing.s4),
    ) {
        // Render the raw state so we can verify in dev builds that the VM is
        // wired and producing values without leaking a polished UI before
        // nubecita-1d5 lands. `Text(state.toString())` is intentionally
        // unhelpful — the nubecita-1d5 screen replaces this entirely.
        @Suppress("ComposableLambdaParameterPosition", "unused")
        Text(
            text = "FeedState: $state",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
