package net.kikin.nubecita.designsystem.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
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
 * Single-selection pill row used on the profile screen (Posts / Replies /
 * Media, plus an own-profile-only Likes pill) and the Chats screen
 * (Messages / Requests). Built as a [FlowRow] of M3 Expressive
 * [ToggleButton]s, every pill sized to one uniform width — the widest
 * pill's — and the connected group centered on each line.
 *
 * **Why uniform-width via a measure pass, not a weighted
 * [androidx.compose.material3.ButtonGroup] or bare content-sizing.**
 * A weighted `ButtonGroup` gave every pill `weight = 1f` (equal share of the
 * row); with four pills and longer locales (es "Publicaciones" / "Multimedia",
 * pt "Publicações") that share is narrower than the label, so labels truncated
 * ("Replie", "Media" cut off). Bare content-sizing (each pill its own width)
 * fixes truncation but reads unevenly — short pills cluster to one side, and a
 * pill that wraps alone onto a second line looks lost. So instead a
 * [SubcomposeLayout] measures every pill at its natural width, takes the widest,
 * and renders the [FlowRow] with every pill pinned to that width. The widest
 * label defines the width, so **nothing ever truncates**; all pills are the same
 * even size; and a lone wrapped pill matches the rest instead of stretching. The
 * measure pass is cheap (2–4 pills). Rows still wrap when the uniform pills don't
 * all fit, and [Alignment.CenterHorizontally] centers each line.
 *
 * Single-selection semantics over [ToggleButton]'s boolean toggle: each
 * pill only forwards `onCheckedChange(true)` to [onSelect]; tapping the
 * already-selected pill is a no-op (there is no "no tab selected"
 * state). Selection is hoisted: [selectedValue] is the single source of
 * truth for which pill is active, and the row is a pure projection of it.
 *
 * Pills are joined into one connected control via
 * [ButtonGroupDefaults.connectedLeadingButtonShapes] /
 * [ButtonGroupDefaults.connectedMiddleButtonShapes] /
 * [ButtonGroupDefaults.connectedTrailingButtonShapes] keyed on **list**
 * position (first / middle / last) and the tight
 * [ButtonGroupDefaults.ConnectedSpaceBetween] horizontal spacing, so the
 * row reads as one segmented group with rounded outer corners — matching
 * the Material 3 `SingleSelectConnectedButtonGroupSample`. Shapes follow
 * list position, not visual-row position, so on wrap the last pill of the
 * first visual row keeps its middle (flat-outer) shape and the trailing
 * rounding lands on the list-final pill on the second row — the sample's
 * own wrap behavior. This is a deliberate tradeoff for the connected look;
 * it only shows on the narrow-width four-pill wrap.
 *
 * Each pill carries its [PillTab.iconName] as a leading icon; the
 * `filled` axis of [NubecitaIcon] tracks selection so the active pill's
 * icon reads as selected. A positive [PillTab.badgeCount] renders as an
 * M3 [Badge] over the icon.
 *
 * @param tabs Ordered list of pill configurations. Display order is
 *   the iteration order (left-to-right in LTR, wrapping top-to-bottom).
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
    SubcomposeLayout(modifier = modifier.padding(horizontal = 8.dp)) { constraints ->
        // Pass 1: measure every pill at its natural content width and take the
        // widest, so all pills can render at that one "baseline" width. This makes
        // the row read as even, and a pill that wraps alone onto a second line
        // matches the others instead of taking the whole line.
        val widestPx =
            subcompose(PillSlot.Measure) {
                tabs.forEachIndexed { index, tab ->
                    PillToggle(tab, index, tabs.lastIndex, tab.value == selectedValue, onSelect)
                }
            }.maxOfOrNull { it.measure(Constraints()).width } ?: 0

        // Pass 2: the real wrapping row, every pill pinned to the widest width and
        // the connected group centered on each line.
        val uniformWidth = Modifier.width(widestPx.toDp())
        val placeable =
            subcompose(PillSlot.Content) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        PillToggle(tab, index, tabs.lastIndex, tab.value == selectedValue, onSelect, uniformWidth)
                    }
                }
            }.first().measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private enum class PillSlot { Measure, Content }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> PillToggle(
    tab: PillTab<T>,
    index: Int,
    lastIndex: Int,
    isSelected: Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        modifier = modifier,
        checked = isSelected,
        // Single-select: tapping the active pill fires onCheckedChange(false),
        // which we ignore; tapping an unselected pill fires (true) → onSelect.
        onCheckedChange = { newChecked -> if (newChecked) onSelect(tab.value) },
        // Connected group shapes by list position (M3 SingleSelectConnectedButtonGroupSample).
        shapes =
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
    ) {
        val badge = tab.badgeCount
        if (badge != null && badge > 0) {
            BadgedBox(badge = { Badge { Text(badgeLabel(badge)) } }) {
                NubecitaIcon(name = tab.iconName, contentDescription = null, filled = isSelected)
            }
        } else {
            NubecitaIcon(name = tab.iconName, contentDescription = null, filled = isSelected)
        }
        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
        Text(tab.label)
    }
}

/** Renders the badge count, capped at "99+" so it never exceeds three glyphs. */
private fun badgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()
