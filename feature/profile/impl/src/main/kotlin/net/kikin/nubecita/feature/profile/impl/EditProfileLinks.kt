package net.kikin.nubecita.feature.profile.impl

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.NubecitaLinkFavicon
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink
import net.kikin.nubecita.feature.profile.impl.links.MAX_LINK_TITLE_GRAPHEMES
import net.kikin.nubecita.feature.profile.impl.links.MAX_PROFILE_LINKS
import net.kikin.nubecita.feature.profile.impl.links.linkLabel
import sh.calvin.reorderable.ReorderableColumn

/** Test tags for the Edit Profile links surfaces. */
internal object EditProfileLinksTestTags {
    const val SECTION = "edit_profile_links"
    const val ADD = "edit_profile_links_add"
    const val RETRY = "edit_profile_links_retry"
    const val DIALOG = "edit_profile_link_dialog"
}

/**
 * The Links section of Edit Profile: the draft links (tap to edit, ✕ to
 * remove) and an "Add link" row until the 10-link cap. While the list loads or
 * after a failed load the section shows a placeholder / retry and nothing is
 * editable — Save still works for the other fields.
 */
@Composable
internal fun EditProfileLinksSection(
    state: EditProfileViewState,
    onEvent: (EditProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(EditProfileLinksTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.edit_profile_links_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (state.linksStatus) {
            LinksLoadStatus.Loading ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NubecitaWavyProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.edit_profile_links_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            LinksLoadStatus.Failed ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.edit_profile_links_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onEvent(EditProfileEvent.RetryLinksLoad) },
                        modifier = Modifier.testTag(EditProfileLinksTestTags.RETRY),
                    ) {
                        Text(stringResource(R.string.edit_profile_links_retry))
                    }
                }

            LinksLoadStatus.Loaded -> {
                if (state.links.isEmpty()) {
                    Text(
                        text = stringResource(R.string.edit_profile_links_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ReorderableLinkList(
                        links = state.links,
                        enabled = state.canEditLinks,
                        onEvent = onEvent,
                    )
                }
                if (state.canAddLink) {
                    TextButton(
                        onClick = { onEvent(EditProfileEvent.AddLinkTapped) },
                        modifier = Modifier.testTag(EditProfileLinksTestTags.ADD),
                    ) {
                        NubecitaIcon(name = NubecitaIconName.Add, contentDescription = null, opticalSize = 18.dp)
                        Text(
                            text = stringResource(R.string.edit_profile_links_add),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else if (state.links.size >= MAX_PROFILE_LINKS) {
                    Text(
                        text = stringResource(R.string.edit_profile_links_limit, MAX_PROFILE_LINKS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The draft links as a drag-to-reorder list. Edit Profile is a non-lazy
 * `verticalScroll` column holding at most 10 links, so the library's
 * [ReorderableColumn] (not the lazy variant). A drag only reorders the draft —
 * [EditProfileEvent.MoveLink] is sent once, when the drop settles — and Save
 * writes the new order. Drag isn't reachable with TalkBack, so each row also
 * carries move up / move down custom actions.
 */
@Composable
private fun ReorderableLinkList(
    links: ImmutableList<EditableProfileLink>,
    enabled: Boolean,
    onEvent: (EditProfileEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // SegmentFrequentTick is API 34+ and Compose doesn't guard it; fall back to the
    // text-handle tick below that (same choice as ManageFeedsScreen).
    val reorderTick =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackType.SegmentFrequentTick
        } else {
            HapticFeedbackType.TextHandleMove
        }
    ReorderableColumn(
        list = links,
        onSettle = { from, to -> onEvent(EditProfileEvent.MoveLink(from, to)) },
        // A tick each time the dragged row swaps past another — the library ships no haptics.
        onMove = { haptics.performHapticFeedback(reorderTick) },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { index, link, isDragging ->
        // ReorderableColumn iterates without keys; key by the stable draft id so
        // per-row state (image load, ripple) follows the link as it moves.
        key(link.localId) {
            ReorderableItem {
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) DragElevation else 0.dp,
                    label = "edit-profile-link-drag-elevation",
                )
                EditProfileLinkRow(
                    link = link,
                    index = index,
                    itemCount = links.size,
                    enabled = enabled,
                    elevation = elevation,
                    onEdit = { onEvent(EditProfileEvent.EditLinkTapped(link.localId)) },
                    onRemove = { onEvent(EditProfileEvent.RemoveLink(link.localId)) },
                    onMove = { from, to -> onEvent(EditProfileEvent.MoveLink(from, to)) },
                    dragHandle = {
                        Box(
                            modifier =
                                Modifier
                                    .draggableHandle(enabled = enabled)
                                    .size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaIcon(
                                name = NubecitaIconName.Menu,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

private val DragElevation = 6.dp

@Composable
private fun EditProfileLinkRow(
    link: EditableProfileLink,
    index: Int,
    itemCount: Int,
    enabled: Boolean,
    elevation: Dp,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = linkLabel(link.title, link.url)
    val moveUpLabel = stringResource(R.string.edit_profile_link_move_up)
    val moveDownLabel = stringResource(R.string.edit_profile_link_move_down)
    val a11yActions =
        remember(enabled, index, itemCount, moveUpLabel, moveDownLabel, onMove) {
            buildList {
                if (enabled && index > 0) {
                    add(
                        CustomAccessibilityAction(moveUpLabel) {
                            onMove(index, index - 1)
                            true
                        },
                    )
                }
                if (enabled && index < itemCount - 1) {
                    add(
                        CustomAccessibilityAction(moveDownLabel) {
                            onMove(index, index + 1)
                            true
                        },
                    )
                }
            }
        }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        // A drag-lift shadow only — no tonal lift on an in-layout surface.
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The handle and the ✕ sit outside the tappable area, so a tap on
            // the handle doesn't also open the editor.
            dragHandle()
            // The one TalkBack-focusable node for the row: tap-to-edit plus the
            // move up / move down actions, which must live on the node TalkBack
            // focuses (a parent's custom actions aren't offered on a merged child).
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.edit_profile_link_edit_action, label),
                            onClick = onEdit,
                        ).semantics { customActions = a11yActions }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    NubecitaLinkFavicon(icon = link.pendingIcon?.bytes ?: link.iconPreviewUrl)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.edit_profile_link_remove, label),
                )
            }
        }
    }
}

/**
 * The add / edit link form. URL is required and normalized (a bare
 * `patreon.com/name` becomes `https://…`); title is optional with a live
 * grapheme counter. `http://` and known URL shorteners show non-blocking
 * warnings. Confirm fetches the favicon for a new / changed URL (progress on
 * the button) before closing.
 */
@Composable
internal fun LinkEditorDialog(
    editor: LinkEditorState,
    onEvent: (EditProfileEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(EditProfileEvent.LinkEditorDismissed) },
        modifier = Modifier.testTag(EditProfileLinksTestTags.DIALOG),
        title = {
            Text(
                stringResource(
                    if (editor.editingId == null) R.string.edit_profile_link_add_title else R.string.edit_profile_link_edit_title,
                ),
            )
        },
        text = { LinkEditorForm(editor = editor, onEvent = onEvent) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(EditProfileEvent.LinkEditorConfirmed) },
                enabled = editor.canConfirm,
            ) {
                if (editor.isFetchingIcon) {
                    NubecitaWavyProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.edit_profile_link_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EditProfileEvent.LinkEditorDismissed) }) {
                Text(stringResource(R.string.edit_profile_link_cancel))
            }
        },
    )
}

/** The dialog body, split out so screenshot tests can render it without a window. */
@Composable
internal fun LinkEditorForm(
    editor: LinkEditorState,
    onEvent: (EditProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val urlError =
            when {
                editor.isUrlInvalid -> stringResource(R.string.edit_profile_link_url_invalid)
                editor.isDuplicate -> stringResource(R.string.edit_profile_link_url_duplicate)
                else -> null
            }
        OutlinedTextField(
            value = editor.urlInput,
            onValueChange = { onEvent(EditProfileEvent.LinkUrlChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isFetchingIcon,
            label = { Text(stringResource(R.string.edit_profile_link_url_label)) },
            placeholder = { Text("example.com") },
            singleLine = true,
            isError = urlError != null,
            supportingText = urlError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = editor.titleInput,
            onValueChange = { onEvent(EditProfileEvent.LinkTitleChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !editor.isFetchingIcon,
            label = { Text(stringResource(R.string.edit_profile_link_title_label)) },
            singleLine = true,
            isError = editor.isTitleOverLimit,
            supportingText = {
                Text(stringResource(R.string.edit_profile_grapheme_counter, editor.titleGraphemes, MAX_LINK_TITLE_GRAPHEMES))
            },
            // The last field: IME "Done" confirms the link, same gate as the button.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(onDone = {
                    if (editor.canConfirm) onEvent(EditProfileEvent.LinkEditorConfirmed)
                }),
        )
        if (editor.isInsecure) LinkWarning(stringResource(R.string.edit_profile_link_warning_insecure))
        if (editor.isShortener) LinkWarning(stringResource(R.string.edit_profile_link_warning_shortener))
    }
}

@Composable
private fun LinkWarning(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Error,
            contentDescription = null,
            opticalSize = 18.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
