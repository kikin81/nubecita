package net.kikin.nubecita

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import net.kikin.nubecita.ui.main.MainScreen

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Main> {
                    MainScreen(modifier = Modifier.safeDrawingPadding())
                }
            },
    )
}
