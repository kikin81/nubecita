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
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.api.Settings
import net.kikin.nubecita.feature.search.api.Search

/**
 * Provides `@MainShell`-qualified placeholder entries for the three
 * top-level destinations whose `:feature:*:impl` modules don't exist
 * yet (Search, Chats, You) plus the You tab's `Settings` sub-route.
 *
 * Each entry renders a [PlaceholderScreen] labelled "<Destination> —
 * coming soon". When a destination's real `:impl` module ships (under
 * its own feature epic), the corresponding `@Provides` here is removed
 * and the new module's `@MainShell EntryProviderInstaller` takes over —
 * a clean module boundary, no bridging artifacts.
 *
 * `:feature:feed:impl` already provides the Feed entry under
 * `@MainShell` (after migration in this change), so Feed is not
 * placeholdered here.
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

    @Provides
    @IntoSet
    @MainShell
    fun provideChatsPlaceholderEntries(): EntryProviderInstaller =
        {
            entry<Chats> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_tab_chats))
            }
        }

    @Provides
    @IntoSet
    @MainShell
    fun provideProfilePlaceholderEntries(): EntryProviderInstaller =
        {
            entry<Profile> { _ ->
                // Both `Profile(null)` (You-tab home) and `Profile(handle = "alice")`
                // (cross-tab navigation push) render the same placeholder. The real
                // :feature:profile:impl will use the handle param to load the right
                // profile.
                PlaceholderScreen(label = stringResource(R.string.main_shell_tab_you))
            }
            entry<Settings> {
                PlaceholderScreen(label = stringResource(R.string.main_shell_settings))
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
