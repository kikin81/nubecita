package net.kikin.nubecita.feature.settings.impl

import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
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
 *
 * Rows render through the design system's M3 Expressive grouped list
 * ([NubecitaListGroup] + [NubecitaListItem]), the same components the Settings
 * home uses, rather than hand-rolled `Row`s split by `HorizontalDivider`. The
 * flat run is split into three groups by purpose:
 *
 *  1. **App links** — "Source on GitHub" and "Rate Nubecita", the two rows that
 *     leave the app for an external destination.
 *  2. **Special thanks** — the contributor roster, captioned with the existing
 *     `about_thanks_section` string (previously a hand-rolled section header).
 *  3. **Legal** — "Open source licenses" on its own, an in-app sub-route push.
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
    // Stable reference for the row lambdas: AboutLinkRow captures its onClick in
    // structural equality, so a fresh `{ onEvent(...) }` per recomposition would
    // defeat @Immutable skipping (same reasoning as SettingsContent).
    val currentOnEvent by rememberUpdatedState(onEvent)
    val sourceLabel = stringResource(R.string.about_source_label)
    val sourceContentDesc = stringResource(R.string.about_source_content_desc)
    val rateLabel = stringResource(R.string.about_rate_label)
    val rateContentDesc = stringResource(R.string.about_rate_content_desc)
    val licensesLabel = stringResource(R.string.about_licenses_label)

    val appLinkRows =
        remember(sourceLabel, sourceContentDesc, rateLabel, rateContentDesc) {
            persistentListOf(
                AboutLinkRow(
                    label = sourceLabel,
                    trailingContentDescription = sourceContentDesc,
                    onClick = { currentOnEvent(AboutEvent.SourceTapped) },
                ),
                AboutLinkRow(
                    label = rateLabel,
                    trailingContentDescription = rateContentDesc,
                    onClick = { currentOnEvent(AboutEvent.RateAppTapped) },
                ),
            )
        }
    val legalRows =
        remember(licensesLabel) {
            persistentListOf(
                AboutLinkRow(
                    label = licensesLabel,
                    trailingContentDescription = null,
                    onClick = { currentOnEvent(AboutEvent.LicensesTapped) },
                ),
            )
        }

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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AboutHeader(versionLabel = versionLabel)

            NubecitaListGroup(items = appLinkRows) { row, shapes ->
                AboutLinkRowContent(row = row, shapes = shapes)
            }

            NubecitaListGroup(
                items = state.thanks,
                label = stringResource(R.string.about_thanks_section),
            ) { row, shapes ->
                ThanksRowContent(
                    row = row,
                    shapes = shapes,
                    onClick = { currentOnEvent(AboutEvent.ThanksRowTapped(row.did)) },
                )
            }

            NubecitaListGroup(items = legalRows) { row, shapes ->
                AboutLinkRowContent(row = row, shapes = shapes)
            }
        }
    }
}

@Composable
private fun AboutHeader(versionLabel: String) {
    // 8dp on top of the content Column's 16dp inset keeps the header's 24dp
    // gutter from before the grouped-list migration.
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp)) {
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

/**
 * One "navigate away" row in an About group. The trailing chevron keeps its own
 * [trailingContentDescription] so the row still announces the destination
 * ("Open the Nubecita source code on GitHub") alongside its label, exactly as
 * the pre-migration hand-rolled row did.
 */
@Immutable
private data class AboutLinkRow(
    val label: String,
    val trailingContentDescription: String?,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutLinkRowContent(
    row: AboutLinkRow,
    shapes: ListItemShapes,
) {
    NubecitaListItem(
        shapes = shapes,
        headlineContent = {
            Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
        },
        onClick = row.onClick,
        trailingContent = {
            NubecitaIcon(
                name = NubecitaIconName.ChevronRight,
                contentDescription = row.trailingContentDescription,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThanksRowContent(
    row: ThanksRowUi,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    // All three lines live in headlineContent with NO supportingContent, which
    // is load-bearing rather than stylistic.
    //
    // M3's list-item measure policy picks the three-line item type from
    // `isSupportingMultiline` — whether the SUPPORTING placeable's first and
    // last baselines differ. A Column of two Texts always differs, so putting
    // the handle and blurb there forced every roster row to the three-line
    // minimum height with top-aligned content, no matter how short its blurb.
    // Rows with a one-line blurb ended up with roughly twice as much space
    // below the text as above (nubecita-1ow5.8).
    //
    // With no supporting slot the row is measured against its actual content,
    // so short and long entries pad evenly and the avatar centres against the
    // text block — which is how the roster read before the list migration.
    NubecitaListItem(
        shapes = shapes,
        headlineContent = {
            // The headline falls back to the handle when a contributor has no
            // display name, so rendering "@handle" underneath unconditionally
            // printed it twice ("zenos00.bsky.social" / "@zenos00.bsky.social").
            // isNullOrBlank rather than a null check: displayName is hydrated
            // from the profile later and an empty string is not a name.
            val displayName = row.displayName?.takeIf { it.isNotBlank() }
            Column {
                Text(
                    text = displayName ?: row.handle,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (displayName != null) {
                    Text(
                        text = "@${row.handle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(row.blurbRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
        leadingContent = {
            NubecitaAvatar(model = row.avatarUrl, contentDescription = null, size = 40.dp)
        },
    )
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
