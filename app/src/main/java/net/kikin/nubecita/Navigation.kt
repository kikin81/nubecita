package net.kikin.nubecita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.EntryPointAccessors
import net.kikin.nubecita.designsystem.component.NubecitaLogomark
import net.kikin.nubecita.navigation.NavigationEntryPoint
import net.kikin.nubecita.shell.MainShell

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint =
        remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, NavigationEntryPoint::class.java)
        }
    val navigator = remember(entryPoint) { entryPoint.navigator() }
    val outerInstallers = remember(entryPoint) { entryPoint.outerEntryProviderInstallers() }

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
                // The system SplashScreen API overlays this surface via
                // setKeepOnScreenCondition while SessionStateProvider is still Loading.
                // Once the bootstrap resolves, MainActivity's reactive collector calls
                // navigator.replaceTo(Main or Login) and Splash leaves the back stack.
                //
                // We render the brand cloud here (rather than an empty Box) so the
                // brief window between the system splash dismissing and the route
                // swap shows brand identity instead of an empty background.
                entry<Splash> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        NubecitaLogomark(modifier = Modifier.size(96.dp))
                    }
                }
                entry<Main> {
                    MainShell(modifier = Modifier.safeDrawingPadding())
                }
                outerInstallers.forEach { installer -> installer() }
            },
    )
}
