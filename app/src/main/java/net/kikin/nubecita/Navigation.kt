package net.kikin.nubecita

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.EntryPointAccessors
import net.kikin.nubecita.navigation.NavigationEntryPoint
import net.kikin.nubecita.ui.main.MainScreen

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Main)
    val context = LocalContext.current
    val installers =
        remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, NavigationEntryPoint::class.java)
                .entryProviderInstallers()
        }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        // SceneSetupNavEntryDecorator is internal in nav3-ui 1.1.1 — NavDisplay applies
        // it itself. We supply the two decorators that are public + required for
        // hiltViewModel() inside NavEntries to work and for state to survive
        // recomposition / configuration change.
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Main> {
                    MainScreen(modifier = Modifier.safeDrawingPadding())
                }
                installers.forEach { installer -> installer() }
            },
    )
}
