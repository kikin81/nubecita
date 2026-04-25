package net.kikin.nubecita

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.EntryPointAccessors
import net.kikin.nubecita.navigation.NavigationEntryPoint
import net.kikin.nubecita.ui.main.MainScreen

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint =
        remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, NavigationEntryPoint::class.java)
        }
    val navigator = remember(entryPoint) { entryPoint.navigator() }
    val installers = remember(entryPoint) { entryPoint.entryProviderInstallers() }

    NavDisplay(
        modifier = modifier,
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
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
