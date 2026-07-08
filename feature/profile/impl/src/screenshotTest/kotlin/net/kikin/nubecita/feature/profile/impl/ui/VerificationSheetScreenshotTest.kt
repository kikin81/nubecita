package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.VerifierUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [VerificationSheetContent] — the body the
 * verification [VerificationSheet] hosts. The `ModalBottomSheet` wrapper
 * itself has no baseline (layoutlib doesn't render it), so these fixtures
 * drive the content directly at a representative sheet width across the
 * loaded / loading / error states and both badge tiers.
 *
 * Regenerate with
 * `./gradlew :feature:profile:impl:updateDebugScreenshotTest`.
 */
private val SAMPLE_VERIFIERS =
    persistentListOf(
        VerifierUi(
            did = "did:plc:nyt",
            handle = "nytimes.com",
            displayName = "The New York Times",
            verifiedAt = Instant.parse("2026-05-01T00:00:00Z"),
        ),
        VerifierUi(
            did = "did:plc:bsky",
            handle = "bsky.app",
            displayName = null,
            verifiedAt = Instant.parse("2026-04-15T00:00:00Z"),
        ),
    )

@Composable
private fun SheetFixture(content: @Composable () -> Unit) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Box(modifier = Modifier.width(360.dp)) { content() }
        }
    }
}

@PreviewTest
@Preview(name = "sheet-verified-loaded-light", showBackground = true)
@Preview(name = "sheet-verified-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VerificationSheetVerifiedLoadedScreenshot() {
    SheetFixture {
        VerificationSheetContent(
            badge = VerifiedBadge.Verified,
            verifiers = SAMPLE_VERIFIERS,
            isLoading = false,
            isError = false,
        )
    }
}

@PreviewTest
@Preview(name = "sheet-trusted-verifier-loaded-light", showBackground = true)
@Preview(name = "sheet-trusted-verifier-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VerificationSheetTrustedVerifierLoadedScreenshot() {
    SheetFixture {
        VerificationSheetContent(
            badge = VerifiedBadge.TrustedVerifier,
            verifiers = SAMPLE_VERIFIERS,
            isLoading = false,
            isError = false,
        )
    }
}

@PreviewTest
@Preview(name = "sheet-loading-light", showBackground = true)
@Preview(name = "sheet-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VerificationSheetLoadingScreenshot() {
    SheetFixture {
        VerificationSheetContent(
            badge = VerifiedBadge.Verified,
            verifiers = persistentListOf(),
            isLoading = true,
            isError = false,
        )
    }
}

@PreviewTest
@Preview(name = "sheet-error-light", showBackground = true)
@Preview(name = "sheet-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VerificationSheetErrorScreenshot() {
    SheetFixture {
        VerificationSheetContent(
            badge = VerifiedBadge.Verified,
            verifiers = persistentListOf(),
            isLoading = false,
            isError = true,
        )
    }
}
