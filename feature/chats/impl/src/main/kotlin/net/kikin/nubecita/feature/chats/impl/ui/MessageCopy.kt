package net.kikin.nubecita.feature.chats.impl.ui

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.R

/**
 * The Copy action for this message, or `null` when there is nothing to copy —
 * a deleted message, or one whose only content is a quoted-post embed. The
 * menu hides the row entirely rather than offering an action that silently
 * does nothing.
 */
internal fun MessageUi.copyActionOrNull(onCopy: (String) -> Unit): (() -> Unit)? = if (isDeleted || text.isBlank()) null else ({ onCopy(text) })

/**
 * Puts [text] on the clipboard and confirms it — but only below API 33.
 *
 * From Android 13 the platform shows its own confirmation for every clipboard
 * write, so an in-app toast on top of it reports the same event twice. The
 * platform UI is the one users already recognise, so ours yields to it.
 */
internal suspend fun copyMessageText(
    context: Context,
    clipboard: Clipboard,
    text: String,
) {
    val label = context.getString(R.string.chat_copy_action)
    clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.chat_copied_confirmation, Toast.LENGTH_SHORT).show()
    }
}
