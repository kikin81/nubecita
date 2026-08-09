package net.kikin.nubecita.feature.login.impl

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaActorRow
import net.kikin.nubecita.designsystem.component.NubecitaActorRowDensity
import net.kikin.nubecita.designsystem.component.NubecitaPrimaryButton
import net.kikin.nubecita.designsystem.spacing
import timber.log.Timber

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // rememberUpdatedState so the long-lived, viewModel-keyed LaunchedEffect below
    // always reads the current context without restarting the effect. (In practice
    // the Activity context is stable for the composition's lifetime — a config
    // change recreates the Activity and this effect — so this is defensive/idiomatic
    // rather than a live-bug fix.)
    val context by rememberUpdatedState(LocalContext.current)

    // The result is intentionally ignored: the prompt-shown gate is flipped
    // by the VM before the launcher fires, so denial doesn't loop the prompt,
    // and grant is observable system-wide (the FCM service can post on the
    // next push). No state mutation is needed here.
    val postNotificationsPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.LaunchCustomTab ->
                    try {
                        CustomTabsIntent
                            .Builder()
                            .build()
                            .launchUrl(context, effect.url.toUri())
                    } catch (notFound: ActivityNotFoundException) {
                        // No Activity handles the VIEW intent — no browser / Custom Tabs
                        // provider installed or enabled. Surface a recoverable error via
                        // the VM instead of crashing the app (nubecita-ywme).
                        Timber.tag("LoginScreen").w(notFound, "No browser to launch OAuth/signup URL")
                        viewModel.handleEvent(LoginEvent.CustomTabLaunchFailed)
                    }
                // Post-login routing is owned by MainActivity's reactive observer of
                // SessionStateProvider.state — once completeLogin succeeds and the state
                // transitions to SignedIn, MainActivity calls navigator.replaceTo(Main).
                // The only screen-side action is the POST_NOTIFICATIONS launcher, gated
                // by the VM's NotificationsPromptDecider (Android 13+, first sign-in on
                // this install). When the gate is false, this branch is a no-op.
                is LoginEffect.LoginSucceeded ->
                    if (effect.requestPostNotificationsPermission) {
                        postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
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

    // Scaffold + WindowInsets.safeDrawing (which includes IME) is the canonical
    // edge-to-edge pattern for a screen with a TextField — keeps the handle
    // field visible when the soft keyboard opens AND keeps title/submit clear
    // of status / gesture bars without an opaque scrim. consumeWindowInsets
    // prevents a future nested scrollable from double-insetting.
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        // Cap the form column to a comfortable single-column width on
        // tablets / unfolded foldables — without this, the OutlinedTextField
        // and primary CTA stretch edge-to-edge on Expanded width-class
        // devices and the form reads like an oversized banner. 480dp is the
        // canonical max-form-column width.
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            // The form scrolls. It previously filled the viewport exactly and
            // handed the suggestion list whatever height was left over
            // (`weight(1f, fill = false)`), which is nothing once the IME is
            // open on a short screen — the list measured to ~0dp and the
            // typeahead looked broken (nubecita-kfxi). Counter-intuitively the
            // UNFOLDED inner display is the worst case: it is landscape, so at
            // 1840px it is *shorter* than the 2092px folded outer display.
            //
            // `heightIn(min = maxHeight)` keeps the un-scrolled screen
            // pixel-identical — the column still fills the viewport, so
            // `Alignment.CenterVertically` still centres a short form. Once the
            // content genuinely exceeds the viewport it scrolls instead of
            // starving whichever child holds the weight.
            val viewportHeight = maxHeight
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
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
                // The title block yields the screen to the results while the user
                // is choosing an account. It is worth about three rows, and with
                // the keyboard up there is only half a screen to divide — the
                // heading orients someone arriving at the screen, not someone
                // already mid-search. It returns as soon as the list closes.
                AnimatedVisibility(visible = state.suggestions.isEmpty()) {
                    // Same spacing the outer Column applied to these children before
                    // they were grouped, so the un-collapsed screen is unchanged.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4),
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
                    }
                }

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
                    // Autofill hint so password managers (1Password, Google
                    // Autofill) recognise this as the account identifier and
                    // offer the saved Bluesky handle. Username + EmailAddress
                    // because handles (you.bsky.social) are stored either way
                    // across managers. The password isn't entered here — it's
                    // entered in the OAuth browser (Custom Tab to the PDS) — so
                    // there's no in-app password field to hint.
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                )

                // Suggestions sit BELOW the field as a sibling — deliberately not
                // an ExposedDropdownMenuBox, which wraps the field and rewrites its
                // semantics. That would break LoginHandleAutofillTest, which asserts
                // the field's ContentType and finds it via hasSetTextAction() on the
                // assumption there is exactly one editable field. A plain sibling
                // leaves the field untouched, and suggestion rows are not editable.
                // No weight() here, and it could not work if there were: inside a
                // vertically scrollable Column children are measured against an
                // unbounded max height, so there is no "remaining space" for a
                // weight to divide. The list takes its natural height (at most
                // SUGGESTION_LIMIT rows) and the page scrolls if that overflows,
                // so it can never be starved to 0dp the way the weighted version
                // was with the IME open (nubecita-kfxi).
                LoginSuggestions(
                    suggestions = state.suggestions,
                    query = state.handle,
                    onSelect = { onEvent(LoginEvent.SuggestionSelected(it)) },
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

                // The sign-up prompt is for someone who does NOT have an account.
                // The moment a handle is being typed that no longer describes the
                // user, so the block yields the screen the same way the title
                // above it does — leaving just the field, the results and the
                // sign-in button while they are logging in. On a short screen
                // with the IME open those two rows are the difference between a
                // usable suggestion list and a cramped one (nubecita-kfxi).
                //
                // It comes BACK on an error, because that is exactly when it is
                // needed again: `login_error_handle_not_found` reads "…or create
                // a new Bluesky account below" and would otherwise point at a
                // block this screen had just hidden.
                AnimatedVisibility(visible = state.handle.isBlank() || state.errorMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4),
                    ) {
                        Spacer(Modifier.height(MaterialTheme.spacing.s2))

                        Text(
                            text = stringResource(R.string.login_signup_cta_supporting),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { onEvent(LoginEvent.OpenSignup) },
                            enabled = !state.isLoading,
                        ) {
                            Text(stringResource(R.string.login_signup_cta_label))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun displayStringFor(error: LoginError): String =
    when (error) {
        LoginError.BlankHandle -> stringResource(R.string.login_error_blank_handle)
        is LoginError.HandleNotFound ->
            stringResource(R.string.login_error_handle_not_found, error.handle)
        LoginError.Network -> stringResource(R.string.login_error_network)
        LoginError.Generic -> stringResource(R.string.login_error_generic_failure)
        LoginError.BrowserUnavailable -> stringResource(R.string.login_error_no_browser)
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

@Preview(name = "Handle-not-found error", showBackground = true)
@Composable
private fun LoginScreenHandleNotFoundPreview() {
    NubecitaTheme {
        LoginScreen(
            state =
                LoginState(
                    handle = "alise.bsky.social",
                    errorMessage = LoginError.HandleNotFound("alise.bsky.social"),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "Network error", showBackground = true)
@Composable
private fun LoginScreenNetworkErrorPreview() {
    NubecitaTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", errorMessage = LoginError.Network),
            onEvent = {},
        )
    }
}

@Preview(name = "Generic error", showBackground = true)
@Composable
private fun LoginScreenGenericErrorPreview() {
    NubecitaTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", errorMessage = LoginError.Generic),
            onEvent = {},
        )
    }
}

/**
 * Account suggestions for what has been typed so far. Hidden entirely when
 * empty — including while a query is in flight — so the list does not flicker
 * in and out on every keystroke.
 */
@Composable
private fun LoginSuggestions(
    suggestions: ImmutableList<HandleSuggestion>,
    query: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Highlight what the user actually typed, normalized the same way the query
    // to the AppView was — otherwise a leading "@" would match nothing.
    val match = query.trim().removePrefix("@")
    AnimatedVisibility(visible = suggestions.isNotEmpty(), modifier = modifier) {
        OutlinedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // Deliberately NOT verticalScroll here. The form column above now
            // scrolls, and nesting a second vertical scroller inside it makes
            // the drag ambiguous — the inner one swallows the gesture and the
            // page below it becomes hard to reach. At SUGGESTION_LIMIT rows the
            // list is short enough that the page scroll covers it.
            Column {
                suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider()
                    LoginSuggestionRow(
                        suggestion = suggestion,
                        query = match,
                        onClick = { onSelect(suggestion.handle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginSuggestionRow(
    suggestion: HandleSuggestion,
    query: String,
    onClick: () -> Unit,
) {
    NubecitaActorRow(
        actor = suggestion.actor,
        onClick = onClick,
        query = query,
        // Pre-login rows are mostly strangers to the user, so an initial beats an
        // empty circle for telling two similar handles apart.
        showAvatarFallback = true,
        // The list shares the screen with the keyboard; comfortable density fits
        // only two accounts before scrolling.
        density = NubecitaActorRowDensity.Compact,
    ) {
        // Resolved after the row is already on screen, so its absence is the
        // normal first state rather than an error.
        suggestion.pdsHost?.let { host ->
            Text(
                text = networkLabelFor(host),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Bluesky-operated PDS hosts are internal shard names — shiitake, morel,
 * russula — that mean nothing to a user and differ arbitrarily between accounts
 * on the same service. Collapse them to one label; show a real host only when
 * it is genuinely somewhere else, which is the only case the line informs.
 */
@Composable
private fun networkLabelFor(host: String): String =
    if (host == "bsky.social" || host == "host.bsky.network" || host.endsWith(".host.bsky.network")) {
        stringResource(R.string.login_suggestion_network_bluesky)
    } else {
        host
    }
