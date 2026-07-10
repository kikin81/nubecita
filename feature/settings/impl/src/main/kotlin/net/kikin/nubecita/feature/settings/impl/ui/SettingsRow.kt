package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * One renderable row inside a [SettingsSection] card. Variant choice
 * determines which Material 3 Expressive controls render in the
 * trailing slot and how taps are interpreted:
 *
 * - [Action] — tap fires a one-shot effect (e.g. Sign Out, "Open
 *   system notification settings"). No trailing widget.
 * - [Toggle] — boolean preference; trailing `Switch`. Tapping the
 *   row toggles the value (same effect as tapping the switch
 *   directly).
 * - [Picker] — multi-choice preference; trailing slot shows the
 *   current value's display string. Tapping the row is expected to
 *   open a radio-button dialog hosted by the section's screen.
 * - [Link] — semantically identical to [Action] in v1 (renders the
 *   same), but reserved for rows that open an external destination
 *   (web settings, OS settings deep-link). Section tasks layer in a
 *   trailing "open in new" badge once that icon is added to
 *   [NubecitaIconName].
 * - [Info] — non-interactive informational row (e.g. Version row in
 *   About). Renders the same shape + tone as the other variants but
 *   has no click handler, no ripple, and no clickable semantics —
 *   screen readers announce it as text, not a disabled button.
 *
 * Equality is structural on the value fields. Lambdas (`onClick`,
 * `onCheckedChange`) participate in equality — callers should
 * `remember { ... }` them at the call site if the surrounding
 * recomposition cadence is high enough to matter.
 */
@Immutable
sealed interface SettingsRow {
    val icon: NubecitaIconName?
    val label: String
    val supportingText: String?

    @Immutable
    data class Action(
        override val icon: NubecitaIconName?,
        override val label: String,
        override val supportingText: String? = null,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingsRow

    @Immutable
    data class Toggle(
        override val icon: NubecitaIconName?,
        override val label: String,
        override val supportingText: String? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsRow

    @Immutable
    data class Picker(
        override val icon: NubecitaIconName?,
        override val label: String,
        override val supportingText: String? = null,
        val currentValue: String,
        val onClick: () -> Unit,
    ) : SettingsRow

    @Immutable
    data class Link(
        override val icon: NubecitaIconName?,
        override val label: String,
        override val supportingText: String? = null,
        val isDestructive: Boolean = false,
        val onClick: () -> Unit,
    ) : SettingsRow

    @Immutable
    data class Info(
        override val icon: NubecitaIconName?,
        override val label: String,
        override val supportingText: String? = null,
    ) : SettingsRow
}
