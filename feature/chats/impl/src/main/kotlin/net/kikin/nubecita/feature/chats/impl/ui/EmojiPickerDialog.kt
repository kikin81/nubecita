package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.emoji2.emojipicker.EmojiPickerView

/**
 * Full emoji picker for chat reactions. Hosts the AndroidX [EmojiPickerView] (a
 * scrollable legacy View) in a Compose [Dialog] — NOT a draggable ModalBottomSheet,
 * whose drag-to-dismiss fights the view's vertical scroll (see the Phase 2 spec).
 * `onEmojiPicked` yields exactly one emoji, passed through verbatim (no truncation).
 *
 * The `onEmojiPicked` name reads as past tense to the compose:parameter-naming
 * ktlint rule, but it is the project's contract name for "an emoji was picked"
 * (mirrors the underlying `setOnEmojiPickedListener`), so the lint is suppressed.
 */
@Suppress("ktlint:compose:parameter-naming")
@Composable
internal fun EmojiPickerDialog(
    onEmojiPicked: (emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        setOnEmojiPickedListener { item -> onEmojiPicked(item.emoji) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }
    }
}
