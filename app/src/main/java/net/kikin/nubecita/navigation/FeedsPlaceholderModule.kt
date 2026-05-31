package net.kikin.nubecita.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.feeds.api.Feeds

/**
 * `:app`-side placeholder for the deferred Feeds-management screen.
 *
 * Per the `:api`-first stub convention, `:feature:feeds:api` ships only the
 * `Feeds` `NavKey`; the full `:feature:feeds:impl` lands later in its own
 * epic. Until then `:app` registers this `@MainShell`
 * `EntryProviderInstaller` so the Feed chip row's trailing button
 * (`LocalMainShellNavState.current.add(Feeds)`) resolves to a "coming soon"
 * placeholder on `MainShell`'s inner back stack.
 *
 * When `:feature:feeds:impl` arrives, delete this module and add the impl
 * module's own `@MainShell` provider — no bridging artifacts.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object FeedsPlaceholderModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideFeedsPlaceholderEntry(): EntryProviderInstaller =
        {
            entry<Feeds> {
                FeedsPlaceholderScreen()
            }
        }
}

@Composable
private fun FeedsPlaceholderScreen() {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Manage feeds — coming soon",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
