package net.kikin.nubecita.feature.notifications.impl.di

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
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
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.notifications.impl.NotificationsScreen
import net.kikin.nubecita.feature.notifications.impl.findSelectedPostUri
import net.kikin.nubecita.feature.notifications.impl.findSelectedProfileHandle

@Module
@InstallIn(SingletonComponent::class)
internal object NotificationsNavigationModule {
    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Provides
    @IntoSet
    @MainShell
    fun provideNotificationsEntries(): EntryProviderInstaller =
        {
            entry<NotificationsTab>(
                metadata =
                    ListDetailSceneStrategy.listPane(
                        detailPlaceholder = {
                            DetailPaneEmptyState(
                                icon = NubecitaIconName.Notifications,
                                message = stringResource(R.string.nubecita_detail_pane_select_notification),
                            )
                        },
                    ),
            ) {
                val navState = LocalMainShellNavState.current
                val selectedPostUri = findSelectedPostUri(navState.backStack)
                val selectedProfileHandle = findSelectedProfileHandle(navState.backStack)
                NotificationsScreen(
                    selectedPostUri = selectedPostUri,
                    selectedProfileHandle = selectedProfileHandle,
                )
            }
        }
}
