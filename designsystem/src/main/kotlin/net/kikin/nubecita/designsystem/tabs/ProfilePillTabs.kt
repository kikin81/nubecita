package net.kikin.nubecita.designsystem.tabs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Visual + semantic configuration for a single pill in [ProfilePillTabs].
 *
 * Generic over [T] so consumers pass typed values (e.g. a per-screen
 * `ProfileTab` enum) and the `onSelect` callback fires with the same
 * type — no string-keyed indirection, no opaque identifiers. The
 * design system declares only the shape; consumers own the enum.
 *
 * Annotated [Immutable] so Compose can skip recomposition of the tab
 * row when the list of pills hasn't changed by value. This is a
 * promise from the consumer: callers MUST pass a stable [T] (enum,
 * primitive, `@Immutable` data class). All current call sites pass
 * a screen-local enum, which satisfies the contract.
 */
@Immutable
public data class PillTab<T>(
    val value: T,
    val label: String,
    val iconName: NubecitaIconName,
)

/**
 * Single-selection segmented control used on the profile screen
 * (Posts / Replies / Media). Built on M3 Expressive's [ButtonGroup]
 * with [androidx.compose.material3.ButtonGroupScope.toggleableItem]
 * children — replaces the deprecated `SegmentedButton` per the M3
 * docs' migration guidance.
 *
 * Single-selection semantics over [ButtonGroup]'s boolean-toggle
 * primitive: each item only forwards `onCheckedChange(true)` to
 * [onSelect]; tapping the already-selected pill is a no-op (we
 * don't fire `onSelect` for `onCheckedChange(false)`, since
 * "deselecting the active tab" is not a valid state). Selection
 * state is hoisted via [selectedValue]; the composable does not
 * internally re-render until the caller updates that value.
 *
 * Each item carries its [PillTab.iconName] as a leading icon. The
 * `filled` axis of [NubecitaIcon] tracks selection so the selected
 * pill's icon reads as the active state.
 *
 * Equal-width pills: every item gets `weight = 1f` so the row
 * distributes available width evenly. The fixed three-item set never
 * overflows; the required `overflowIndicator` slot is intentionally
 * empty.
 *
 * @param tabs Ordered list of pill configurations. Display order is
 *   the iteration order (left-to-right in LTR).
 * @param selectedValue The currently-active pill's [PillTab.value].
 *   When [selectedValue] does not match any `tabs[i].value` (e.g.
 *   during a transient mismatch while caller state catches up), no
 *   pill renders as selected — the component never crashes on stale
 *   input.
 * @param onSelect Invoked when the user taps an unselected pill.
 *   Receives the tapped pill's [PillTab.value]. NOT invoked when the
 *   user taps the already-selected pill.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
public fun <T> ProfilePillTabs(
    tabs: ImmutableList<PillTab<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    ButtonGroup(
        // The 3 profile tabs always fit; the overflow slot is required
        // by the API surface but is unreachable in this configuration.
        overflowIndicator = {},
        modifier = modifier.padding(horizontal = ButtonGroupHorizontalPadding),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.value == selectedValue
            toggleableItem(
                checked = isSelected,
                label = tab.label,
                onCheckedChange = { newChecked ->
                    // Tapping the selected pill fires onCheckedChange(false)
                    // which we ignore — there is no "no tab selected"
                    // state. Tapping an unselected pill fires
                    // onCheckedChange(true) which delegates to onSelect.
                    if (newChecked) onSelect(tab.value)
                },
                icon = {
                    NubecitaIcon(
                        name = tab.iconName,
                        contentDescription = null,
                        filled = isSelected,
                    )
                },
                weight = 1f,
            )
        }
    }
}

private val ButtonGroupHorizontalPadding = 16.dp
