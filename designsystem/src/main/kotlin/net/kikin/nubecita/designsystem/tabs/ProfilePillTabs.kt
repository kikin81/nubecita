package net.kikin.nubecita.designsystem.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Visual + semantic configuration for a single pill in [ProfilePillTabs].
 *
 * Generic over [T] so consumers pass typed values (e.g. a per-screen
 * `ProfileTab` enum) and the `onSelect` callback fires with the same
 * type — no string-keyed indirection, no opaque identifiers. The
 * design system declares only the shape; consumers own the enum.
 */
public data class PillTab<T>(
    val value: T,
    val label: String,
    val iconName: NubecitaIconName,
)

/**
 * Pill-shaped tab row used on the profile screen (Posts / Replies /
 * Media). Each pill is 36 dp tall. The active tab fills with the
 * theme's `primary` color and its icon renders with the `FILL`
 * variable axis at 1; inactive tabs render transparent with
 * `onSurface` content color and `FILL = 0`.
 *
 * Implemented as a thin wrapper over [PrimaryTabRow] — the indicator
 * slot is suppressed (`indicator = {}`) and the pill background is
 * painted directly via the selected tab's [Modifier.background] gated
 * on `isSelected`. The bottom divider is suppressed so the row reads
 * as freestanding pill chrome rather than M3's default underline-tab
 * pattern. See the inline comment in the implementation for the
 * z-order rationale (M3 1.5 draws the indicator slot on top of tabs).
 *
 * @param tabs Ordered list of pill configurations. Display order is
 *   the iteration order (left-to-right in LTR).
 * @param selectedValue The currently-active pill's [PillTab.value].
 *   When [selectedValue] does not match any `tabs[i].value` (e.g.
 *   during a transient mismatch while caller state catches up), the
 *   first tab is rendered as selected as a graceful fallback — the
 *   component never crashes on stale input.
 * @param onSelect Invoked when the user taps a pill. Receives the
 *   tapped pill's [PillTab.value]. State hoisting is preserved —
 *   the composable does not internally re-render with a new
 *   selection until the caller updates [selectedValue].
 */
@Composable
public fun <T> ProfilePillTabs(
    tabs: List<PillTab<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)

    // M3 1.5 draws the indicator slot ON TOP of the tabs (see
    // TabRow.kt TabRowImpl: tabs placed first, indicator placed last —
    // last-placed wins z-order). A pill background drawn through the
    // indicator slot would obscure the selected tab's icon + label.
    // Instead, suppress the indicator slot entirely and paint the pill
    // as the Tab's own Modifier.background, gated on `isSelected`.
    // Tradeoff: no slide animation between tabs (the pill snaps). The
    // M3 expressive look is preserved; a sliding indicator can be a
    // future polish bead if design wants it.
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = {},
        // Suppress the default HorizontalDivider — the pills are
        // freestanding chrome, not an M3 underline-tab row.
        divider = {},
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val pillModifier =
                if (isSelected) {
                    Modifier
                        .height(PillHeight)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.height(PillHeight)
                }
            Tab(
                selected = isSelected,
                onClick = { onSelect(tab.value) },
                modifier = pillModifier,
                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = PillHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PillIconLabelGap),
                ) {
                    NubecitaIcon(
                        name = tab.iconName,
                        contentDescription = null,
                        filled = isSelected,
                        tint = LocalContentColor.current,
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private val PillHeight = 36.dp
private val PillHorizontalPadding = 12.dp
private val PillIconLabelGap = 6.dp
