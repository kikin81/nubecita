package net.kikin.nubecita.feature.settings.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.moderation.api.BlockedAccounts
import net.kikin.nubecita.feature.settings.api.ContentFilters
import net.kikin.nubecita.feature.settings.impl.ui.SettingsRow
import net.kikin.nubecita.feature.settings.impl.ui.SettingsSection

/**
 * Moderation hub (`nubecita-oftc.17`) — a settings sub-screen grouping the
 * moderation tools so the main Settings page stays lean. Today: Content filters
 * (a settings sub-route) + Blocked accounts (a `:feature:moderation` route);
 * room for muted accounts/words/lists later. Pure navigation — no VM; rows push
 * their NavKey via [onNavigateTo].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModerationScreen(
    onBack: () -> Unit,
    onNavigateTo: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentFiltersLabel = stringResource(R.string.settings_content_filters_label)
    val blockedAccountsLabel = stringResource(R.string.settings_blocked_accounts_label)
    val currentOnNavigateTo by rememberUpdatedState(onNavigateTo)
    val rows =
        remember(contentFiltersLabel, blockedAccountsLabel) {
            persistentListOf(
                SettingsRow.Action(
                    icon = null,
                    label = contentFiltersLabel,
                    onClick = { currentOnNavigateTo(ContentFilters) },
                ),
                SettingsRow.Action(
                    icon = null,
                    label = blockedAccountsLabel,
                    onClick = { currentOnNavigateTo(BlockedAccounts) },
                ),
            )
        }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_moderation_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.settings_moderation_back),
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
                    .verticalScroll(rememberScrollState()),
        ) {
            SettingsSection(rows = rows)
        }
    }
}
