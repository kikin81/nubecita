package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaLinkChip
import net.kikin.nubecita.designsystem.component.NubecitaLinkFavicon
import net.kikin.nubecita.feature.profile.impl.ProfileLinkUi
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.links.splitForOverflow
import net.kikin.nubecita.feature.profile.impl.links.truncatePillLabel

/** Test tags for the profile-links surfaces. */
internal object ProfileLinksTestTags {
    const val ROW = "profile_links_row"
    const val OVERFLOW_PILL = "profile_links_overflow"
    const val SHEET = "profile_links_sheet"
    const val SHEET_CONTENT = "profile_links_sheet_content"
}

/**
 * The profile's links under the bio: at most four pills, then a "+N" pill
 * that opens [ProfileLinksSheet]. Deterministic — no measuring what fits in
 * two rows (see `openspec/changes/add-profile-links/design.md` Decision 1), so
 * very long titles on a narrow phone can wrap to a third row.
 *
 * Renders nothing for an empty list. Taps open an in-app Custom Tab (same path
 * as bio links); long-press shows the full URL in a tooltip.
 */
@Composable
internal fun ProfileLinksRow(
    links: ImmutableList<ProfileLinkUi>,
    onOverflowClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    val (visible, overflowCount) = remember(links) { splitForOverflow(links) }
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag(ProfileLinksTestTags.ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        // Each pill pads its 32dp visual out to a 48dp touch target, which
        // already leaves a 16dp visual gap between rows.
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        visible.forEach { link ->
            key(link.uri) { ProfileLinkPill(link = link) }
        }
        if (overflowCount > 0) {
            NubecitaLinkChip(
                label = stringResource(R.string.profile_links_overflow, overflowCount),
                onClick = onOverflowClick,
                onClickLabel = stringResource(R.string.profile_links_overflow_action),
                modifier = Modifier.testTag(ProfileLinksTestTags.OVERFLOW_PILL),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLinkPill(
    link: ProfileLinkUi,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(link.url) } },
        state = tooltipState,
        // Long-press is driven by the chip below so it coexists with the tap;
        // TooltipBox's own gesture handling would compete with it.
        enableUserInput = false,
        modifier = modifier,
    ) {
        NubecitaLinkChip(
            label = truncatePillLabel(link.label),
            emphasized = link.isSupportLink,
            icon = { NubecitaLinkFavicon(iconUrl = link.iconUrl) },
            onClick = { openBioLinkInCustomTab(context, link.url) },
            onClickLabel = stringResource(R.string.profile_link_open, link.host),
            onLongClick = { scope.launch { tooltipState.show() } },
            onLongClickLabel = stringResource(R.string.profile_link_show_url),
        )
    }
}

/**
 * Bottom sheet listing every profile link (up to 10) as full-width rows.
 * Hosted by `ProfileScreenContent` when `linksSheetVisible` is true; dismissed
 * via `ProfileEvent.LinksSheetDismissed`. Like `VerificationSheet`, the modal
 * wrapper has no pixel baseline — [ProfileLinksSheetContent] carries them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileLinksSheet(
    links: ImmutableList<ProfileLinkUi>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag(ProfileLinksTestTags.SHEET),
    ) {
        ProfileLinksSheetContent(links = links)
    }
}

@Composable
internal fun ProfileLinksSheetContent(
    links: ImmutableList<ProfileLinkUi>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(ProfileLinksTestTags.SHEET_CONTENT)
                .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_links_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        links.forEach { link ->
            key(link.uri) { ProfileLinkSheetRow(link = link) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLinkSheetRow(
    link: ProfileLinkUi,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(link.url) } },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.profile_link_open, link.host),
                        onLongClick = { scope.launch { tooltipState.show() } },
                        onLongClickLabel = stringResource(R.string.profile_link_show_url),
                        onClick = { openBioLinkInCustomTab(context, link.url) },
                    ).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                NubecitaLinkFavicon(iconUrl = link.iconUrl)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
