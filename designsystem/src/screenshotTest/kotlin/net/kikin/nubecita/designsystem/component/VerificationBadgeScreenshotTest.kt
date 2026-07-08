package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Visual baseline for [VerificationBadge] across both tiers. Guards the glyph mapping
 * (check_circle vs verified rosette), the fixed verified-blue tint, and that
 * [VerifiedBadge.None] renders nothing. Light + dark confirm the tint is a constant
 * platform signal, not a theme-accent derivation.
 */
@PreviewTest
@Preview(name = "verification-badge-light", showBackground = true)
@Preview(name = "verification-badge-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun VerificationBadgeScreenshot() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // None renders nothing (no visible glyph, no layout).
        VerificationBadge(badge = VerifiedBadge.None)
        VerificationBadge(badge = VerifiedBadge.Verified)
        VerificationBadge(badge = VerifiedBadge.TrustedVerifier)
        // Larger size as used by the profile header.
        VerificationBadge(badge = VerifiedBadge.Verified, size = 24.dp)
        VerificationBadge(badge = VerifiedBadge.TrustedVerifier, size = 24.dp)
    }
}
