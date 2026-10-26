package net.kikin.nubecita.wear

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import net.kikin.nubecita.wear.home.HomeScreen
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

/**
 * Root of the Wear app: the single [AppScaffold] (it owns `TimeText` and the
 * shared scaffold state every screen's `ScreenScaffold` plugs into) around a
 * Navigation 3 back stack. Swipe-to-dismiss pops the stack.
 */
@Composable
fun NubecitaWearApp() {
    NubecitaWearTheme {
        AppScaffold {
            val backStack = rememberNavBackStack(WearHome)
            NavDisplay(
                backStack = backStack,
                sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                entryProvider =
                    entryProvider {
                        entry<WearHome> { HomeScreen() }
                    },
            )
        }
    }
}
