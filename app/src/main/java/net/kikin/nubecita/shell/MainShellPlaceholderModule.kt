package net.kikin.nubecita.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.R
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.search.api.Search

/**
 * Provides `@MainShell`-qualified placeholder entries for the
 * top-level destinations whose `:feature:*:impl` modules don't exist
 * yet (Search).
 *
 * Each entry renders a [PlaceholderScreen] labelled "<Destination> —
 * coming soon". When a destination's real `:impl` module ships (under
 * its own feature epic), the corresponding `@Provides` here is removed
 * and the new module's `@MainShell EntryProviderInstaller` takes over —
 * a clean module boundary, no bridging artifacts.
 *
 * `:feature:feed:impl` provides the Feed entry,
 * `:feature:profile:impl` provides the Profile + Settings entries, and
 * `:feature:chats:impl` provides the Chats entry — none are placeholdered here.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object MainShellPlaceholderModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideSearchPlaceholderEntries(): EntryProviderInstaller =
        {
            entry<Search> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_tab_search))
            }
        }
}

@Composable
private fun PlaceholderScreen(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.main_shell_placeholder_coming_soon, label))
    }
}
