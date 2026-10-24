package net.kikin.nubecita.feature.search.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.component.DetailPaneEmptyState
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.api.Hashtag
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.search.impl.HashtagScreen
import net.kikin.nubecita.feature.search.impl.HashtagViewModel
import net.kikin.nubecita.feature.search.impl.SearchScreen

/**
 * Registers the Search tab-home entry on the `@MainShell`-qualified
 * `EntryProviderInstaller` multibinding so `MainShell` can resolve the
 * `Search` NavKey to a real Composable.
 *
 * The entry is tagged as the **list pane** of `MainShell`'s inner
 * `NavDisplay` `ListDetailSceneStrategy` (mirrors Feed / Chats): on Compact
 * width the strategy collapses to single-pane and the placeholder is never
 * composed; on Medium/Expanded the search results fill the list pane and the
 * placeholder fills the detail pane until a `detailPane()`-tagged entry
 * (`PostDetailRoute`) is pushed by a result tap. Without this anchor a pushed
 * `detailPane()` entry would orphan full-screen on tablets (nubecita-h5zd.2).
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SearchNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideSearchEntries(): EntryProviderInstaller =
        {
            entry<Search>(
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            DetailPaneEmptyState(
                                icon = NubecitaIconName.Article,
                                message = stringResource(R.string.nubecita_detail_pane_select_post),
                            )
                        },
                    ),
            ) {
                SearchScreen()
            }
            // Hashtag is a sub-route pushed from any post-text host, so — like
            // Profile(handle = …) — it stacks in the DETAIL pane rather than
            // re-anchoring the list pane (which would evict e.g. the Feed on
            // Medium/Expanded). Its post taps stack above it in the same pane.
            entry<Hashtag>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                val navState = LocalMainShellNavState.current
                val viewModel =
                    hiltViewModel<HashtagViewModel, HashtagViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                HashtagScreen(viewModel = viewModel, onBack = { navState.removeLast() })
            }
        }
}
