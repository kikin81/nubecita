package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.NubecitaPalette
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Account-verification badge atom.
 *
 * Renders the filled glyph for a given [VerifiedBadge] in the fixed verified-blue
 * ([NubecitaPalette.VerifiedBlue]) and renders **nothing** for [VerifiedBadge.None].
 * It owns its own screen-reader description (read via `stringResource`) so a11y copy stays
 * consistent at every call site (feed post cards, profile header) — deliberately NOT a
 * hoisted parameter, since the badge's meaning is intrinsic and every caller would pass
 * the identical string.
 *
 * The atom is stateless and non-interactive — it never handles taps. Interactive
 * surfaces (e.g. the profile header opening the explanation sheet) wrap it in their own
 * clickable and provide the action semantics.
 */
@Composable
public fun VerificationBadge(
    badge: VerifiedBadge,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val (name, description) =
        when (badge) {
            VerifiedBadge.None -> return
            VerifiedBadge.Verified ->
                NubecitaIconName.CheckCircle to stringResource(R.string.verification_badge_verified_description)
            VerifiedBadge.TrustedVerifier ->
                NubecitaIconName.Verified to stringResource(R.string.verification_badge_trusted_verifier_description)
        }
    NubecitaIcon(
        name = name,
        contentDescription = description,
        filled = true,
        tint = NubecitaPalette.VerifiedBlue,
        modifier = modifier.size(size),
    )
}
