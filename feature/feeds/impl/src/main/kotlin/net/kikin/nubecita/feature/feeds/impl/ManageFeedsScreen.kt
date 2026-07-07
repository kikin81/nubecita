package net.kikin.nubecita.feature.feeds.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Pinned-feeds management screen — `@MainShell` sub-route for the
 * `Feeds` `NavKey`. Scaffolded in nubecita-ydfn.1; the pinned-feeds
 * list, drag-to-reorder, and remove affordances land in nubecita-ydfn.4
 * (ViewModel wiring) atop the design in
 * `docs/superpowers/specs/2026-07-05-manage-pinned-feeds-design.md`.
 *
 * Full-screen on all widths for now (a plain inner sub-route); an
 * `adaptiveDialog()` presentation can be layered on in the UI task if
 * desired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageFeedsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feeds_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.feeds_manage_back_content_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // Placeholder body — the pinned-feeds list is added in nubecita-ydfn.4.
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {}
    }
}
