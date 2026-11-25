package net.kikin.nubecita.designsystem.input

import android.content.res.Configuration
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch

/**
 * Whether a hardware (QWERTY) keyboard is attached and usable: a Bluetooth or USB keyboard,
 * an XR headset's paired keyboard, a Chromebook. Keyboard shortcuts and their focus anchors
 * are gated on this, so a touch-only phone composes exactly what it did before.
 *
 * Read from [LocalConfiguration], which recomposes when a keyboard is plugged in or removed.
 */
@Composable
@ReadOnlyComposable
fun rememberHasHardwareKeyboard(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
        configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
}

/**
 * A key press of [key] with no modifier held: the trigger for single-letter shortcuts
 * (`j`, `k`, `r`, `l`, `/`). Key-up and auto-repeat do not match, so holding a key fires
 * the action once.
 *
 * Handle these in `Modifier.onKeyEvent`, never `onPreviewKeyEvent`: a focused text field
 * consumes printable keys before they bubble up to an ancestor's `onKeyEvent`, which is
 * what keeps typing a `j` in the composer or search field from firing a shortcut.
 */
fun KeyEvent.isPlainPress(key: Key): Boolean = isFirstPress(key) && !isCtrlPressed && !isAltPressed && !isMetaPressed && !isShiftPressed

/** A key press of Ctrl+[key] (Shift, Alt and Meta released): app-wide commands. */
fun KeyEvent.isCtrlPress(key: Key): Boolean = isFirstPress(key) && isCtrlPressed && !isAltPressed && !isMetaPressed && !isShiftPressed

private fun KeyEvent.isFirstPress(key: Key): Boolean = type == KeyEventType.KeyDown && this.key == key && nativeKeyEvent.repeatCount == 0

/**
 * The keyboard focus anchors currently on screen, most recent last. When focus leaves the app
 * entirely (the focused element left composition: a dialog closed, a pane popped, a list item
 * scrolled away), the shell calls [restore] so the next key still has somewhere to go.
 *
 * Plain registration, read only by the shell; nothing is broadcast.
 */
@Stable
class KeyboardFocusAnchors {
    private val anchors = mutableListOf<FocusRequesterModifierNode>()

    internal fun add(anchor: FocusRequesterModifierNode) {
        anchors += anchor
    }

    internal fun remove(anchor: FocusRequesterModifierNode) {
        anchors -= anchor
    }

    /** Focuses the most recently added anchor that can take focus; false if none could. */
    fun restore(): Boolean = anchors.asReversed().any { it.requestFocus() }
}

/**
 * The shell's [KeyboardFocusAnchors], or null outside the main shell (previews, the outer
 * login / onboarding shell), where anchors simply do not register.
 */
val LocalKeyboardFocusAnchors = staticCompositionLocalOf<KeyboardFocusAnchors?> { null }

/**
 * Makes this element a keyboard focus anchor while [enabled]: focusable, given focus once
 * when it is attached, and registered with [anchors] (the shell's, read from
 * [LocalKeyboardFocusAnchors] in composition) so focus can be handed back to it later.
 * Unchanged when not enabled.
 *
 * Compose dispatches a key event only along the path from the focused node to the root, so
 * with nothing focused (a fresh launch, or right after touch input) no `onKeyEvent` handler
 * in the app sees a key at all. An anchor gives that path a starting point. Pass
 * [rememberHasHardwareKeyboard] as [enabled] so touch-only devices never take the focus.
 *
 * The anchor draws nothing and is skipped by accessibility services, which move their own
 * focus independently of input focus.
 */
fun Modifier.keyboardFocusAnchor(
    enabled: Boolean,
    anchors: KeyboardFocusAnchors?,
): Modifier = if (enabled) this then KeyboardFocusAnchorElement(anchors, listKeys = false) then Modifier.focusable() else this

/**
 * [keyboardFocusAnchor] for a scrolling list of posts: the list itself takes focus when it
 * appears, and while it (not one of its items) holds focus, `j` / `k` move focus into its
 * first visible focusable item. From there the items' own handlers (`PostCard`'s) take over.
 *
 * The anchor only reacts while it is the focused node itself, so a `j` that bubbles up from
 * the last card (no card below) does not bounce focus back to the top.
 */
fun Modifier.listKeyboardAnchor(
    enabled: Boolean,
    anchors: KeyboardFocusAnchors?,
): Modifier = if (enabled) this then KeyboardFocusAnchorElement(anchors, listKeys = true) then Modifier.focusable() else this

private data class KeyboardFocusAnchorElement(
    val anchors: KeyboardFocusAnchors?,
    val listKeys: Boolean,
) : ModifierNodeElement<KeyboardFocusAnchorNode>() {
    override fun create() = KeyboardFocusAnchorNode(anchors, listKeys)

    override fun update(node: KeyboardFocusAnchorNode) {
        node.update(anchors, listKeys)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "keyboardFocusAnchor"
        properties["listKeys"] = listKeys
    }
}

private class KeyboardFocusAnchorNode(
    private var anchors: KeyboardFocusAnchors?,
    private var listKeys: Boolean,
) : Modifier.Node(),
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {
    private var anchorFocused = false

    fun update(
        anchors: KeyboardFocusAnchors?,
        listKeys: Boolean,
    ) {
        this.listKeys = listKeys
        if (anchors === this.anchors) return
        if (isAttached) {
            this.anchors?.remove(this)
            anchors?.add(this)
        }
        this.anchors = anchors
    }

    override fun onAttach() {
        anchors?.add(this)
        // Posted: the focusable that follows this node in the chain attaches after it.
        coroutineScope.launch { requestFocus() }
    }

    override fun onDetach() {
        anchors?.remove(this)
        anchorFocused = false
    }

    override fun onFocusEvent(focusState: FocusState) {
        anchorFocused = focusState.isFocused
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!listKeys || !anchorFocused) return false
        if (!event.isPlainPress(Key.J) && !event.isPlainPress(Key.K)) return false
        return currentValueOf(LocalFocusManager).moveFocus(FocusDirection.Enter)
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean = false
}
