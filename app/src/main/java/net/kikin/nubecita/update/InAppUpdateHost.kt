package net.kikin.nubecita.update

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.R
import net.kikin.nubecita.core.update.InAppUpdateController
import net.kikin.nubecita.core.update.UpdateState

/**
 * Activity-level host for the FLEXIBLE "update downloaded → restart" snackbar.
 * Sits above the nav (in MainActivity's outer Surface) so it persists across
 * screen changes. IMMEDIATE renders its own full-screen Play UI — not here.
 */
@Composable
fun InAppUpdateHost(
    controller: InAppUpdateController,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val isReadyToInstall = state is UpdateState.ReadyToInstall
    val hostState = remember { SnackbarHostState() }
    val message = stringResource(R.string.in_app_update_downloaded)
    val action = stringResource(R.string.in_app_update_restart)

    LaunchedEffect(isReadyToInstall) {
        if (isReadyToInstall) {
            val result =
                hostState.showSnackbar(
                    message = message,
                    actionLabel = action,
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed) {
                controller.completeFlexibleUpdate()
            }
        }
    }
    SnackbarHost(hostState, modifier = modifier.navigationBarsPadding())
}
