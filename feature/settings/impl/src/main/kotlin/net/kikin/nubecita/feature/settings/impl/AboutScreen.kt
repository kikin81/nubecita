package net.kikin.nubecita.feature.settings.impl

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.review.PlayStoreLauncher
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.settings.api.AboutLicenses

/**
 * Stateful About screen (`nubecita-jkg8`). Hoists [AboutViewModel], collects
 * its effects, and projects [AboutState] through the stateless [AboutContent].
 * Navigation is a screen concern (the VM never touches `LocalMainShellNavState`):
 * profile + licenses pushes go through [onNavigateTo]; the GitHub link opens a
 * Custom Tab.
 *
 * Presented full-screen on phone and inside the Settings dialog (content-swap)
 * on tablet — the route is tagged `adaptiveDialog()` in `SettingsNavigationModule`.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    val versionLabel = rememberAppVersionLabel()
    val onEvent = remember(viewModel) { viewModel::handleEvent }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutEffect.LaunchUri ->
                    try {
                        CustomTabsIntent
                            .Builder()
                            .setShowTitle(true)
                            .build()
                            .launchUrl(context, Uri.parse(effect.uri))
                    } catch (_: ActivityNotFoundException) {
                        // No browser available — silent no-op.
                    }
                is AboutEffect.NavigateToProfile ->
                    // DID over handle so the link survives a handle change.
                    currentOnNavigateTo(Profile(handle = effect.did))
                AboutEffect.OpenLicenses ->
                    currentOnNavigateTo(AboutLicenses)
                AboutEffect.OpenPlayStore ->
                    PlayStoreLauncher.openListing(context)
            }
        }
    }

    AboutContent(
        state = state,
        versionLabel = versionLabel,
        onEvent = onEvent,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless About body. Extracted so preview / screenshot-test composables can
 * drive the layout without a Hilt graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutContent(
    state: AboutState,
    versionLabel: String,
    onEvent: (AboutEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.about_back_content_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            AboutHeader(versionLabel = versionLabel)

            NavRow(
                label = stringResource(R.string.about_source_label),
                contentDescription = stringResource(R.string.about_source_content_desc),
                onClick = { onEvent(AboutEvent.SourceTapped) },
            )
            NavRow(
                label = stringResource(R.string.about_rate_label),
                contentDescription = stringResource(R.string.about_rate_content_desc),
                onClick = { onEvent(AboutEvent.RateAppTapped) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeader(text = stringResource(R.string.about_thanks_section))
            state.thanks.forEach { row ->
                ThanksRow(row = row, onClick = { onEvent(AboutEvent.ThanksRowTapped(row.did)) })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            NavRow(
                label = stringResource(R.string.about_licenses_label),
                contentDescription = null,
                onClick = { onEvent(AboutEvent.LicensesTapped) },
            )
        }
    }
}

@Composable
private fun AboutHeader(versionLabel: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.about_app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = versionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun NavRow(
    label: String,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        NubecitaIcon(name = NubecitaIconName.ChevronRight, contentDescription = contentDescription)
    }
}

@Composable
private fun ThanksRow(
    row: ThanksRowUi,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NubecitaAvatar(model = row.avatarUrl, contentDescription = null, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName ?: row.handle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "@${row.handle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(row.blurbRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Runtime-read versionName + versionCode via PackageManager (the module has no
// BuildConfig of its own). Mirrors the reader in SettingsScreen; top-level
// `private` is file-scoped in Kotlin, so there is no clash.
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewState(): AboutState =
    AboutState(
        isLoadingThanks = false,
        thanks =
            persistentListOf(
                ThanksRowUi(
                    did = "did:plc:alice",
                    handle = "stavfx.com",
                    displayName = "Stav",
                    avatarUrl = null,
                    blurbRes = R.string.about_thanks_stavfx,
                ),
                ThanksRowUi(
                    did = "did:plc:bob",
                    handle = "vmlara.bsky.social",
                    displayName = "V. M. Lara",
                    avatarUrl = null,
                    blurbRes = R.string.about_thanks_vmlara,
                ),
            ),
    )

@Preview(name = "About — light", showBackground = true, heightDp = 720)
@Preview(name = "About — dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutContentPreview() {
    NubecitaTheme {
        Surface {
            AboutContent(
                state = previewState(),
                versionLabel = "1.175.1 (1175001)",
                onEvent = {},
                onBack = {},
            )
        }
    }
}
