package net.kikin.nubecita.feature.composer.impl.internal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Per-photo alt-text editor, rendered as a layer **inside** the composer's own
 * surface (driven by `ComposerState.altEditTarget`). Because the composer is an
 * `adaptiveDialog()` entry, this layer inherits its presentation — full-screen
 * on phone, within the centered dialog card on tablet.
 *
 * Messages-inspired layout, top to bottom: a top bar (close · "N of M" · Done),
 * a [HorizontalPager] over the photos, a thumbnail filmstrip (✓ = described),
 * and a single alt-text field pinned to the bottom that edits whichever photo is
 * currently paged to. Pinning the field at the bottom keeps it directly above
 * the keyboard (the layer owns its own `imePadding`, since it replaces the
 * composer Scaffold body and so can't inherit its IME handling); the photo +
 * filmstrip sit above it. The field binds to canonical state on
 * [ComposerAttachment.alt] via [onSetAlt] (plain value/onValueChange — alt has
 * no cursor-aware reducer work, so the `TextFieldState` exception isn't needed).
 */
@Composable
internal fun AltEditorLayer(
    attachments: ImmutableList<ComposerAttachment>,
    initialIndex: Int,
    onSetAlt: (index: Int, text: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Defensive: nothing to describe (shouldn't happen while open) → close.
    if (attachments.isEmpty()) {
        onClose()
        return
    }
    val pagerState =
        rememberPagerState(
            initialPage = initialIndex.coerceIn(0, attachments.lastIndex),
            pageCount = { attachments.size },
        )
    val scope = rememberCoroutineScope()
    // The bottom field edits whichever photo is currently paged to. Read
    // currentPage directly (it's already discrete Compose State, so this
    // recomposes exactly when the page changes) and clamp against the *current*
    // attachments — no derivedStateOf/remember, which would capture a stale list.
    val page = pagerState.currentPage.coerceIn(0, attachments.lastIndex)
    val current = attachments[page]

    // The editor replaces the composer body, so a system back-press must close
    // the editor (return to the body) rather than falling through to the
    // composer's own discard/close BackHandler.
    BackHandler(onBack = onClose)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // Own the window insets: status bar at the top, nav-bar/IME at the
        // bottom (chained so the keyboard subsumes the nav-bar area when open).
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Top bar: close + "N of M" position + a prominent Done button.
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.composer_alt_editor_close_action),
                    )
                }
                Text(
                    text = stringResource(R.string.composer_alt_editor_position, page + 1, attachments.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Button(onClick = onClose) {
                    Text(stringResource(R.string.composer_alt_editor_done_action))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // Stable page identity (defensive — the list can't reorder while
                // the editor is open, but this keeps page state tied to the photo).
                key = { attachments[it].uri.toString() },
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 16.dp,
            ) { pageIndex ->
                NubecitaAsyncImage(
                    model = attachments[pageIndex].uri,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp)),
                )
            }

            AltEditorFilmstrip(
                attachments = attachments,
                selectedIndex = page,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            // Single alt field pinned to the bottom, editing the current photo.
            // Sits directly above the keyboard thanks to the Column's imePadding.
            OutlinedTextField(
                value = current.alt,
                onValueChange = { onSetAlt(page, it) },
                label = { Text(stringResource(R.string.composer_alt_editor_field_label)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AltEditorFilmstrip(
    attachments: ImmutableList<ComposerAttachment>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = attachments,
            key = { _, item -> item.uri.toString() },
        ) { index, attachment ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            if (selected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp),
                                )
                            } else {
                                Modifier
                            },
                        ),
            ) {
                NubecitaAsyncImage(
                    model = attachment.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                // ✓ overlay once described, so completion is glanceable.
                if (attachment.alt.isNotBlank()) {
                    NubecitaIcon(
                        name = NubecitaIconName.Check,
                        contentDescription = null,
                        filled = true,
                        opticalSize = 14.dp,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
                // Whole tile is the jump target.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable { onSelect(index) },
                )
            }
        }
    }
}
