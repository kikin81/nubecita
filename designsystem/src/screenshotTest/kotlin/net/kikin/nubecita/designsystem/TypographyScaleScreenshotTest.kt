package net.kikin.nubecita.designsystem

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.designsystem.preview.TypographyScale

/**
 * Visual baseline for the full typography scale, including the
 * [NubecitaExtendedTypography] entries (`mono`, `displayName`, `handle`)
 * that don't fit the M3 [androidx.compose.material3.Typography] slots.
 *
 * The fixture is the regression net for variable-font axis wiring — a
 * misconfigured Fraunces `SOFT` axis or a missed JetBrains Mono `wght`
 * variation entry shows up as a glyph-metrics drift here before it
 * reaches the profile hero. Real-device verification is still required
 * for the SOFT axis specifically per
 * `feedback_compose_glyph_iteration_workflow.md` (Layoutlib may stub
 * variable axes); this fixture catches everything else.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun TypographyScaleScreenshotPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            TypographyScale()
        }
    }
}
