package net.kikin.nubecita.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.theme.NubecitaTheme

@Suppress("UnusedParameter")
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainScreenEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        MainScreenContent(
            state = state,
            onRefresh = { viewModel.handleEvent(MainScreenEvent.Refresh) },
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun MainScreenContent(
    state: MainScreenState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            state.items.forEach { Greeting(it) }
        }
        Button(onClick = onRefresh) { Text("Refresh") }
    }
}

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun MainScreenContentPreview() {
    NubecitaTheme {
        MainScreenContent(
            state =
                MainScreenState(
                    items = persistentListOf("Android"),
                    isLoading = false,
                ),
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun MainScreenContentPortraitPreview() {
    NubecitaTheme {
        MainScreenContent(
            state =
                MainScreenState(
                    items = persistentListOf("Android"),
                    isLoading = false,
                ),
            onRefresh = {},
        )
    }
}
