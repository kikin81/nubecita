package net.kikin.nubecita.feature.composer.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.ComposerSubmitEvent
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEvents
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.ComposerScreen
import net.kikin.nubecita.feature.composer.impl.ComposerViewModel

/**
 * `@MainShell`-qualified [EntryProviderInstaller] that registers
 * [ComposerRoute] inside `MainShell`'s inner `NavDisplay`.
 *
 * Per the spec's *Composer registers as an `@MainShell` Nav3 entry
 * for Compact-width hosting* requirement: this entry is the
 * Compact-width hosting path. At Medium/Expanded widths the composer
 * is overlaid as a `Dialog` via the `MainShell`-scoped composer
 * launcher (introduced in `nubecita-wtq.7`); the entry registered
 * here is unused on those widths.
 *
 * The entry block:
 *
 * 1. Reads the per-NavEntry [ComposerViewModel] via the assisted-
 *    inject Hilt bridge. The `creationCallback` hands the route
 *    instance to `ComposerViewModel.Factory.create(route)` so the VM
 *    sees a typed [ComposerRoute] (mirrors the
 *    `:feature:postdetail:impl` `PostDetailNavigationModule`
 *    precedent).
 *
 * 2. Wires the screen's nav callbacks to `LocalMainShellNavState`'s
 *    `removeLast` mutator per the MVI-effect convention — the VM
 *    MUST NOT inject the nav state holder, so this entry is the
 *    seam where `ComposerEffect.NavigateBack` and
 *    `ComposerEffect.OnSubmitSuccess` translate into a
 *    NavDisplay-side pop.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ComposerNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideComposerEntries(): EntryProviderInstaller =
        {
            entry<ComposerRoute> { route ->
                val navState = LocalMainShellNavState.current
                val composerSubmitEvents = LocalComposerSubmitEvents.current
                val viewModel =
                    hiltViewModel<ComposerViewModel, ComposerViewModel.Factory>(
                        creationCallback = { factory -> factory.create(route) },
                    )
                ComposerScreen(
                    onNavigateBack = { navState.removeLast() },
                    // Submit-success emits a `ComposerSubmitEvent` on the
                    // shell-scoped bus (used by the feed for the success
                    // snackbar + optimistic `replyCount + 1` on the parent
                    // post — see `LocalComposerSubmitEvents`) and pops the
                    // composer route. The feed picks the new post up on
                    // its next refresh.
                    onSubmitSuccess = { newPostUri, replyToUri ->
                        composerSubmitEvents.emit(
                            ComposerSubmitEvent(
                                newPostUri = newPostUri.raw,
                                replyToUri = replyToUri,
                            ),
                        )
                        navState.removeLast()
                    },
                    viewModel = viewModel,
                )
            }
        }
}
