package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaPalette
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.semanticColors

/**
 * The Nubecita Pro "Supporter" badge — a compact gold pill pairing a
 * medal glyph with a "Supporter" label.
 *
 * Visual language: a solid [semanticColors.supporterAccent] (warm gold)
 * fill carrying dark content ([NubecitaPalette.Neutral10]), mirroring the
 * `onSuccess` / `onWarning` "dark content on a warm fill" pattern. Gold is
 * light in both themes, so fixed-dark content stays high-contrast in light
 * AND dark — the legible, theme-correct choice over gold-on-gold text.
 *
 * Deliberately NEUTRAL: it reads as a patron/supporter marker, not an
 * identity-verification ("verified") signal — hence the `WorkspacePremium`
 * medal glyph rather than the `Verified` checkmark, and the "Supporter"
 * wording rather than "Verified account".
 *
 * # Reuse
 *
 * This is the single shared badge composable. Tier 1 (self-visible, this
 * change) renders it on a Pro user's OWN profile hero. The later Tier 2
 * (networked, public) badge reuses this exact composable — gating lives at
 * the call site, never inside the badge.
 *
 * # Accessibility
 *
 * The "Supporter" label is the badge's sole a11y output: the [Row] merges
 * descendant semantics so TalkBack announces "Supporter" once, and the
 * decorative medal glyph carries a null `contentDescription`.
 */
@Composable
fun SupporterBadge(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.semanticColors.supporterAccent)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WorkspacePremium,
            contentDescription = null,
            filled = true,
            opticalSize = 16.dp,
            tint = NubecitaPalette.Neutral10,
        )
        Text(
            text = stringResource(R.string.supporter_badge_label),
            style = MaterialTheme.typography.labelMedium,
            color = NubecitaPalette.Neutral10,
        )
    }
}
