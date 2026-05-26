package net.kikin.nubecita.feature.settings.impl

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.settings.impl.ui.SettingsHeader
import net.kikin.nubecita.feature.settings.impl.ui.SettingsRow
import net.kikin.nubecita.feature.settings.impl.ui.SettingsSection
import net.kikin.nubecita.feature.settings.impl.ui.SwitchAccountRow

/**
 * Stateful Settings screen. Owns the [SettingsViewModel] + effect
 * collector + snackbar host. Delegates rendering to
 * [SettingsContent] which previews and screenshot tests can exercise
 * with fixture inputs.
 *
 * Adaptive shape (spec: "Settings screen adapts shape to window size
 * class"): the screen renders as a full-screen route below the
 * Medium width breakpoint, and as a centered modal with scrim at-or-
 * above Medium. The window-size-class read uses the project-wide
 * pattern from `:designsystem`/`MainShell`/etc. —
 * `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeast
 * Breakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)`.
 *
 * On Sign Out success, the screen unmounts when
 * `SessionStateProvider` transitions and MainActivity replaces to
 * Login — no nav effect required.
 */
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val signOutErrorMsg = stringResource(R.string.settings_signout_error)
    val switchAccountComingSoonMsg =
        stringResource(R.string.settings_switch_account_coming_soon)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Capture the LaunchedEffect's CoroutineScope so each snackbar
        // show runs in its own child job. If we awaited
        // `snackbarHostState.showSnackbar(...)` inline inside the
        // collector, a `currentSnackbarData?.dismiss()` interrupting
        // the suspended showSnackbar would throw CancellationException,
        // propagate out of the `collect { }` lambda, and tear down the
        // entire effects coroutine — every subsequent LaunchUri /
        // snackbar effect would silently never reach the user.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.ShowSignOutError ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(signOutErrorMsg)
                    }
                SettingsEffect.ShowSwitchAccountComingSoon ->
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(switchAccountComingSoonMsg)
                    }
                is SettingsEffect.LaunchUri -> {
                    // Match the project-wide pattern from
                    // :feature:feed:impl/FeedScreen.kt onExternalEmbedTap:
                    // CustomTabsIntent pins to a Chrome Custom Tab when
                    // available (better UX than the system browser handing
                    // back via cold-start), and the narrowed catch swallows
                    // ONLY the documented "no CCT-capable browser installed"
                    // case. Other launch failures propagate so genuine bugs
                    // surface in logcat instead of being hidden by a
                    // blanket catch.
                    try {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, Uri.parse(effect.uri))
                    } catch (_: ActivityNotFoundException) {
                        // No browser available — silent no-op.
                    }
                }
                SettingsEffect.OpenSystemNotificationSettings -> {
                    // Deep-link into the per-app, per-channel system
                    // notification settings. Narrow catch on
                    // ActivityNotFoundException so a genuinely unsupported
                    // OEM (no such system activity) becomes a silent no-op
                    // rather than crashing — other launch failures
                    // propagate to logcat.
                    val intent =
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // No system activity available — silent no-op.
                    }
                }
            }
        }
    }

    val versionLabel = rememberAppVersionLabel()
    val isAtLeastMedium =
        currentWindowAdaptiveInfoV2()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isAtLeastMedium) {
        SettingsModalWrapper(
            onClose = onBack,
            // Gate dismissal during in-flight sign-out so the outer modal
            // matches the inner AlertDialog's guard. Without this, a
            // scrim tap or Back press mid-sign-out tears down the screen,
            // viewModelScope cancels, and a queued ShowSignOutError
            // never reaches the (now-gone) snackbar host.
            isDismissEnabled = state.status !is SettingsStatus.SigningOut,
            snackbarHostState = snackbarHostState,
        ) {
            SettingsContent(
                state = state,
                onEvent = viewModel::handleEvent,
                versionLabel = versionLabel,
            )
        }
    } else {
        SettingsScaffoldWrapper(
            onBack = onBack,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        ) { padding ->
            SettingsContent(
                state = state,
                onEvent = viewModel::handleEvent,
                versionLabel = versionLabel,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * Phone (Compact-width) wrapper — the existing Scaffold + TopAppBar
 * layout. Back arrow lives in the top-leading slot; snackbar host
 * lives in the Scaffold's standard slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffoldWrapper(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription =
                                stringResource(R.string.settings_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content,
    )
}

/**
 * Tablet / foldable / desktop (Medium-or-above width) wrapper —
 * centered modal over a scrim with an X-close affordance in the
 * top-trailing slot. Bounded by 640dp max width and 80% of the
 * available height (the section column inside is wrapped in
 * `Modifier.verticalScroll`, so taller content scrolls inside the
 * modal rather than clipping).
 *
 * Dismissal paths: tap outside the surface (scrim), press back
 * (Dialog's default), or tap the close button.
 */
@Composable
private fun SettingsModalWrapper(
    onClose: () -> Unit,
    isDismissEnabled: Boolean,
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    // 80% is a CAP, not a fixed height. heightIn(max) lets the Surface
    // shrink to natural content height when the section roster is
    // short — important on wide tablet windows where forcing 80%
    // would leave a large empty modal. When content exceeds the cap,
    // the inner verticalScroll inside SettingsContent provides
    // scrolling within the bound.
    val configuration = LocalConfiguration.current
    val maxHeightDp = (configuration.screenHeightDp * 0.80f).dp

    Dialog(
        onDismissRequest = { if (isDismissEnabled) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(fraction = 0.92f)
                    .heightIn(max = maxHeightDp)
                    .wrapContentHeight(),
        ) {
            Box {
                Column {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = onClose, enabled = isDismissEnabled) {
                            NubecitaIcon(
                                name = NubecitaIconName.Close,
                                contentDescription =
                                    stringResource(R.string.settings_close_content_description),
                            )
                        }
                    }
                    content()
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsViewState,
    onEvent: (SettingsEvent) -> Unit,
    versionLabel: String,
    modifier: Modifier = Modifier,
) {
    // Header values are session-derived in SettingsViewModel.init: the
    // handle arrives via filterIsInstance<SignedIn>().take(1) on the
    // session flow; displayName + avatarUrl arrive after a
    // ActorProfileRepository.fetchProfile round-trip (silent failure
    // → null → header falls back to "Hi!" + initials disc). The handle
    // should always become non-null inside MainShell since the outer
    // Navigator gates entry on SignedIn; the empty-string fallback is
    // defensive and would only render in the one-frame window before
    // the flow's first emission lands.
    val handle = state.handle.orEmpty()
    val displayName = state.displayName
    val avatarUrl = state.avatarUrl

    // Stabilize onEvent across recompositions — SettingsRow data classes
    // capture the onClick lambda in their structural equality, so a
    // fresh `{ onEvent(...) }` per recomp would defeat @Immutable
    // skipping and force SegmentedListItem to redraw on every state
    // change (per SettingsRow.kt's KDoc warning about caller-side
    // remembering). rememberUpdatedState gives us a stable State<T>
    // reference whose .value always points at the current onEvent.
    val currentOnEvent by rememberUpdatedState(onEvent)
    val signOutLabel = stringResource(R.string.settings_signout)
    val notificationsRowLabel = stringResource(R.string.settings_notifications_row_label)
    val versionRowLabel = stringResource(R.string.settings_version_row_label)

    val notificationsRows =
        remember(notificationsRowLabel) {
            persistentListOf(
                // SettingsRow.Link semantically signals "opens an external
                // destination" — here, the OS app-notification-settings
                // page. v1 content for the canonical Notifications
                // section; in-app per-reason toggles arrive in a later
                // epic and will join this list.
                SettingsRow.Link(
                    icon = null,
                    label = notificationsRowLabel,
                    onClick = { currentOnEvent(SettingsEvent.NotificationsTapped) },
                ),
            )
        }
    val accountRows =
        remember(signOutLabel) {
            persistentListOf(
                SettingsRow.Action(
                    icon = null,
                    label = signOutLabel,
                    isDestructive = true,
                    onClick = { currentOnEvent(SettingsEvent.SignOutTapped) },
                ),
            )
        }
    val aboutRows =
        remember(versionRowLabel, versionLabel) {
            persistentListOf(
                // Non-interactive: the version is informational. Info renders
                // the same visual rhythm (Surface tone + segmented shape) as
                // the surrounding action rows but has no click handler, no
                // ripple, and announces as text to screen readers.
                SettingsRow.Info(
                    icon = null,
                    label = versionRowLabel,
                    supportingText = versionLabel,
                ),
            )
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsHeader(
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
            avatarHue = state.avatarHue,
            onManageAccountClick = { onEvent(SettingsEvent.ManageAccountTapped) },
        )
        SwitchAccountRow(
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
            avatarHue = state.avatarHue,
            onTap = { onEvent(SettingsEvent.SwitchAccountTapped) },
        )
        // Canonical section roster (spec: feature-settings — "Settings
        // screen renders sections in a canonical fixed order"). Sections
        // that don't have content yet are omitted entirely so the empty-
        // section caption rule from the spec is satisfied:
        //
        //   1. Open links & sharing — filled by nubecita-ajty
        //   2. Display              — filled by nubecita-37to.3
        //   3. Notifications        — system-settings deep-link today
        //                              (v1 content); in-app per-reason
        //                              toggles arrive in a later epic.
        //   4. Content & moderation — filled by nubecita-37to.5
        //   5. Account              — Sign Out lives here today
        //   6. About                — Version row lives here today
        //   7. Data usage           — filled by nubecita-37to.8
        SettingsSection(rows = notificationsRows)
        SettingsSection(rows = accountRows)
        SettingsSection(rows = aboutRows)
    }

    if (state.confirmDialogOpen) {
        SignOutConfirmDialog(
            isSigningOut = state.status is SettingsStatus.SigningOut,
            onConfirm = { onEvent(SettingsEvent.ConfirmSignOut) },
            onDismiss = { onEvent(SettingsEvent.DismissDialog) },
        )
    }
}

// Runtime-read versionName + versionCode via PackageManager so :feature:settings:impl
// doesn't need its own BuildConfig. The (String, Int) overload is deprecated on
// API 33+ in favor of (String, PackageInfoFlags); SDK-gate so compileSdk 37 doesn't
// surface a deprecation warning on every build, while still working on minSdk 24.
// PackageInfoCompat covers the deprecated-on-API-28 versionCode getter.
@Composable
private fun rememberAppVersionLabel(): String {
    val context = LocalContext.current
    val unknown = stringResource(R.string.settings_version_unknown)
    return remember(context, unknown) {
        try {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            val name = info.versionName ?: unknown
            val code = PackageInfoCompat.getLongVersionCode(info)
            "$name ($code)"
        } catch (_: PackageManager.NameNotFoundException) {
            unknown
        }
    }
}

@Composable
private fun SignOutConfirmDialog(
    isSigningOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSigningOut) onDismiss() },
        title = { Text(stringResource(R.string.settings_signout_dialog_title)) },
        text = { Text(stringResource(R.string.settings_signout_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSigningOut) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.settings_signout_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSigningOut) {
                Text(stringResource(R.string.settings_signout_dialog_cancel))
            }
        },
    )
}
