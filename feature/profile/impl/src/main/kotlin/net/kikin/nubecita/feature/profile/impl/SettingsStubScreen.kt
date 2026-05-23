package net.kikin.nubecita.feature.profile.impl

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * On Sign Out success, the screen unmounts when
 * `SessionStateProvider` transitions and MainActivity replaces to
 * Login — no nav effect required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsStubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsStubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val signOutErrorMsg = stringResource(R.string.profile_settings_signout_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsStubEffect.ShowSignOutError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(signOutErrorMsg)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.profile_settings_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        SettingsStubContent(
            state = state,
            onEvent = viewModel::handleEvent,
            versionLabel = rememberAppVersionLabel(),
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun SettingsStubContent(
    state: SettingsStubViewState,
    onEvent: (SettingsStubEvent) -> Unit,
    versionLabel: String,
    modifier: Modifier = Modifier,
) {
    // Header data is hardcoded for v1 of the shell — real values flow
    // from the session repo through SettingsStubViewModel under task 2.8.
    // The composable shape stays final so 2.8 is a state-wiring change
    // only, no further layout churn.
    val handle = "kikin.bsky.social"
    val displayName: String? = null
    val avatarUrl: String? = null

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
            onManageAccountClick = {
                // Wires to a LaunchUri("https://bsky.app/settings") effect in 2.8.
            },
        )
        SwitchAccountRow(
            avatarUrl = avatarUrl,
            onTap = {
                // Wires to a "Coming soon" snackbar effect in 2.8.
            },
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
