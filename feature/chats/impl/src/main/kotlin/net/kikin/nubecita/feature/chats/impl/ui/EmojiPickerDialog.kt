package net.kikin.nubecita.feature.chats.impl.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.emoji2.emojipicker.EmojiPickerView
import net.kikin.nubecita.feature.chats.impl.R

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
            // EmojiPickerView is a legacy View: it reads the XML theme, NOT the
            // Compose MaterialTheme this dialog sits inside. The app's XML theme is
            // hard-coded Light with no values-night, so without this wrapper the
            // picker painted near-black category headers on the dark Surface above
            // (nubecita-io24.4).
            //
            // Darkness comes from the rendered colour scheme rather than
            // isSystemInDarkTheme(): nubecita has an in-app theme selector, so the
            // system setting can disagree with what the user is actually looking at.
            // Luminance of `surface` is the honest signal and works under dynamic
            // colour too.
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val themedContext = emojiPickerThemedContext(LocalContext.current, isDark)
            AndroidView(
                factory = {
                    EmojiPickerView(themedContext).apply {
                        setOnEmojiPickedListener { item -> onEmojiPicked(item.emoji) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }
    }
}

/**
 * Wraps [context] in the picker theme matching the app's current appearance.
 *
 * Extracted so it can be asserted directly: the bug this fixes is entirely
 * about which text colour the legacy View resolves, and a Compose screenshot
 * cannot see inside an AndroidView. `EmojiPickerViewThemeTest` resolves
 * `android:textColorPrimary` off the returned context and checks it actually
 * inverts.
 */
internal fun emojiPickerThemedContext(
    context: Context,
    isDark: Boolean,
): Context =
    ContextThemeWrapper(
        context,
        if (isDark) R.style.Theme_Nubecita_EmojiPicker_Dark else R.style.Theme_Nubecita_EmojiPicker_Light,
    )
