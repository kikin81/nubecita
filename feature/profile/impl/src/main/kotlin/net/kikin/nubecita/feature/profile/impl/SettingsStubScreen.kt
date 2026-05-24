package net.kikin.nubecita.feature.profile.impl

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.ui.settings.SettingsHeader
import net.kikin.nubecita.feature.profile.impl.ui.settings.SettingsRow
import net.kikin.nubecita.feature.profile.impl.ui.settings.SettingsSection
import net.kikin.nubecita.feature.profile.impl.ui.settings.SwitchAccountRow

/**
 * Stateful Settings stub screen. Owns the [SettingsStubViewModel] +
 * effect collector + snackbar host. Delegates rendering to
 * [SettingsStubContent] which previews and screenshot tests can
 * exercise with fixture inputs.
 *
 * Adaptive shape (task 2.7 / spec: "Settings screen adapts shape to
 * window size class"): the screen renders as a full-screen route
 * below the Medium width breakpoint, and as a centered modal with
 * scrim at-or-above Medium. The window-size-class read uses the
 * project-wide pattern from `:designsystem`/`MainShell`/etc. —
 * `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeast
 * Breakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)`.
 *
 * On Sign Out success, the screen unmounts when
 * `SessionStateProvider` transitions and MainActivity replaces to
 * Login — no nav effect required.
 */
@Composable
internal fun SettingsStubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsStubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val signOutErrorMsg = stringResource(R.string.profile_settings_signout_error)
    val switchAccountComingSoonMsg =
        stringResource(R.string.profile_settings_switch_account_coming_soon)
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsStubEffect.ShowSignOutError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(signOutErrorMsg)
                }
                SettingsStubEffect.ShowSwitchAccountComingSoon -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(switchAccountComingSoonMsg)
                }
                is SettingsStubEffect.LaunchUri -> {
                    // ACTION_VIEW lets the OS route to the user's preferred
                    // handler — Chrome Custom Tabs when installed, system
                    // browser otherwise. FLAG_ACTIVITY_NEW_TASK is required
                    // because we're starting the intent from a non-Activity
                    // Context (the Compose application context).
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
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
            snackbarHostState = snackbarHostState,
        ) {
            SettingsStubContent(
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
            SettingsStubContent(
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription =
                                stringResource(R.string.profile_settings_back_content_description),
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
    snackbarHostState: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(fraction = 0.92f)
                    .fillMaxHeight(fraction = 0.80f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription =
                                stringResource(R.string.profile_settings_close_content_description),
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SettingsStubContent(
    state: SettingsStubViewState,
    onEvent: (SettingsStubEvent) -> Unit,
    versionLabel: String,
    modifier: Modifier = Modifier,
) {
    // Header values are session-derived in SettingsStubViewModel.init: the
    // handle lands synchronously from SessionStateProvider; displayName +
    // avatarUrl arrive after a ProfileRepository.fetchHeader round-trip
    // (silent failure → null → header falls back to "Hi!" + initials disc).
    // The handle should always be non-null inside MainShell since the
    // outer Navigator gates entry on SignedIn; the empty-string fallback
    // is defensive and would only render if the session state went stale
    // mid-composition.
    val handle = state.handle.orEmpty()
    val displayName = state.displayName
    val avatarUrl = state.avatarUrl

    val accountRows =
        persistentListOf(
            SettingsRow.Action(
                icon = null,
                label = stringResource(R.string.profile_settings_signout),
                isDestructive = true,
                onClick = { onEvent(SettingsStubEvent.SignOutTapped) },
            ),
        )
    val aboutRows =
        persistentListOf(
            SettingsRow.Action(
                icon = null,
                label = stringResource(R.string.profile_settings_version_row_label),
                supportingText = versionLabel,
                onClick = {},
            ),
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsHeader(
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
            onManageAccountClick = { onEvent(SettingsStubEvent.ManageAccountTapped) },
        )
        SwitchAccountRow(
            handle = handle,
            displayName = displayName,
            avatarUrl = avatarUrl,
            onTap = { onEvent(SettingsStubEvent.SwitchAccountTapped) },
        )
        // Canonical section roster (spec: feature-settings — "Settings
        // screen renders sections in a canonical fixed order"). Sections
        // that don't have content yet are omitted entirely so the empty-
        // section caption rule from the spec is satisfied:
        //
        //   1. Open links & sharing — filled by nubecita-ajty
        //   2. Display              — filled by nubecita-37to.3
        //   3. Notifications        — filled by nubecita-37to.4
        //   4. Content & moderation — filled by nubecita-37to.5
        //   5. Account              — Sign Out lives here today (this task)
        //   6. About                — Version row lives here today (this task)
        //   7. Data usage           — filled by nubecita-37to.8
        SettingsSection(rows = accountRows)
        SettingsSection(rows = aboutRows)
    }

    if (state.confirmDialogOpen) {
        SignOutConfirmDialog(
            isSigningOut = state.status is SettingsStubStatus.SigningOut,
            onConfirm = { onEvent(SettingsStubEvent.ConfirmSignOut) },
            onDismiss = { onEvent(SettingsStubEvent.DismissDialog) },
        )
    }
}

// Runtime-read versionName + versionCode via PackageManager so :feature:profile:impl
// doesn't need its own BuildConfig. The (String, Int) overload is deprecated on
// API 33+ in favor of (String, PackageInfoFlags); SDK-gate so compileSdk 37 doesn't
// surface a deprecation warning on every build, while still working on minSdk 24.
// PackageInfoCompat covers the deprecated-on-API-28 versionCode getter.
@Composable
private fun rememberAppVersionLabel(): String {
    val context = LocalContext.current
    val unknown = stringResource(R.string.profile_settings_version_unknown)
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
        title = { Text(stringResource(R.string.profile_settings_signout_dialog_title)) },
        text = { Text(stringResource(R.string.profile_settings_signout_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSigningOut) {
                if (isSigningOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.profile_settings_signout_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSigningOut) {
                Text(stringResource(R.string.profile_settings_signout_dialog_cancel))
            }
        },
    )
}
