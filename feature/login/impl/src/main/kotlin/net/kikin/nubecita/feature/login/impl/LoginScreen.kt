package net.kikin.nubecita.feature.login.impl

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import net.kikin.nubecita.core.common.navigation.NavigatorEntryPoint
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaPrimaryButton
import net.kikin.nubecita.designsystem.spacing

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator =
        remember(context) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, NavigatorEntryPoint::class.java)
                .navigator()
        }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.LaunchCustomTab ->
                    CustomTabsIntent
                        .Builder()
                        .build()
                        .launchUrl(context, effect.url.toUri())
                LoginEffect.LoginSucceeded -> navigator.goBack()
            }
        }
    }

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
    val errorText = state.errorMessage?.let { displayStringFor(it) }
    val focusManager = LocalFocusManager.current

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
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.s2))

        OutlinedTextField(
            value = state.handle,
            onValueChange = { onEvent(LoginEvent.HandleChanged(it)) },
            label = { Text(stringResource(R.string.login_handle_label)) },
            placeholder = { Text(stringResource(R.string.login_handle_placeholder)) },
            singleLine = true,
            enabled = !state.isLoading,
            isError = errorText != null,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        onEvent(LoginEvent.SubmitLogin)
                    },
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(visible = errorText != null) {
            errorText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        NubecitaPrimaryButton(
            onClick = { onEvent(LoginEvent.SubmitLogin) },
            text = stringResource(R.string.login_submit),
            isLoading = state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun displayStringFor(error: LoginError): String =
    when (error) {
        LoginError.BlankHandle -> stringResource(R.string.login_error_blank_handle)
        is LoginError.Failure ->
            error.cause?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.login_error_generic_failure)
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

@Preview(name = "Blank-handle error", showBackground = true)
@Composable
private fun LoginScreenBlankErrorPreview() {
    NubecitaTheme {
        LoginScreen(
            state = LoginState(handle = "", errorMessage = LoginError.BlankHandle),
            onEvent = {},
        )
    }
}

@Preview(name = "Failure error", showBackground = true)
@Composable
private fun LoginScreenFailureErrorPreview() {
    NubecitaTheme {
        LoginScreen(
            state =
                LoginState(
                    handle = "alice",
                    errorMessage = LoginError.Failure("Handle could not be resolved."),
                ),
            onEvent = {},
        )
    }
}
