package net.kikin.nubecita.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import net.kikin.nubecita.wear.gate.WearGate
import net.kikin.nubecita.wear.gate.WearGateEvent
import net.kikin.nubecita.wear.gate.WearGateViewModel
import net.kikin.nubecita.wear.home.HomeScreen
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.ui.WearTimeText
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

/**
 * Root of the Wear app: the single [AppScaffold] (it owns `TimeText` and the
 * shared scaffold state every screen's `ScreenScaffold` plugs into), the
 * [WearGate] that explains a missing phone app or sign-in, and — once signed
 * in — a Navigation 3 back stack where swipe-to-dismiss pops.
 */
@Composable
fun NubecitaWearApp(viewModel: WearGateViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.handleEvent(WearGateEvent.Resumed)
        onPauseOrDispose {}
    }
    NubecitaWearTheme {
        AppScaffold(timeText = { WearTimeText() }) {
            WearGate(state = state, onEvent = viewModel::handleEvent) { handle ->
                val backStack = rememberNavBackStack(WearHome)
                NavDisplay(
                    backStack = backStack,
                    sceneStrategies = listOf(rememberSwipeDismissableSceneStrategy()),
                    entryProvider =
                        entryProvider {
                            entry<WearHome> { HomeScreen(handle = handle) }
                        },
                )
            }
        }
    }
}
