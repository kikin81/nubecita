package net.kikin.nubecita.feature.login.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.NubecitaTheme

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}

@Composable
internal fun LoginScreen(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in to Bluesky",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Enter your handle to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.handle,
            onValueChange = { onEvent(LoginEvent.HandleChanged(it)) },
            label = { Text("Handle") },
            placeholder = { Text("alice.bsky.social") },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.errorMessage != null,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { onEvent(LoginEvent.SubmitLogin) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.height(20.dp),
                )
            } else {
                Text("Sign in with Bluesky")
            }
        }
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun LoginScreenEmptyPreview() {
    NubecitaTheme {
        LoginScreen(state = LoginState(), onEvent = {})
    }
}

@Preview(name = "Typed", showBackground = true)
@Composable
private fun LoginScreenTypedPreview() {
    NubecitaTheme {
        LoginScreen(state = LoginState(handle = "alice.bsky.social"), onEvent = {})
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun LoginScreenLoadingPreview() {
    NubecitaTheme {
        LoginScreen(state = LoginState(handle = "alice.bsky.social", isLoading = true), onEvent = {})
    }
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    NubecitaTheme {
        LoginScreen(
            state = LoginState(handle = "alice", errorMessage = "Handle could not be resolved."),
            onEvent = {},
        )
    }
}
