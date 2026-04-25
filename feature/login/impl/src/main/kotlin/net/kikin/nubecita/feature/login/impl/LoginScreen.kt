package net.kikin.nubecita.feature.login.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import net.kikin.nubecita.designsystem.spacing

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoginScreen(
    state: LoginState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement =
            Arrangement.spacedBy(
                MaterialTheme.spacing.s4,
                Alignment.CenterVertically,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Sign in to Bluesky",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Enter your handle to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.s2))

        OutlinedTextField(
            value = state.handle,
            onValueChange = { onEvent(LoginEvent.HandleChanged(it)) },
            label = { Text("Handle") },
            placeholder = { Text("alice.bsky.social") },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.errorMessage != null,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(visible = state.errorMessage != null) {
            state.errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = { onEvent(LoginEvent.SubmitLogin) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(20.dp),
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
