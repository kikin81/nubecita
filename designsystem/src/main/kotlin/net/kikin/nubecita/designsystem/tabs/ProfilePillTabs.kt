package net.kikin.nubecita.designsystem.tabs

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
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
    /**
     * Optional count rendered as an M3 [Badge] over the pill's icon. `null`
     * (the default) or a non-positive value renders no badge — so existing
     * call sites that omit it are visually unchanged. Counts above 99 render
     * as "99+".
     */
    val badgeCount: Int? = null,
)

/**
 * Single-selection segmented control used on the profile screen
 * (Posts / Replies / Media, plus an own-profile-only Likes pill —
 * three or four items). Built on M3 Expressive's [ButtonGroup]
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
 * distributes available width evenly. Because every item is weighted,
 * [ButtonGroup] sizes them to their proportional share rather than
 * spilling any into the overflow menu — so no pill is ever dropped,
 * for either the three-item (other user) or four-item (own profile,
 * with Likes) row. The required `overflowIndicator` slot is therefore
 * intentionally empty. On narrow widths or long locales the pill
 * *labels* truncate (they never disappear); tabs stay fully tappable.
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
        // All items are weighted (weight = 1f below), so ButtonGroup sizes
        // them to their share and never overflows — the overflow slot is
        // required by the API but unreachable here, for 3 or 4 pills alike.
        overflowIndicator = {},
        expandedRatio = 0.025f,
        modifier = modifier.padding(horizontal = 8.dp),
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
                    val badge = tab.badgeCount
                    if (badge != null && badge > 0) {
                        BadgedBox(badge = { Badge { Text(badgeLabel(badge)) } }) {
                            NubecitaIcon(
                                name = tab.iconName,
                                contentDescription = null,
                                filled = isSelected,
                            )
                        }
                    } else {
                        NubecitaIcon(
                            name = tab.iconName,
                            contentDescription = null,
                            filled = isSelected,
                        )
                    }
                },
                weight = 1f,
            )
        }
    }
}

/** Renders the badge count, capped at "99+" so it never exceeds three glyphs. */
private fun badgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()
