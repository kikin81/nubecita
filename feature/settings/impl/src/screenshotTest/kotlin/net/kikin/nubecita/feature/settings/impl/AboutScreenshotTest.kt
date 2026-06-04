package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private fun aboutState(): AboutState =
    AboutState(
        isLoadingThanks = false,
        thanks =
            persistentListOf(
                ThanksRowUi(
                    did = "did:plc:stavfx",
                    handle = "stavfx.com",
                    displayName = "Stav",
                    avatarUrl = null,
                    blurbRes = R.string.about_thanks_stavfx,
                ),
                ThanksRowUi(
                    did = "did:plc:vmlara",
                    handle = "vmlara.bsky.social",
                    displayName = "V. M. Lara",
                    avatarUrl = null,
                    blurbRes = R.string.about_thanks_vmlara,
                ),
                ThanksRowUi(
                    did = "did:plc:zenos",
                    handle = "zenos00.bsky.social",
                    displayName = null,
                    avatarUrl = null,
                    blurbRes = R.string.about_thanks_zenos,
                ),
            ),
    )

@PreviewTest
@Preview(name = "about-light", showBackground = true, heightDp = 760)
@Preview(name = "about-dark", showBackground = true, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutContentScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            AboutContent(
                state = aboutState(),
                versionLabel = "1.175.1 (1175001)",
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "about-licenses-light", showBackground = true, heightDp = 600)
@Preview(name = "about-licenses-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutLicensesContentScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            AboutLicensesContent(
                rows =
                    persistentListOf(
                        LicenseRowUi("io.coil-kt:coil", "Coil", "Apache-2.0", "https://coil-kt.github.io"),
                        LicenseRowUi("androidx.media3:media3", "Media3", "Apache-2.0", "https://developer.android.com/media/media3"),
                        LicenseRowUi("io.github.kikin81.atproto:models", "atproto-kotlin", "MIT", null),
                        LicenseRowUi("com.google.dagger:hilt-android", "Hilt", "Apache-2.0", "https://dagger.dev/hilt"),
                    ),
                onBack = {},
                onRowClick = {},
            )
        }
    }
}
