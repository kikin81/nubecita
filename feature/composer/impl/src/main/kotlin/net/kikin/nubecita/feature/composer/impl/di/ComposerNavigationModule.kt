package net.kikin.nubecita.feature.composer.impl.di

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.ComposerSubmitEvent
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEventsEmitter
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.core.common.navigation.adaptiveDialog
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.ComposerScreen
import net.kikin.nubecita.feature.composer.impl.ComposerViewModel

/**
 * `@MainShell`-qualified [EntryProviderInstaller] that registers
 * [ComposerRoute] inside `MainShell`'s inner `NavDisplay`.
 *
 * Tagged with `adaptiveDialog()` metadata, so the `AdaptiveDialogSceneStrategy`
 * (in `:app`) presents this **same** entry full-screen at Compact width and as
 * a centered `Dialog` at Medium/Expanded — no separate overlay/launcher. Callers
 * just `navState.add(ComposerRoute(...))` at any width (see the adaptive-layout
 * convention in CLAUDE.md / `docs/adaptive-layouts.md`).
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
            entry<ComposerRoute>(metadata = adaptiveDialog()) { route ->
                val navState = LocalMainShellNavState.current
                val composerSubmitEventsEmitter = LocalComposerSubmitEventsEmitter.current
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
                        composerSubmitEventsEmitter.emit(
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
