package net.kikin.nubecita.shell

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import net.kikin.nubecita.R

/**
 * The hardware keyboard shortcuts the main shell and `PostCard` handle, for the system
 * Keyboard Shortcut Helper (Meta+/ or the keyboard's shortcut key). Must list exactly what
 * the handlers dispatch: `MainShell`'s `keyboardShortcuts` (tabs, new post, search) and
 * `PostCard`'s `rememberPostKeyboardShortcuts` (j / k / r / l).
 */
internal fun nubecitaKeyboardShortcutGroups(context: Context): List<KeyboardShortcutGroup> {
    fun shortcut(
        labelRes: Int,
        keyCode: Int,
        modifiers: Int = 0,
    ) = KeyboardShortcutInfo(context.getString(labelRes), keyCode, modifiers)

    val tabKeys = listOf(KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5)
    val tabs = TopLevelDestinations.zip(tabKeys) { tab, key -> shortcut(tab.labelRes, key, KeyEvent.META_CTRL_ON) }
    val app =
        KeyboardShortcutGroup(
            context.getString(R.string.app_name),
            tabs +
                listOf(
                    shortcut(R.string.keyboard_shortcut_new_post, KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                    shortcut(R.string.keyboard_shortcut_search, KeyEvent.KEYCODE_SLASH),
                ),
        )
    val posts =
        KeyboardShortcutGroup(
            context.getString(R.string.keyboard_shortcuts_group_posts),
            listOf(
                shortcut(R.string.keyboard_shortcut_next_post, KeyEvent.KEYCODE_J),
                shortcut(R.string.keyboard_shortcut_previous_post, KeyEvent.KEYCODE_K),
                shortcut(R.string.keyboard_shortcut_reply, KeyEvent.KEYCODE_R),
                shortcut(R.string.keyboard_shortcut_like, KeyEvent.KEYCODE_L),
            ),
        )
    return listOf(app, posts)
}
