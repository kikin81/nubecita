package net.kikin.nubecita.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.theme.NubecitaTheme
import net.kikin.nubecita.ui.mvi.Async

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        data = state.data,
        onRefresh = { viewModel.handleEvent(MainScreenEvent.Refresh) },
        modifier = modifier,
    )
}

@Composable
internal fun MainScreen(
    data: Async<ImmutableList<String>>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (data) {
            Async.Uninitialized, Async.Loading -> {
                // Blank
            }
            is Async.Success -> data.value.forEach { Greeting(it) }
            is Async.Failure -> Text("Error loading data: ${data.error.message}")
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
private fun MainScreenPreview() {
    NubecitaTheme {
        MainScreen(
            data = Async.Success(persistentListOf("Android")),
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
private fun MainScreenPortraitPreview() {
    NubecitaTheme {
        MainScreen(
            data = Async.Success(persistentListOf("Android")),
            onRefresh = {},
        )
    }
}
