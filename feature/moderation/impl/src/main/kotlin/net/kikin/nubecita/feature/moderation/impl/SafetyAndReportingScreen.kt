package net.kikin.nubecita.feature.moderation.impl

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.moderation.api.BlockedAccounts
import net.kikin.nubecita.feature.moderation.api.Report

private const val NCMEC_CYBERTIPLINE_URL = "https://report.cybertip.org/"
private const val BLUESKY_COMMUNITY_GUIDELINES_URL = "https://bsky.social/about/support/community-guidelines"

/**
 * Dedicated Safety & Reporting screen ensuring Google Play Child Safety Standards
 * compliance and comprehensive in-app reporting discoverability.
 */
@Composable
internal fun SafetyAndReportingScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    var showReportDialog by remember { mutableStateOf(false) }
    var isChildSafetyReport by remember { mutableStateOf(false) }

    fun launchUrl(url: String) {
        try {
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            // Browser not installed or unavailable
        }
    }

    SafetyAndReportingContent(
        onBack = onBack,
        onReportChildSafetyClick = {
            isChildSafetyReport = true
            showReportDialog = true
        },
        onReportAccountClick = {
            isChildSafetyReport = false
            showReportDialog = true
        },
        onNavigateToBlockedAccounts = { currentOnNavigateTo(BlockedAccounts) },
        onOpenUrl = ::launchUrl,
        modifier = modifier,
    )

    if (showReportDialog) {
        ReportAccountEntryDialog(
            isChildSafety = isChildSafetyReport,
            onDismiss = { showReportDialog = false },
            onConfirm = { input ->
                showReportDialog = false
                val sanitized = input.trim().removePrefix("@")
                currentOnNavigateTo(
                    Report.forAccount(
                        did = sanitized,
                        initialCategory = if (isChildSafetyReport) "childSafety" else null,
                    ),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SafetyAndReportingContent(
    onBack: () -> Unit,
    onReportChildSafetyClick: () -> Unit,
    onReportAccountClick: () -> Unit,
    onNavigateToBlockedAccounts: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.safety_reporting_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.safety_reporting_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Child Safety Standards (Zero Tolerance) Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.safety_child_safety_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error,
                        ) {
                            Text(
                                text = stringResource(R.string.safety_child_safety_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.safety_child_safety_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = onReportChildSafetyClick,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NubecitaIcon(
                            name = NubecitaIconName.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.safety_report_child_safety_action),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // 2. In-App Moderation & Reporting Guide Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.safety_reporting_tools_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = stringResource(R.string.safety_reporting_tools_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.MoreHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.safety_how_to_report_posts_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = stringResource(R.string.safety_how_to_report_accounts_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onReportAccountClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        NubecitaIcon(
                            name = NubecitaIconName.Person,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.safety_report_account_action))
                    }
                }
            }

            // 3. Moderation Tools Group
            val toolsItems =
                remember(onNavigateToBlockedAccounts) {
                    persistentListOf(
                        ModerationToolItem(
                            titleRes = R.string.safety_blocked_accounts_label,
                            icon = NubecitaIconName.Block,
                            onClick = onNavigateToBlockedAccounts,
                        ),
                    )
                }

            NubecitaListGroup(
                items = toolsItems,
                label = stringResource(R.string.safety_tools_title),
            ) { item, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(item.titleRes)) },
                    leadingContent = { NubecitaIcon(name = item.icon, contentDescription = null) },
                    trailingContent = { NubecitaIcon(name = NubecitaIconName.ChevronRight, contentDescription = null) },
                    onClick = item.onClick,
                )
            }

            // 4. External Resources Group
            val resourceItems =
                remember {
                    persistentListOf(
                        ResourceItem(
                            titleRes = R.string.safety_resource_ncmec,
                            url = NCMEC_CYBERTIPLINE_URL,
                        ),
                        ResourceItem(
                            titleRes = R.string.safety_resource_bluesky_guidelines,
                            url = BLUESKY_COMMUNITY_GUIDELINES_URL,
                        ),
                    )
                }

            NubecitaListGroup(
                items = resourceItems,
                label = stringResource(R.string.safety_resources_title),
            ) { item, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(stringResource(item.titleRes)) },
                    leadingContent = { NubecitaIcon(name = NubecitaIconName.Article, contentDescription = null) },
                    trailingContent = { NubecitaIcon(name = NubecitaIconName.ChevronRight, contentDescription = null) },
                    onClick = { onOpenUrl(item.url) },
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private data class ModerationToolItem(
    val titleRes: Int,
    val icon: NubecitaIconName,
    val onClick: () -> Unit,
)

private data class ResourceItem(
    val titleRes: Int,
    val url: String,
)

@Composable
internal fun ReportAccountEntryDialog(
    isChildSafety: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialInput: String = "",
    initialError: Boolean = false,
) {
    var input by remember { mutableStateOf(initialInput) }
    var showError by remember { mutableStateOf(initialError) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isChildSafety) {
                    stringResource(R.string.safety_child_safety_title)
                } else {
                    stringResource(R.string.safety_report_account_dialog_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.safety_report_account_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        showError = false
                    },
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.safety_report_account_dialog_placeholder))
                    },
                    isError = showError,
                    supportingText =
                        if (showError) {
                            { Text(stringResource(R.string.safety_report_account_invalid_error)) }
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = input.trim().removePrefix("@")
                    val isValid =
                        trimmed.startsWith("did:") ||
                            (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length >= 3)
                    if (!isValid) {
                        showError = true
                    } else {
                        onConfirm(trimmed)
                    }
                },
            ) {
                Text(stringResource(R.string.safety_report_account_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.safety_report_account_dialog_cancel))
            }
        },
    )
}
